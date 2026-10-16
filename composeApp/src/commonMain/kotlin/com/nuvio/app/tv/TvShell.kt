package com.nuvio.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.auth.DeviceLinkAuthState
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.concurrent.Volatile

/**
 * Alternative presentation layer used by the Android TV app (`tvApp` module).
 *
 * The shared app keeps owning navigation, data loading, sync and playback orchestration;
 * when a [TvShell] is provided through [LocalTvShell], a few screens hand their already
 * computed state to these renderers instead of drawing the phone/tablet layout.
 * On phone, tablet and iOS the local is `null` and every hook falls back to the mobile UI.
 */
class TvShell(
    /** Wraps the whole app inside the Nuvio theme (focus indication, ripple tuning…). */
    val themeOverlay: @Composable (content: @Composable () -> Unit) -> Unit,
    /** Replaces the bottom/floating navigation bar of the root tabs. */
    val tabsChrome: @Composable (chrome: TvTabsChrome, content: @Composable () -> Unit) -> Unit,
    val home: @Composable (model: TvHomeModel) -> Unit,
    val details: @Composable (model: TvDetailsModel) -> Unit,
    /** Sign-in screen (QR / device link). Null keeps the shared email sign-in screen. */
    val auth: (@Composable (model: TvAuthModel) -> Unit)? = null,
    /** Voice search button shown next to the search field; it reports the recognized text. */
    val voiceSearch: (@Composable (onQuery: (String) -> Unit) -> Unit)? = null,
)

val LocalTvShell = staticCompositionLocalOf<TvShell?> { null }

@Immutable
class TvTabsChrome(
    val selectedTab: AppScreenTab,
    val tabs: List<AppScreenTab>,
    val activeProfile: NuvioProfile?,
    val contentVisible: Boolean,
    val onTabSelected: (AppScreenTab) -> Unit,
    val onSwitchProfile: () -> Unit,
)

enum class TvHomeStatus {
    Loading,
    Content,
    NoSources,
    Error,
    Empty,
}

sealed interface TvHomeRow {
    val key: String

    class Catalog(
        override val key: String,
        val section: HomeCatalogSection,
        val items: List<MetaPreview>,
        val canOpenCatalog: Boolean,
    ) : TvHomeRow

    class Folders(
        override val key: String,
        val collection: Collection,
    ) : TvHomeRow
}

@Immutable
class TvHomeModel(
    val status: TvHomeStatus,
    val errorMessage: String?,
    val heroItems: List<MetaPreview>,
    val continueWatching: List<ContinueWatchingItem>,
    val upcoming: List<ContinueWatchingItem>,
    val rows: List<TvHomeRow>,
    val isPosterWatched: (MetaPreview) -> Boolean,
    val onPosterClick: (MetaPreview) -> Unit,
    val onPosterLongClick: ((MetaPreview) -> Unit)?,
    val onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    val onContinueWatchingLongClick: ((ContinueWatchingItem) -> Unit)?,
    val onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    val onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    val onRetry: () -> Unit,
)

@Immutable
class TvDetailsModel(
    val meta: MetaDetails,
    val isSeries: Boolean,
    val primaryActionLabel: String,
    val primaryActionEnabled: Boolean,
    /** Season of the episode the primary action plays (used to preselect the season tab). */
    val primaryActionSeason: Int?,
    val primaryActionEpisode: Int?,
    /** Resume progress of the primary action, when it resumes a partially watched item. */
    val primaryActionProgress: Float?,
    val onPrimaryAction: () -> Unit,
    val onPrimaryActionManual: (() -> Unit)?,
    val seasons: List<TvSeason>,
    val episodeProgress: (MetaVideo) -> Float?,
    val isEpisodeWatched: (MetaVideo) -> Boolean,
    val onEpisodeClick: (MetaVideo) -> Unit,
    val onEpisodeManualClick: ((MetaVideo) -> Unit)?,
    val isSaved: Boolean,
    val onToggleSaved: () -> Unit,
    val isWatched: Boolean,
    val onToggleWatched: () -> Unit,
    val shuffleActive: Boolean,
    val onShuffleClick: (() -> Unit)?,
    val onOpenMeta: (MetaPreview) -> Unit,
    val onBack: () -> Unit,
    /** MDBList is active: show `meta.externalRatings` (draw them with [TvExternalRatingsRow]). */
    val showExternalRatings: Boolean = false,
)

/** State of the TV sign-in screen, backed by `DeviceLinkAuthRepository` (QR login). */
@Immutable
class TvAuthModel(
    val deviceLinkState: DeviceLinkAuthState,
    /** False when the active server does not support device/QR login. */
    val deviceLinkEnabled: Boolean,
    val serverUrl: String,
    val isCustomServer: Boolean,
    val authError: String?,
    val onStartDeviceLink: () -> Unit,
    val onCancelDeviceLink: () -> Unit,
    /** Shows the shared email/password screen; the Back key returns to the QR screen. */
    val onUseEmail: () -> Unit,
    val onContinueWithoutAccount: () -> Unit,
)

@Immutable
class TvSeason(
    val number: Int,
    val episodes: List<MetaVideo>,
)

/**
 * Remote-control access to the active in-app player. Registered while a player screen is on
 * screen inside a [TvShell]; the TV activity routes D-pad/media keys through it.
 */
interface TvPlayerRemote {
    val controlsVisible: Boolean
    val isPlaying: Boolean

    /** A panel, modal or prompt is open: keys must reach it untouched. */
    val overlayOpen: Boolean

    fun togglePlayback()

    /** Triggers the visible "skip intro/recap/outro" or "next episode" prompt; false if none is shown. */
    fun activatePrompt(): Boolean
    fun seekStep(forward: Boolean)
    fun showControls()
    fun hideControls()

    /** Keeps the controls on screen while the user is navigating them. */
    fun registerActivity()
}

/** Process-wide flags for the Android TV app, set by its activity before any UI is shown. */
object TvAppRuntime {
    /**
     * True inside the `tvApp` APK. The in-app updater then only picks release assets whose
     * file name contains the `TV` token, and the phone app skips them (see `isTvApkAssetName`).
     */
    var isTvApp: Boolean = false
}

object TvPlayerRemoteRegistry {
    @Volatile
    var active: TvPlayerRemote? = null
        internal set
}
