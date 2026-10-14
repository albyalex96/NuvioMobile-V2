plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.sentry.android.gradle) apply false
}

// Single source of truth for the app version (Android + iOS): version.json in the repository root.
// iosApp/Configuration/Version.xcconfig is generated from it so Xcode picks up the same values.
val appVersionFile = rootProject.file("version.json")
@Suppress("UNCHECKED_CAST")
val appVersion = groovy.json.JsonSlurper().parseText(
    providers.fileContents(layout.projectDirectory.file("version.json")).asText.get().removePrefix("﻿"),
) as Map<String, Any?>
val appVersionName = (appVersion["version"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    ?: error("version is missing from ${appVersionFile.path}")
// versionCode is derived from "version" (MAJOR.MINOR.PATCH[-label.N]) so it always grows with
// the version: ((MAJOR * 1000 + MINOR) * 1000 + PATCH) * 100 + (N for pre-releases, 99 for finals).
// A pre-release therefore sorts before its final release. -Pnuvio.app.versionCode overrides it.
fun versionCodeFrom(versionName: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z-]*?\.?(\d+)?)?(?:\+.*)?$""")
        .matchEntire(versionName)
        ?: error("version '$versionName' in ${appVersionFile.path} must look like MAJOR.MINOR.PATCH[-label.N]")
    val (major, minor, patch) = match.destructured.toList().take(3).map(String::toLong)
    require(major <= 20 && minor <= 999 && patch <= 999) {
        "version '$versionName' is out of range for an automatic versionCode"
    }
    val isPreRelease = '-' in versionName.substringBefore('+')
    val preReleaseNumber = match.groupValues[4].toLongOrNull()?.coerceIn(1L, 98L) ?: 1L
    val suffix = if (isPreRelease) preReleaseNumber else 99L
    return (((major * 1_000 + minor) * 1_000 + patch) * 100 + suffix).toInt()
}

val appVersionCode = providers.gradleProperty("nuvio.app.versionCode").orNull
    ?.let { it.trim().toIntOrNull()?.takeIf { code -> code > 0 } ?: error("nuvio.app.versionCode must be a positive integer") }
    ?: versionCodeFrom(appVersionName)

extra["nuvio.app.versionName"] = appVersionName
extra["nuvio.app.versionCode"] = appVersionCode

rootProject.file("iosApp/Configuration/Version.xcconfig").let { xcconfig ->
    val content = """
        |// Generated from version.json by Gradle (versionCode derived from version). Do not edit: change version.json instead.
        |CURRENT_PROJECT_VERSION=$appVersionCode
        |MARKETING_VERSION=$appVersionName
        |""".trimMargin()
    if (!xcconfig.exists() || xcconfig.readText() != content) xcconfig.writeText(content)
}
