package com.nuvio.app.features.library

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_status_completed
import nuvio.composeapp.generated.resources.library_status_dropped
import nuvio.composeapp.generated.resources.library_status_on_hold
import nuvio.composeapp.generated.resources.library_status_plan_to_watch
import nuvio.composeapp.generated.resources.library_status_watching
import nuvio.composeapp.generated.resources.media_movies
import nuvio.composeapp.generated.resources.media_series
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val librarySectionTitleResources: Map<String, StringResource> = mapOf(
    "movie" to Res.string.media_movies,
    "series" to Res.string.media_series,
    "simkl:status:watching" to Res.string.library_status_watching,
    "simkl:status:plantowatch" to Res.string.library_status_plan_to_watch,
    "simkl:status:hold" to Res.string.library_status_on_hold,
    "simkl:status:completed" to Res.string.library_status_completed,
    "simkl:status:dropped" to Res.string.library_status_dropped,
)

@Composable
internal fun localizedLibrarySectionTitle(type: String, fallback: String): String =
    librarySectionTitleResources[type]?.let { stringResource(it) } ?: fallback

@Composable
internal fun rememberLibrarySectionTitleResolver(): (type: String, fallback: String) -> String {
    val titles = librarySectionTitleResources.mapValues { (_, resource) -> stringResource(resource) }
    return { type, fallback -> titles[type] ?: fallback }
}
