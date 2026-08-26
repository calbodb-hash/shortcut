package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object MediaFrameLoader {

    private const val TAG = "MediaFrameLoader"

    // Max 24MB bitmap memory cache to stay extremely light on low-end phones
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(4096, 24576) // 4MB to 24MB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun loadFrame(
        context: Context,
        uriString: String,
        timeMs: Long = 0L,
        targetWidth: Int = 480,
        targetHeight: Int = 480
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || uriString.startsWith("sample://")) {
            return@withContext null
        }

        // Quantize timeMs to 200ms buckets for effective cache reuse during playback/scrubbing
        val bucketTimeMs = (timeMs / 200L) * 200L
        val cacheKey = "$uriString-$bucketTimeMs-$targetWidth-$targetHeight"

        synchronized(memoryCache) {
            memoryCache.get(cacheKey)?.let { return@withContext it }
        }

        val uri = Uri.parse(uriString)
        val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
        val isImage = mimeType.startsWith("image") ||
                uriString.endsWith(".jpg", true) ||
                uriString.endsWith(".jpeg", true) ||
                uriString.endsWith(".png", true) ||
                uriString.endsWith(".webp", true)

        val bitmap: Bitmap? = if (isImage) {
            decodeImageBitmap(context, uri, targetWidth, targetHeight)
        } else {
            extractVideoFrame(context, uri, timeMs, targetWidth, targetHeight)
        }

        if (bitmap != null) {
            synchronized(memoryCache) {
                memoryCache.put(cacheKey, bitmap)
            }
        }

        return@withContext bitmap
    }

    private fun decodeImageBitmap(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > targetHeight || options.outWidth > targetWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Half memory per pixel
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }

            // Check orientation from Exif if available
            decoded?.let { bmp ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val orientation = getExifOrientation(stream)
                        if (orientation != 0f) {
                            val matrix = Matrix().apply { postRotate(orientation) }
                            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                        } else bmp
                    } ?: bmp
                } catch (e: Exception) {
                    bmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding image URI: $uri", e)
            null
        }
    }

    private fun extractVideoFrame(
        context: Context,
        uri: Uri,
        timeMs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val timeUs = (timeMs * 1000L).coerceAtLeast(0L)

            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidth,
                        targetHeight
                    )
                } catch (e: Throwable) {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            // Downscale if necessary
            if (frame != null && (frame.width > targetWidth * 1.5 || frame.height > targetHeight * 1.5)) {
                val scale = minOf(targetWidth.toFloat() / frame.width, targetHeight.toFloat() / frame.height)
                if (scale < 1.0f && scale > 0f) {
                    val scaled = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                    if (scaled != frame) {
                        frame.recycle()
                    }
                    scaled
                } else frame
            } else {
                frame
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice extracting video frame at ${timeMs}ms: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun getExifOrientation(inputStream: InputStream): Float {
        return try {
            val exifInterface = ExifInterface(inputStream)
            when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
