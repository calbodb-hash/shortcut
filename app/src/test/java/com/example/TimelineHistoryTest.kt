package com.example

import com.example.domain.engine.TimelineEngine
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimelineHistoryTest {

    private val engine = TimelineEngine()

    private fun createSampleTimeline(): Timeline {
        val clip1 = VideoClip(
            id = "clip_1",
            sourceUri = "sample1.mp4",
            name = "Clip 1",
            sourceDurationMs = 5000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 5000L,
            timelineStartMs = 0L,
            trackId = "track_video_main",
            zIndex = 0
        )
        val clip2 = VideoClip(
            id = "clip_2",
            sourceUri = "sample2.mp4",
            name = "Clip 2",
            sourceDurationMs = 4000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 4000L,
            timelineStartMs = 5000L,
            trackId = "track_video_main",
            zIndex = 0
        )
        val overlay = VideoClip(
            id = "overlay_1",
            sourceUri = "pip.mp4",
            name = "PiP 1",
            sourceDurationMs = 3000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 3000L,
            timelineStartMs = 2000L,
            trackId = "track_video_overlay",
            zIndex = 5
        )
        return Timeline(
            videoClips = listOf(clip1, clip2, overlay),
            tracks = listOf(
                TimelineTrack(id = "track_video_main", type = TrackType.VIDEO_MAIN, name = "Main", zIndex = 0),
                TimelineTrack(id = "track_video_overlay", type = TrackType.VIDEO_OVERLAY, name = "Overlay", zIndex = 1)
            )
        )
    }

    @Test
    fun `test initial history state has empty stacks`() {
        val history = TimelineHistory(maxHistorySize = 20)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `test undo and redo for clip split cut operation`() {
        val history = TimelineHistory()
        var currentTimeline = createSampleTimeline()
        var currentSelection: SelectionTarget? = SelectionTarget.Video("clip_1")

        // Action: Split clip_1 at 2000ms
        val splitAction = TimelineAction.SplitClip("clip_1", 2000L)
        history.pushState(currentTimeline, splitAction, currentSelection)

        val splitTimeline = engine.splitClip(currentTimeline, "clip_1", 2000L)
        assertEquals(4, splitTimeline.videoClips.size) // 3 originally + 1 from split
        currentTimeline = splitTimeline

        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        // Perform Undo
        val undoResult = history.undo(currentTimeline, currentSelection)
        assertNotNull(undoResult)
        assertEquals(3, undoResult!!.timeline.videoClips.size)
        assertEquals(splitAction, undoResult.action)
        assertEquals(currentSelection, undoResult.selection)
        assertTrue(history.canRedo)

        // Perform Redo
        val redoResult = history.redo(undoResult.timeline, undoResult.selection)
        assertNotNull(redoResult)
        assertEquals(4, redoResult!!.timeline.videoClips.size)
        assertEquals(splitAction, redoResult.action)
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun `test undo and redo for layer z-order reordering`() {
        val history = TimelineHistory()
        var currentTimeline = createSampleTimeline()
        val selection: SelectionTarget = SelectionTarget.Overlay("overlay_1")

        val reorderAction = TimelineAction.ReorderLayers("overlay_1", "Bring Layer to Front")
        history.pushState(currentTimeline, reorderAction, selection)

        val reorderedTimeline = engine.bringToFront(currentTimeline, "clip_1")
        currentTimeline = reorderedTimeline

        val mainClip = currentTimeline.videoClips.first { it.id == "clip_1" }
        val overlayClip = currentTimeline.videoClips.first { it.id == "overlay_1" }
        assertTrue(mainClip.zIndex > overlayClip.zIndex)

        // Undo layer reorder
        val undoResult = history.undo(currentTimeline, selection)
        assertNotNull(undoResult)
        val undoneMain = undoResult!!.timeline.videoClips.first { it.id == "clip_1" }
        val undoneOverlay = undoResult.timeline.videoClips.first { it.id == "overlay_1" }
        assertEquals(0, undoneMain.zIndex)
        assertEquals(5, undoneOverlay.zIndex)
        assertTrue(undoneOverlay.zIndex > undoneMain.zIndex)

        // Redo layer reorder
        val redoResult = history.redo(undoResult.timeline, undoResult.selection)
        assertNotNull(redoResult)
        val redoneMain = redoResult!!.timeline.videoClips.first { it.id == "clip_1" }
        val redoneOverlay = redoResult.timeline.videoClips.first { it.id == "overlay_1" }
        assertTrue(redoneMain.zIndex > redoneOverlay.zIndex)
    }

    @Test
    fun `test new mutation clears redo stack`() {
        val history = TimelineHistory()
        val t0 = createSampleTimeline()
        
        // 1st mutation
        history.pushState(t0, TimelineAction.SplitClip("clip_1", 2000L))
        val t1 = engine.splitClip(t0, "clip_1", 2000L)

        // 2nd mutation
        history.pushState(t1, TimelineAction.TrimClip("clip_2", 0L, 2000L))
        val t2 = engine.trimClip(t1, "clip_2", 0L, 2000L)

        // Undo once -> t1
        val undo1 = history.undo(t2)
        assertNotNull(undo1)
        assertTrue(history.canRedo)
        assertEquals(1, history.redoCount)

        // Push new action -> redo stack must be cleared
        val t3 = engine.deleteClip(undo1!!.timeline, "overlay_1")
        history.pushState(undo1.timeline, TimelineAction.DeleteClip("overlay_1"))

        assertFalse(history.canRedo)
        assertEquals(0, history.redoCount)
        assertTrue(history.canUndo)
    }

    @Test
    fun `test maximum history capacity enforces boundary and drops oldest entry`() {
        val history = TimelineHistory(maxHistorySize = 3)
        var current = createSampleTimeline()

        // Push 4 entries
        for (i in 1..4) {
            val action = TimelineAction.GenericAction("Action $i")
            history.pushState(current, action)
            current = current.copy(videoClips = current.videoClips.map { it.copy(zIndex = i) })
        }

        assertEquals(3, history.undoCount)

        // Pop 3 times: should get Action 4, Action 3, Action 2 (Action 1 dropped)
        val u1 = history.undo(current)
        assertEquals("Action 4", u1?.action?.description)

        val u2 = history.undo(u1!!.timeline)
        assertEquals("Action 3", u2?.action?.description)

        val u3 = history.undo(u2!!.timeline)
        assertEquals("Action 2", u3?.action?.description)

        assertFalse(history.canUndo)
    }

    @Test
    fun `test clear history empties both stacks`() {
        val history = TimelineHistory()
        val t0 = createSampleTimeline()

        history.pushState(t0, TimelineAction.SplitClip("clip_1", 1000L))
        val t1 = engine.splitClip(t0, "clip_1", 1000L)
        history.undo(t1)

        assertTrue(history.canRedo)
        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
