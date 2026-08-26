package com.example.domain.engine

import android.content.Context
import android.net.Uri
import com.example.domain.model.*
import java.util.UUID

interface ITimelineEngine {
    fun splitClip(timeline: Timeline, clipId: String, splitPositionTimelineMs: Long): Timeline
    fun trimClip(timeline: Timeline, clipId: String, newSourceStartMs: Long, newSourceEndMs: Long): Timeline
    fun deleteClip(timeline: Timeline, clipId: String): Timeline
    fun duplicateClip(timeline: Timeline, clipId: String): Timeline
    fun reorderClips(timeline: Timeline, fromIndex: Int, toIndex: Int): Timeline
    fun updateClipTransform(timeline: Timeline, clipId: String, transform: Transform): Timeline
    fun updateClipCrop(timeline: Timeline, clipId: String, crop: CropState): Timeline
    fun updateClipFramingMode(timeline: Timeline, clipId: String, framingMode: FramingMode): Timeline
    fun resetClipTransform(timeline: Timeline, clipId: String): Timeline
    fun resetClipAdjustments(timeline: Timeline, clipId: String): Timeline
    fun resetClipCrop(timeline: Timeline, clipId: String): Timeline
    fun updateClipAdjustments(timeline: Timeline, clipId: String, adjustments: ColorAdjustment): Timeline
    fun updateClipFilter(timeline: Timeline, clipId: String, filter: FilterPreset): Timeline
    fun updateClipEffect(timeline: Timeline, clipId: String, effect: EffectPreset): Timeline
    fun updateClipSpeed(timeline: Timeline, clipId: String, speed: Float): Timeline
    fun updateClipVolume(timeline: Timeline, clipId: String, volume: Float, isMuted: Boolean): Timeline
    fun replaceClipSource(timeline: Timeline, clipId: String, newSourceUri: String, newName: String, newDurationMs: Long): Timeline
    fun freezeFrame(timeline: Timeline, clipId: String, atTimeMs: Long, freezeDurationMs: Long = 3000L): Timeline
    fun addTextLayer(timeline: Timeline, textLayer: TextLayer): Timeline
    fun updateTextLayer(timeline: Timeline, textLayer: TextLayer): Timeline
    fun updateTextLayerTime(timeline: Timeline, layerId: String, newStartMs: Long, newDurationMs: Long): Timeline
    fun deleteTextLayer(timeline: Timeline, layerId: String): Timeline
    fun addAudioClip(timeline: Timeline, audioClip: AudioClip): Timeline
    fun splitAudioClip(timeline: Timeline, audioId: String, splitPositionTimelineMs: Long): Timeline
    fun trimAudioClip(timeline: Timeline, audioId: String, newSourceStartMs: Long, newSourceEndMs: Long): Timeline
    fun moveAudioClip(timeline: Timeline, audioId: String, newTimelineStartMs: Long): Timeline
    fun duplicateAudioClip(timeline: Timeline, audioId: String): Timeline
    fun deleteAudioClip(timeline: Timeline, audioId: String): Timeline
    fun updateAudioClipTime(timeline: Timeline, audioId: String, newStartMs: Long, newDurationMs: Long): Timeline
    fun updateAudioClipVolume(timeline: Timeline, audioId: String, volume: Float, isMuted: Boolean = false): Timeline
    fun updateAudioClipFade(timeline: Timeline, audioId: String, fadeInMs: Long, fadeOutMs: Long): Timeline
    fun updateAudioClipPan(timeline: Timeline, audioId: String, pan: Float): Timeline
    fun updateAudioClipSpeed(timeline: Timeline, audioId: String, speed: Float): Timeline
    fun toggleAudioClipDucking(timeline: Timeline, audioId: String, enabled: Boolean, level: Float): Timeline
    fun extractAudioFromVideo(timeline: Timeline, videoClipId: String, muteOriginalVideo: Boolean = true): Timeline
    fun toggleTrackMute(timeline: Timeline, trackId: String): Timeline
    fun toggleTrackLock(timeline: Timeline, trackId: String): Timeline
    fun toggleTrackVisibility(timeline: Timeline, trackId: String): Timeline
}

class TimelineEngine : ITimelineEngine {

    override fun splitClip(timeline: Timeline, clipId: String, splitPositionTimelineMs: Long): Timeline {
        val clipIndex = timeline.videoClips.indexOfFirst { it.id == clipId }
        if (clipIndex == -1) return timeline

        val clip = timeline.videoClips[clipIndex]
        val clipRelativeSplitMs = splitPositionTimelineMs - clip.timelineStartMs
        val sourceSplitMs = clip.sourceStartTimeMs + (clipRelativeSplitMs * clip.speed).toLong()

        // Guard minimum clip length (100ms on either side)
        if (sourceSplitMs <= clip.sourceStartTimeMs + TimelineTimeUtils.MIN_CLIP_DURATION_MS ||
            sourceSplitMs >= clip.sourceEndTimeMs - TimelineTimeUtils.MIN_CLIP_DURATION_MS
        ) {
            return timeline
        }

        val firstPart = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (Part 1)",
            sourceEndTimeMs = sourceSplitMs
        )

        val secondPart = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (Part 2)",
            sourceStartTimeMs = sourceSplitMs
        )

        val updatedClips = timeline.videoClips.toMutableList().apply {
            removeAt(clipIndex)
            add(clipIndex, secondPart)
            add(clipIndex, firstPart)
        }

        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun trimClip(
        timeline: Timeline,
        clipId: String,
        newSourceStartMs: Long,
        newSourceEndMs: Long
    ): Timeline {
        val updatedClips = timeline.videoClips.map { clip ->
            if (clip.id == clipId) {
                val minDur = TimelineTimeUtils.MIN_CLIP_DURATION_MS
                val validStart = newSourceStartMs.coerceIn(0L, clip.sourceDurationMs - minDur)
                val validEnd = newSourceEndMs.coerceIn(validStart + minDur, clip.sourceDurationMs)
                clip.copy(
                    sourceStartTimeMs = validStart,
                    sourceEndTimeMs = validEnd
                )
            } else clip
        }
        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun deleteClip(timeline: Timeline, clipId: String): Timeline {
        val updatedClips = timeline.videoClips.filterNot { it.id == clipId }
        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun duplicateClip(timeline: Timeline, clipId: String): Timeline {
        val clipIndex = timeline.videoClips.indexOfFirst { it.id == clipId }
        if (clipIndex == -1) return timeline

        val original = timeline.videoClips[clipIndex]
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)"
        )

        val updatedClips = timeline.videoClips.toMutableList().apply {
            add(clipIndex + 1, duplicate)
        }
        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun reorderClips(timeline: Timeline, fromIndex: Int, toIndex: Int): Timeline {
        if (fromIndex !in timeline.videoClips.indices || toIndex !in timeline.videoClips.indices || fromIndex == toIndex) {
            return timeline
        }
        val updatedClips = timeline.videoClips.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun updateClipTransform(timeline: Timeline, clipId: String, transform: Transform): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(transform = transform) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun updateClipCrop(timeline: Timeline, clipId: String, crop: CropState): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(transform = it.transform.copy(crop = crop)) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun updateClipFramingMode(timeline: Timeline, clipId: String, framingMode: FramingMode): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(transform = it.transform.copy(framingMode = framingMode)) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun resetClipTransform(timeline: Timeline, clipId: String): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(transform = Transform(crop = it.transform.crop)) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun resetClipAdjustments(timeline: Timeline, clipId: String): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(adjustments = ColorAdjustment()) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun resetClipCrop(timeline: Timeline, clipId: String): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(transform = it.transform.copy(crop = CropState())) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun updateClipAdjustments(timeline: Timeline, clipId: String, adjustments: ColorAdjustment): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(adjustments = adjustments) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun updateClipFilter(timeline: Timeline, clipId: String, filter: FilterPreset): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(filter = filter) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun updateClipEffect(timeline: Timeline, clipId: String, effect: EffectPreset): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(effect = effect) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun updateClipSpeed(timeline: Timeline, clipId: String, speed: Float): Timeline {
        val validSpeed = speed.coerceIn(0.1f, 10.0f)
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(speed = validSpeed) else it
        }
        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun updateClipVolume(timeline: Timeline, clipId: String, volume: Float, isMuted: Boolean): Timeline {
        val updatedClips = timeline.videoClips.map {
            if (it.id == clipId) it.copy(volume = volume.coerceIn(0f, 2f), isMuted = isMuted) else it
        }
        return timeline.copy(videoClips = updatedClips)
    }

    override fun replaceClipSource(
        timeline: Timeline,
        clipId: String,
        newSourceUri: String,
        newName: String,
        newDurationMs: Long
    ): Timeline {
        val updatedClips = timeline.videoClips.map { clip ->
            if (clip.id == clipId) {
                val validDuration = newDurationMs.coerceAtLeast(TimelineTimeUtils.MIN_CLIP_DURATION_MS)
                val newStart = clip.sourceStartTimeMs.coerceIn(0L, validDuration - TimelineTimeUtils.MIN_CLIP_DURATION_MS)
                val newEnd = clip.sourceEndTimeMs.coerceIn(newStart + TimelineTimeUtils.MIN_CLIP_DURATION_MS, validDuration)
                clip.copy(
                    sourceUri = newSourceUri,
                    name = newName,
                    sourceDurationMs = validDuration,
                    sourceStartTimeMs = newStart,
                    sourceEndTimeMs = newEnd
                )
            } else clip
        }
        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun freezeFrame(
        timeline: Timeline,
        clipId: String,
        atTimeMs: Long,
        freezeDurationMs: Long
    ): Timeline {
        val clipIndex = timeline.videoClips.indexOfFirst { it.id == clipId }
        if (clipIndex == -1) return timeline

        val clip = timeline.videoClips[clipIndex]
        val clampedFreezeDur = freezeDurationMs.coerceIn(500L, 10000L)
        val clipStart = clip.timelineStartMs

        val updatedClips = mutableListOf<VideoClip>()
        for (i in timeline.videoClips.indices) {
            val c = timeline.videoClips[i]
            if (c.id == clipId) {
                val relTimeMs = atTimeMs - clipStart
                if (relTimeMs >= TimelineTimeUtils.MIN_CLIP_DURATION_MS && relTimeMs <= (clip.trimmedDurationMs - TimelineTimeUtils.MIN_CLIP_DURATION_MS)) {
                    // Split in middle and insert freeze frame
                    val sourceSplit = (clip.sourceStartTimeMs + (relTimeMs * clip.speed).toLong())
                        .coerceIn(clip.sourceStartTimeMs, clip.sourceEndTimeMs)
                    val part1 = clip.copy(sourceEndTimeMs = sourceSplit)
                    val freezeClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${clip.name} (Freeze)",
                        mediaType = MediaType.IMAGE,
                        sourceDurationMs = clampedFreezeDur,
                        sourceStartTimeMs = 0L,
                        sourceEndTimeMs = clampedFreezeDur,
                        speed = 1.0f
                    )
                    val part2 = clip.copy(
                        id = UUID.randomUUID().toString(),
                        sourceStartTimeMs = sourceSplit
                    )
                    updatedClips.add(part1)
                    updatedClips.add(freezeClip)
                    updatedClips.add(part2)
                } else {
                    // Insert adjacent
                    val freezeClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${clip.name} (Freeze)",
                        mediaType = MediaType.IMAGE,
                        sourceDurationMs = clampedFreezeDur,
                        sourceStartTimeMs = 0L,
                        sourceEndTimeMs = clampedFreezeDur,
                        speed = 1.0f
                    )
                    updatedClips.add(c)
                    updatedClips.add(freezeClip)
                }
            } else {
                updatedClips.add(c)
            }
        }

        val updatedTimeline = timeline.copy(videoClips = updatedClips)
        return updatedTimeline.copy(videoClips = updatedTimeline.recalculateClipTimelineStarts())
    }

    override fun addTextLayer(timeline: Timeline, textLayer: TextLayer): Timeline {
        val updatedLayers = timeline.textLayers + textLayer
        return timeline.copy(textLayers = updatedLayers)
    }

    override fun updateTextLayer(timeline: Timeline, textLayer: TextLayer): Timeline {
        val updatedLayers = timeline.textLayers.map {
            if (it.id == textLayer.id) textLayer else it
        }
        return timeline.copy(textLayers = updatedLayers)
    }

    override fun updateTextLayerTime(timeline: Timeline, layerId: String, newStartMs: Long, newDurationMs: Long): Timeline {
        val updatedLayers = timeline.textLayers.map {
            if (it.id == layerId) {
                it.copy(
                    timelineStartMs = newStartMs.coerceAtLeast(0L),
                    timelineDurationMs = newDurationMs.coerceAtLeast(TimelineTimeUtils.MIN_CLIP_DURATION_MS)
                )
            } else it
        }
        return timeline.copy(textLayers = updatedLayers)
    }

    override fun deleteTextLayer(timeline: Timeline, layerId: String): Timeline {
        val updatedLayers = timeline.textLayers.filterNot { it.id == layerId }
        return timeline.copy(textLayers = updatedLayers)
    }

    override fun addAudioClip(timeline: Timeline, audioClip: AudioClip): Timeline {
        val updatedAudios = timeline.audioClips + audioClip
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun splitAudioClip(
        timeline: Timeline,
        audioId: String,
        splitPositionTimelineMs: Long
    ): Timeline {
        val audioIndex = timeline.audioClips.indexOfFirst { it.id == audioId }
        if (audioIndex == -1) return timeline

        val audio = timeline.audioClips[audioIndex]
        val clipRelativeSplitMs = splitPositionTimelineMs - audio.timelineStartMs
        val sourceSplitMs = audio.sourceStartTimeMs + (clipRelativeSplitMs * audio.speed).toLong()

        // Guard minimum duration (100ms on either side)
        if (sourceSplitMs <= audio.sourceStartTimeMs + TimelineTimeUtils.MIN_CLIP_DURATION_MS ||
            sourceSplitMs >= audio.sourceEndTimeMs - TimelineTimeUtils.MIN_CLIP_DURATION_MS
        ) {
            return timeline
        }

        val firstPart = audio.copy(
            id = UUID.randomUUID().toString(),
            title = "${audio.title} (Part 1)",
            sourceEndTimeMs = sourceSplitMs,
            timelineDurationMs = ((sourceSplitMs - audio.sourceStartTimeMs) / audio.speed).toLong()
        )

        val secondPartDuration = ((audio.sourceEndTimeMs - sourceSplitMs) / audio.speed).toLong()
        val secondPart = audio.copy(
            id = UUID.randomUUID().toString(),
            title = "${audio.title} (Part 2)",
            sourceStartTimeMs = sourceSplitMs,
            timelineStartMs = splitPositionTimelineMs,
            timelineDurationMs = secondPartDuration
        )

        val updatedAudios = timeline.audioClips.toMutableList().apply {
            removeAt(audioIndex)
            add(audioIndex, firstPart)
            add(audioIndex + 1, secondPart)
        }

        return timeline.copy(audioClips = updatedAudios)
    }

    override fun trimAudioClip(
        timeline: Timeline,
        audioId: String,
        newSourceStartMs: Long,
        newSourceEndMs: Long
    ): Timeline {
        val updatedAudios = timeline.audioClips.map { clip ->
            if (clip.id == audioId) {
                val minDur = TimelineTimeUtils.MIN_CLIP_DURATION_MS
                val validStart = newSourceStartMs.coerceIn(0L, clip.sourceDurationMs - minDur)
                val validEnd = newSourceEndMs.coerceIn(validStart + minDur, clip.sourceDurationMs)
                val newTrimmedDuration = ((validEnd - validStart) / clip.speed).toLong().coerceAtLeast(minDur)
                clip.copy(
                    sourceStartTimeMs = validStart,
                    sourceEndTimeMs = validEnd,
                    timelineDurationMs = newTrimmedDuration
                )
            } else clip
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun moveAudioClip(
        timeline: Timeline,
        audioId: String,
        newTimelineStartMs: Long
    ): Timeline {
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                it.copy(timelineStartMs = newTimelineStartMs.coerceAtLeast(0L))
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun duplicateAudioClip(timeline: Timeline, audioId: String): Timeline {
        val audioIndex = timeline.audioClips.indexOfFirst { it.id == audioId }
        if (audioIndex == -1) return timeline

        val original = timeline.audioClips[audioIndex]
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            title = "${original.title} (Copy)",
            timelineStartMs = original.timelineStartMs + original.trimmedDurationMs
        )

        val updatedAudios = timeline.audioClips.toMutableList().apply {
            add(audioIndex + 1, duplicate)
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun deleteAudioClip(timeline: Timeline, audioId: String): Timeline {
        val updatedAudios = timeline.audioClips.filterNot { it.id == audioId }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun updateAudioClipTime(
        timeline: Timeline,
        audioId: String,
        newStartMs: Long,
        newDurationMs: Long
    ): Timeline {
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                it.copy(
                    timelineStartMs = newStartMs.coerceAtLeast(0L),
                    timelineDurationMs = newDurationMs.coerceAtLeast(TimelineTimeUtils.MIN_CLIP_DURATION_MS)
                )
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun updateAudioClipVolume(
        timeline: Timeline,
        audioId: String,
        volume: Float,
        isMuted: Boolean
    ): Timeline {
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                it.copy(volume = volume.coerceIn(0f, 2.0f), isMuted = isMuted)
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun updateAudioClipFade(
        timeline: Timeline,
        audioId: String,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Timeline {
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                it.copy(
                    fadeInMs = fadeInMs.coerceAtLeast(0L),
                    fadeOutMs = fadeOutMs.coerceAtLeast(0L)
                )
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun updateAudioClipPan(timeline: Timeline, audioId: String, pan: Float): Timeline {
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                it.copy(pan = pan.coerceIn(-1.0f, 1.0f))
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun updateAudioClipSpeed(timeline: Timeline, audioId: String, speed: Float): Timeline {
        val validSpeed = speed.coerceIn(0.1f, 10.0f)
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                val newTrimmedDuration = ((it.sourceEndTimeMs - it.sourceStartTimeMs) / validSpeed).toLong()
                    .coerceAtLeast(TimelineTimeUtils.MIN_CLIP_DURATION_MS)
                it.copy(speed = validSpeed, timelineDurationMs = newTrimmedDuration)
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun toggleAudioClipDucking(
        timeline: Timeline,
        audioId: String,
        enabled: Boolean,
        level: Float
    ): Timeline {
        val updatedAudios = timeline.audioClips.map {
            if (it.id == audioId) {
                it.copy(duckingEnabled = enabled, duckingLevel = level.coerceIn(0f, 1f))
            } else it
        }
        return timeline.copy(audioClips = updatedAudios)
    }

    override fun extractAudioFromVideo(
        timeline: Timeline,
        videoClipId: String,
        muteOriginalVideo: Boolean
    ): Timeline {
        val videoClip = timeline.videoClips.find { it.id == videoClipId } ?: return timeline

        val extractedAudio = AudioClip(
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
            volume = videoClip.volume,
            isMuted = false,
            trackId = "track_audio_music"
        )

        val updatedVideos = if (muteOriginalVideo) {
            timeline.videoClips.map {
                if (it.id == videoClipId) it.copy(isMuted = true) else it
            }
        } else {
            timeline.videoClips
        }

        return timeline.copy(
            videoClips = updatedVideos,
            audioClips = timeline.audioClips + extractedAudio
        )
    }

    override fun toggleTrackMute(timeline: Timeline, trackId: String): Timeline {
        val updatedTracks = timeline.tracks.map {
            if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it
        }
        return timeline.copy(tracks = updatedTracks)
    }

    override fun toggleTrackLock(timeline: Timeline, trackId: String): Timeline {
        val updatedTracks = timeline.tracks.map {
            if (it.id == trackId) it.copy(isLocked = !it.isLocked) else it
        }
        return timeline.copy(tracks = updatedTracks)
    }

    override fun toggleTrackVisibility(timeline: Timeline, trackId: String): Timeline {
        val updatedTracks = timeline.tracks.map {
            if (it.id == trackId) it.copy(isVisible = !it.isVisible) else it
        }
        return timeline.copy(tracks = updatedTracks)
    }
}
