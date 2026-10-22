package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import com.nuvio.app.features.cloudstream.CloudStreamSettingsPageContent
import com.nuvio.app.features.cloudstream.CloudStreamUnsupportedContent
import com.nuvio.app.features.cloudstream.cloudStreamRuntimeSupported

internal actual fun LazyListScope.cloudStreamSettingsContent() {
    item(key = "cloudstream_settings") {
        if (cloudStreamRuntimeSupported()) {
            CloudStreamSettingsPageContent(modifier = Modifier.fillMaxWidth())
        } else {
            CloudStreamUnsupportedContent(modifier = Modifier.fillMaxWidth())
        }
    }
}
