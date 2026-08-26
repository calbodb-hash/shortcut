package com.example.domain.engine

import com.example.domain.model.*
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Deterministic, centralized coordinate and time mapping engine for Short Cut.
 *
 * Handles:
 * - Timeline Time <-> Pixel conversions given pixels-per-second (zoom level)
 * - Source Media Time <-> Timeline Position conversions with speed scale adjustments
 * - Snapping detection (clip boundaries, playhead, timeline edges)
 * - Timeline duration calculations across multiple tracks
 */
object TimelineTimeMapper {

    /**
     * Converts a timeline time in milliseconds to a horizontal pixel coordinate.
     */
    fun timelineMsToPixels(timeMs: Long, pxPerSecond: Float): Float {
        return (timeMs.coerceAtLeast(0L) / 1000f) * pxPerSecond
    }

    /**
     * Converts a horizontal pixel coordinate to timeline time in milliseconds.
     */
    fun pixelsToTimelineMs(pixels: Float, pxPerSecond: Float): Long {
        if (pxPerSecond <= 0f) return 0L
        return ((pixels.coerceAtLeast(0f) / pxPerSecond) * 1000f).roundToLong().coerceAtLeast(0L)
    }

    /**
     * Converts a timeline position to the corresponding source media playback time.
     *
     * Example:
     * clip.timelineStartMs = 3000ms
     * clip.sourceStartTimeMs = 5000ms
     * clip.speed = 2.0x
     *
     * timelinePosition = 4000ms (1000ms into the clip)
     * localTime = 1000ms * 2.0 = 2000ms
     * sourceTime = 5000ms + 2000ms = 7000ms
     */
    fun timelineToSourceTime(timelinePositionMs: Long, clip: VideoClip): Long {
        val clipOffsetTimelineMs = timelinePositionMs - clip.timelineStartMs
        val clampedOffsetMs = clipOffsetTimelineMs.coerceIn(0L, clip.trimmedDurationMs)
        val sourceOffsetMs = (clampedOffsetMs * clip.speed).roundToLong()
        return (clip.sourceStartTimeMs + sourceOffsetMs).coerceIn(0L, clip.sourceDurationMs)
    }

    /**
     * Converts a source media time back to the timeline position.
     */
    fun sourceToTimelineTime(sourceTimeMs: Long, clip: VideoClip): Long {
        val sourceOffsetMs = sourceTimeMs - clip.sourceStartTimeMs
        val clampedSourceOffsetMs = sourceOffsetMs.coerceIn(0L, (clip.sourceEndTimeMs - clip.sourceStartTimeMs))
        val timelineOffsetMs = if (clip.speed > 0f) (clampedSourceOffsetMs / clip.speed).roundToLong() else 0L
        return clip.timelineStartMs + timelineOffsetMs
    }

    /**
     * Calculates the true total duration of a timeline across all tracks (video, audio, text layers).
     */
    fun calculateTotalDurationMs(timeline: Timeline): Long {
        val maxVideoDuration = timeline.videoClips.maxOfOrNull { it.timelineStartMs + it.trimmedDurationMs } ?: 0L
        val maxAudioDuration = timeline.audioClips.maxOfOrNull { it.timelineStartMs + it.timelineDurationMs } ?: 0L
        val maxTextDuration = timeline.textLayers.maxOfOrNull { it.timelineStartMs + it.timelineDurationMs } ?: 0L
        return maxOf(maxVideoDuration, maxAudioDuration, maxTextDuration).coerceAtLeast(0L)
    }

    /**
     * Collects all candidate snap points from a timeline.
     */
    fun collectSnapPoints(
        timeline: Timeline,
        playheadMs: Long? = null,
        excludeClipId: String? = null
    ): List<SnapPoint> {
        val points = mutableListOf<SnapPoint>()

        // 1. Timeline Start (0ms)
        points.add(SnapPoint(0L, "Timeline Start (0:00)", SnapType.TIMELINE_START))

        // 2. Playhead (if provided)
        if (playheadMs != null && playheadMs > 0L) {
            points.add(SnapPoint(playheadMs, "Playhead", SnapType.PLAYHEAD))
        }

        // 3. Video Clip Boundaries
        for (clip in timeline.videoClips) {
            if (clip.id == excludeClipId) continue

            points.add(
                SnapPoint(
                    timeMs = clip.timelineStartMs,
                    description = "${clip.name} Start",
                    type = SnapType.CLIP_START
                )
            )
            points.add(
                SnapPoint(
                    timeMs = clip.timelineStartMs + clip.trimmedDurationMs,
                    description = "${clip.name} End",
                    type = SnapType.CLIP_END
                )
            )
        }

        // 4. Audio Clip Boundaries
        for (audio in timeline.audioClips) {
            points.add(
                SnapPoint(
                    timeMs = audio.timelineStartMs,
                    description = "${audio.title} Start",
                    type = SnapType.CLIP_START
                )
            )
            points.add(
                SnapPoint(
                    timeMs = audio.timelineStartMs + audio.timelineDurationMs,
                    description = "${audio.title} End",
                    type = SnapType.CLIP_END
                )
            )
        }

        // 5. Timeline End
        val totalDuration = calculateTotalDurationMs(timeline)
        if (totalDuration > 0L) {
            points.add(SnapPoint(totalDuration, "Timeline End", SnapType.TIMELINE_END))
        }

        return points.distinctBy { it.timeMs }
    }

    /**
     * Finds the closest snap position within the specified snap threshold in milliseconds.
     */
    fun findSnapPosition(
        targetTimeMs: Long,
        timeline: Timeline,
        playheadMs: Long? = null,
        excludeClipId: String? = null,
        snapThresholdMs: Long = 150L
    ): SnapResult {
        val snapPoints = collectSnapPoints(timeline, playheadMs, excludeClipId)
        var closestPoint: SnapPoint? = null
        var minDiff = Long.MAX_VALUE

        for (point in snapPoints) {
            val diff = abs(point.timeMs - targetTimeMs)
            if (diff <= snapThresholdMs && diff < minDiff) {
                minDiff = diff
                closestPoint = point
            }
        }

        return if (closestPoint != null) {
            SnapResult(
                snappedTimeMs = closestPoint.timeMs,
                originalTimeMs = targetTimeMs,
                didSnap = true,
                snapPoint = closestPoint
            )
        } else {
            SnapResult(
                snappedTimeMs = targetTimeMs,
                originalTimeMs = targetTimeMs,
                didSnap = false,
                snapPoint = null
            )
        }
    }
}
