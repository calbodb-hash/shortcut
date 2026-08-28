package com.example.domain.model

sealed interface TimelineAction {
    val description: String

    data class SplitClip(
        val clipId: String,
        val splitPositionMs: Long,
        override val description: String = "Split Clip"
    ) : TimelineAction

    data class TrimClip(
        val clipId: String,
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        override val description: String = "Trim Clip"
    ) : TimelineAction

    data class MoveClip(
        val clipId: String,
        val newStartMs: Long,
        override val description: String = "Move Clip"
    ) : TimelineAction

    data class DeleteClip(
        val clipId: String,
        override val description: String = "Delete Clip"
    ) : TimelineAction

    data class ReorderClips(
        val fromIndex: Int,
        val toIndex: Int,
        override val description: String = "Reorder Clips"
    ) : TimelineAction

    data class ReorderLayers(
        val itemId: String,
        val layerAction: String = "Reorder Layer",
        override val description: String = layerAction
    ) : TimelineAction

    data class DuplicateClip(
        val clipId: String,
        override val description: String = "Duplicate Clip"
    ) : TimelineAction

    data class AddOverlay(
        val overlayId: String,
        val name: String = "Overlay",
        override val description: String = "Add Overlay"
    ) : TimelineAction

    data class UpdateTransform(
        val clipId: String,
        val oldTransform: Transform? = null,
        val newTransform: Transform? = null,
        override val description: String = "Transform Layer"
    ) : TimelineAction

    data class UpdateAdjustment(
        val clipId: String,
        val oldAdjustment: ColorAdjustment? = null,
        val newAdjustment: ColorAdjustment? = null,
        override val description: String = "Color Adjustments"
    ) : TimelineAction

    data class UpdateFilter(
        val clipId: String,
        val filterName: String,
        override val description: String = "Filter ($filterName)"
    ) : TimelineAction

    data class UpdateSpeed(
        val clipId: String,
        val oldSpeed: Float = 1f,
        val newSpeed: Float = 1f,
        override val description: String = "Change Speed (${String.format("%.1fx", newSpeed)})"
    ) : TimelineAction

    data class UpdateVolume(
        val clipId: String,
        val volume: Float,
        override val description: String = "Clip Volume"
    ) : TimelineAction

    data class FreezeFrame(
        val clipId: String,
        override val description: String = "Freeze Frame"
    ) : TimelineAction

    data class ReplaceMedia(
        val clipId: String,
        override val description: String = "Replace Media"
    ) : TimelineAction

    data class AddText(
        val textLayerId: String,
        val text: String = "",
        override val description: String = "Add Text"
    ) : TimelineAction

    data class UpdateText(
        val textLayerId: String,
        override val description: String = "Edit Text"
    ) : TimelineAction

    data class DeleteText(
        val textLayerId: String,
        override val description: String = "Delete Text"
    ) : TimelineAction

    data class AddAudio(
        val audioId: String,
        val title: String = "Audio",
        override val description: String = "Add Audio"
    ) : TimelineAction

    data class SplitAudio(
        val audioId: String,
        override val description: String = "Split Audio"
    ) : TimelineAction

    data class TrimAudio(
        val audioId: String,
        override val description: String = "Trim Audio"
    ) : TimelineAction

    data class MoveAudio(
        val audioId: String,
        override val description: String = "Move Audio"
    ) : TimelineAction

    data class DeleteAudio(
        val audioId: String,
        override val description: String = "Delete Audio"
    ) : TimelineAction

    data class DuplicateAudio(
        val audioId: String,
        override val description: String = "Duplicate Audio"
    ) : TimelineAction

    data class UpdateAudioParams(
        val audioId: String,
        val paramName: String,
        override val description: String = "Audio $paramName"
    ) : TimelineAction

    data class ExtractAudio(
        val clipId: String,
        override val description: String = "Extract Audio"
    ) : TimelineAction

    data class GenericAction(
        override val description: String
    ) : TimelineAction
}

data class TimelineHistoryEntry(
    val timeline: Timeline,
    val action: TimelineAction,
    val selection: SelectionTarget? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class UndoResult(
    val timeline: Timeline,
    val action: TimelineAction,
    val selection: SelectionTarget?
)

class TimelineHistory(
    private val maxHistorySize: Int = 50
) {
    private val undoStack = ArrayDeque<TimelineHistoryEntry>()
    private val redoStack = ArrayDeque<TimelineHistoryEntry>()

    fun pushState(
        currentState: Timeline,
        action: TimelineAction,
        selection: SelectionTarget? = null
    ) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(
            TimelineHistoryEntry(
                timeline = currentState,
                action = action,
                selection = selection
            )
        )
        redoStack.clear()
    }

    // Backward-compatibility overload for simple push
    fun pushState(currentState: Timeline) {
        pushState(currentState, TimelineAction.GenericAction("Edit Timeline"))
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    val lastUndoAction: TimelineAction? get() = undoStack.lastOrNull()?.action
    val lastRedoAction: TimelineAction? get() = redoStack.lastOrNull()?.action

    fun undo(
        currentState: Timeline,
        currentSelection: SelectionTarget? = null
    ): UndoResult? {
        if (undoStack.isEmpty()) return null
        val previousEntry = undoStack.removeLast()
        redoStack.addLast(
            TimelineHistoryEntry(
                timeline = currentState,
                action = previousEntry.action,
                selection = currentSelection
            )
        )
        return UndoResult(
            timeline = previousEntry.timeline,
            action = previousEntry.action,
            selection = previousEntry.selection
        )
    }

    fun undoTimeline(currentState: Timeline): Timeline? {
        return undo(currentState, null)?.timeline
    }

    fun redo(
        currentState: Timeline,
        currentSelection: SelectionTarget? = null
    ): UndoResult? {
        if (redoStack.isEmpty()) return null
        val nextEntry = redoStack.removeLast()
        undoStack.addLast(
            TimelineHistoryEntry(
                timeline = currentState,
                action = nextEntry.action,
                selection = currentSelection
            )
        )
        return UndoResult(
            timeline = nextEntry.timeline,
            action = nextEntry.action,
            selection = nextEntry.selection
        )
    }

    fun redoTimeline(currentState: Timeline): Timeline? {
        return redo(currentState, null)?.timeline
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

