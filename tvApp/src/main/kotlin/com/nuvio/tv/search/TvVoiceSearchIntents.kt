package com.nuvio.tv.search

import android.app.SearchManager
import android.content.Intent
import android.provider.MediaStore
import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildSearchDeepLinkUrl
import com.nuvio.app.core.deeplink.handleAppUrl

private val log = Logger.withTag("TvVoiceSearch")

/** Assistant intent of "search X on <app>" (Google TV / Android TV). */
private const val ACTION_ASSISTANT_SEARCH = "com.google.android.gms.actions.SEARCH_ACTION"

/**
 * Searches started from the system (remote microphone, Google Assistant "Cerca X su Nuvio",
 * "Play X on Nuvio"): they are turned into a `nuvio://search` link, which opens the search
 * screen with the query, or the details of the title when it matches exactly.
 */
object TvVoiceSearchIntents {

    /** Returns true when [intent] was a search request. */
    fun handle(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        if (action != Intent.ACTION_SEARCH &&
            action != ACTION_ASSISTANT_SEARCH &&
            action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
        ) {
            return false
        }
        val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
        if (query.isEmpty()) {
            log.w { "System search intent without a query ($action)" }
            return false
        }
        log.i { "System search intent ($action, ${query.length} chars)" }
        handleAppUrl(buildSearchDeepLinkUrl(query))
        return true
    }
}
