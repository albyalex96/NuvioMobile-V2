package com.nuvio.app.features.settings

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_nav_bar_style_compact
import nuvio.composeapp.generated.resources.settings_nav_bar_style_expanded
import org.jetbrains.compose.resources.StringResource

/** Layout of the glass navigation bar shown at the top center (TV for now). */
enum class TopNavBarStyle(
    val key: String,
    val labelRes: StringResource,
) {
    /** Every section shows its icon and its name. */
    EXPANDED("expanded", Res.string.settings_nav_bar_style_expanded),

    /** Icons only; the name appears next to the selected or focused section. */
    COMPACT("compact", Res.string.settings_nav_bar_style_compact),
    ;

    companion object {
        fun fromKey(key: String?): TopNavBarStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: EXPANDED
    }
}
