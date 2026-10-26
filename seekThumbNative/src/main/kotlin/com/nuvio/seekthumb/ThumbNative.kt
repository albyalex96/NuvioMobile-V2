package com.nuvio.seekthumb

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * JNI bridge to libnuvioseekthumb (src/main/cpp/thumb_shim.cpp): a software keyframe decoder over MPV's FFmpeg.
 * Ported from Nuvio Arc (GPL-3.0). Loading and logging are left to the caller.
 */
object ThumbNative {
    const val INFO_WIDTH = 0
    const val INFO_HEIGHT = 1
    const val INFO_BIT_DEPTH = 2
    const val INFO_SAR_NUM = 3
    const val INFO_SAR_DEN = 4
    const val INFO_TRC = 5
    const val INFO_PRIMARIES = 6
    const val INFO_MATRIX = 7
    const val INFO_RANGE = 8
    const val INFO_MAX_CLL = 9
    const val INFO_MASTERING_MAX_NITS = 10
    const val INFO_DOVI_IPT = 11
    const val INFO_STRIPS = 12
    const val INFO_COUNT = 13

    /** Loads the JNI library. Throws [UnsatisfiedLinkError] when it is missing. */
    fun loadLibrary() {
        System.loadLibrary("nuvioseekthumb")
    }

    @JvmStatic external fun nativeInit(): Boolean
    @JvmStatic external fun nativeLastError(): String
    @JvmStatic external fun nativeBackend(): String
    /** [decoder]: FFmpeg decoder name. width/height: coded size. */
    @JvmStatic external fun nativeOpen(decoder: String, extradata: ByteArray?, width: Int, height: Int, threads: Int): Long
    @JvmStatic external fun nativeDecode(handle: Long, data: ByteArray, len: Int, info: IntArray): Int
    /** As [nativeDecode], from a direct buffer: the native side copies from its address, no Java array. */
    @JvmStatic external fun nativeDecodeDirect(handle: Long, data: ByteBuffer, len: Int, info: IntArray): Int
    @JvmStatic external fun nativeRender(
        handle: Long, bitmap: Bitmap, transfer: Int, bt2020: Boolean, fullRange: Boolean,
        peakNits: Float, rowSkip: Boolean,
    ): Int
    /** Strips an Annex B HEVC keyframe would decode in, 0 = whole. */
    @JvmStatic external fun nativeStripCount(data: ByteArray, len: Int): Int
    @JvmStatic external fun nativeTrim(handle: Long)
    @JvmStatic external fun nativeRelease(handle: Long)
    /** Returns freed allocator pages to the system (mallopt M_PURGE). */
    @JvmStatic external fun nativePurge()
}
