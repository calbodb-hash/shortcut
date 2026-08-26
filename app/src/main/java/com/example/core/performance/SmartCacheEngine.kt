package com.example.core.performance

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance smart caching engine for video editor timeline, thumbnails,
 * audio waveforms and partial render segments without causing out-of-memory errors on low-end devices.
 */
class SmartCacheEngine(
    maxMemoryBytes: Int = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(64 * 1024 * 1024)
) {
    // Bitmap Thumbnail Cache
    private val thumbnailCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    // Audio Waveform points cache (normalized float arrays)
    private val waveformCache = LruCache<String, FloatArray>(100)

    // Segment render cache tracking modified timeline regions
    private val renderSegmentCache = mutableMapOf<String, String>()

    fun getThumbnail(clipId: String, timestampMs: Long): Bitmap? {
        val key = "${clipId}_${timestampMs / 500}" // Quantize by 500ms intervals for cache hits
        return thumbnailCache.get(key)
    }

    fun putThumbnail(clipId: String, timestampMs: Long, bitmap: Bitmap) {
        val key = "${clipId}_${timestampMs / 500}"
        thumbnailCache.put(key, bitmap)
    }

    fun getWaveform(audioId: String): FloatArray? {
        return waveformCache.get(audioId)
    }

    fun putWaveform(audioId: String, points: FloatArray) {
        waveformCache.put(audioId, points)
    }

    fun invalidateClip(clipId: String) {
        // Drop cache entries for clip
        thumbnailCache.evictAll()
    }

    fun clearAll() {
        thumbnailCache.evictAll()
        waveformCache.evictAll()
        renderSegmentCache.clear()
    }

    fun getEstimatedMemoryUsedMb(): Float {
        return thumbnailCache.size() / (1024f * 1024f)
    }
}
