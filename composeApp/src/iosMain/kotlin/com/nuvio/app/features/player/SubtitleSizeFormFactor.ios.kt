package com.nuvio.app.features.player

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

internal actual fun isTabletSubtitleFormFactor(): Boolean =
    UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad
