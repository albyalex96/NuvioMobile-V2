package com.nuvio.app.features.livetv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GolfCourse
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsHockey
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsRugby
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.ui.graphics.vector.ImageVector

/** The sports the leagues are grouped into. */
enum class LiveTvSport(val icon: ImageVector) {
    Soccer(Icons.Rounded.SportsSoccer),
    Basketball(Icons.Rounded.SportsBasketball),
    AmericanFootball(Icons.Rounded.SportsFootball),
    Hockey(Icons.Rounded.SportsHockey),
    Baseball(Icons.Rounded.SportsBaseball),
    Rugby(Icons.Rounded.SportsRugby),
    Cricket(Icons.Rounded.SportsCricket),
    AussieRules(Icons.Rounded.SportsRugby),
    Fighting(Icons.Rounded.SportsMma),
    Racing(Icons.Rounded.SportsMotorsports),
    Golf(Icons.Rounded.GolfCourse),
    Tennis(Icons.Rounded.SportsTennis),
    Volleyball(Icons.Rounded.SportsVolleyball),
    Lacrosse(Icons.Rounded.EmojiEvents),
    Other(Icons.Rounded.EmojiEvents),
}

/** Where a league's events come from. Every source is public and needs no key. */
sealed interface LiveTvSportsFeed {
    /** ESPN's scoreboard, one request per day. [query] is appended (college: every game). */
    data class Espn(val path: String, val query: String = "") : LiveTvSportsFeed

    /** A franchise cricket league: ESPN only answers a whole season at a time. */
    data class CricketSeason(val leagueId: String) : LiveTvSportsFeed

    /** International cricket (Tests, ODIs, T20Is) from ESPN's daily score panel. */
    data object CricketPanel : LiveTvSportsFeed

    /** AHL, ECHL, PWHL and junior hockey from HockeyTech, the stats system their own sites use. */
    data class HockeyTech(val code: String) : LiveTvSportsFeed

    /** Minor League Baseball from MLB's own stats service ([sportId] is the level). */
    data class Milb(val sportId: Int) : LiveTvSportsFeed

    /** Event dates from sporting-events.org, for sports ESPN doesn't cover. */
    data class Fixtures(val slug: String) : LiveTvSportsFeed
}

/**
 * One competition the Sports section can show.
 *  - [isEvent]: one event with many competitors (a race, a tournament, a fight card) instead of two teams.
 *  - [defaultEnabled]: shown until the user picks their own leagues.
 */
data class LiveTvSportsLeague(
    val id: String,
    val label: String,
    val sport: LiveTvSport,
    val feed: LiveTvSportsFeed,
    val isEvent: Boolean = false,
    val defaultEnabled: Boolean = false,
) {
    val icon: ImageVector get() = sport.icon
}

private fun espn(
    path: String,
    label: String,
    sport: LiveTvSport,
    isEvent: Boolean = false,
    on: Boolean = false,
    query: String = "",
) = LiveTvSportsLeague(path, label, sport, LiveTvSportsFeed.Espn(path, query), isEvent, on)

private fun fixtures(slug: String, label: String, sport: LiveTvSport, on: Boolean = false) =
    LiveTvSportsLeague("fixtures/$slug", label, sport, LiveTvSportsFeed.Fixtures(slug), isEvent = true, defaultEnabled = on)

internal val liveTvSportsLeagues: List<LiveTvSportsLeague> = listOf(
    // ---- Soccer: Italy first, then Europe, then the rest of the world
    espn("soccer/ita.1", "Serie A", LiveTvSport.Soccer, on = true),
    espn("soccer/ita.2", "Serie B", LiveTvSport.Soccer, on = true),
    espn("soccer/ita.coppa_italia", "Coppa Italia", LiveTvSport.Soccer, on = true),
    espn("soccer/ita.super_cup", "Supercoppa Italiana", LiveTvSport.Soccer, on = true),
    espn("soccer/uefa.champions", "Champions League", LiveTvSport.Soccer, on = true),
    espn("soccer/uefa.europa", "Europa League", LiveTvSport.Soccer, on = true),
    espn("soccer/uefa.europa.conf", "Conference League", LiveTvSport.Soccer, on = true),
    espn("soccer/eng.1", "Premier League", LiveTvSport.Soccer, on = true),
    espn("soccer/esp.1", "LaLiga", LiveTvSport.Soccer, on = true),
    espn("soccer/ger.1", "Bundesliga", LiveTvSport.Soccer, on = true),
    espn("soccer/fra.1", "Ligue 1", LiveTvSport.Soccer, on = true),
    espn("soccer/eng.2", "Championship", LiveTvSport.Soccer),
    espn("soccer/eng.3", "League One", LiveTvSport.Soccer),
    espn("soccer/eng.4", "League Two", LiveTvSport.Soccer),
    espn("soccer/eng.fa", "FA Cup", LiveTvSport.Soccer),
    espn("soccer/eng.league_cup", "Carabao Cup", LiveTvSport.Soccer),
    espn("soccer/sco.1", "Scottish Premiership", LiveTvSport.Soccer),
    espn("soccer/eng.w.1", "Women's Super League", LiveTvSport.Soccer),
    espn("soccer/fifa.world", "World Cup", LiveTvSport.Soccer, on = true),
    espn("soccer/uefa.euro", "European Championship", LiveTvSport.Soccer, on = true),
    espn("soccer/uefa.nations", "Nations League", LiveTvSport.Soccer, on = true),
    espn("soccer/fifa.worldq.uefa", "World Cup Qualifying (Europe)", LiveTvSport.Soccer, on = true),
    espn("soccer/uefa.euroq", "Euro Qualifying", LiveTvSport.Soccer, on = true),
    espn("soccer/fifa.friendly", "International Friendlies", LiveTvSport.Soccer),
    espn("soccer/usa.1", "MLS", LiveTvSport.Soccer),
    espn("soccer/usa.nwsl", "NWSL", LiveTvSport.Soccer),
    espn("soccer/mex.1", "Liga MX", LiveTvSport.Soccer),
    espn("soccer/idn.1", "Liga 1 Indonesia", LiveTvSport.Soccer, on = true),
    espn("soccer/fifa.worldq.concacaf", "World Cup Qualifying (CONCACAF)", LiveTvSport.Soccer),
    espn("soccer/concacaf.nations.league", "CONCACAF Nations League", LiveTvSport.Soccer),
    espn("soccer/concacaf.gold", "Gold Cup", LiveTvSport.Soccer),
    espn("soccer/conmebol.america", "Copa América", LiveTvSport.Soccer),
    espn("soccer/usa.ncaa.m.1", "Men's College Soccer", LiveTvSport.Soccer),
    espn("soccer/usa.ncaa.w.1", "Women's College Soccer", LiveTvSport.Soccer),
    // ---- Basketball
    espn("basketball/nba", "NBA", LiveTvSport.Basketball, on = true),
    espn("basketball/wnba", "WNBA", LiveTvSport.Basketball),
    espn("basketball/nba-development", "NBA G League", LiveTvSport.Basketball),
    espn("basketball/nbl", "NBL (Australia)", LiveTvSport.Basketball),
    espn("basketball/mens-college-basketball", "Men's College Basketball", LiveTvSport.Basketball, query = "&groups=50"),
    espn("basketball/womens-college-basketball", "Women's College Basketball", LiveTvSport.Basketball, query = "&groups=50"),
    // ---- American football
    espn("football/nfl", "NFL", LiveTvSport.AmericanFootball, on = true),
    espn("football/college-football", "College Football", LiveTvSport.AmericanFootball, query = "&groups=80"),
    espn("football/cfl", "CFL", LiveTvSport.AmericanFootball),
    espn("football/ufl", "UFL", LiveTvSport.AmericanFootball),
    // ---- Hockey
    espn("hockey/nhl", "NHL", LiveTvSport.Hockey, on = true),
    LiveTvSportsLeague("hockeytech/ahl", "AHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("ahl")),
    LiveTvSportsLeague("hockeytech/pwhl", "PWHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("pwhl")),
    LiveTvSportsLeague("hockeytech/echl", "ECHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("echl")),
    LiveTvSportsLeague("hockeytech/ohl", "OHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("ohl")),
    LiveTvSportsLeague("hockeytech/whl", "WHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("whl")),
    LiveTvSportsLeague("hockeytech/lhjmq", "QMJHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("lhjmq")),
    LiveTvSportsLeague("hockeytech/ushl", "USHL", LiveTvSport.Hockey, LiveTvSportsFeed.HockeyTech("ushl")),
    espn("hockey/mens-college-hockey", "Men's College Hockey", LiveTvSport.Hockey),
    espn("hockey/womens-college-hockey", "Women's College Hockey", LiveTvSport.Hockey),
    espn("hockey/olympics-mens-ice-hockey", "Olympic Hockey", LiveTvSport.Hockey),
    espn("hockey/olympics-womens-ice-hockey", "Olympic Women's Hockey", LiveTvSport.Hockey),
    // ---- Baseball
    espn("baseball/mlb", "MLB", LiveTvSport.Baseball, on = true),
    LiveTvSportsLeague("milb/11", "Triple-A", LiveTvSport.Baseball, LiveTvSportsFeed.Milb(11)),
    LiveTvSportsLeague("milb/12", "Double-A", LiveTvSport.Baseball, LiveTvSportsFeed.Milb(12)),
    LiveTvSportsLeague("milb/13", "High-A", LiveTvSport.Baseball, LiveTvSportsFeed.Milb(13)),
    LiveTvSportsLeague("milb/14", "Single-A", LiveTvSport.Baseball, LiveTvSportsFeed.Milb(14)),
    espn("baseball/world-baseball-classic", "World Baseball Classic", LiveTvSport.Baseball),
    espn("baseball/college-baseball", "College Baseball", LiveTvSport.Baseball),
    espn("baseball/college-softball", "College Softball", LiveTvSport.Baseball),
    espn("baseball/llb", "Little League World Series", LiveTvSport.Baseball),
    espn("baseball/dominican-winter-league", "Dominican Winter League", LiveTvSport.Baseball),
    espn("baseball/puerto-rican-winter-league", "Puerto Rican Winter League", LiveTvSport.Baseball),
    espn("baseball/venezuelan-winter-league", "Venezuelan Winter League", LiveTvSport.Baseball),
    espn("baseball/mexican-winter-league", "Mexican Pacific League", LiveTvSport.Baseball),
    espn("baseball/caribbean-series", "Caribbean Series", LiveTvSport.Baseball),
    // ---- Rugby
    espn("rugby/180659", "Six Nations", LiveTvSport.Rugby, on = true),
    espn("rugby/289234", "Rugby Test Matches", LiveTvSport.Rugby),
    espn("rugby/244293", "The Rugby Championship", LiveTvSport.Rugby),
    espn("rugby/164205", "Rugby World Cup", LiveTvSport.Rugby),
    espn("rugby/17567", "Nations Championship", LiveTvSport.Rugby),
    espn("rugby-league/3", "NRL", LiveTvSport.Rugby),
    fixtures("rugby-union", "Rugby Union", LiveTvSport.Rugby),
    fixtures("rugby-league", "Rugby League", LiveTvSport.Rugby),
    // ---- Cricket
    LiveTvSportsLeague("cricket/panel", "International Cricket", LiveTvSport.Cricket, LiveTvSportsFeed.CricketPanel),
    LiveTvSportsLeague("cricket/8048", "Indian Premier League", LiveTvSport.Cricket, LiveTvSportsFeed.CricketSeason("8048")),
    LiveTvSportsLeague("cricket/8044", "Big Bash League", LiveTvSport.Cricket, LiveTvSportsFeed.CricketSeason("8044")),
    LiveTvSportsLeague("cricket/21275", "SA20", LiveTvSport.Cricket, LiveTvSportsFeed.CricketSeason("21275")),
    LiveTvSportsLeague("cricket/21266", "Major League Cricket", LiveTvSport.Cricket, LiveTvSportsFeed.CricketSeason("21266")),
    fixtures("cricket", "Cricket", LiveTvSport.Cricket),
    // ---- Aussie rules
    espn("australian-football/afl", "AFL", LiveTvSport.AussieRules),
    // ---- Fighting
    espn("mma/ufc", "UFC", LiveTvSport.Fighting, isEvent = true, on = true),
    espn("mma/pfl", "PFL", LiveTvSport.Fighting, isEvent = true),
    fixtures("boxing", "Boxing", LiveTvSport.Fighting),
    // ---- Racing
    espn("racing/f1", "Formula 1", LiveTvSport.Racing, isEvent = true, on = true),
    fixtures("motogp", "MotoGP", LiveTvSport.Racing, on = true),
    espn("racing/irl", "IndyCar", LiveTvSport.Racing, isEvent = true),
    espn("racing/nascar-premier", "NASCAR Cup", LiveTvSport.Racing, isEvent = true),
    espn("racing/nascar-secondary", "NASCAR O'Reilly Series", LiveTvSport.Racing, isEvent = true),
    espn("racing/nascar-truck", "NASCAR Truck Series", LiveTvSport.Racing, isEvent = true),
    fixtures("horse-racing", "Horse Racing", LiveTvSport.Racing),
    fixtures("cycling", "Cycling", LiveTvSport.Racing),
    // ---- Tennis
    espn("tennis/atp", "ATP Tour", LiveTvSport.Tennis, isEvent = true, on = true),
    espn("tennis/wta", "WTA Tour", LiveTvSport.Tennis, isEvent = true),
    // ---- Golf
    espn("golf/pga", "PGA Tour", LiveTvSport.Golf, isEvent = true),
    espn("golf/eur", "DP World Tour", LiveTvSport.Golf, isEvent = true),
    espn("golf/lpga", "LPGA Tour", LiveTvSport.Golf, isEvent = true),
    espn("golf/liv", "LIV Golf", LiveTvSport.Golf, isEvent = true),
    espn("golf/champions-tour", "PGA Tour Champions", LiveTvSport.Golf, isEvent = true),
    espn("golf/ntw", "Korn Ferry Tour", LiveTvSport.Golf, isEvent = true),
    // ---- Volleyball, lacrosse and the rest
    espn("volleyball/womens-college-volleyball", "Women's College Volleyball", LiveTvSport.Volleyball),
    espn("volleyball/mens-college-volleyball", "Men's College Volleyball", LiveTvSport.Volleyball),
    espn("lacrosse/pll", "Premier Lacrosse League", LiveTvSport.Lacrosse),
    espn("lacrosse/nll", "National Lacrosse League", LiveTvSport.Lacrosse),
    espn("lacrosse/mens-college-lacrosse", "Men's College Lacrosse", LiveTvSport.Lacrosse),
    espn("lacrosse/womens-college-lacrosse", "Women's College Lacrosse", LiveTvSport.Lacrosse),
    espn("field-hockey/womens-college-field-hockey", "College Field Hockey", LiveTvSport.Other),
    fixtures("darts", "Darts", LiveTvSport.Other),
    fixtures("snooker", "Snooker", LiveTvSport.Other),
)

internal val liveTvSportsLeaguesById: Map<String, LiveTvSportsLeague> = liveTvSportsLeagues.associateBy { it.id }
