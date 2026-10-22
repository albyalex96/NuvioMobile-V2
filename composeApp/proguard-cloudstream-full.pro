# Full builds can load Android CloudStream .cs3 packages dynamically.
# Keep shrinking/obfuscation enabled, but skip R8 optimization passes that become
# prohibitively slow with the CloudStream compatibility runtime dependency graph.
-dontoptimize

# Some CloudStream providers are compiled against fuzzywuzzy and resolve this
# package by its original JVM name from dynamically loaded .cs3 dex files.
-keep class me.xdrop.fuzzywuzzy.** { *; }
-dontwarn me.xdrop.fuzzywuzzy.**

# Dynamic CloudStream providers can link directly against kotlinx.serialization
# ABI classes, so their JVM names must remain available in full release builds.
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Several providers also call CloudStream cryptography helpers directly.
-keep class dev.whyoleg.cryptography.** { *; }
-keep interface dev.whyoleg.cryptography.** { *; }
-dontwarn dev.whyoleg.cryptography.**

# CloudStream providers compiled against the TMDB (uwetrottmann) client are loaded
# dynamically via DexClassLoader and resolve these classes at runtime.
-keep class com.uwetrottmann.tmdb2.** { *; }
-keep interface com.uwetrottmann.tmdb2.** { *; }
-dontwarn com.uwetrottmann.tmdb2.**

# Retrofit is the transport used by the TMDB client above.
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn retrofit2.**

# NewPipe extractor is referenced by the bundled YoutubeExtractor provider.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# keepattributes required so dynamically loaded providers resolve generic signatures.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# CloudStream3 compatibility stubs and runtime API (resolved by name from DexClassLoader providers)
-keep class com.lagradost.cloudstream3.** { *; }
-keepclassmembers class com.lagradost.cloudstream3.** { *; }
-keep class com.lagradost.nicehttp.** { *; }
-keepclassmembers class com.lagradost.nicehttp.** { *; }
-keep class com.lagradost.api.** { *; }
-keepclassmembers class com.lagradost.api.** { *; }

# CloudStream integration runtime — UI state, serialization, plugin metadata
-keep class com.nuvio.app.features.cloudstream.** { *; }
-keep interface com.nuvio.app.features.cloudstream.** { *; }
-keep enum com.nuvio.app.features.cloudstream.** { *; }

# Jackson (used by CloudStream DataStore)
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# DexClassLoader runtime deps — extensions resolve by FQN from host classloader
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# CloudStream skipped optional deps
-dontwarn org.mozilla.javascript.**
-dontwarn com.google.re2j.**


# HTTP and HTML libraries that CloudStream providers link against by their original names
# (NiceHttp exposes OkHttp types; most providers parse pages with Jsoup). Without these,
# release builds crash with NoClassDefFoundError (e.g. okhttp3.Request$Builder).
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okio.**
-keep class org.jsoup.** { *; }
-keep interface org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
