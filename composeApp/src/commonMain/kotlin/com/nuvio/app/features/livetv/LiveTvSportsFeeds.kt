package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** The days the Sports section asks for: yesterday to the day after tomorrow (UTC). */
internal data class LiveTvSportsWindow(val days: List<String>) {
    val first: String get() = days.first()
    val last: String get() = days.last()

    fun contains(millis: Long): Boolean = sportsDayKey(millis) in first..last

    companion object {
        fun around(nowMillis: Long): LiveTvSportsWindow =
            LiveTvSportsWindow((-1..2).map { sportsDayKey(nowMillis + it * DAY_MILLIS) })
    }
}

/**
 * Reads every kind of [LiveTvSportsFeed] into [LiveTvSportsEvent]s. All sources are public and
 * keyless (HockeyTech's keys are the public ones its leagues' own sites use).
 */
internal object LiveTvSportsFeeds {
    private val log = Logger.withTag("LiveTvSports")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val headers = mapOf("Accept" to "application/json", "User-Agent" to "Nuvio/1.0")

    private const val ESPN = "https://site.api.espn.com/apis/site/v2/sports/"
    private const val CRICKET_PANEL = "https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates="
    private const val HOCKEYTECH = "https://lscluster.hockeytech.com/feed/index.php?feed=modulekit&fmt=json&lang=en"
    private const val MLB = "https://statsapi.mlb.com/api/v1/"
    private const val FIXTURES = "https://sporting-events.org/data/"
    private val cricketFormats = setOf("Test", "ODI", "T20I")

    private val hockeyTechKeys = mapOf(
        "ahl" to "50c2cd9b5e18e390", "echl" to "2c2b89ea7345cae8", "pwhl" to "446521baf8c38984",
        "ohl" to "f1aa699db3d81487", "whl" to "f1aa699db3d81487", "lhjmq" to "f1aa699db3d81487",
        "ushl" to "e828f89b243dc43f",
    )

    // Slow-changing answers (cricket seasons, fixture files) are kept for a while.
    private class Cached(val atMillis: Long, val body: String)
    private val cache = mutableMapOf<String, Cached>()
    private val cacheMutex = Mutex()

    private suspend fun get(url: String): String = httpGetTextWithHeaders(url, headers)

    private suspend fun getCached(url: String, maxAgeMillis: Long): String {
        val now = Clock.System.now().toEpochMilliseconds()
        cacheMutex.withLock { cache[url]?.takeIf { now - it.atMillis < maxAgeMillis }?.let { return it.body } }
        val body = get(url)
        cacheMutex.withLock { cache[url] = Cached(now, body) }
        return body
    }

    private fun parse(raw: String): JsonObject? = json.parseToJsonElement(raw) as? JsonObject

    suspend fun fetch(league: LiveTvSportsLeague, window: LiveTvSportsWindow): List<LiveTvSportsEvent> =
        when (val feed = league.feed) {
            is LiveTvSportsFeed.Espn -> espnScoreboard(league, feed, window)
            is LiveTvSportsFeed.CricketSeason -> cricketSeason(league, feed.leagueId, window)
            LiveTvSportsFeed.CricketPanel -> cricketPanel(league, window)
            is LiveTvSportsFeed.HockeyTech -> hockeyTech(league, feed.code, window)
            is LiveTvSportsFeed.Milb -> milb(league, feed.sportId, window)
            is LiveTvSportsFeed.Fixtures -> fixtures(league, feed.slug)
        }

    // ------------------------------------------------------------------------------- ESPN

    /**
     * ESPN rejects date ranges ("dates=YYYYMMDD-YYYYMMDD" answers 400), so each day is asked on
     * its own, in parallel. A league fails only if every day fails.
     */
    private suspend fun espnScoreboard(
        league: LiveTvSportsLeague,
        feed: LiveTvSportsFeed.Espn,
        window: LiveTvSportsWindow,
    ): List<LiveTvSportsEvent> = coroutineScope {
        val results = window.days.map { day ->
            async {
                runCatching {
                    val root = parse(get("$ESPN${feed.path}/scoreboard?dates=$day${feed.query}"))
                    root?.arr("events").orEmpty().mapNotNull { parseEspnEvent(it as? JsonObject ?: return@mapNotNull null, league) }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    log.d { "ESPN ${league.id} $day failed: ${error.message}" }
                }
            }
        }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrNull().orEmpty() }
    }

    internal fun parseEspnEvent(event: JsonObject, league: LiveTvSportsLeague, fullNames: Boolean = false): LiveTvSportsEvent? {
        val id = event.str("id") ?: return null
        val startMillis = event.str("date")?.let(::parseSportsInstant) ?: return null
        val competitions = event.arr("competitions").mapNotNull { it as? JsonObject }
        val statusType = (event.obj("status") ?: competitions.firstOrNull()?.obj("status"))?.obj("type")
        val state = when (statusType?.str("state")) {
            "in" -> LiveTvSportsState.Live
            "post" -> LiveTvSportsState.Finished
            else -> LiveTvSportsState.Upcoming
        }
        // Fight cards list every bout as a competition; the main event comes last. Races,
        // tournaments and golf have a whole field: those are shown by their name only.
        val competition = when {
            league.sport == LiveTvSport.Fighting -> competitions.lastOrNull()
            league.isEvent -> null
            else -> competitions.firstOrNull()
        }
        val competitors = competition?.arr("competitors").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val team = item.obj("team")
            val athlete = item.obj("athlete")
            val name = team?.str("displayName") ?: athlete?.str("displayName") ?: return@mapNotNull null
            LiveTvSportsCompetitor(
                name = name,
                shortName = if (fullNames) name else team?.str("shortDisplayName") ?: athlete?.str("shortName") ?: name,
                logoUrl = team?.str("logo") ?: athlete?.obj("flag")?.str("href"),
                score = item.str("score"),
                isHome = item.str("homeAway") == "home",
                isWinner = (item["winner"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        }.sortedBy { if (it.isHome) 1 else 0 }
        val broadcasts = buildList {
            competitions.forEach { comp ->
                comp.arr("broadcasts").forEach { broadcast ->
                    (broadcast as? JsonObject)?.arr("names")?.forEach { name ->
                        (name as? JsonPrimitive)?.contentOrNull?.let(::add)
                    }
                }
                comp.arr("geoBroadcasts").forEach { broadcast ->
                    (broadcast as? JsonObject)?.obj("media")?.str("shortName")?.let(::add)
                }
            }
        }.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase)
        return LiveTvSportsEvent(
            id = id,
            league = league,
            name = event.str("name") ?: event.str("shortName") ?: competitors.joinToString(" vs ") { it.name },
            startMillis = startMillis,
            state = state,
            statusDetail = statusType?.str("shortDetail") ?: statusType?.str("detail").orEmpty(),
            competitors = competitors,
            broadcasts = broadcasts,
        )
    }

    // ------------------------------------------------------------------------------- Cricket

    /** A franchise league: the default answer plus the season(s) the window falls in. */
    private suspend fun cricketSeason(
        league: LiveTvSportsLeague,
        leagueId: String,
        window: LiveTvSportsWindow,
    ): List<LiveTvSportsEvent> {
        val years = setOf(window.first.take(4), window.last.take(4))
        val urls = listOf("${ESPN}cricket/$leagueId/scoreboard") + years.map { "${ESPN}cricket/$leagueId/scoreboard?season=$it" }
        val results = coroutineScope {
            urls.map { url -> async { runCatching { getCached(url, 10 * MINUTE_MILLIS) } } }.awaitAll()
        }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        return results.mapNotNull { it.getOrNull() }
            .flatMap { raw -> parse(raw)?.arr("events").orEmpty() }
            .mapNotNull { parseEspnEvent(it as? JsonObject ?: return@mapNotNull null, league, fullNames = true) }
            .filter { window.contains(it.startMillis) }
            .distinctBy { it.id }
    }

    /** International Tests, ODIs and T20Is from ESPN's daily score panel. */
    private suspend fun cricketPanel(league: LiveTvSportsLeague, window: LiveTvSportsWindow): List<LiveTvSportsEvent> {
        val results = coroutineScope {
            window.days.map { day -> async { runCatching { getCached(CRICKET_PANEL + day, 3 * MINUTE_MILLIS) } } }.awaitAll()
        }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        return results.mapNotNull { it.getOrNull() }
            .flatMap { raw -> parse(raw)?.arr("scores").orEmpty() }
            .flatMap { block -> (block as? JsonObject)?.arr("events").orEmpty() }
            .mapNotNull { element ->
                val event = element as? JsonObject ?: return@mapNotNull null
                val format = event.arr("competitions").firstObj()?.obj("class")?.str("generalClassCard")
                if (format !in cricketFormats) return@mapNotNull null
                parseEspnEvent(event, league, fullNames = true)
            }
            .distinctBy { it.id }
    }

    // ------------------------------------------------------------------------------- HockeyTech

    /** The league's score bar: a week either side of today, with live scores. */
    private suspend fun hockeyTech(league: LiveTvSportsLeague, code: String, window: LiveTvSportsWindow): List<LiveTvSportsEvent> {
        val key = hockeyTechKeys[code] ?: return emptyList()
        val raw = get("$HOCKEYTECH&view=scorebar&client_code=$code&key=$key&numberofdaysback=7&numberofdaysahead=7")
        // Some answers come wrapped in brackets or parentheses (JSONP style).
        val body = raw.trim().removePrefix("(").removeSuffix(")")
        val bar = parse(body)?.obj("SiteKit")?.arr("Scorebar").orEmpty()
        return bar.mapNotNull { element ->
            val game = element as? JsonObject ?: return@mapNotNull null
            hockeyTechEvent(league, code, game)?.takeIf { window.contains(it.startMillis) }
        }
    }

    private fun hockeyTechEvent(league: LiveTvSportsLeague, code: String, game: JsonObject): LiveTvSportsEvent? {
        val id = game.str("ID") ?: return null
        val start = game.str("GameDateISO8601")?.let(::parseSportsInstant) ?: return null
        val raw = game.str("GameStatusStringLong") ?: game.str("GameStatusString").orEmpty()
        val low = raw.lowercase()
        val statusCode = game.str("GameStatus")
        val state = when {
            low.startsWith("final") || statusCode == "4" -> LiveTvSportsState.Finished
            low.contains("postpon") || low.contains("cancel") || low == "ppd" -> LiveTvSportsState.Upcoming
            statusCode == "2" || statusCode == "3" -> LiveTvSportsState.Live
            else -> LiveTvSportsState.Upcoming
        }
        fun side(prefix: String, home: Boolean, otherGoals: Int?): LiveTvSportsCompetitor? {
            val city = game.str("${prefix}City").orEmpty()
            val nick = game.str("${prefix}Nickname").orEmpty()
            val name = listOf(city, nick).filter(String::isNotBlank).joinToString(" ")
                .ifBlank { game.str("${prefix}LongName") ?: return null }
            val goals = game.str("${prefix}Goals")
            val teamId = game.str("${prefix}ID")
            val logo = game.str("${prefix}Logo")?.takeIf { it.startsWith("http") }?.replace("http://", "https://")
                ?: teamId?.let { "https://assets.leaguestat.com/$code/logos/$it.png" }
            return LiveTvSportsCompetitor(
                name = name,
                shortName = nick.ifBlank { name },
                logoUrl = logo,
                score = goals,
                isHome = home,
                isWinner = state == LiveTvSportsState.Finished && goals?.toIntOrNull() != null && otherGoals != null &&
                    goals.toInt() > otherGoals,
            )
        }
        val homeGoals = game.str("HomeGoals")?.toIntOrNull()
        val awayGoals = game.str("VisitorGoals")?.toIntOrNull()
        val away = side("Visitor", home = false, otherGoals = homeGoals) ?: return null
        val home = side("Home", home = true, otherGoals = awayGoals) ?: return null
        return LiveTvSportsEvent(
            id = "$code-$id",
            league = league,
            name = "${away.name} at ${home.name}",
            startMillis = start,
            state = state,
            statusDetail = if (state == LiveTvSportsState.Upcoming) "" else raw,
            competitors = listOf(away, home),
            broadcasts = emptyList(),
        )
    }

    // ------------------------------------------------------------------------------- Minor League Baseball

    private suspend fun milb(league: LiveTvSportsLeague, sportId: Int, window: LiveTvSportsWindow): List<LiveTvSportsEvent> {
        fun dashed(day: String) = "${day.take(4)}-${day.substring(4, 6)}-${day.substring(6, 8)}"
        val url = "${MLB}schedule?sportId=$sportId&startDate=${dashed(window.first)}&endDate=${dashed(window.last)}" +
            "&hydrate=team,linescore,broadcasts(all)"
        val root = parse(get(url)) ?: return emptyList()
        return root.arr("dates").flatMap { (it as? JsonObject)?.arr("games").orEmpty() }
            .mapNotNull { milbEvent(league, it as? JsonObject ?: return@mapNotNull null) }
    }

    private fun milbEvent(league: LiveTvSportsLeague, game: JsonObject): LiveTvSportsEvent? {
        val id = game.str("gamePk") ?: return null
        val start = game.str("gameDate")?.let(::parseSportsInstant) ?: return null
        val status = game.obj("status")
        val detailed = status?.str("detailedState").orEmpty()
        val postponed = Regex("Postponed|Cancelled|Canceled|Suspended", RegexOption.IGNORE_CASE).containsMatchIn(detailed)
        val state = when {
            postponed -> LiveTvSportsState.Upcoming
            status?.str("abstractGameState") == "Final" -> LiveTvSportsState.Finished
            status?.str("abstractGameState") == "Live" -> LiveTvSportsState.Live
            else -> LiveTvSportsState.Upcoming
        }
        val line = game.obj("linescore")
        val inning = listOfNotNull(line?.str("inningState") ?: line?.str("inningHalf"), line?.str("currentInningOrdinal"))
            .joinToString(" ")
        val teams = game.obj("teams") ?: return null
        fun side(key: String): LiveTvSportsCompetitor? {
            val entry = teams.obj(key) ?: return null
            val team = entry.obj("team") ?: return null
            val teamId = team.str("id") ?: return null
            val name = team.str("name") ?: return null
            return LiveTvSportsCompetitor(
                name = name,
                shortName = team.str("teamName") ?: name,
                logoUrl = "https://www.mlbstatic.com/team-logos/$teamId.svg",
                score = entry.str("score"),
                isHome = key == "home",
                isWinner = entry.str("isWinner") == "true",
            )
        }
        val away = side("away") ?: return null
        val home = side("home") ?: return null
        val broadcasts = game.arr("broadcasts").mapNotNull { element ->
            val broadcast = element as? JsonObject ?: return@mapNotNull null
            broadcast.str("name")?.takeIf { broadcast.str("type")?.equals("TV", ignoreCase = true) != false }
        }.distinct()
        return LiveTvSportsEvent(
            id = "milb-$id",
            league = league,
            name = "${away.name} at ${home.name}",
            startMillis = start,
            state = state,
            statusDetail = when {
                postponed -> detailed
                state == LiveTvSportsState.Live -> inning
                state == LiveTvSportsState.Finished -> "Final"
                else -> ""
            },
            competitors = listOf(away, home),
            broadcasts = broadcasts,
        )
    }

    // ------------------------------------------------------------------------------- sporting-events.org

    /** Event dates (some with a time) for sports ESPN doesn't cover. The file changes rarely. */
    private suspend fun fixtures(league: LiveTvSportsLeague, slug: String): List<LiveTvSportsEvent> {
        val root = parse(getCached("$FIXTURES$slug.json", 30 * MINUTE_MILLIS)) ?: return emptyList()
        val zone = TimeZone.currentSystemDefault()
        return root.arr("events").mapIndexedNotNull { index, element ->
            val event = element as? JsonObject ?: return@mapIndexedNotNull null
            val date = event.str("date_utc") ?: return@mapIndexedNotNull null
            val name = event.str("fixture") ?: return@mapIndexedNotNull null
            val time = event.str("time_utc")
            val start = if (time != null) {
                parseSportsInstant("${date}T${time.take(5)}Z")
            } else {
                // A date only: that day on the device's own calendar.
                runCatching { LocalDate.parse(date).atStartOfDayIn(zone).toEpochMilliseconds() }.getOrNull()
            } ?: return@mapIndexedNotNull null
            val home = event.str("home_team")
            val away = event.str("away_team")
            LiveTvSportsEvent(
                id = "fx:$slug:${event.str("match") ?: index.toString()}:$date",
                league = league,
                name = if (home != null && away != null) "$home v $away" else name,
                startMillis = start,
                state = if (event.str("status")?.contains("finish", ignoreCase = true) == true) {
                    LiveTvSportsState.Finished
                } else {
                    LiveTvSportsState.Upcoming
                },
                statusDetail = listOfNotNull(event.str("city"), event.str("country")).joinToString(", "),
                competitors = emptyList(),
                broadcasts = listOfNotNull(event.str("broadcast")),
                hasTime = time != null,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

internal const val MINUTE_MILLIS = 60_000L
internal const val HOUR_MILLIS = 60L * MINUTE_MILLIS
internal const val DAY_MILLIS = 24L * HOUR_MILLIS

/** "YYYYMMDD" of [millis] in UTC (the day ESPN files a game under). */
internal fun sportsDayKey(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}"
}

private val sportsInstantRegex =
    Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(Z|[+-]\d{2}:?\d{2})?""")

/** ISO date-times as the feeds write them: with or without seconds, "Z" or an offset ("-05:00"). */
internal fun parseSportsInstant(value: String): Long? {
    val match = sportsInstantRegex.find(value) ?: return null
    val g = match.groupValues
    val local = runCatching {
        LocalDateTime(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0)
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()
    }.getOrNull() ?: return null
    val zone = g[7]
    if (zone.isEmpty() || zone == "Z") return local
    val digits = zone.drop(1).replace(":", "")
    val offsetMinutes = (digits.take(2).toIntOrNull() ?: 0) * 60 + (digits.drop(2).toIntOrNull() ?: 0)
    val sign = if (zone.first() == '-') -1 else 1
    return local - sign * offsetMinutes * MINUTE_MILLIS
}

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()
private fun List<JsonElement>.firstObj() = firstOrNull() as? JsonObject
