package com.example.domain.model

enum class AspectRatio(
    val label: String,
    val widthRatio: Float,
    val heightRatio: Float,
    val iconName: String,
    val isDefault: Boolean = false
) {
    RATIO_9_16("9:16", 9f, 16f, "portrait_shorts", true),
    RATIO_16_9("16:9", 16f, 9f, "landscape_wide"),
    RATIO_1_1("1:1", 1f, 1f, "square_post"),
    RATIO_4_5("4:5", 4f, 5f, "portrait_feed");

    val aspectRatioFloat: Float get() = widthRatio / heightRatio
}

enum class MediaType {
    VIDEO,
    IMAGE
}

enum class FilterPreset(val displayName: String, val colorMatrix: FloatArray? = null) {
    ORIGINAL("Original"),
    CINEMATIC("Cinematic"),
    VINTAGE("Vintage"),
    CYBERPUNK("Cyberpunk"),
    WARM("Warm Sunset"),
    COOL("Cool Breeze"),
    NOIR("B&W Noir"),
    VIVID("Vivid Pop")
}

enum class EffectPreset(val displayName: String, val description: String) {
    NONE("None", "No active effect"),
    FLASH("White Flash", "Bright impact flash transition"),
    SHAKE("Camera Shake", "Dynamic rhythmic camera shake"),
    ZOOM_PULSE("Zoom Pulse", "Bass-driven beat zoom"),
    GLITCH("Cyber Glitch", "Digital artifact distortion"),
    SOFT_GLOW("Dreamy Glow", "Soft luminous bloom"),
    FILM_GRAIN("Film Grain", "Analog 35mm cinema texture")
}

enum class FramingMode(val displayName: String) {
    FIT("Fit"),
    FILL("Fill"),
    ORIGINAL("Original")
}

data class CropState(
    val isEnabled: Boolean = false,
    val left: Float = 0f,    // normalized 0.0 to 1.0
    val top: Float = 0f,     // normalized 0.0 to 1.0
    val right: Float = 1f,   // normalized 0.0 to 1.0
    val bottom: Float = 1f,  // normalized 0.0 to 1.0
    val targetRatio: AspectRatio? = null
) {
    val widthNormalized: Float get() = (right - left).coerceIn(0.05f, 1f)
    val heightNormalized: Float get() = (bottom - top).coerceIn(0.05f, 1f)
    val isDefault: Boolean get() = !isEnabled || (left == 0f && top == 0f && right == 1f && bottom == 1f)
}

data class Transform(
    val scale: Float = 1.0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotation: Float = 0f,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val framingMode: FramingMode = FramingMode.FIT,
    val crop: CropState = CropState(),
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f
) {
    val isDefault: Boolean
        get() = scale == 1.0f &&
                translationX == 0f &&
                translationY == 0f &&
                rotation == 0f &&
                !isFlippedHorizontal &&
                !isFlippedVertical &&
                framingMode == FramingMode.FIT &&
                crop.isDefault
}

data class ColorAdjustment(
    val brightness: Float = 0f, // -100 to +100
    val contrast: Float = 0f,   // -100 to +100
    val saturation: Float = 0f, // -100 to +100
    val exposure: Float = 0f,   // -100 to +100
    val temperature: Float = 0f,// -100 (Cool) to +100 (Warm)
    val tint: Float = 0f,       // -100 (Green) to +100 (Magenta)
    val opacity: Float = 1.0f   // 0.0 to 1.0
) {
    val isDefault: Boolean
        get() = brightness == 0f &&
                contrast == 0f &&
                saturation == 0f &&
                exposure == 0f &&
                temperature == 0f &&
                tint == 0f &&
                opacity == 1.0f
}

data class VideoClip(
    val id: String,
    val sourceUri: String,
    val mediaType: MediaType = MediaType.VIDEO,
    val name: String = "Clip",
    val sourceDurationMs: Long = 5000L,
    val sourceStartTimeMs: Long = 0L,
    val sourceEndTimeMs: Long = 5000L,
    val timelineStartMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val transform: Transform = Transform(),
    val adjustments: ColorAdjustment = ColorAdjustment(),
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val effect: EffectPreset = EffectPreset.NONE,
    val thumbnailColorSeed: Long = 0xFF2A2E44
) {
    val trimmedDurationMs: Long
        get() = ((sourceEndTimeMs - sourceStartTimeMs) / speed).toLong().coerceAtLeast(100L)
}

enum class AudioTrackType(val displayName: String) {
    MUSIC("Music"),
    VOICE_OVER("Voice-over"),
    SFX("Sound Effect"),
    EXTRACTED("Extracted Audio"),
    ORIGINAL_VIDEO("Original Audio")
}

data class AudioClip(
    val id: String,
    val sourceUri: String,
    val title: String = "Audio Track",
    val audioTrackType: AudioTrackType = AudioTrackType.MUSIC,
    val sourceDurationMs: Long = 10000L,
    val sourceStartTimeMs: Long = 0L,
    val sourceEndTimeMs: Long = sourceDurationMs,
    val timelineStartMs: Long = 0L,
    val timelineDurationMs: Long = sourceDurationMs,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val speed: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0f (Left) to +1.0f (Right), 0.0f is Center
    val duckingEnabled: Boolean = false,
    val duckingLevel: Float = 0.3f, // 0.0 to 1.0
    val trackId: String = "track_audio_music"
) {
    val trimmedDurationMs: Long
        get() = ((sourceEndTimeMs - sourceStartTimeMs) / speed).toLong().coerceAtLeast(100L)

    /**
     * Computes the effective gain multiplier (0.0 to 2.0) at a specific relative offset within this clip
     * considering muting, clip volume, fade-in, and fade-out envelopes.
     */
    fun calculateEffectiveVolumeAt(offsetWithinClipMs: Long, isTrackMuted: Boolean = false): Float {
        if (isMuted || isTrackMuted) return 0f
        if (offsetWithinClipMs < 0 || offsetWithinClipMs > trimmedDurationMs) return 0f

        var gain = volume.coerceIn(0f, 2.0f)

        // Fade in calculation
        if (fadeInMs > 0L && offsetWithinClipMs < fadeInMs) {
            val progress = (offsetWithinClipMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
            gain *= progress
        }

        // Fade out calculation
        val timeUntilEnd = trimmedDurationMs - offsetWithinClipMs
        if (fadeOutMs > 0L && timeUntilEnd < fadeOutMs) {
            val progress = (timeUntilEnd.toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f)
            gain *= progress
        }

        return gain.coerceIn(0f, 2.0f)
    }
}

sealed interface SelectionTarget {
    data class Video(val clipId: String) : SelectionTarget
    data class Overlay(val overlayId: String) : SelectionTarget
    data class Audio(val audioId: String) : SelectionTarget
    data class Text(val layerId: String) : SelectionTarget
}

data class TextLayer(
    val id: String,
    val text: String,
    val timelineStartMs: Long = 0L,
    val timelineDurationMs: Long = 3000L,
    val positionX: Float = 0f, // -1f to 1f normalized
    val positionY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val fontSizeSp: Float = 22f,
    val textColorArgb: Long = 0xFFFFFFFF,
    val backgroundColorArgb: Long = 0x88000000,
    val hasBackground: Boolean = false,
    val isBold: Boolean = true,
    val alignment: Int = 1, // 0: Start, 1: Center, 2: End
    val linkedToObjectId: String? = null
)

data class Timeline(
    val videoClips: List<VideoClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val textLayers: List<TextLayer> = emptyList(),
    val tracks: List<TimelineTrack> = listOf(
        TimelineTrack(id = "track_video_main", type = TrackType.VIDEO, name = "Main Video", zIndex = 0),
        TimelineTrack(id = "track_text_main", type = TrackType.TEXT, name = "Text & Titles", zIndex = 1),
        TimelineTrack(id = "track_audio_music", type = TrackType.AUDIO, name = "Music", zIndex = 2),
        TimelineTrack(id = "track_audio_voice", type = TrackType.AUDIO, name = "Voice-over", zIndex = 3),
        TimelineTrack(id = "track_audio_sfx", type = TrackType.AUDIO, name = "Sound Effects", zIndex = 4)
    )
) {
    val totalDurationMs: Long
        get() {
            val maxVideo = videoClips.maxOfOrNull { it.timelineStartMs + it.trimmedDurationMs } ?: 0L
            val maxAudio = audioClips.maxOfOrNull { it.timelineStartMs + it.timelineDurationMs } ?: 0L
            val maxText = textLayers.maxOfOrNull { it.timelineStartMs + it.timelineDurationMs } ?: 0L
            return maxOf(maxVideo, maxAudio, maxText).coerceAtLeast(0L)
        }

    val isNotEmpty: Boolean get() = videoClips.isNotEmpty() || audioClips.isNotEmpty() || textLayers.isNotEmpty()

    fun recalculateClipTimelineStarts(): List<VideoClip> {
        var currentStart = 0L
        return videoClips.map { clip ->
            val updated = clip.copy(timelineStartMs = currentStart)
            currentStart += updated.trimmedDurationMs
            updated
        }
    }
}

enum class ExportResolution(val label: String, val width: Int, val height: Int, val bitrateKbps: Int) {
    RES_720P("720p HD", 720, 1280, 4_500),
    RES_1080P("1080p Full HD", 1080, 1920, 8_000),
    RES_4K("4K Ultra HD", 2160, 3840, 25_000)
}

enum class ExportFrameRate(val label: String, val fps: Int) {
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60)
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 / AVC (Fast & Compatible)", "video/avc"),
    H265("H.265 / HEVC (High Efficiency)", "video/hevc")
}

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    val codec: ExportCodec = ExportCodec.H264,
    val audioBitrateKbps: Int = 192,
    val useHardwareAcceleration: Boolean = true
)

data class Project(
    val id: String,
    val title: String,
    val canvasRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val timeline: Timeline = Timeline(),
    val exportSettings: ExportSettings = ExportSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDraft: Boolean = true,
    val thumbnailUri: String? = null
)
