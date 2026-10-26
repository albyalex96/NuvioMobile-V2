// Native keyframe decoder of the seek thumbnails (libnuvioseekthumb.so). A plain Android library because the
// Kotlin Multiplatform Android target of :composeApp cannot build native code.
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.nuvio.seekthumb"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
