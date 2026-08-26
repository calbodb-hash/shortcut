package com.example.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.domain.model.AudioClip
import com.example.domain.model.AudioTrackType
import com.example.domain.model.VideoClip
import java.util.UUID

data class AudioFileMetadata(
    val title: String,
    val durationMs: Long,
    val artist: String?,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?
)

data class StockAudioItem(
    val id: String,
    val title: String,
    val category: AudioTrackType,
    val durationMs: Long,
    val description: String,
    val simulatedUri: String
)

object AudioImportHelper {

    val STOCK_PRESETS = listOf(
        // Music
        StockAudioItem(
            id = "stock_music_upbeat",
            title = "Upbeat Energetic Beat",
            category = AudioTrackType.MUSIC,
            durationMs = 15000L,
            description = "High BPM energetic synth hook for viral Shorts",
            simulatedUri = "sample://audio/music/upbeat_energetic.mp3"
        ),
        StockAudioItem(
            id = "stock_music_lofi",
            title = "Chill Sunset Lo-Fi",
            category = AudioTrackType.MUSIC,
            durationMs = 20000L,
            description = "Warm relaxing vinyl beat with mellow chords",
            simulatedUri = "sample://audio/music/chill_lofi.mp3"
        ),
        StockAudioItem(
            id = "stock_music_synthwave",
            title = "Cyber Synth Drift",
            category = AudioTrackType.MUSIC,
            durationMs = 18000L,
            description = "Retro futuristic 80s bass synth rhythm",
            simulatedUri = "sample://audio/music/cyber_synth.mp3"
        ),
        // Sound Effects (SFX)
        StockAudioItem(
            id = "stock_sfx_whoosh",
            title = "Dynamic Fast Whoosh",
            category = AudioTrackType.SFX,
            durationMs = 800L,
            description = "Cinematic high-speed air transition swipe",
            simulatedUri = "sample://audio/sfx/whoosh.mp3"
        ),
        StockAudioItem(
            id = "stock_sfx_impact",
            title = "Heavy Bass Impact",
            category = AudioTrackType.SFX,
            durationMs = 1500L,
            description = "Deep low-end cinematic sub-bass slam",
            simulatedUri = "sample://audio/sfx/bass_impact.mp3"
        ),
        StockAudioItem(
            id = "stock_sfx_shutter",
            title = "Camera Shutter Click",
            category = AudioTrackType.SFX,
            durationMs = 600L,
            description = "Mechanical DSLR camera shutter snapshot",
            simulatedUri = "sample://audio/sfx/camera_shutter.mp3"
        ),
        StockAudioItem(
            id = "stock_sfx_glitch",
            title = "Cyber Digital Glitch",
            category = AudioTrackType.SFX,
            durationMs = 900L,
            description = "Distorted electronic data stutter burst",
            simulatedUri = "sample://audio/sfx/cyber_glitch.mp3"
        ),
        StockAudioItem(
            id = "stock_sfx_pop",
            title = "Bubble UI Pop",
            category = AudioTrackType.SFX,
            durationMs = 400L,
            description = "Clean bouncy pop sound for animated text",
            simulatedUri = "sample://audio/sfx/bubble_pop.mp3"
        ),
        StockAudioItem(
            id = "stock_sfx_bell",
            title = "Clean Notification Chime",
            category = AudioTrackType.SFX,
            durationMs = 1100L,
            description = "Crystal clear bell ding for call-to-actions",
            simulatedUri = "sample://audio/sfx/notification_bell.mp3"
        )
    )

    fun parseAudioMetadata(context: Context, uri: Uri): AudioFileMetadata {
        var durationMs = 10000L
        var title = uri.lastPathSegment ?: "Audio Track"
        var artist: String? = null
        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var channels: Int? = null

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationMs = durStr.toLongOrNull() ?: 10000L
            }

            val titleStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!titleStr.isNullOrBlank()) {
                title = titleStr
            }

            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            bitrate = bitrateStr?.toIntOrNull()

            val rateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            sampleRate = rateStr?.toIntOrNull()

            retriever.release()
        } catch (e: Exception) {
            title = uri.lastPathSegment ?: "Imported Audio"
        }

        return AudioFileMetadata(
            title = title,
            durationMs = durationMs,
            artist = artist,
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels
        )
    }

    /**
     * Creates an independent AudioClip extracted from a VideoClip.
     */
    fun createExtractedAudioClip(videoClip: VideoClip): AudioClip {
        return AudioClip(
            id = UUID.randomUUID().toString(),
            sourceUri = videoClip.sourceUri,
            title = "${videoClip.name} (Audio)",
            audioTrackType = AudioTrackType.EXTRACTED,
            sourceDurationMs = videoClip.sourceDurationMs,
            sourceStartTimeMs = videoClip.sourceStartTimeMs,
            sourceEndTimeMs = videoClip.sourceEndTimeMs,
            timelineStartMs = videoClip.timelineStartMs,
            timelineDurationMs = videoClip.trimmedDurationMs,
            speed = videoClip.speed,
            volume = 1.0f,
            isMuted = false,
            trackId = "track_audio_music"
        )
    }

    fun createAudioClipFromUri(context: Context, uri: Uri, trackType: AudioTrackType = AudioTrackType.MUSIC): AudioClip {
        val meta = parseAudioMetadata(context, uri)
        val trackId = when (trackType) {
            AudioTrackType.MUSIC -> "track_audio_music"
            AudioTrackType.VOICE_OVER -> "track_audio_voice"
            AudioTrackType.SFX -> "track_audio_sfx"
            else -> "track_audio_music"
        }
        return AudioClip(
            id = "audio_${UUID.randomUUID().toString().take(8)}",
            sourceUri = uri.toString(),
            title = meta.title,
            audioTrackType = trackType,
            sourceDurationMs = meta.durationMs,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = meta.durationMs,
            timelineStartMs = 0L,
            timelineDurationMs = meta.durationMs,
            volume = 1.0f,
            isMuted = false,
            trackId = trackId
        )
    }

    fun extractAudioFromVideoUri(context: Context, uri: Uri): AudioClip {
        val meta = parseAudioMetadata(context, uri)
        return AudioClip(
            id = "audio_extracted_${UUID.randomUUID().toString().take(8)}",
            sourceUri = uri.toString(),
            title = "${meta.title} (Extracted)",
            audioTrackType = AudioTrackType.EXTRACTED,
            sourceDurationMs = meta.durationMs,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = meta.durationMs,
            timelineStartMs = 0L,
            timelineDurationMs = meta.durationMs,
            volume = 1.0f,
            isMuted = false,
            trackId = "track_audio_music"
        )
    }

    fun getStockMusicTracks(): List<StockAudioItem> =
        STOCK_PRESETS.filter { it.category == AudioTrackType.MUSIC }

    fun getStockSoundEffects(): List<StockAudioItem> =
        STOCK_PRESETS.filter { it.category == AudioTrackType.SFX }
}

fun StockAudioItem.toAudioClip(timelineStartMs: Long = 0L): AudioClip {
    val trackId = when (this.category) {
        AudioTrackType.MUSIC -> "track_audio_music"
        AudioTrackType.VOICE_OVER -> "track_audio_voice"
        AudioTrackType.SFX -> "track_audio_sfx"
        else -> "track_audio_music"
    }
    return AudioClip(
        id = "stock_${this.id}_${UUID.randomUUID().toString().take(6)}",
        sourceUri = this.simulatedUri,
        title = this.title,
        audioTrackType = this.category,
        sourceDurationMs = this.durationMs,
        sourceStartTimeMs = 0L,
        sourceEndTimeMs = this.durationMs,
        timelineStartMs = timelineStartMs,
        timelineDurationMs = this.durationMs,
        volume = 1.0f,
        isMuted = false,
        trackId = trackId
    )
}
