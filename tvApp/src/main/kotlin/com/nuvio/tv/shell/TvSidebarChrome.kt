package com.nuvio.tv.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import co.touchlab.kermit.Logger
import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.parseHexColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.TopNavBarStyle
import com.nuvio.app.tv.TvFocusRegions
import com.nuvio.app.tv.TvGlassNavItem
import com.nuvio.app.tv.TvGlassNavigationBar
import com.nuvio.app.tv.rememberTvGlassBackdrop
import com.nuvio.app.tv.tvGlassBackdropSource
import com.nuvio.tv.settings.TvTopBarBlurSupported
import com.nuvio.tv.settings.rememberTvLayoutSettings
import com.nuvio.app.tv.TvTabsChrome
import com.nuvio.app.tv.tvChromeRegion
import com.nuvio.app.tv.tvEnterOn
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvRemoteImage
import kotlinx.coroutines.delay

private val log = Logger.withTag("TvSidebar")

// Sizes and timings of NuvioTV's "modern sidebar" (ComponentTokens.sidebar, MainActivity).
private val PanelWidth = 230.dp
private val PanelRadius = 30.dp
private val PillHeight = 52.dp
private val LeadingVisual = 34.dp
private val ItemIconSize = 22.dp
private val ContentGap = 14.dp
private val ChromeStart = 14.dp
private val ChromeTop = 16.dp
private val TopBarTop = 10.dp
private const val PILL_AUTO_COLLAPSE_MS = 3_000L
private const val PANEL_IN_MS = 345
private const val PANEL_OUT_MS = 385
private const val ITEM_ANIMATION_MS = 150

/** Frames (one attempt each) to focus the selected tab when the panel opens. */
private const val SELECTED_FOCUS_ATTEMPTS = 12

/** Attempts (and interval) to move the focus into a page opened from the sidebar. */
private const val CONTENT_FOCUS_ATTEMPTS = 10
private const val CONTENT_FOCUS_RETRY_MS = 100L

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Root tabs chrome for TV, ported from NuvioTV's modern sidebar: the page uses the whole screen,
 * a floating pill shows the open section at the top left, and a rounded panel with the profile
 * and the sections opens with D-pad left (from the first column) or Back.
 */
@Composable
fun TvSidebarChrome(chrome: TvTabsChrome, content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    // Glass top bar (Appearance -> Navigation bar): replaces pill + sidebar while enabled.
    val topBar by ThemeSettingsRepository.topNavBarEnabled.collectAsStateWithLifecycle()
    val topBarStyle by ThemeSettingsRepository.topNavBarStyle.collectAsStateWithLifecycle()
    val glowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var topBarFocused by remember { mutableStateOf(false) }
    var pillIconOnly by remember { mutableStateOf(false) }
    var pillExpandRequests by remember { mutableIntStateOf(0) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var contentFocusRequests by remember { mutableIntStateOf(0) }
    val selectedRequester = remember { FocusRequester() }
    val topBarRequester = remember { FocusRequester() }
    val showPill = !topBar && chrome.selectedTab != AppScreenTab.Search
    val layoutSettings = rememberTvLayoutSettings()
    // NuvioTV keeps the blur opt-in: with it, the whole page is redrawn into a blurred layer on
    // every frame. Without it the page is not a blur source at all and the bar is a solid glass.
    val glassBlur = topBar && layoutSettings.topBarBlur && TvTopBarBlurSupported
    val hazeBackdrop = rememberTvGlassBackdrop()
    val glassBackdrop = if (glassBlur) hazeBackdrop else null

    LaunchedEffect(topBar) {
        log.i { "Navigation chrome: ${if (topBar) "glass top bar" else "sidebar"}" }
        if (topBar) expanded = false
    }
    LaunchedEffect(glassBlur) {
        if (topBar) log.i { "Glass top bar blur ${if (glassBlur) "on" else "off"}" }
    }

    fun focusTopBar(reason: String) {
        log.d { "Top bar focused ($reason)" }
        runCatching { topBarRequester.requestFocus() }
            .onFailure { log.w(it) { "Top bar not ready for focus" } }
    }

    fun openSidebar(reason: String) {
        if (topBar) {
            focusTopBar(reason)
            return
        }
        if (expanded) return
        log.d { "Sidebar opened ($reason)" }
        expanded = true
    }

    fun closeSidebar(focusContent: Boolean) {
        if (!expanded) return
        expanded = false
        // The panel leaves the composition: the content takes the focus back.
        if (focusContent) TvFocusRegions.request(force = true)
    }

    // Back from the first level of a page (e.g. Settings categories) opens the sidebar.
    DisposableEffect(Unit) {
        TvFocusRegions.chromeOpener = { openSidebar("page back") }
        onDispose { TvFocusRegions.chromeOpener = null }
    }
    // Back on a root page opens the sidebar; with the sidebar open it is the app's own Back.
    PlatformBackHandler(enabled = !expanded && !(topBar && topBarFocused), onBack = { openSidebar("back") })

    // The open panel always starts on the section on screen (it is also the highlighted one).
    LaunchedEffect(expanded, chrome.selectedTab) {
        if (!expanded || topBar) return@LaunchedEffect
        repeat(SELECTED_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { selectedRequester.requestFocus() }.getOrDefault(false)) {
                log.d { "Sidebar focus on the selected section ${chrome.selectedTab}" }
                return@LaunchedEffect
            }
        }
        log.w { "Could not focus the selected section ${chrome.selectedTab} in the sidebar" }
    }
    // A section chosen in the sidebar: the focus goes into the opened page, retried while the page
    // is still composing (lazy lists attach their first item a few frames later).
    LaunchedEffect(contentFocusRequests) {
        if (contentFocusRequests == 0) return@LaunchedEffect
        repeat(CONTENT_FOCUS_ATTEMPTS) { attempt ->
            withFrameNanos { }
            if (expanded) return@LaunchedEffect
            if (contentHasFocus) {
                log.d { "Focus moved into ${chrome.selectedTab} (attempt $attempt)" }
                return@LaunchedEffect
            }
            TvFocusRegions.request(force = true)
            delay(CONTENT_FOCUS_RETRY_MS)
        }
        if (!contentHasFocus) log.w { "Focus did not reach ${chrome.selectedTab} after a sidebar selection" }
    }
    // The pill shows the label after a section change, then shrinks to the icon.
    LaunchedEffect(chrome.selectedTab) {
        pillIconOnly = false
        pillExpandRequests++
    }
    LaunchedEffect(pillIconOnly, pillExpandRequests, expanded) {
        if (pillIconOnly || expanded) return@LaunchedEffect
        delay(PILL_AUTO_COLLAPSE_MS)
        pillIconOnly = true
    }

    val transition = updateTransition(targetState = expanded, label = "tvSidebar")
    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(PANEL_IN_MS, easing = FastOutSlowInEasing)
            } else {
                tween(PANEL_OUT_MS, easing = LinearOutSlowInEasing)
            }
        },
        label = "tvSidebarProgress",
    ) { if (it) 1f else 0f }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.nuvio.colors.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Pages other than Home keep their header below the floating pill.
                .padding(
                    top = if ((showPill || topBar) && chrome.selectedTab != AppScreenTab.Home) TvDimens.chromeTopInset else 0.dp,
                )
                .tvGlassBackdropSource(glassBackdrop)
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && !expanded && !topBar) {
                        when (event.key) {
                            Key.DirectionDown -> pillIconOnly = true
                            Key.DirectionUp -> {
                                pillIconOnly = false
                                pillExpandRequests++
                            }
                        }
                    }
                    false
                }
                .onKeyEvent { event ->
                    when {
                        expanded || event.type != KeyEventType.KeyDown -> false
                        // Up that the page cannot use (first row) reaches the glass top bar.
                        topBar && event.key == Key.DirectionUp -> {
                            if (!focusManager.moveFocus(FocusDirection.Up)) focusTopBar("top edge")
                            true
                        }
                        // Left that the page cannot use (first column) opens the sidebar.
                        !topBar && event.key == Key.DirectionLeft -> {
                            if (!focusManager.moveFocus(FocusDirection.Left)) openSidebar("left edge")
                            true
                        }
                        else -> false
                    }
                },
        ) {
            content()
        }

        if (showPill && progress < 0.98f) {
            CollapsedSidebarPill(
                tab = chrome.selectedTab,
                iconOnly = pillIconOnly,
                onExpand = { openSidebar("pill") },
                modifier = Modifier
                    .zIndex(1f)
                    .align(Alignment.TopStart)
                    .offset(x = ChromeStart, y = ChromeTop)
                    .graphicsLayer {
                        alpha = 1f - progress
                        val scale = 0.9f + 0.1f * (1f - progress)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }

        if (topBar) {
            val items = chrome.tabs.map { tab ->
                TvGlassNavItem(
                    key = tab.name,
                    label = tab.label(),
                    selected = tab == chrome.selectedTab,
                    icon = tab.painter(),
                    onClick = {
                        log.d { "Tab selected from top bar: $tab" }
                        chrome.onTabSelected(tab)
                        // Land on the first element of the opened page (also when re-selecting it).
                        TvFocusRegions.request(force = true)
                    },
                )
            }
            val profileItem = TvGlassNavItem(
                key = "profile",
                label = chrome.activeProfile?.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.tv_nav_profile),
                selected = false,
                leading = { ProfileAvatar(profile = chrome.activeProfile, size = 26.dp) },
                onClick = {
                    log.d { "Profile switch from top bar" }
                    chrome.onSwitchProfile()
                },
            )
            TvGlassNavigationBar(
                items = listOf(profileItem) + items,
                backdrop = glassBackdrop,
                expanded = topBarStyle == TopNavBarStyle.EXPANDED,
                glowEnabled = glowEnabled,
                selectedRequester = selectedRequester,
                modifier = Modifier
                    .zIndex(1f)
                    .align(Alignment.TopCenter)
                    .padding(top = TopBarTop)
                    .focusRequester(topBarRequester)
                    .onFocusChanged { topBarFocused = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            // Back to the page: its primary region takes the focus.
                            TvFocusRegions.request(force = true)
                            true
                        } else {
                            false
                        }
                    }
                    .tvEnterOn(selectedRequester)
                    .tvChromeRegion(),
            )
        }

        if (!topBar && (expanded || progress > 0.01f)) {
            Box(
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.TopStart)
                    .width(PanelWidth)
                    .fillMaxHeight()
                    .padding(start = ChromeStart, top = ChromeTop, bottom = 12.dp, end = 8.dp)
                    .graphicsLayer {
                        alpha = progress
                        val scale = 0.92f + 0.08f * progress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .onPreviewKeyEvent { event ->
                        if (!expanded || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            // Focus never escapes into the page behind the panel.
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionRight -> {
                                closeSidebar(focusContent = true)
                                true
                            }
                            Key.DirectionLeft -> true
                            else -> false
                        }
                    }
                    .tvEnterOn(selectedRequester)
                    .tvChromeRegion(),
            ) {
                SidebarPanel(
                    chrome = chrome,
                    focusEnabled = expanded,
                    selectedRequester = selectedRequester,
                    onTabClick = { tab ->
                        log.i { "Tab selected from sidebar: $tab (was ${chrome.selectedTab})" }
                        // Collapse first: the panel gives up the focus, then the page takes it.
                        closeSidebar(focusContent = false)
                        chrome.onTabSelected(tab)
                        // Land on the first element of the opened page (also when re-selecting it).
                        contentFocusRequests++
                    },
                    onProfileClick = {
                        closeSidebar(focusContent = false)
                        chrome.onSwitchProfile()
                    },
                )
            }
        }
    }
}

@Composable
private fun SidebarPanel(
    chrome: TvTabsChrome,
    focusEnabled: Boolean,
    selectedRequester: FocusRequester,
    onTabClick: (AppScreenTab) -> Unit,
    onProfileClick: () -> Unit,
) {
    val shape = RoundedCornerShape(PanelRadius)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF161618).copy(alpha = 0.97f), shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            SidebarProfileItem(
                profile = chrome.activeProfile,
                focusEnabled = focusEnabled,
                onClick = onProfileClick,
                modifier = Modifier.fillMaxWidth(0.92f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.offset(y = (-12).dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chrome.tabs.forEach { tab ->
                    key(tab) {
                        val selected = tab == chrome.selectedTab
                        SidebarNavigationItem(
                            label = tab.label(),
                            icon = tab.painter(),
                            selected = selected,
                            focusEnabled = focusEnabled,
                            onClick = { onTabClick(tab) },
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .then(if (selected) Modifier.focusRequester(selectedRequester) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarNavigationItem(
    label: String,
    icon: Painter,
    selected: Boolean,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.nuvio.colors.accent
    val textPrimary = MaterialTheme.nuvio.colors.textPrimary
    val onOverlay = Color.White.copy(alpha = 0.78f)
    val background by animateColorAsState(
        targetValue = when {
            focused && selected -> accent.copy(alpha = 0.28f)
            focused -> Color.White.copy(alpha = 0.12f)
            selected -> accent.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        animationSpec = tween(ITEM_ANIMATION_MS),
        label = "tvSidebarItemBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> accent
            focused -> textPrimary
            else -> onOverlay
        },
        animationSpec = tween(ITEM_ANIMATION_MS),
        label = "tvSidebarItemContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = tween(ITEM_ANIMATION_MS),
        label = "tvSidebarItemScale",
    )
    val iconBrush = if (selected) MaterialTheme.themePalette.accentBrush() else null
    val shape = CircleShape

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.hasFocus }
            .sidebarConfirmKeys(focusEnabled, onClick)
            .focusProperties { canFocus = focusEnabled }
            .clip(shape)
            .background(background, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(LeadingVisual), contentAlignment = Alignment.Center) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (iconBrush != null) Color.White else contentColor,
                modifier = Modifier
                    .size(ItemIconSize)
                    .then(if (iconBrush != null) Modifier.brushTint(iconBrush) else Modifier),
            )
        }
        Spacer(modifier = Modifier.width(ContentGap))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SidebarProfileItem(
    profile: NuvioProfile?,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.hasFocus }
            .sidebarConfirmKeys(focusEnabled, onClick)
            .focusProperties { canFocus = focusEnabled }
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(profile = profile, size = LeadingVisual)
        Spacer(modifier = Modifier.width(ContentGap + 4.dp))
        Column {
            Text(
                text = profile?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (focused) {
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

/** Floating pill with the open section (NuvioTV `CollapsedSidebarPill`); not a D-pad target. */
@Composable
private fun CollapsedSidebarPill(
    tab: AppScreenTab,
    iconOnly: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .height(PillHeight)
            .clip(shape)
            .background(Color(0xFF1C1C1E).copy(alpha = 0.96f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand,
            ),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(LeadingVisual), contentAlignment = Alignment.Center) {
                Icon(
                    painter = tab.painter(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(LeadingVisual - 12.dp),
                )
            }
            AnimatedVisibility(
                visible = !iconOnly,
                enter = expandHorizontally(tween(ITEM_ANIMATION_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = shrinkHorizontally(tween(ITEM_ANIMATION_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Text(
                    text = tab.label(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(lineHeight = 30.sp),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 9.dp, end = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: NuvioProfile?, size: androidx.compose.ui.unit.Dp) {
    val avatarColor = remember(profile?.avatarColorHex) {
        profile?.avatarColorHex?.let(::parseHexColor) ?: Color(0xFF1E88E5)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = profile?.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            TvRemoteImage(url = avatarUrl, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = profile?.name?.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

/**
 * OK on a sidebar entry, handled here like the TV cards: the press must start on the entry
 * (key down) and the action runs on release, so nothing else on the way can swallow it.
 * The clickable stays for touch and mouse.
 */
@Composable
private fun Modifier.sidebarConfirmKeys(enabled: Boolean, onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { if (!it.hasFocus) pressed = false }
        .onPreviewKeyEvent { event ->
            if (event.key !in ConfirmKeys) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (enabled) pressed = true
                    enabled
                }
                KeyEventType.KeyUp -> {
                    val click = pressed && enabled
                    pressed = false
                    if (click) currentOnClick()
                    click
                }
                else -> false
            }
        }
}

/** Paints the (white) icon with [brush], like NuvioTV's accent-gradient selected icon. */
private fun Modifier.brushTint(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
        }
    }

@Composable
private fun AppScreenTab.painter(): Painter = when (this) {
    AppScreenTab.Home -> rememberVectorPainter(Icons.Filled.Home)
    AppScreenTab.Search -> painterResource(R.drawable.tv_sidebar_search)
    AppScreenTab.Library -> painterResource(R.drawable.tv_sidebar_library)
    AppScreenTab.LiveTv -> painterResource(R.drawable.tv_sidebar_live_tv)
    AppScreenTab.Settings -> painterResource(R.drawable.tv_sidebar_settings)
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
