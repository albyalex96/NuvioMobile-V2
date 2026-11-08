<#
Baseline Profile for the phone app or the TV app, on a device connected with adb (Android 13+).

  .\baselineprofile\generate.ps1 -App phone -Step install    # 1. installs the benchmark app
      (open it on the device, sign in, enable "remember last profile", reach the home)
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
    Write-Host "Installed $package. Open it, sign in, enable 'remember last profile' and reach the home, then run -Step generate."
    exit 0
}

$outputDir = Join-Path $root "baselineprofile\build\outputs\connected_android_test_additional_output"
if (Test-Path $outputDir) { Remove-Item -Recurse -Force $outputDir }

& .\gradlew.bat ":baselineprofile:connectedBenchmarkAndroidTest" `
    "-Pnuvio.android.distribution=full" `
    "-Pandroid.testInstrumentationRunnerArguments.targetPackage=$package" `
    "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
if ($LASTEXITCODE -ne 0) { throw "Generation failed" }

$profile = Get-ChildItem -Path $outputDir -Recurse -Filter "*-baseline-prof.txt" |
    Where-Object { $_.Name -notlike "*startup-prof*" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $profile) { throw "No baseline profile found in $outputDir" }

$target = Join-Path $root "$module\src\main\baseline-prof.txt"
Copy-Item $profile.FullName $target -Force
$lines = (Get-Content $target | Measure-Object -Line).Lines
Write-Host "Baseline profile copied to $target ($lines rules)."
