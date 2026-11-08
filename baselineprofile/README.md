# Baseline Profile

The Baseline Profile lists the classes and methods used at startup and on the home. Android
compiles them when the app is installed, so the first launches are as fast as the later ones.
The files are `androidApp/src/main/baseline-prof.txt` (phone) and
`tvApp/src/main/baseline-prof.txt` (TV); release builds include them automatically.

Regenerate them from time to time (for example before a major release), because new code is
not in the profile.

## Requirements

- A device or emulator connected with adb, Android 13 or later (no root needed).
- The TV app can be generated on a phone: it runs the same code.

## Steps

```powershell
.\baselineprofile\generate.ps1 -App phone -Step install
# on the device: open "Nuvio" (benchmark), sign in, enable "remember last profile", reach the home
.\baselineprofile\generate.ps1 -App phone -Step generate
```

Then the same with `-App tv`. The script copies the result into the app's `src/main`.

The benchmark apps (`com.nuvioenhanced.alby.benchmark`, `com.nuvioenhanced.alby.tv.benchmark`)
are release builds without R8, signed with the debug key; they do not replace the installed
release or debug apps.
