package com.nuvio.app

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.disk.DiskCache
import coil3.map.Mapper
import coil3.memory.MemoryCache
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Size
import okio.Path.Companion.toOkioPath
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.svg.SvgDecoder
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.app.core.logging.AppLogPlatform
import com.nuvio.app.core.logging.AppLogger
import com.nuvio.app.core.logging.StartupTrace
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.core.network.NuvioDns
import com.nuvio.app.core.network.dns.DnsOverHttpsRepository
import com.nuvio.app.core.network.dns.DnsOverHttpsStorage
import com.nuvio.app.features.player.seekthumb.SeekThumbnailPlatform
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        StartupTrace.mark("Application.onCreate")
        AppLogPlatform.initialize(this)
        AppLogger.install(
            debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        // Before any request (workers included): every OkHttp client resolves through NuvioDns.
        DnsOverHttpsStorage.initialize(this)
        DnsOverHttpsRepository.ensureLoaded()
        LanMediaProxy.initialize(this)
        SeekThumbnailPlatform.initialize(this)
        StartupTrace.mark("Application.onCreate done")
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val tv = isTvPackage(context)
        Logger.withTag("ImageLoader").i { "Creating image loader (tv=$tv, 16 requests per host)" }
        return ImageLoader.Builder(context)
            .crossfade(!tv)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .apply { if (tv) configureForTv(context) }
            .components {
                add(CustomPosterFallbackInterceptor())
                if (tv) add(TmdbOriginalSizeMapper)
                add(SvgDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = {
                            HttpClient(OkHttp) {
                                engine {
                                    config {
                                        dns(NuvioDns)
                                        // OkHttp's default allows 5 parallel requests per host: the home asks
                                        // dozens of posters from the same CDN at startup and they queue up.
                                        dispatcher(okhttp3.Dispatcher().apply {
                                            maxRequests = 32
                                            maxRequestsPerHost = 16
                                        })
                                        if (tv) {
                                            // NuvioTV timeouts: a stalled image must not hold a slot for long.
                                            connectTimeout(4, TimeUnit.SECONDS)
                                            readTimeout(5, TimeUnit.SECONDS)
                                            callTimeout(8, TimeUnit.SECONDS)
                                        }
                                    }
                                }
                            }
                        },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    /**
     * Image settings of NuvioTV for TV boxes (Fire TV sticks have 1-2 GB of RAM and a slow GPU):
     * RGB565 bitmaps on the heap (half the memory of ARGB_8888; images with transparency, such as
     * logos, keep ARGB_8888), inexact sizes so smaller cached versions are reused, and a memory
     * cache sized on the device RAM. No crossfade: cards draw their image at once.
     */
    private fun ImageLoader.Builder.configureForTv(context: android.content.Context): ImageLoader.Builder {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val cachePercent = when {
            totalRamMb <= 2048 -> 0.15
            totalRamMb <= 3072 -> 0.20
            else -> 0.25
        }
        // Boxes under ~2.3 GB (Fire TV sticks): at most 40 MB of decoded pictures, about three
        // Home screens of posters plus a backdrop, so the pictures leave room for the rest.
        val smallBox = totalRamMb < SMALL_BOX_RAM_MB
        val memoryCacheBytes = MemoryCache.Builder().maxSizePercent(context, cachePercent).build().maxSize
            .let { budget -> if (smallBox) minOf(budget, SMALL_BOX_PICTURE_CACHE_BYTES) else budget }
        // Nothing is drawn larger than the screen: its longer side bounds every decode.
        val metrics = context.resources.displayMetrics
        val maxSide = maxOf(metrics.widthPixels, metrics.heightPixels).let { side ->
            if (side <= 0) FALLBACK_MAX_BITMAP_SIDE else side.coerceAtLeast(MIN_MAX_BITMAP_SIDE)
        }
        // More room for posters on disk when the box has plenty of free storage.
        val freeStorage = runCatching { context.cacheDir.usableSpace }.getOrDefault(0L)
        val diskCacheBytes = when {
            freeStorage > 10L * 1024 * MB -> 1024L * MB
            totalRamMb >= 3_300 -> 300L * MB
            else -> 200L * MB
        }
        Logger.withTag("ImageLoader").i {
            "TV image settings: RAM ${totalRamMb} MB, memory cache ${memoryCacheBytes / MB} MB" +
                "${if (smallBox) " (small box)" else ""}, max picture side ${maxSide}px, disk cache ${diskCacheBytes / MB} MB"
        }
        return this
            .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryCacheBytes).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(diskCacheBytes)
                    .build()
            }
            .maxBitmapSize(Size(maxSide, maxSide))
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .allowRgb565(true)
    }

    private fun isTvPackage(context: android.content.Context): Boolean =
        TvAppRuntime.isTvApp || context.packageName.let { it.endsWith(".tv") || ".tv." in it }
}

private const val MB = 1024L * 1024
private const val SMALL_BOX_RAM_MB = 2_355L
private const val SMALL_BOX_PICTURE_CACHE_BYTES = 40L * MB
private const val FALLBACK_MAX_BITMAP_SIDE = 1920
private const val MIN_MAX_BITMAP_SIDE = 1280

/**
 * TMDB pictures asked at original size (some catalogs serve multi-MB `/t/p/original/` URLs, slow
 * to download and decode) are fetched at the TMDB size closest to the card instead. Only
 * poster-sized targets (up to 780 px) change; screen-sized backdrops keep the original.
 */
private val TmdbOriginalSizeMapper = Mapper<String, String> { data, options ->
    val width = (options.size.width as? Dimension.Pixels)?.px
    if (!data.startsWith(TMDB_ORIGINAL_PREFIX) || width == null || width !in 1..780) return@Mapper null
    val bucket = when {
        width <= 342 -> "w342"
        width <= 500 -> "w500"
        else -> "w780"
    }
    data.replace("/t/p/original/", "/t/p/$bucket/")
}

private const val TMDB_ORIGINAL_PREFIX = "https://image.tmdb.org/t/p/original/"
