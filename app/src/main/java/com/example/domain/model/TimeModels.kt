package com.example.domain.model

import java.util.Locale

/**
 * High-precision time representations, formatting, and snapping utilities
 * for the Short Cut timeline engine.
 */
object TimelineTimeUtils {
    const val MIN_CLIP_DURATION_MS = 100L
    const val DEFAULT_FRAME_RATE = 30
    const val MS_PER_SECOND = 1000L
    const val US_PER_MS = 1000L

    /**
     * Formats milliseconds into standard MM:SS.ss timecode.
     * Example: 65250ms -> "01:05.25"
     */
    fun formatTimecode(timeMs: Long, showMillis: Boolean = true): String {
        val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val millis = (timeMs % 1000L) / 10L // 2 digits (hundredths)

        return if (showMillis) {
            String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, millis)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats milliseconds into concise seconds string for ruler labels.
     * Example: 0ms -> "0s", 1500ms -> "1.5s", 12000ms -> "12s"
     */
    fun formatRulerLabel(timeMs: Long): String {
        val seconds = timeMs / 1000f
        return if (seconds == seconds.toLong().toFloat()) {
            "${seconds.toLong()}s"
        } else {
            String.format(Locale.US, "%.1fs", seconds)
        }
    }
}

/**
 * Track Types supported by the multi-track timeline architecture.
 */
enum class TrackType {
    VIDEO,
    OVERLAY,
    AUDIO,
    TEXT,
    CAPTION,
    EFFECT
}

/**
 * Scalable Track Model representing an independent layer on the timeline.
 */
data class TimelineTrack(
    val id: String,
    val type: TrackType,
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val zIndex: Int = 0
)

/**
 * Snapping target on the timeline.
 */
data class SnapPoint(
    val timeMs: Long,
    val description: String,
    val type: SnapType
)

enum class SnapType {
    TIMELINE_START,
    TIMELINE_END,
    CLIP_START,
    CLIP_END,
    PLAYHEAD
}

data class SnapResult(
    val snappedTimeMs: Long,
    val originalTimeMs: Long,
    val didSnap: Boolean,
    val snapPoint: SnapPoint? = null
)
