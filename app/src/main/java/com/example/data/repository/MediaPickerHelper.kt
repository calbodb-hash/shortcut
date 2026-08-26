package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.domain.model.*
import java.util.UUID

object MediaPickerHelper {

    fun createStockSampleClips(): List<VideoClip> {
        return listOf(
            VideoClip(
                id = UUID.randomUUID().toString(),
                sourceUri = "sample://video/neon_city_intro.mp4",
                mediaType = MediaType.VIDEO,
                name = "Cyber City Beat.mp4",
                sourceDurationMs = 6000L,
                sourceStartTimeMs = 0L,
                sourceEndTimeMs = 6000L,
                timelineStartMs = 0L,
                speed = 1.0f,
                thumbnailColorSeed = 0xFF8A2BE2,
                transform = Transform(scale = 1.0f)
            ),
            VideoClip(
                id = UUID.randomUUID().toString(),
                sourceUri = "sample://video/sunset_skate_drop.mp4",
                mediaType = MediaType.VIDEO,
                name = "Sunset Skate Drop.mp4",
                sourceDurationMs = 8000L,
                sourceStartTimeMs = 500L,
                sourceEndTimeMs = 6500L,
                timelineStartMs = 6000L,
                speed = 1.2f,
                thumbnailColorSeed = 0xFFFF4500,
                transform = Transform(scale = 1.05f)
            ),
            VideoClip(
                id = UUID.randomUUID().toString(),
                sourceUri = "sample://video/synth_outro_burst.mp4",
                mediaType = MediaType.VIDEO,
                name = "Synth Glitch Outro.mp4",
                sourceDurationMs = 4500L,
                sourceStartTimeMs = 0L,
                sourceEndTimeMs = 4500L,
                timelineStartMs = 11000L,
                speed = 1.0f,
                thumbnailColorSeed = 0xFF00CED1,
                transform = Transform()
            )
        )
    }

    fun parseMediaUri(context: Context, uri: Uri): VideoClip {
        // Attempt to persist URI permission for long-term project access
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.d("MediaPickerHelper", "Persistable permission not supported for URI: $uri")
        }

        val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
        val uriStr = uri.toString().lowercase()
        val isImage = mimeType.startsWith("image") ||
                uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") ||
                uriStr.endsWith(".png") || uriStr.endsWith(".webp") || uriStr.endsWith(".gif")

        var name: String? = null

        // Try querying OpenableColumns.DISPLAY_NAME
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("MediaPickerHelper", "Failed to query display name: ${e.message}")
        }

        var durationMs = if (isImage) 3000L else 5000L

        if (!isImage) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    val parsed = durationStr.toLongOrNull()
                    if (parsed != null && parsed > 0L) {
                        durationMs = parsed
                    }
                }
                val titleStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (name.isNullOrBlank() && !titleStr.isNullOrBlank()) {
                    name = titleStr
                }
                retriever.release()
            } catch (e: Exception) {
                Log.d("MediaPickerHelper", "Failed to extract video metadata: ${e.message}")
            }
        }

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: if (isImage) "Photo_${UUID.randomUUID().toString().take(4)}" else "Video_${UUID.randomUUID().toString().take(4)}"
        }

        val colorSeed = 0xFF000000L or (uri.hashCode().toLong() and 0x00FFFFFFL) or 0x00222222L

        return VideoClip(
            id = UUID.randomUUID().toString(),
            sourceUri = uri.toString(),
            mediaType = if (isImage) MediaType.IMAGE else MediaType.VIDEO,
            name = name ?: "Clip",
            sourceDurationMs = durationMs,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = durationMs,
            timelineStartMs = 0L,
            thumbnailColorSeed = colorSeed
        )
    }
}

