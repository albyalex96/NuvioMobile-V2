package com.nuvio.app.features.player

import android.content.res.Resources

internal actual fun isTabletSubtitleFormFactor(): Boolean =
    Resources.getSystem().configuration.smallestScreenWidthDp >= 600
