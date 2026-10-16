package com.nuvio.tv.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import co.touchlab.kermit.Logger
import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.parseHexColor
import com.nuvio.app.tv.TvFocusRegions
import com.nuvio.app.tv.TvTabsChrome
import com.nuvio.app.tv.tvChromeRegion
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvRemoteImage
import com.nuvio.tv.ui.tvFocusable

private val log = Logger.withTag("TvSidebar")
private val SidebarExpandedWidth = 236.dp
private val SidebarItemShape = RoundedCornerShape(12.dp)

/** Root tabs chrome for TV: a collapsible left rail replacing the phone navigation bars. */
@Composable
fun TvSidebarChrome(chrome: TvTabsChrome, content: @Composable () -> Unit) {
    var sidebarFocused by remember { mutableStateOf(false) }
    val selectedRequester = remember { FocusRequester() }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarFocused) SidebarExpandedWidth else TvDimens.sidebarCollapsedWidth,
        animationSpec = tween(220),
        label = "tvSidebarWidth",
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.nuvio.colors.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = TvDimens.sidebarCollapsedWidth),
        ) {
            content()
        }

        AnimatedVisibility(
            visible = sidebarFocused,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.85f),
                            0.45f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .zIndex(2f)
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(
                    if (sidebarFocused) MaterialTheme.nuvio.colors.surface else MaterialTheme.nuvio.colors.background,
                )
                .padding(horizontal = 12.dp, vertical = 28.dp)
                .onFocusChanged { sidebarFocused = it.hasFocus }
                // Entering the rail (D-pad left from the page) always lands on the open tab.
                .focusProperties {
                    onEnter = {
                        if (runCatching { selectedRequester.requestFocus() }.getOrDefault(false)) {
                            log.d { "Sidebar entered on the selected tab" }
                        }
                    }
                }
                .tvChromeRegion(),
        ) {
            // Profile pinned at the top, navigation centered on the screen's horizontal midline.
            ProfileItem(
                profile = chrome.activeProfile,
                expanded = sidebarFocused,
                onClick = chrome.onSwitchProfile,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chrome.tabs.forEach { tab ->
                    val selected = tab == chrome.selectedTab
                    SidebarItem(
                        icon = tab.icon(),
                        label = tab.label(),
                        selected = selected,
                        expanded = sidebarFocused,
                        onClick = {
                            chrome.onTabSelected(tab)
                            // Leave the sidebar and land on the first element of the opened page.
                            TvFocusRegions.request(force = true)
                        },
                        modifier = if (selected) Modifier.focusRequester(selectedRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.nuvio.colors
    val background by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            selected -> Color.White.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        label = "tvSidebarItemBackground",
    )
    val foreground = when {
        focused -> Color.Black
        selected -> colors.textPrimary
        else -> colors.textMuted
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .tvFocusable(
                shape = SidebarItemShape,
                onClick = onClick,
                focusedScale = 1.03f,
                borderWidth = 0.dp,
                onFocusChange = { focused = it },
            )
            .background(background),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = foreground, modifier = Modifier.size(24.dp))
            if (expanded) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected && !focused && !expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun ProfileItem(
    profile: NuvioProfile?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .tvFocusable(
                shape = SidebarItemShape,
                onClick = onClick,
                focusedScale = 1.03f,
                borderWidth = 0.dp,
                onFocusChange = { focused = it },
            )
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            ProfileAvatar(profile = profile, focused = focused)
            if (expanded) {
                Column {
                    Text(
                        text = profile?.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.nuvio.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.tv_nav_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.nuvio.colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: NuvioProfile?, focused: Boolean) {
    val avatarColor = remember(profile?.avatarColorHex) {
        profile?.avatarColorHex?.let(::parseHexColor) ?: Color(0xFF1E88E5)
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(avatarColor)
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.12f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = profile?.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            TvRemoteImage(url = avatarUrl, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = profile?.name?.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

private fun AppScreenTab.icon(): ImageVector = when (this) {
    AppScreenTab.Home -> Icons.Filled.Home
    AppScreenTab.Search -> Icons.Filled.Search
    AppScreenTab.Library -> Icons.Filled.VideoLibrary
    AppScreenTab.LiveTv -> Icons.Filled.LiveTv
    AppScreenTab.Settings -> Icons.Filled.Settings
}

@Composable
private fun AppScreenTab.label(): String = stringResource(
    when (this) {
        AppScreenTab.Home -> R.string.tv_nav_home
        AppScreenTab.Search -> R.string.tv_nav_search
        AppScreenTab.Library -> R.string.tv_nav_library
        AppScreenTab.LiveTv -> R.string.tv_nav_live_tv
        AppScreenTab.Settings -> R.string.tv_nav_settings
    },
)
