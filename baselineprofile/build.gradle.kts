// Baseline Profile generator for the phone app (:androidApp) and the TV app (:tvApp).
// It runs on a connected device (Android 13+, no root needed) against the "benchmark" build
// type: release code without R8, signed with the debug key. See README.md.
plugins {
    alias(libs.plugins.androidTest)
}

android {
    namespace = "com.nuvio.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "full")
    }

    // The phone app is the build target; the TV app is installed separately and selected with
    // the targetPackage instrumentation argument (the test drives any installed package).
    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    // Only the benchmark variant can measure the release-like app.
    beforeVariants(selector().withBuildType("debug")) { it.enable = false }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}
