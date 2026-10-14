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
val appVersionName = (appVersion["versionName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    ?: error("versionName is missing from ${appVersionFile.path}")
val appVersionCode = (appVersion["versionCode"] as? Number)?.toInt()?.takeIf { it > 0 }
    ?: error("versionCode is missing or invalid in ${appVersionFile.path}")

extra["nuvio.app.versionName"] = appVersionName
extra["nuvio.app.versionCode"] = appVersionCode

rootProject.file("iosApp/Configuration/Version.xcconfig").let { xcconfig ->
    val content = """
        |// Generated from version.json by Gradle. Do not edit: change version.json instead.
        |CURRENT_PROJECT_VERSION=$appVersionCode
        |MARKETING_VERSION=$appVersionName
        |""".trimMargin()
    if (!xcconfig.exists() || xcconfig.readText() != content) xcconfig.writeText(content)
}
