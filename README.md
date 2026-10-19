<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <h3>Nuvio Enhanced</h3>

  <p>
    An unofficial fork of <a href="https://github.com/NuvioMedia/NuvioMobile">Nuvio</a>, the free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Releases](https://github.com/albyalex96/NuvioMobile-V2/releases/latest) · [What's new in Enhanced](./ENHANCEMENTS.md) · [Novità di Enhanced (IT)](./ENHANCEMENTS.it.md) · [Original project](https://github.com/NuvioMedia/NuvioMobile) · [Support Nuvio](https://nuvio.tv/support)

</div>

## About this fork

Nuvio Enhanced is maintained at [albyalex96/NuvioMobile-V2](https://github.com/albyalex96/NuvioMobile-V2) (branch `enhanced/v2`) and regularly merges the upstream [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) (`cmp-rewrite`). It keeps everything from the original app and adds extra features: a dedicated Android TV app, Live TV with EPG, Chromecast, HLS downloads, the Stream Parser, audio delay, an in-app logger and much more.

The full list is in [ENHANCEMENTS.md](./ENHANCEMENTS.md) (English) and [ENHANCEMENTS.it.md](./ENHANCEMENTS.it.md) (Italiano).

> This is not the official Nuvio app. Please report issues with Enhanced features [here](https://github.com/albyalex96/NuvioMobile-V2/issues), not to the original project.

## Get Nuvio Enhanced

All builds are published on the [GitHub releases page](https://github.com/albyalex96/NuvioMobile-V2/releases/latest):

- **Android phone/tablet:** `Nuvio-Enhanced-Alby-<version>-<abi>.apk` (package `com.nuvioenhanced.alby`)
- **Android TV:** `Nuvio-Enhanced-Alby-TV-<version>-<abi>.apk` (package `com.nuvioenhanced.alby.tv`)

Pick the APK that matches your device ABI (usually `arm64-v8a`). Enhanced installs side by side with the official app, and the built-in updater only offers the APK for the app you are using (phone or TV).

iOS builds are not distributed by this fork; you can build them from source (see below).

Looking for the official app? Get it from [nuvio.tv](https://nuvio.tv), [Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app) or the [official releases](https://github.com/NuvioMedia/NuvioMobile/releases/latest).

## Build from source

```bash
git clone -b enhanced/v2 https://github.com/albyalex96/NuvioMobile-V2.git
cd NuvioMobile-V2
```

The app version is set in [`version.json`](./version.json).

### Android

Android development requires Android Studio and the Android SDK.

```bash
# Phone / tablet app
./gradlew :androidApp:assembleFullDebug

# Android TV app
./gradlew :tvApp:assembleFullDebug
```

### iOS

iOS development requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## Credits

Nuvio is created by the [NuvioMedia](https://github.com/NuvioMedia) team: all credit for the original app goes to them. Parts of the Android TV app, the layout settings and the audio delay are ported from [NuvioTV](https://github.com/NuvioMedia/NuvioTV); Live TV and EPG are based on [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced).

## License

[GNU General Public License v3.0](./LICENSE)
