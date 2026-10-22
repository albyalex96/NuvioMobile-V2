package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope

/** CloudStream page: the full builds host the manager, the store builds have no CloudStream. */
internal expect fun LazyListScope.cloudStreamSettingsContent()
