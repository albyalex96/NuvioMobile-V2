package com.nuvio.app.features.ai

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetails
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class AiWebSearchContext(
    val promptContext: String,
    val sources: List<AiWebSource>,
)

/** Tavily search for questions the title metadata cannot answer; results are cached in memory. */
internal object AiWebSearchService {
    private val log = Logger.withTag(AI_ASSISTANT_LOG_TAG)
    private val json = Json { ignoreUnknownKeys = true }
    private val memoryCache = linkedMapOf<String, AiWebSearchContext>()

    suspend fun search(
        apiKey: String,
        meta: MetaDetails,
        question: String,
    ): AiWebSearchContext? {
        if (apiKey.isBlank()) return null
        if (!shouldSearchWeb(question)) {
            log.d { "Web search skipped: the question can be answered from the title metadata" }
            return null
        }
        val cacheKey = "${meta.id}|${question.trim().lowercase()}"
        memoryCache[cacheKey]?.let {
            log.d { "Web search served from cache (${it.sources.size} sources)" }
            return it
        }

        val query = buildString {
            append('"')
            append(meta.name)
            append("\" ")
            meta.releaseInfo?.takeIf(String::isNotBlank)?.let {
                append(it)
                append(' ')
            }
            append(meta.type)
            append(' ')
            append(question.trim().take(MAX_QUESTION_LENGTH))
        }
        val body = buildJsonObject {
            put("api_key", apiKey)
            put("query", query)
            put("search_depth", "basic")
            put("topic", "general")
            put("max_results", MAX_SEARCH_RESULTS)
            put("include_answer", false)
            put("include_raw_content", false)
        }
        val response = httpRequestRaw(
            method = "POST",
            url = "https://api.tavily.com/search",
            headers = mapOf("Content-Type" to "application/json"),
            body = body.toString(),
        )
        if (response.status !in 200..299) {
            log.w { "Tavily search failed with HTTP ${response.status}" }
            return null
        }

        val results = runCatching {
            json.parseToJsonElement(response.body)
                .jsonObject["results"]
                ?.jsonArray
                .orEmpty()
                .mapNotNull { element ->
                    val item = element.jsonObject
                    val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val url = item["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val content = item["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (title.isBlank() || url.isBlank() || content.isBlank()) {
                        null
                    } else {
                        SearchResult(title = title, url = url, content = content)
                    }
                }
        }.onFailure { error -> log.w(error) { "Invalid Tavily response" } }
            .getOrNull()
            .orEmpty()
        log.i { "Web search returned ${results.size} sources" }
        if (results.isEmpty()) return null

        val context = AiWebSearchContext(
            promptContext = buildString {
                results.forEachIndexed { index, result ->
                    appendLine("[${index + 1}] ${result.title}")
                    appendLine("URL: ${result.url}")
                    appendLine(result.content.take(MAX_RESULT_CONTENT_LENGTH))
                    appendLine()
                }
            }.trim(),
            sources = results.map { result ->
                AiWebSource(title = result.title, url = result.url)
            },
        )
        if (memoryCache.size >= MAX_CACHE_ENTRIES) {
            memoryCache.remove(memoryCache.keys.first())
        }
        memoryCache[cacheKey] = context
        return context
    }

    internal fun shouldSearchWeb(question: String): Boolean {
        val normalized = question.trim().lowercase()
        if (normalized.length < 8) return false
        return localOnlyQuestionMarkers.none(normalized::contains)
    }
}

private data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
)

/** Questions the title metadata already answers: no web search credits are spent on them. */
private val localOnlyQuestionMarkers = listOf(
    // English
    "worth watching",
    "similar title",
    "what genre",
    "how long",
    "runtime",
    "rating",
    "summarize",
    "before watching",
    // Italian
    "vale la pena",
    "titoli simili",
    "che genere",
    "quanto dura",
    "durata",
    "voto",
    "valutazione",
    "riassumi",
    "prima di guardar",
    // Turkish
    "izlemeye değer",
    "benzer yapım",
    "türü ne",
    "hangi tür",
    "süresi ne",
    "puanı ne",
    "özetle",
    "izlemeden önce",
)

private const val MAX_SEARCH_RESULTS = 5
private const val MAX_RESULT_CONTENT_LENGTH = 900
private const val MAX_QUESTION_LENGTH = 350
private const val MAX_CACHE_ENTRIES = 40
