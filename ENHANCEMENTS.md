# Nuvio Enhanced — Enhancements

Everything Nuvio Enhanced adds on top of the original [Nuvio](https://github.com/NuvioMedia/NuvioMobile). Features from the upstream app (including Watch Party, MDBList lists and the P2P engine) are kept and merged regularly; this page only lists what is specific to this fork.

🇮🇹 [Versione italiana](./ENHANCEMENTS.it.md) · [Back to README](./README.md)

Most settings are saved per profile and synced with your Nuvio account, so they follow you across devices.

---

## Contents

- [Android TV app](#android-tv-app)
- [Live TV and EPG](#live-tv-and-epg)
- [Chromecast and media proxy](#chromecast-and-media-proxy)
- [Downloads](#downloads)
- [Player](#player)
- [Streams](#streams)
- [Appearance and interface](#appearance-and-interface)
- [Diagnostics](#diagnostics)
- [Sync and settings](#sync-and-settings)
- [Releases and updates](#releases-and-updates)

---

## Android TV app

A dedicated Android TV app, released as a separate APK (`Nuvio-Enhanced-Alby-TV-…`). It shares all the logic of the phone app (accounts, profiles, sync, streams, player) and adds a remote-friendly interface inspired by [NuvioTV](https://github.com/NuvioMedia/NuvioTV).

- **Navigation:** NuvioTV-style "modern sidebar" with a floating pill showing the current section, or an optional **glass top navigation bar** (icons + labels or icons only, with optional glow).
- **Home:** hero area with an **auto-advancing carousel** (choose up to 2 hero catalogs, synced with the profile), rows sized like NuvioTV, Continue Watching in three styles (Card, Wide, Poster) with badges for time left, next episode, new episode and new season, optional focused-poster expansion.
- **"TV Layout" settings page:** poster width and corner radius presets, landscape posters, labels under posters, Continue Watching style, poster expansion delay, full-screen hero backdrop, plus shortcuts to the shared Home and Continue Watching options.
- **Details page:** full-screen backdrop, NuvioTV-style hero, play button (hold OK to choose the source), library/watched/shuffle buttons, MDBList ratings, season posters or text tabs, horizontal or list episode cards (follows the phone setting, with blurred unwatched episodes and expandable overviews), **per-episode IMDb ratings section**, clickable cast that opens the person page, collection and similar titles.
- **Card options menu:** hold OK or press Menu on any poster or Continue Watching card: view details, watch a random episode, resume/play from start, choose source, mark as watched (without flicker in Continue Watching), add/remove from library, remove from Continue Watching. Streams have their own TV menu too.
- **Continue Watching on the Android TV home screen:** the row is published to the launcher as a Nuvio "Continue watching" channel and in the "Play next" row (with progress and episode artwork). Opening an item can show the card options menu (resume, choose source and play, details…), resume directly, open the stream list of the right episode or open the details page. Configurable in TV Layout → Android TV home screen.
- **Remote control in the player:** OK play/pause, left/right cumulative seek with preview (step grows while held), focus on the timeline, skip intro/next episode prompts, panels and dialogs that receive focus automatically.
- **Focus handling everywhere:** focus follows navigation, never gets lost in hidden tabs, settings work in levels (categories → panel → sub-pages, Back goes up one level), dialogs focus the primary button, text fields open the keyboard only when you press OK.
- **QR code sign-in** (same device-link flow as NuvioTV) with email/password as a fallback.
- **Voice search** with the system speech recognizer.
- Centered scrolling with fading edges in Settings, visible focus on profile selection and filter chips.

## Live TV and EPG

Ported from [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) and extended.

- Sources: **M3U playlists** (URL or local file), **Xtream** and **Stalker** providers.
- **XMLTV EPG** (`.xml` / `.xml.gz`), auto-detected from playlists and providers, matched by `tvg-id` or channel name; the current program is shown in the player.
- Dedicated Live TV tab (phone, tablet, TV and iOS native tab bar), favorites, search and categories.
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
- **Background downloads section:** battery optimization status and shortcut, with tips for manufacturers with aggressive power saving.

## Player

- **Custom seek interval** (1–20 seconds) for on-screen buttons, double tap (cumulative), notification/media session and iOS remote commands, with a dynamic icon.
- **"Still watching?" prompt** (Netflix style): after a configurable number of consecutive auto-played episodes, optionally only at night (22:00–04:00), with a 10-second countdown before the player closes. A separate time-based version for **Live TV** (15 min – 6 h).
- **Audio delay** (ported from NuvioTV): ±60 s in 25 ms steps, remembered **per audio output** (speaker, Bluetooth, HDMI…), with support for the `behaviorHints.audioDelayMs` stream hint from addons and plugins. Works with ExoPlayer and mpv.
- **Chapters** (ported from luqmanfadlli's fork): chapter marks on the seek bar, the current chapter title above it and a **Chapters** panel to jump to any chapter, on phone, tablet and the TV app. Read from MKV/MP4 files with ExoPlayer and directly from mpv (Android libmpv and iOS).
- **Private Viewing** (ported from luqmanfadlli's fork): a switch in Playback → Player that stops saving watch progress, Continue Watching and watched status and stops scrobbling to connected trackers, for the in-app player and for external player results. Synced with the profile. The stream menu also has **Watch privately** to watch a single movie or episode privately (the following episodes are tracked as usual).
- **Discord Rich Presence** (ported from luqmanfadlli's fork, Android phone and TV): Settings → Integrations → Discord shows what you are watching on your Discord profile (title, episode, poster, play/pause, elapsed/remaining time, optional IMDb button, optional Live TV channel). Log in with the Discord web login; the account and all the presence settings sync with the Nuvio profile, so the TV picks up the account logged in on the phone. Never shown with Private Viewing. Uses the account token like Kizzy, which may go against Discord's Terms of Service.

## Streams

- **Stream Parser:** an alternative stream card that reads any stream description and shows resolution badge with animated glow, source, video/audio tags, channels, audio languages (flags), size (estimated when missing), cached/torrent/proxied status, with a unique color per tag.
- **Sort by video quality:** merges all addons and plugins into one list ordered 8K → SD.
- **Stream actions menu** (⋮ button) with **"Original description"** to see and copy the raw addon text.
- Plugin "group streams by repository" preference now syncs with the profile.

## Appearance and interface

- **Full AMOLED mode:** extra toggle that also turns surfaces and cards pure black.
- **Date format** presets (system, DD/MM/YYYY, MM/DD/YYYY, YYYY-MM-DD, DD MMM YYYY…) applied to every date in the app.
- **Episode code format:** S1 E2, S01E02, S1E2, 01x02, 1x2.
- **Expandable episode overviews** with an animated layout in both episode list styles.
- **Landscape posters** applied everywhere (grids, catalogs, library, search, collection folders) without ever cropping portrait posters; collection folders respect their own shape.
- **Gradient primary action buttons** with icons (Play, Resume, Choose episodes, View details).
- New Live TV navigation icon.
- **Complete Italian translation.**

## Diagnostics

- **In-app logger** (Settings → Advanced → Diagnostics): records app logs for 24 hours, filters by level and multiple categories, live updates, copy, share as a file, import the process logcat (Android). Secrets, tokens and addon configs are redacted before saving.

## Sync and settings

- **Settings search** covers every option, ignoring case and accents.
- Additional settings synced with the profile: hero toggle and hero sources, media proxy options, logger toggle, Live TV preferences, plugin grouping, Stream Parser options.
- Fixed ANRs (app freezes) when opening Settings while the Home was loading.

## Releases and updates

- Releases published at [albyalex96/NuvioMobile-V2](https://github.com/albyalex96/NuvioMobile-V2/releases) with automatic release notes, separate APKs for phone and TV.
- Centralized versioning in `version.json` with automatic version code.
- Own app identity (`com.nuvioenhanced.alby`, "Nuvio Enhanced"): installs alongside the official app.
- The updater downloads releases from this fork and picks the right APK (phone or TV) for the installed app.
