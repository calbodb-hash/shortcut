package com.example

import com.example.data.local.ProjectConverters
import com.example.domain.engine.TimelineEngine
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiLayerDomainTest {

    private val engine = TimelineEngine()
    private val converters = ProjectConverters()

    private fun createMultiLayerTimeline(): Timeline {
        val mainVideo = VideoClip(
            id = "clip_main",
            sourceUri = "file://video_main.mp4",
            name = "Main Video",
            sourceDurationMs = 6000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 6000L,
            timelineStartMs = 0L,
            trackId = "track_video_main",
            zIndex = 0
        )
        val overlayVideo = VideoClip(
            id = "clip_overlay",
            sourceUri = "file://video_pip.mp4",
            name = "PiP Video",
            sourceDurationMs = 4000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 4000L,
            timelineStartMs = 1000L,
            trackId = "track_video_overlay",
            zIndex = 5
        )
        val textLayer = TextLayer(
            id = "text_title",
            text = "Dynamic Title",
            timelineStartMs = 500L,
            timelineDurationMs = 3000L,
            trackId = "track_text_main",
            zIndex = 10,
            linkedToObjectId = "clip_main"
        )
        val audioClip = AudioClip(
            id = "audio_bgm",
            sourceUri = "file://music.mp3",
            title = "Background Track",
            sourceDurationMs = 10000L,
            timelineStartMs = 0L,
            timelineDurationMs = 10000L,
            trackId = "track_audio_music"
        )
        val tracks = listOf(
            TimelineTrack(id = "track_video_main", type = TrackType.VIDEO_MAIN, name = "Main Video", zIndex = 0),
            TimelineTrack(id = "track_video_overlay", type = TrackType.VIDEO_OVERLAY, name = "Overlay", zIndex = 1),
            TimelineTrack(id = "track_text_main", type = TrackType.TEXT, name = "Text", zIndex = 2),
            TimelineTrack(id = "track_audio_music", type = TrackType.AUDIO, name = "Music", zIndex = 3)
        )
        return Timeline(
            videoClips = listOf(mainVideo, overlayVideo),
            textLayers = listOf(textLayer),
            audioClips = listOf(audioClip),
            tracks = tracks
        )
    }

    // ==========================================================
    // 1. ABSTRACTION & INTERFACE CONFORMANCE TESTS
    // ==========================================================

    @Test
    fun `test TimelineItem and VisualTimelineItem polymorphism`() {
        val timeline = createMultiLayerTimeline()
        val mainVideo: VisualTimelineItem = timeline.videoClips[0]
        val overlayVideo: VisualTimelineItem = timeline.videoClips[1]
        val textLayer: VisualTimelineItem = timeline.textLayers[0]
        val audioClip: TimelineItem = timeline.audioClips[0]

        assertEquals("clip_main", mainVideo.id)
        assertEquals("track_video_main", mainVideo.trackId)
        assertEquals(0L, mainVideo.timelineStartMs)
        assertEquals(6000L, mainVideo.timelineDurationMs)
        assertEquals(0, mainVideo.zIndex)

        assertEquals("clip_overlay", overlayVideo.id)
        assertEquals("track_video_overlay", overlayVideo.trackId)
        assertEquals(1000L, overlayVideo.timelineStartMs)
        assertEquals(4000L, overlayVideo.timelineDurationMs)
        assertEquals(5, overlayVideo.zIndex)

        assertEquals("text_title", textLayer.id)
        assertEquals("track_text_main", textLayer.trackId)
        assertEquals(10, textLayer.zIndex)
        assertEquals("clip_main", textLayer.parentItemId)

        assertEquals("audio_bgm", audioClip.id)
        assertEquals("track_audio_music", audioClip.trackId)
    }

    @Test
    fun `test default properties for VideoClip and TextLayer`() {
        val defaultVideo = VideoClip(id = "v_def", sourceUri = "sample.mp4")
        assertEquals("track_video_main", defaultVideo.trackId)
        assertEquals(0, defaultVideo.zIndex)
        assertNull(defaultVideo.parentItemId)

        val defaultText = TextLayer(id = "t_def", text = "Sample")
        assertEquals("track_text_main", defaultText.trackId)
        assertEquals(10, defaultText.zIndex)
        assertNull(defaultText.parentItemId)

        val defaultAudio = AudioClip(id = "a_def", sourceUri = "sample.mp3")
        assertEquals("track_audio_music", defaultAudio.trackId)
    }

    // ==========================================================
    // 2. Z-ORDER ENGINE TESTS (bringForward, sendBackward, etc.)
    // ==========================================================

    @Test
    fun `test bringForward moves item one step up in z-order`() {
        val timeline = createMultiLayerTimeline()
        // Initial order by zIndex: mainVideo(0), overlayVideo(5), textLayer(10)
        val updated = engine.bringForward(timeline, "clip_main")

        val main = updated.videoClips.find { it.id == "clip_main" }!!
        val overlay = updated.videoClips.find { it.id == "clip_overlay" }!!
        val text = updated.textLayers.find { it.id == "text_title" }!!

        // Now mainVideo should have higher z-index than overlayVideo
        assertTrue(main.zIndex > overlay.zIndex)
        assertTrue(text.zIndex > main.zIndex)
    }

    @Test
    fun `test bringForward on topmost item returns unchanged timeline`() {
        val timeline = createMultiLayerTimeline()
        // textLayer is already topmost (zIndex 10)
        val updated = engine.bringForward(timeline, "text_title")
        assertEquals(timeline, updated)
    }

    @Test
    fun `test sendBackward moves item one step down in z-order`() {
        val timeline = createMultiLayerTimeline()
        // Initial order: mainVideo(0) < overlayVideo(5) < textLayer(10)
        val updated = engine.sendBackward(timeline, "text_title")

        val main = updated.videoClips.find { it.id == "clip_main" }!!
        val overlay = updated.videoClips.find { it.id == "clip_overlay" }!!
        val text = updated.textLayers.find { it.id == "text_title" }!!

        // text should now be below overlay, but above main
        assertTrue(text.zIndex < overlay.zIndex)
        assertTrue(text.zIndex > main.zIndex)
    }

    @Test
    fun `test sendBackward on bottommost item returns unchanged timeline`() {
        val timeline = createMultiLayerTimeline()
        // clip_main is bottommost (zIndex 0)
        val updated = engine.sendBackward(timeline, "clip_main")
        assertEquals(timeline, updated)
    }

    @Test
    fun `test bringToFront moves item to the top of the visual stack`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.bringToFront(timeline, "clip_main")

        val main = updated.videoClips.find { it.id == "clip_main" }!!
        val overlay = updated.videoClips.find { it.id == "clip_overlay" }!!
        val text = updated.textLayers.find { it.id == "text_title" }!!

        assertTrue(main.zIndex > overlay.zIndex)
        assertTrue(main.zIndex > text.zIndex)
    }

    @Test
    fun `test bringToFront on topmost item returns unchanged timeline`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.bringToFront(timeline, "text_title")
        assertEquals(timeline, updated)
    }

    @Test
    fun `test sendToBack moves item to the bottom of the visual stack`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.sendToBack(timeline, "text_title")

        val main = updated.videoClips.find { it.id == "clip_main" }!!
        val overlay = updated.videoClips.find { it.id == "clip_overlay" }!!
        val text = updated.textLayers.find { it.id == "text_title" }!!

        assertTrue(text.zIndex < main.zIndex)
        assertTrue(text.zIndex < overlay.zIndex)
    }

    @Test
    fun `test sendToBack on bottommost item returns unchanged timeline`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.sendToBack(timeline, "clip_main")
        assertEquals(timeline, updated)
    }

    @Test
    fun `test setItemZIndex sets explicit z-index`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.setItemZIndex(timeline, "clip_overlay", 99)
        val overlay = updated.videoClips.find { it.id == "clip_overlay" }!!
        assertEquals(99, overlay.zIndex)

        val updatedText = engine.setItemZIndex(timeline, "text_title", 42)
        val text = updatedText.textLayers.find { it.id == "text_title" }!!
        assertEquals(42, text.zIndex)
    }

    // ==========================================================
    // 3. TRACK LOCKING & Z-ORDER INTERACTION TESTS
    // ==========================================================

    @Test
    fun `test track locking prevents z-order manipulation on locked items`() {
        val timeline = createMultiLayerTimeline()
        val lockedTimeline = engine.toggleTrackLock(timeline, "track_video_overlay")
        assertTrue(engine.isTrackLocked(lockedTimeline, "track_video_overlay"))

        // Attempting to bring overlay forward or send backward on locked track must return unmodified timeline
        val attemptedForward = engine.bringForward(lockedTimeline, "clip_overlay")
        assertEquals(lockedTimeline, attemptedForward)

        val attemptedBackward = engine.sendBackward(lockedTimeline, "clip_overlay")
        assertEquals(lockedTimeline, attemptedBackward)

        val attemptedTop = engine.bringToFront(lockedTimeline, "clip_overlay")
        assertEquals(lockedTimeline, attemptedTop)

        val attemptedSet = engine.setItemZIndex(lockedTimeline, "clip_overlay", 100)
        assertEquals(lockedTimeline, attemptedSet)
    }

    @Test
    fun `test text track locking prevents text z-order manipulation`() {
        val timeline = createMultiLayerTimeline()
        val lockedTimeline = engine.toggleTrackLock(timeline, "track_text_main")

        val attemptedBackward = engine.sendBackward(lockedTimeline, "text_title")
        assertEquals(lockedTimeline, attemptedBackward)
    }

    // ==========================================================
    // 4. NON-DESTRUCTIVE & INTEGRITY GUARANTEES
    // ==========================================================

    @Test
    fun `test z-order operations preserve temporal alignment and trim durations`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.bringToFront(timeline, "clip_main")

        val main = updated.videoClips.find { it.id == "clip_main" }!!
        assertEquals(0L, main.timelineStartMs)
        assertEquals(6000L, main.trimmedDurationMs)
        assertEquals(0L, main.sourceStartTimeMs)
        assertEquals(6000L, main.sourceEndTimeMs)
        assertEquals(1.0f, main.speed, 0.001f)

        val text = updated.textLayers.find { it.id == "text_title" }!!
        assertEquals(500L, text.timelineStartMs)
        assertEquals(3000L, text.timelineDurationMs)
        assertEquals("Dynamic Title", text.text)
        assertEquals("clip_main", text.linkedToObjectId)
    }

    @Test
    fun `test primary video sequence ordering is preserved during z-order updates`() {
        val timeline = createMultiLayerTimeline()
        val updated = engine.bringToFront(timeline, "clip_main")

        // The list order of video clips must remain consistent
        assertEquals(listOf("clip_main", "clip_overlay"), updated.videoClips.map { it.id })
    }

    // ==========================================================
    // 5. SERIALIZATION & BACKWARD COMPATIBILITY TESTS
    // ==========================================================

    @Test
    fun `test timeline serialization and deserialization with full multi-layer metadata`() {
        val timeline = createMultiLayerTimeline()
        val json = converters.timelineToJson(timeline)
        val restored = converters.jsonToTimeline(json)

        assertEquals(timeline.videoClips.size, restored.videoClips.size)
        assertEquals(timeline.textLayers.size, restored.textLayers.size)
        assertEquals(timeline.audioClips.size, restored.audioClips.size)
        assertEquals(timeline.tracks.size, restored.tracks.size)

        val mainRestored = restored.videoClips.find { it.id == "clip_main" }!!
        assertEquals("track_video_main", mainRestored.trackId)
        assertEquals(0, mainRestored.zIndex)

        val overlayRestored = restored.videoClips.find { it.id == "clip_overlay" }!!
        assertEquals("track_video_overlay", overlayRestored.trackId)
        assertEquals(5, overlayRestored.zIndex)

        val textRestored = restored.textLayers.find { it.id == "text_title" }!!
        assertEquals("track_text_main", textRestored.trackId)
        assertEquals(10, textRestored.zIndex)
        assertEquals("clip_main", textRestored.parentItemId)
        assertEquals("clip_main", textRestored.linkedToObjectId)
    }

    @Test
    fun `test legacy project JSON without trackId and zIndex deserializes safely`() {
        val legacyJson = """
            {
                "videoClips": [
                    {
                        "id": "legacy_clip_1",
                        "sourceUri": "sample.mp4",
                        "name": "Old Clip",
                        "sourceDurationMs": 5000,
                        "sourceStartTimeMs": 0,
                        "sourceEndTimeMs": 5000,
                        "timelineStartMs": 0
                    }
                ],
                "textLayers": [
                    {
                        "id": "legacy_text_1",
                        "text": "Legacy Title",
                        "timelineStartMs": 0,
                        "timelineDurationMs": 3000
                    }
                ],
                "audioClips": []
            }
        """.trimIndent()

        val restored = converters.jsonToTimeline(legacyJson)
        assertEquals(1, restored.videoClips.size)
        assertEquals(1, restored.textLayers.size)

        val video = restored.videoClips[0]
        assertEquals("legacy_clip_1", video.id)
        assertEquals("track_video_main", video.trackId)
        assertEquals(0, video.zIndex)
        assertNull(video.parentItemId)

        val text = restored.textLayers[0]
        assertEquals("legacy_text_1", text.id)
        assertEquals("track_text_main", text.trackId)
        assertEquals(10, text.zIndex)
        assertNull(text.parentItemId)
    }

    @Test
    fun `test legacy project with linkedToObjectId maps to parentItemId`() {
        val legacyJson = """
            {
                "videoClips": [
                    {
                        "id": "clip_parent_1",
                        "sourceUri": "sample.mp4"
                    }
                ],
                "textLayers": [
                    {
                        "id": "text_child_1",
                        "text": "Child Title",
                        "linkedToObjectId": "clip_parent_1"
                    }
                ],
                "audioClips": []
            }
        """.trimIndent()

        val restored = converters.jsonToTimeline(legacyJson)
        val text = restored.textLayers[0]
        assertEquals("clip_parent_1", text.linkedToObjectId)
        assertEquals("clip_parent_1", text.parentItemId)
    }
}
