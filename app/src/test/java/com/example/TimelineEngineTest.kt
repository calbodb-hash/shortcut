package com.example

import com.example.domain.engine.TimelineEngine
import com.example.domain.engine.TimelineTimeMapper
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TimelineEngineTest {

    private val engine = TimelineEngine()

    private fun createSampleTimeline(): Timeline {
        val clip1 = VideoClip(
            id = "clip_1",
            sourceUri = "sample://video1.mp4",
            name = "Clip 1",
            sourceDurationMs = 5000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 5000L,
            timelineStartMs = 0L,
            speed = 1.0f
        )
        val clip2 = VideoClip(
            id = "clip_2",
            sourceUri = "sample://video2.mp4",
            name = "Clip 2",
            sourceDurationMs = 6000L,
            sourceStartTimeMs = 1000L,
            sourceEndTimeMs = 5000L, // trimmed duration: 4000ms
            timelineStartMs = 5000L,
            speed = 1.0f
        )
        return Timeline(videoClips = listOf(clip1, clip2))
    }

    // ==========================================
    // 1. DURATION & MULTI-TRACK TESTS
    // ==========================================

    @Test
    fun `test total duration calculation single track`() {
        val timeline = createSampleTimeline()
        // Clip 1 = 5000ms, Clip 2 = 4000ms -> Total = 9000ms
        assertEquals(9000L, timeline.totalDurationMs)
    }

    @Test
    fun `test total duration with longer audio track`() {
        val baseTimeline = createSampleTimeline()
        val longAudio = AudioClip(
            id = "audio_1",
            sourceUri = "sample://music.mp3",
            title = "Long Music",
            sourceDurationMs = 20000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 15000L,
            timelineStartMs = 0L,
            timelineDurationMs = 15000L
        )
        val multiTrack = baseTimeline.copy(audioClips = listOf(longAudio))
        // Max of Video (9000ms) and Audio (15000ms) -> 15000ms
        assertEquals(15000L, multiTrack.totalDurationMs)
    }

    @Test
    fun `test total duration with text layer extending beyond video`() {
        val baseTimeline = createSampleTimeline()
        val textLayer = TextLayer(
            id = "text_1",
            text = "Outro",
            timelineStartMs = 8000L,
            timelineDurationMs = 4000L // Ends at 12000ms
        )
        val multiTrack = baseTimeline.copy(textLayers = listOf(textLayer))
        assertEquals(12000L, multiTrack.totalDurationMs)
    }

    @Test
    fun `test empty timeline duration is zero`() {
        val emptyTimeline = Timeline()
        assertEquals(0L, emptyTimeline.totalDurationMs)
        assertFalse(emptyTimeline.isNotEmpty)
    }

    // ==========================================
    // 2. SPLIT OPERATION TESTS
    // ==========================================

    @Test
    fun `test split clip operation in middle`() {
        val timeline = createSampleTimeline()
        val splitTimeline = engine.splitClip(timeline, "clip_1", 2000L)

        assertEquals(3, splitTimeline.videoClips.size)
        // First part: 0 to 2000ms
        assertEquals(2000L, splitTimeline.videoClips[0].trimmedDurationMs)
        assertEquals(0L, splitTimeline.videoClips[0].timelineStartMs)
        // Second part: 2000 to 5000ms = 3000ms
        assertEquals(3000L, splitTimeline.videoClips[1].trimmedDurationMs)
        assertEquals(2000L, splitTimeline.videoClips[1].timelineStartMs)
        // Third clip starts at 5000ms
        assertEquals(5000L, splitTimeline.videoClips[2].timelineStartMs)
        assertEquals(9000L, splitTimeline.totalDurationMs)
    }

    @Test
    fun `test split clip at boundary is guarded and no-op`() {
        val timeline = createSampleTimeline()
        // Splitting within 50ms of start should be guarded by MIN_CLIP_DURATION_MS (100ms)
        val splitStart = engine.splitClip(timeline, "clip_1", 50L)
        assertEquals(2, splitStart.videoClips.size)

        // Splitting within 50ms of end should also be guarded
        val splitEnd = engine.splitClip(timeline, "clip_1", 4950L)
        assertEquals(2, splitEnd.videoClips.size)
    }

    @Test
    fun `test split non-existent clip ID is safe no-op`() {
        val timeline = createSampleTimeline()
        val split = engine.splitClip(timeline, "non_existent_id", 2000L)
        assertEquals(timeline, split)
    }

    @Test
    fun `test split clip with speed 2x`() {
        val clip = VideoClip(
            id = "clip_fast",
            sourceUri = "sample://fast.mp4",
            name = "Fast Clip",
            sourceDurationMs = 10000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 10000L,
            timelineStartMs = 0L,
            speed = 2.0f // 10000 source ms plays in 5000 timeline ms
        )
        val timeline = Timeline(videoClips = listOf(clip))
        // Split at timeline 2000ms -> source split at 4000ms
        val splitTimeline = engine.splitClip(timeline, "clip_fast", 2000L)

        assertEquals(2, splitTimeline.videoClips.size)
        // Part 1: source 0..4000ms @ 2x = 2000 timeline ms
        assertEquals(4000L, splitTimeline.videoClips[0].sourceEndTimeMs)
        assertEquals(2000L, splitTimeline.videoClips[0].trimmedDurationMs)
        // Part 2: source 4000..10000ms @ 2x = 3000 timeline ms
        assertEquals(4000L, splitTimeline.videoClips[1].sourceStartTimeMs)
        assertEquals(3000L, splitTimeline.videoClips[1].trimmedDurationMs)
    }

    // ==========================================
    // 3. TRIM OPERATION TESTS
    // ==========================================

    @Test
    fun `test trim clip start and end`() {
        val timeline = createSampleTimeline()
        val trimmedTimeline = engine.trimClip(timeline, "clip_1", 1000L, 4000L)

        // Clip 1 trimmed to 3000ms
        assertEquals(3000L, trimmedTimeline.videoClips[0].trimmedDurationMs)
        assertEquals(1000L, trimmedTimeline.videoClips[0].sourceStartTimeMs)
        assertEquals(4000L, trimmedTimeline.videoClips[0].sourceEndTimeMs)
        // Clip 2 timeline start should now ripple to 3000ms
        assertEquals(3000L, trimmedTimeline.videoClips[1].timelineStartMs)
        assertEquals(7000L, trimmedTimeline.totalDurationMs)
    }

    @Test
    fun `test trim clip clamped to minimum duration`() {
        val timeline = createSampleTimeline()
        // Try trimming end before start
        val trimmed = engine.trimClip(timeline, "clip_1", 2000L, 1000L)
        // End should be clamped to at least start + 100ms
        assertTrue(trimmed.videoClips[0].trimmedDurationMs >= TimelineTimeUtils.MIN_CLIP_DURATION_MS)
    }

    // ==========================================
    // 4. DUPLICATE & DELETE & REORDER TESTS
    // ==========================================

    @Test
    fun `test duplicate clip operation`() {
        val timeline = createSampleTimeline()
        val duplicated = engine.duplicateClip(timeline, "clip_1")

        assertEquals(3, duplicated.videoClips.size)
        assertEquals("Clip 1 (Copy)", duplicated.videoClips[1].name)
        assertNotEquals(duplicated.videoClips[0].id, duplicated.videoClips[1].id)
        // Start times should be correctly sequenced
        assertEquals(0L, duplicated.videoClips[0].timelineStartMs)
        assertEquals(5000L, duplicated.videoClips[1].timelineStartMs)
        assertEquals(10000L, duplicated.videoClips[2].timelineStartMs)
    }

    @Test
    fun `test delete clip operation ripples subsequent clips`() {
        val timeline = createSampleTimeline()
        val deleted = engine.deleteClip(timeline, "clip_1")

        assertEquals(1, deleted.videoClips.size)
        assertEquals("clip_2", deleted.videoClips[0].id)
        assertEquals(0L, deleted.videoClips[0].timelineStartMs)
        assertEquals(4000L, deleted.totalDurationMs)
    }

    @Test
    fun `test reorder clips operation`() {
        val timeline = createSampleTimeline()
        val reordered = engine.reorderClips(timeline, 0, 1)

        assertEquals("clip_2", reordered.videoClips[0].id)
        assertEquals(0L, reordered.videoClips[0].timelineStartMs)
        assertEquals("clip_1", reordered.videoClips[1].id)
        assertEquals(4000L, reordered.videoClips[1].timelineStartMs)
    }

    // ==========================================
    // 5. TIME MAPPING & SNAPPING TESTS
    // ==========================================

    @Test
    fun `test time to pixel conversion and back`() {
        val pxPerSecond = 100f
        val timeMs = 2500L
        val pixels = TimelineTimeMapper.timelineMsToPixels(timeMs, pxPerSecond)
        assertEquals(250f, pixels, 0.01f)

        val convertedMs = TimelineTimeMapper.pixelsToTimelineMs(pixels, pxPerSecond)
        assertEquals(timeMs, convertedMs)
    }

    @Test
    fun `test timeline to source time with speed 1x and 2x`() {
        val clipNormal = VideoClip(
            id = "c1",
            sourceUri = "uri",
            sourceDurationMs = 10000L,
            sourceStartTimeMs = 2000L,
            sourceEndTimeMs = 8000L,
            timelineStartMs = 1000L,
            speed = 1.0f
        )
        // 2000ms on timeline is 1000ms into clip -> 2000 + 1000 = 3000ms source
        val sourceTime1 = TimelineTimeMapper.timelineToSourceTime(2000L, clipNormal)
        assertEquals(3000L, sourceTime1)

        val clipFast = clipNormal.copy(speed = 2.0f)
        // 2000ms on timeline is 1000ms into clip -> 1000 * 2 = 2000ms -> 2000 + 2000 = 4000ms source
        val sourceTime2 = TimelineTimeMapper.timelineToSourceTime(2000L, clipFast)
        assertEquals(4000L, sourceTime2)
    }

    @Test
    fun `test snapping engine`() {
        val timeline = createSampleTimeline() // clip1: 0..5000ms, clip2: 5000..9000ms

        // Candidate near 5000ms (e.g. 5080ms with 150ms threshold)
        val snapResult = TimelineTimeMapper.findSnapPosition(
            targetTimeMs = 5080L,
            timeline = timeline,
            snapThresholdMs = 150L
        )

        assertTrue(snapResult.didSnap)
        assertEquals(5000L, snapResult.snappedTimeMs)

        // Candidate far away (e.g. 5300ms)
        val noSnapResult = TimelineTimeMapper.findSnapPosition(
            targetTimeMs = 5300L,
            timeline = timeline,
            snapThresholdMs = 150L
        )
        assertFalse(noSnapResult.didSnap)
        assertEquals(5300L, noSnapResult.snappedTimeMs)
    }

    // ==========================================
    // 6. UNDO / REDO HISTORY TESTS
    // ==========================================

    @Test
    fun `test undo redo history stack`() {
        val history = TimelineHistory()
        val initialTimeline = createSampleTimeline()

        history.pushState(initialTimeline)
        val splitTimeline = engine.splitClip(initialTimeline, "clip_1", 2000L)

        assertTrue(history.canUndo)
        val undoneTimeline = history.undo(splitTimeline)
        assertNotNull(undoneTimeline)
        assertEquals(2, undoneTimeline?.videoClips?.size)

        assertTrue(history.canRedo)
        val redoneTimeline = history.redo(undoneTimeline!!)
        assertNotNull(redoneTimeline)
        assertEquals(3, redoneTimeline?.videoClips?.size)
    }

    @Test
    fun `test new operation clears redo stack`() {
        val history = TimelineHistory()
        val initial = createSampleTimeline()

        history.pushState(initial)
        val step1 = engine.splitClip(initial, "clip_1", 2000L)

        val undone = history.undo(step1)!!
        assertTrue(history.canRedo)

        // Make new edit
        history.pushState(undone)
        val step2 = engine.deleteClip(undone, "clip_1")

        // Redo should now be invalid / empty
        assertFalse(history.canRedo)
    }

    // ==========================================
    // 7. PHASE 03: CLIP EDITING & TRANSFORM TESTS
    // ==========================================

    @Test
    fun `test update clip transform and reset`() {
        val timeline = createSampleTimeline()
        val customTransform = Transform(
            scale = 1.8f,
            rotation = 45f,
            translationX = 120f,
            translationY = -80f,
            isFlippedHorizontal = true,
            isFlippedVertical = false
        )

        val updated = engine.updateClipTransform(timeline, "clip_1", customTransform)
        val targetClip = updated.videoClips.first { it.id == "clip_1" }
        assertEquals(1.8f, targetClip.transform.scale, 0.001f)
        assertEquals(45f, targetClip.transform.rotation, 0.001f)
        assertEquals(120f, targetClip.transform.translationX, 0.001f)
        assertEquals(-80f, targetClip.transform.translationY, 0.001f)
        assertTrue(targetClip.transform.isFlippedHorizontal)
        assertFalse(targetClip.transform.isFlippedVertical)

        // Reset transform
        val reset = engine.resetClipTransform(updated, "clip_1")
        val resetClip = reset.videoClips.first { it.id == "clip_1" }
        assertEquals(1.0f, resetClip.transform.scale, 0.001f)
        assertEquals(0f, resetClip.transform.rotation, 0.001f)
        assertEquals(0f, resetClip.transform.translationX, 0.001f)
        assertEquals(0f, resetClip.transform.translationY, 0.001f)
        assertFalse(resetClip.transform.isFlippedHorizontal)
        assertFalse(resetClip.transform.isFlippedVertical)
    }

    @Test
    fun `test update clip crop and reset`() {
        val timeline = createSampleTimeline()
        val cropState = CropState(
            isEnabled = true,
            left = 0.1f,
            top = 0.15f,
            right = 0.9f,
            bottom = 0.85f,
            targetRatio = AspectRatio.RATIO_16_9
        )

        val updated = engine.updateClipCrop(timeline, "clip_1", cropState)
        val targetClip = updated.videoClips.first { it.id == "clip_1" }
        assertTrue(targetClip.transform.crop.isEnabled)
        assertEquals(0.1f, targetClip.transform.crop.left, 0.001f)
        assertEquals(0.15f, targetClip.transform.crop.top, 0.001f)
        assertEquals(0.9f, targetClip.transform.crop.right, 0.001f)
        assertEquals(0.85f, targetClip.transform.crop.bottom, 0.001f)
        assertEquals(AspectRatio.RATIO_16_9, targetClip.transform.crop.targetRatio)

        // Reset crop
        val reset = engine.resetClipCrop(updated, "clip_1")
        val resetClip = reset.videoClips.first { it.id == "clip_1" }
        assertFalse(resetClip.transform.crop.isEnabled)
        assertEquals(0f, resetClip.transform.crop.left, 0.001f)
        assertEquals(1f, resetClip.transform.crop.right, 0.001f)
    }

    @Test
    fun `test update clip framing mode`() {
        val timeline = createSampleTimeline()
        val updatedFill = engine.updateClipFramingMode(timeline, "clip_1", FramingMode.FILL)
        assertEquals(FramingMode.FILL, updatedFill.videoClips.first { it.id == "clip_1" }.transform.framingMode)

        val updatedOriginal = engine.updateClipFramingMode(updatedFill, "clip_1", FramingMode.ORIGINAL)
        assertEquals(FramingMode.ORIGINAL, updatedOriginal.videoClips.first { it.id == "clip_1" }.transform.framingMode)
    }

    @Test
    fun `test update clip adjustments and reset`() {
        val timeline = createSampleTimeline()
        val customAdj = ColorAdjustment(
            brightness = 25f,
            contrast = -15f,
            saturation = 40f,
            exposure = 10f,
            temperature = -20f,
            tint = 15f,
            opacity = 0.75f
        )

        val updated = engine.updateClipAdjustments(timeline, "clip_1", customAdj)
        val targetClip = updated.videoClips.first { it.id == "clip_1" }
        assertEquals(25f, targetClip.adjustments.brightness, 0.001f)
        assertEquals(-15f, targetClip.adjustments.contrast, 0.001f)
        assertEquals(40f, targetClip.adjustments.saturation, 0.001f)
        assertEquals(0.75f, targetClip.adjustments.opacity, 0.001f)

        // Reset adjustments
        val reset = engine.resetClipAdjustments(updated, "clip_1")
        val resetClip = reset.videoClips.first { it.id == "clip_1" }
        assertEquals(0f, resetClip.adjustments.brightness, 0.001f)
        assertEquals(0f, resetClip.adjustments.contrast, 0.001f)
        assertEquals(0f, resetClip.adjustments.saturation, 0.001f)
        assertEquals(1.0f, resetClip.adjustments.opacity, 0.001f)
    }

    @Test
    fun `test replace clip source preserving timeline properties`() {
        val timeline = createSampleTimeline()
        // Clip 1 has timelineStart=0, duration=5000ms
        val replaced = engine.replaceClipSource(
            timeline = timeline,
            clipId = "clip_1",
            newSourceUri = "file:///storage/new_video.mp4",
            newName = "New Video Source",
            newDurationMs = 8000L
        )

        val targetClip = replaced.videoClips.first { it.id == "clip_1" }
        assertEquals("file:///storage/new_video.mp4", targetClip.sourceUri)
        assertEquals("New Video Source", targetClip.name)
        assertEquals(8000L, targetClip.sourceDurationMs)
        assertEquals(0L, targetClip.sourceStartTimeMs)
        // Trimmed duration clamped to min of previous (5000) and new (8000) -> 5000
        assertEquals(5000L, targetClip.trimmedDurationMs)
        assertEquals(0L, targetClip.timelineStartMs)
    }

    @Test
    fun `test freeze frame creation`() {
        val timeline = createSampleTimeline()
        // Freeze frame at 2000ms of clip_1 for 3000ms
        val frozenTimeline = engine.freezeFrame(
            timeline = timeline,
            clipId = "clip_1",
            atTimeMs = 2000L,
            freezeDurationMs = 3000L
        )

        // Clip 1 was 5000ms. Split at 2000ms -> Part 1 (2000ms) + Freeze (3000ms) + Part 2 (3000ms) + Clip 2 (4000ms)
        assertEquals(4, frozenTimeline.videoClips.size)

        val part1 = frozenTimeline.videoClips[0]
        val freezeClip = frozenTimeline.videoClips[1]
        val part2 = frozenTimeline.videoClips[2]
        val clip2 = frozenTimeline.videoClips[3]

        assertEquals(0L, part1.timelineStartMs)
        assertEquals(2000L, part1.trimmedDurationMs)

        assertEquals(2000L, freezeClip.timelineStartMs)
        assertEquals(3000L, freezeClip.trimmedDurationMs)
        assertEquals(MediaType.IMAGE, freezeClip.mediaType)
        assertTrue(freezeClip.name.contains("Freeze"))

        assertEquals(5000L, part2.timelineStartMs)
        assertEquals(3000L, part2.trimmedDurationMs)

        assertEquals(8000L, clip2.timelineStartMs)
        assertEquals(4000L, clip2.trimmedDurationMs)

        assertEquals(12000L, frozenTimeline.totalDurationMs)
    }

    // ==========================================
    // 8. PHASE 3.5: TRACK LOCKING & SELECTION TESTS
    // ==========================================

    @Test
    fun `test track locking prevents clip split`() {
        val baseTimeline = createSampleTimeline()
        val lockedTimeline = engine.toggleTrackLock(baseTimeline, "track_video_main")
        assertTrue(engine.isTrackLocked(lockedTimeline, "track_video_main"))

        // Attempting to split clip on locked track should return unmodified timeline
        val attemptedSplit = engine.splitClip(lockedTimeline, "clip_1", 2000L)
        assertEquals(lockedTimeline.videoClips.size, attemptedSplit.videoClips.size)
        assertEquals(lockedTimeline.videoClips, attemptedSplit.videoClips)
    }

    @Test
    fun `test track locking prevents clip trim and delete`() {
        val baseTimeline = createSampleTimeline()
        val lockedTimeline = engine.toggleTrackLock(baseTimeline, "track_video_main")

        // Attempt trim
        val attemptedTrim = engine.trimClip(lockedTimeline, "clip_1", 1000L, 4000L)
        assertEquals(lockedTimeline.videoClips, attemptedTrim.videoClips)

        // Attempt delete
        val attemptedDelete = engine.deleteClip(lockedTimeline, "clip_1")
        assertEquals(lockedTimeline.videoClips, attemptedDelete.videoClips)
    }

    @Test
    fun `test audio track locking prevents audio operations`() {
        val baseTimeline = createSampleTimeline()
        val audioClip = AudioClip(
            id = "audio_1",
            sourceUri = "sample://music.mp3",
            title = "Test Music",
            sourceDurationMs = 10000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 8000L,
            timelineStartMs = 0L,
            timelineDurationMs = 8000L
        )
        val withAudio = baseTimeline.copy(audioClips = listOf(audioClip))
        val lockedAudioTimeline = engine.toggleTrackLock(withAudio, "track_audio_music")

        // Attempt delete audio clip
        val attemptedDelete = engine.deleteAudioClip(lockedAudioTimeline, "audio_1")
        assertEquals(1, attemptedDelete.audioClips.size)

        // Attempt trim audio clip
        val attemptedTrim = engine.trimAudioClip(lockedAudioTimeline, "audio_1", 1000L, 5000L)
        assertEquals(8000L, attemptedTrim.audioClips[0].timelineDurationMs)
    }

    @Test
    fun `test text layer linked to clip retained during timeline operations`() {
        val baseTimeline = createSampleTimeline()
        val linkedText = TextLayer(
            id = "text_linked_1",
            text = "Title on Clip 1",
            timelineStartMs = 1000L,
            timelineDurationMs = 2000L,
            linkedToObjectId = "clip_1"
        )
        val timelineWithText = baseTimeline.copy(textLayers = listOf(linkedText))

        assertEquals("clip_1", timelineWithText.textLayers[0].linkedToObjectId)
        val updatedText = engine.updateTextLayer(timelineWithText, linkedText.copy(text = "Updated Title"))
        assertEquals("clip_1", updatedText.textLayers[0].linkedToObjectId)
        assertEquals("Updated Title", updatedText.textLayers[0].text)
    }
}
