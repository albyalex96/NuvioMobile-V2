# Nuvio Enhanced — Enhancements

Everything Nuvio Enhanced adds on top of the original [Nuvio](https://github.com/NuvioMedia/NuvioMobile). Features from the upstream app (including Watch Party, MDBList lists and the P2P engine) are kept and merged regularly; this page only lists what is specific to this fork.

🇮🇹 [Versione italiana](./ENHANCEMENTS.it.md) · [Back to README](./README.md)

Most settings are saved per profile and synced with your Nuvio account, so they follow you across devices.

---

## Contents

- [Android TV app](#android-tv-app)
- [Phone as TV remote](#phone-as-tv-remote)
- [Windows desktop app](#windows-desktop-app)
- [Live TV and EPG](#live-tv-and-epg)
- [Chromecast and media proxy](#chromecast-and-media-proxy)
- [Downloads](#downloads)
- [Player](#player)
- [Streams](#streams)
- [Integrations](#integrations)
- [CloudStream plugins](#cloudstream-plugins)
- [Network](#network)
- [Appearance and interface](#appearance-and-interface)
- [Diagnostics](#diagnostics)
- [Sync and settings](#sync-and-settings)
- [Releases and updates](#releases-and-updates)

---

## Android TV app

A dedicated Android TV app, released as a separate APK (`Nuvio-Enhanced-Alby-TV-…`). It shares all the logic of the phone app (accounts, profiles, sync, streams, player) and adds a remote-friendly interface inspired by [NuvioTV](https://github.com/NuvioMedia/NuvioTV).

- **Navigation:** NuvioTV-style "modern sidebar" with a floating pill showing the current section, or an optional **glass top navigation bar** (icons + labels or icons only, with optional glow).
- **Home:** NuvioTV "Modern" hero below the navigation bar with an **auto-advancing carousel** (choose up to 2 hero catalogs, synced with the profile), enriched metadata, ratings and age rating, full-screen backdrop kept behind every row. The hero can be **expanded** (plot, Play and Library buttons, hold Play to choose the source manually) and stays open while scrolling the rows. Rows sized and scrolled like NuvioTV, Continue Watching in three styles (Card, Wide, Poster) with badges for time left, next episode, new episode and new season, optional focused-poster expansion.
- **"TV Layout" settings page:** poster width and corner radius presets, landscape posters, labels under posters, Continue Watching style, poster expansion delay, full-screen hero backdrop, plus shortcuts to the shared Home and Continue Watching options.
- **Details page:** full-screen backdrop, NuvioTV-style hero, play button (hold OK to choose the source), library/watched/shuffle buttons, MDBList ratings, season posters or text tabs, horizontal or list episode cards (follows the phone setting, with blurred unwatched episodes and expandable overviews), **per-episode IMDb ratings section**, expandable synopsis, season actions menu on long press, clickable cast that opens the person page, collection and similar titles.
- **Card options menu:** hold OK or press Menu on any poster or Continue Watching card: view details, watch a random episode, resume/play from start, choose source, mark as watched (without flicker in Continue Watching), add/remove from library, remove from Continue Watching. Streams have their own TV menu too.
- **Continue Watching on the Android TV home screen:** the row is published to the launcher as a Nuvio "Continue watching" channel and in the "Play next" row (with progress and episode artwork). Opening an item can show the card options menu (resume, choose source and play, details…), resume directly, open the stream list of the right episode or open the details page. Configurable in TV Layout → Android TV home screen.
- **Remote control in the player:** OK play/pause, left/right cumulative seek with preview (step grows while held), **seek thumbnails** even with the controls hidden, media keys, focus on the timeline, skip intro/next episode prompts, compact resume prompt, panels and dialogs that receive focus automatically. In the Live TV channels panel, a long press on OK opens the channel's EPG guide.
- **Focus handling everywhere:** focus follows navigation, never gets lost in hidden tabs, settings work in levels (categories → panel → sub-pages, Back goes up one level), dialogs focus the primary button, text fields open the keyboard only when you press OK.
- **QR code sign-in** (same device-link flow as NuvioTV) with email/password as a fallback.
- **Voice search** with the system speech recognizer and from the Google Assistant, plus an **Android TV global search provider** (Nuvio results in the system search). Recent searches always shown as scrollable chips.
- Centered scrolling with fading edges in Settings, visible focus on profile selection and filter chips.
- **Performance work:** baseline profile, lighter image loading, NuvioTV-style row scrolling, throttled held D-pad repeats, low-resolution blur instead of real-time blur, optional blur behind the glass top bar, hero paused while the Home is hidden.

## Phone as TV remote

- **Remote control for Android TV playback** from the phone: pair with a code, the in-app QR scanner or automatic TV discovery on the network (manual address as a fallback).
- Controls in a background notification, on the lock screen and with the volume keys, shown automatically when the TV is detected online.
- Quick Settings tile "Nuvio TV remote" to open (or close) the notification controls even with the app closed.
- **Telegram login on the TV** done from the paired phone.
- **View and manage the TV logs** from the phone (Settings → Advanced → Diagnostics → TV logs, shown when a TV is paired).
- **Watch Party companion chat** on the phone.

## Windows desktop app

A Windows app built from the same code, distributed as an MSI installer with in-app updates.

- **libmpv player** with keyboard shortcuts, mouse wheel volume, fullscreen button, cursor auto-hide, controls shown on mouse movement, seek bar hover preview with **FFmpeg seek thumbnails**, compact resume prompt.
- In-app trailers and hero trailer playback, animated artwork playback modes.
- **Chromecast** through the Cast v2 protocol (receivers found through the Windows firewall), with a **LAN media proxy** for header-protected and local streams.
- **P2P streaming** with the Nuvio engine, **JS scraper plugins** on QuickJS.
- Downloads folder, episode folders and HLS downloads with FFmpeg.
- Mouse-friendly interface: rows draggable with the mouse, right mouse button as long press, per-device poster width, hover scale and hover preview; desktop counts as its own device for the per-device player settings.

## Live TV and EPG

Ported from [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) and extended.

- Sources: **M3U playlists** (URL or local file), **Xtream** and **Stalker** providers.
- **XMLTV EPG** (`.xml` / `.xml.gz`), auto-detected from playlists and providers, matched by `tvg-id` or channel name; the current program is shown in the player.
- Dedicated Live TV tab (phone, tablet, TV and iOS native tab bar), favorites, search and categories.
- **Section tabs** at the top of the Live TV tab (Channels, Guide, Sports, On Demand), with icons and animations:
  - **Guide:** full TV guide grid with all channels on one timeline, a pulsing "now" line, a progress fill on airing programmes and a "Now" button to jump back to the current time. Tapping an airing programme opens the channel, otherwise the channel guide.
  - **Sports:** live and upcoming matches (football, NBA, NFL, NHL, MLB, UFC) from ESPN, with a blinking LIVE badge, animated scores, a countdown in the last hour, league filters and the **matching channels of your playlists** to start watching with one tap.
  - **On Demand:** movies and series of **Xtream** providers, with categories, search, posters and a season/episode sheet; titles open in the regular player.
- Player integration: channel list panel, zapping between channels, channel logo and name overlay. Live channels never create watch progress or scrobbling entries.
- **Channel management:** show/hide channels and whole categories, search, select/deselect all, and **custom channel order** with drag and drop (Up/Down buttons on TV).
- Playlists, EPG sources, favorites and channel preferences sync with the profile (credentials and local files stay on the device).

## Chromecast and media proxy

- **Chromecast** (Android) with the Default Media Receiver: the Cast device works as a player engine, so progress, scrobbling, next episode, "Still watching?" and custom seek keep working.
- Cast button in the player and "Cast to Chromecast" in the stream menu; smooth hand-off between phone and TV.
- **HLS support** on Chromecast: correct segment format detection (TS / fMP4 / packed audio) and handling of disguised segments (wrong extension or content type).
- **Audio track selection** on the receiver, with your preferred audio language applied automatically.
- **Compatibility warnings** for codecs the receiver may not support (HEVC, AV1, Dolby Vision, DTS, TrueHD).
- **Reusable LAN media proxy:** forwards addon headers, serves P2P streams and local downloads to the Cast device, rewrites HLS/DASH manifests and adds CORS. Automatic proxy fallback for HLS/DASH streams that fail directly.
- **"Media proxy" settings page:** live status, fixed port, manual network address, keep Wi-Fi/CPU awake, default User-Agent, timeouts, CORS and manifest rewriting switches, Chromecast proxy options.

## Downloads

- **Downloads settings page:** choose an **external folder** (Storage Access Framework), storage summary with used/free space bars, file cards with play, resume, external player, Chromecast, share, file info, pause/retry and delete.
- **External files:** videos already in the folder (copied from elsewhere or from another profile) can be played directly, with matching subtitles picked up automatically.
- **HLS downloads** (Android): choose video quality, audio tracks and subtitles in a dialog (preferred audio language preselected), background download with pause/resume and AES-128 support, then conversion without re-encoding to **MP4 or MKV** with embedded tracks.
- **Season downloads** with episode folders, downloads grouped by title.
- **Background downloads section:** battery optimization status and shortcut, with tips for manufacturers with aggressive power saving.

## Player

- **Custom seek interval** (1–20 seconds) for on-screen buttons, double tap (cumulative), notification/media session and iOS remote commands, with a dynamic icon.
- **"Still watching?" prompt** (Netflix style): after a configurable number of consecutive auto-played episodes, optionally only at night (22:00–04:00), with a 10-second countdown before the player closes (or exits to the details page), with per-device options. A separate time-based version for **Live TV** (15 min – 6 h).
- **Seek thumbnails** for single files and HLS streams, with separate options for phone and TV.
- **Picture-in-picture button** in the player top bar (Android phones with PiP support), besides the automatic PiP when leaving the app.
- **Glass toolbars:** with liquid glass on, the player toolbar buttons (subtitles, audio, speed, next episode…) sit in a glass capsule and the close button in a glass circle; they follow the "glass backgrounds on controls" option.
- **Lighter glass in the player:** the glass behind the player controls reads the video at a lower rate and resolution, and almost stops while the video is paused, so it uses less battery and keeps playback smooth.
- **Cleaner controls:** options to remove the glass backgrounds behind player buttons and time pills, and to hide the skip back/forward buttons (double-tap seeking still works).
- **Custom player buttons:** in Playback settings, "Player buttons" lets you choose which buttons stay on the player bar, which sit behind the arrow, and their order (with reset). Buttons that do not apply to the current video are skipped. Picture-in-picture, Cast and watch party in the top bar can be reordered or hidden too.
- **Richer controls:** title logo, Stream Parser badges, source name, clock and end time above the timeline.
- **Dialogue boost, dynamic range compression and volume boost** sliders in the audio panel (volume boost up to +10 dB, remembered per audio output device).
- **Automatic stereo downmix on Bluetooth** (ExoPlayer, phone and tablet): surround audio is decoded with FFmpeg and mixed down to a clean 2.0, so dialogue keeps its level on earbuds and speakers.
- **Subtitles:** Nuvio libass atlas pipeline for ASS/SSA, subtitle style applied to embedded ASS tracks, hold-to-repeat subtitle delay buttons, and optional **auto-sync** for add-on, OpenSubtitles and SubDL subtitles (ASS/SSA matched by dialogue, SRT/VTT fallback).
- **Subtitle settings per device type:** style, appearance, languages and libass options are kept separately for phone, tablet, TV and Windows (synced with the profile), and Settings shows only the ones of the device in use.
- **Provider priority auto-play:** auto-play picks the stream following your preferred provider order.
- Watch progress is saved when leaving the app.
- **Audio delay** (ported from NuvioTV): ±60 s in 25 ms steps, remembered **per audio output** (speaker, Bluetooth, HDMI…), with support for the `behaviorHints.audioDelayMs` stream hint from addons and plugins. Works with ExoPlayer and mpv.
- **Chapters** (ported from luqmanfadlli's fork): chapter marks on the seek bar, the current chapter title above it and a **Chapters** panel to jump to any chapter, on phone, tablet and the TV app. Read from MKV/MP4 files with ExoPlayer and directly from mpv (Android libmpv and iOS).
- **Private Viewing** (ported from luqmanfadlli's fork): a switch in Playback → Player that stops saving watch progress, Continue Watching and watched status and stops scrobbling to connected trackers, for the in-app player and for external player results. Synced with the profile. The stream menu also has **Watch privately** to watch a single movie or episode privately (the following episodes are tracked as usual).
- **Discord Rich Presence** (ported from luqmanfadlli's fork, Android phone and TV): Settings → Integrations → Discord shows what you are watching on your Discord profile (title, episode, poster, play/pause, elapsed/remaining time, optional IMDb button, optional Live TV channel). Log in with the Discord web login; the account and all the presence settings sync with the Nuvio profile, so the TV picks up the account logged in on the phone. Never shown with Private Viewing. Uses the account token like Kizzy, which may go against Discord's Terms of Service.
- **Post-play recommendations** (ported from NuvioTV): near the end of a movie, or of a series with no next episode, a full-screen panel suggests similar titles from the "More like this" source (TMDB, Trakt or Simkl) with backdrop, logo, metadata, **MDBList ratings** and plot. Browse up to 12 titles, **Play** (movies start right away, series open their details), **Details**, **Back to player**, and an in-panel **trailer** that starts after a 5-second countdown (Android full and desktop). Movies follow credits and post-credits scenes when known, otherwise a configurable **Movie Recommendation Timing** (80–100%); series follow the next episode threshold. Works with the TV remote and on desktop. Toggle in Playback → Auto-play, synced with the profile.

## Streams

- **Stream Parser:** an alternative stream card that reads any stream description and shows resolution badge with animated glow, source, video/audio tags, channels, audio languages (flags), size (estimated when missing), cached/torrent/proxied status, with a unique color per tag.
- **Sort by video quality:** merges all addons and plugins into one list ordered 8K → SD.
- **Stream actions menu** (⋮ button) with **"Original description"** to see and copy the raw addon text.
- **Shown qualities filter:** hide the resolutions you don't want in the stream list.
- Full plot in the tablet and TV stream selection panel.
- Plugin "group streams by repository" preference now syncs with the profile.

## Integrations

- **Reorganized Integrations page** with sections for metadata (TMDB, MDBList), timestamps, lists/trackers, subtitles and extras, each service with its own page.
- **Timestamps:** AniSkip, IntroDB and **SkipDB**, with a provider priority order.
- **Trackers and lists:** **MyAnimeList**, **Kitsu**, **AniList** and **WeTrakr** (device code login), with login sync, library integration and a **per-service library picker**.
- **Telegram:** login and stream search from Telegram (images, GIFs and short clips filtered out).
- **AI Assistant** integration.
- Every integration is listed in Settings → Licenses and attributions.

## CloudStream plugins

- CloudStream plugin runtime with storage and profile sync: plugins provide sources, catalogs and optionally full details pages. Dedicated settings page.

## Network

- **DNS over HTTPS** (Settings → Advanced → Network): Cloudflare, Google, Quad9, AdGuard, Mullvad, OpenDNS or a custom resolver, fallback to the device DNS, test button and optional sync with the profile. Used by every network client in the app (addons, images, downloads, media proxy…).

## Appearance and interface

- **Full AMOLED mode:** extra toggle that also turns surfaces and cards pure black.
- **See-through details buttons:** over the cinematic or dominant color background, the details buttons and chips (actions, seasons, trailers) become see-through so the artwork shows behind them; can be turned off in the details page settings.
- **Round profile buttons:** on phones, edit/switch profile sit as round icon buttons on the profile header and Edit Profile saves from a round button in the top bar; can be turned off in Appearance to get the full-width buttons back.
- **Compact theme picker:** in Appearance the theme list is folded into one row showing the current theme; tap it to open the full list.
- **New sliders:** settings sliders (playback, streams, posters, theme colors) have a slimmer track and a round thumb in the theme accent color.
- **Liquid glass navigation bar:** with liquid glass on, the floating bar can be Liquid, Frosted, Clear or Solid, with adjustable blur, refraction and tint, toggles for vibrancy, edge highlight, shadow, chromatic aberration and a liquid lens under the selected tab, plus a custom color for the unselected icons (Navigation bar sheet, with reset).
- **Liquid glass effects:** optional toggle (off by default, not on TV) that turns sheets, player controls and panels, the skip button, stream cards and stream filters into liquid glass, refracting the video or the content behind them. With the cinematic background, the streams screen and the details page (phone) refract the artwork too. Liquid glass, the navigation bar glass, the see-through and round buttons, and the player options (glass backgrounds, skip buttons, player buttons) are synced with the profile.
- **Liquid glass on Windows:** the desktop app gets the same liquid glass option: glass sheets and the floating navigation bar with its jelly selection and glass styles (the extra glow of the phone bar is not used on desktop). The player controls are glass too, refracting a small copy of the video that almost stops while it is paused.
- **Date format** presets (system, DD/MM/YYYY, MM/DD/YYYY, YYYY-MM-DD, DD MMM YYYY…) applied to every date in the app.
- **Episode code format:** S1 E2, S01E02, S1E2, 01x02, 1x2.
- **Expandable episode overviews** with an animated layout in both episode list styles.
- **Landscape posters** applied everywhere (grids, catalogs, library, search, collection folders) without ever cropping portrait posters; collection folders respect their own shape.
- **Horizontal list** episode card style.
- **Redesigned Account settings page.**
- Localized country and language names; TMDB backdrops and posters in the Trailers & Images section.
- **Gradient primary action buttons** with icons (Play, Resume, Choose episodes, View details).
- **Top 10 rows** on the home (phone, tablet and TV): two ranked rows with the first 10 titles of a movie catalog and a series catalog, drawn with big numbers next to the posters. Catalogs chosen in Settings → Content discovery → Top 10, synced per profile.
- New Live TV navigation icon.
- **Complete Italian translation.**

## Diagnostics

- **In-app logger** (Settings → Advanced → Diagnostics): records app logs for 24 hours, filters by level and multiple categories, live updates, copy, share as a file, import the process logcat (Android). Secrets, tokens and addon configs are redacted before saving. Includes a **startup timing trace**.
- **Faster startup:** the system splash stays until the Home shows its first content, and heavy work (Supabase client, Cast, saved downloads, Telegram check, release notifications) is moved off the critical path.

## Sync and settings

- **Settings search** covers every option, ignoring case and accents, with result highlight and an **"Only Enhanced features"** filter.
- Additional settings synced with the profile: hero toggle and hero sources, media proxy options, logger toggle, Live TV preferences, plugin grouping, Stream Parser options.
- Fixed ANRs (app freezes) when opening Settings while the Home was loading.

## Releases and updates

- Releases published at [albyalex96/NuvioMobile-V2](https://github.com/albyalex96/NuvioMobile-V2/releases) with automatic release notes, separate APKs for phone and TV and an MSI installer for Windows.
- Redesigned changelog panel with the notes of the installed version, optional background auto-download of updates (testing).
- **What's new in previous versions:** a version selector with the changelog of every earlier release, laid out like the one of the latest version. The update banner hides while a video plays and comes back on the other pages.
- Centralized versioning in `version.json` with automatic version code.
- Own app identity (`com.nuvioenhanced.alby`, "Nuvio Enhanced"): installs alongside the official app.
- The updater downloads releases from this fork and picks the right APK (phone or TV) for the installed app.
