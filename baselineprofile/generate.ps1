<#
Baseline Profile for the phone app or the TV app, on a device connected with adb (Android 13+).

  .\baselineprofile\generate.ps1 -App phone -Step install    # 1. installs the benchmark app
      (no sign-in needed: the generator continues without an account)
  .\baselineprofile\generate.ps1 -App phone -Step generate   # 2. records and copies the profile

Same with -App tv. The benchmark apps have their own package (".benchmark"), so they do not
touch the installed release or debug apps.
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet("phone", "tv")][string]$App,
    [Parameter(Mandatory = $true)][ValidateSet("install", "generate")][string]$Step
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$module = if ($App -eq "tv") { "tvApp" } else { "androidApp" }
$package = if ($App -eq "tv") { "com.nuvioenhanced.alby.tv.benchmark" } else { "com.nuvioenhanced.alby.benchmark" }

if ($Step -eq "install") {
    & .\gradlew.bat ":${module}:installFullBenchmark" "-Pnuvio.android.distribution=full"
    if ($LASTEXITCODE -ne 0) { throw "Install failed" }
    Write-Host "Installed $package. Now run -Step generate."
    exit 0
}

# The test runs through adb, not the Gradle connected test: that one reinstalls the target app
# (and uninstalls it after a failure), which wipes the sign-in done in the install step.
# Note: the benchmark library reinstalls the app before recording (its data is wiped); the
# generator continues without an account, so no sign-in is needed.
& .\gradlew.bat ":baselineprofile:assembleBenchmark" "-Pnuvio.android.distribution=full"
if ($LASTEXITCODE -ne 0) { throw "Test APK build failed" }

$testApk = Join-Path $root "baselineprofile\build\outputs\apk\benchmark\baselineprofile-benchmark.apk"
adb install -r -t $testApk
if ($LASTEXITCODE -ne 0) { throw "Test APK install failed (accept the USB install prompt on the device)" }

$deviceOutput = "/sdcard/Android/media/com.nuvio.baselineprofile"
adb shell "rm -rf $deviceOutput" | Out-Null

$result = adb shell am instrument -w `
    -e targetPackage $package `
    -e class com.nuvio.baselineprofile.BaselineProfileGenerator `
    com.nuvio.baselineprofile/androidx.test.runner.AndroidJUnitRunner
$result | Write-Host
if (($result -join "`n") -notmatch "OK \(1 test\)") { throw "Generation failed" }

$outputDir = Join-Path $root "baselineprofile\build\outputs\device-baseline-profile"
if (Test-Path $outputDir) { Remove-Item -Recurse -Force $outputDir }
New-Item -ItemType Directory -Force $outputDir | Out-Null
adb pull "$deviceOutput/." $outputDir | Out-Null

# The rule writes "<test>-startup-prof.txt" (startup and home rules) plus a timestamped copy.
$profile = Get-ChildItem -Path $outputDir -Recurse -Filter "*-prof.txt" |
    Where-Object { $_.Name -notmatch "\d{4}-\d{2}-\d{2}" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $profile) { throw "No baseline profile found in $outputDir" }

$target = Join-Path $root "$module\src\main\baseline-prof.txt"
Copy-Item $profile.FullName $target -Force
$lines = (Get-Content $target | Measure-Object -Line).Lines
Write-Host "Baseline profile copied to $target ($lines rules)."
