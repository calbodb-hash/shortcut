package com.example.domain.model

import java.util.UUID

sealed interface TimelineAction {
    val description: String

    data class SplitClip(
        val originalClipId: String,
        val splitPositionMs: Long,
        override val description: String = "Split Clip"
    ) : TimelineAction

    data class TrimClip(
        val clipId: String,
        val oldStartMs: Long,
        val oldEndMs: Long,
        val newStartMs: Long,
        val newEndMs: Long,
        override val description: String = "Trim Clip"
    ) : TimelineAction

    data class DeleteClip(
        val clip: VideoClip,
        val index: Int,
        override val description: String = "Delete Clip"
    ) : TimelineAction

    data class ReorderClips(
        val fromIndex: Int,
        val toIndex: Int,
        override val description: String = "Reorder Clips"
    ) : TimelineAction

    data class DuplicateClip(
        val clipId: String,
        override val description: String = "Duplicate Clip"
    ) : TimelineAction

    data class UpdateTransform(
        val clipId: String,
        val oldTransform: Transform,
        val newTransform: Transform,
        override val description: String = "Update Transform"
    ) : TimelineAction

    data class UpdateAdjustment(
        val clipId: String,
        val oldAdjustment: ColorAdjustment,
        val newAdjustment: ColorAdjustment,
        override val description: String = "Color Adjustments"
    ) : TimelineAction

    data class UpdateSpeed(
        val clipId: String,
        val oldSpeed: Float,
        val newSpeed: Float,
        override val description: String = "Change Speed"
    ) : TimelineAction

    data class AddText(
        val textLayer: TextLayer,
        override val description: String = "Add Text Layer"
    ) : TimelineAction

    data class DeleteText(
        val textLayer: TextLayer,
        override val description: String = "Delete Text Layer"
    ) : TimelineAction
}

class TimelineHistory(
    private val maxHistorySize: Int = 30
) {
    private val undoStack = ArrayDeque<Timeline>()
    private val redoStack = ArrayDeque<Timeline>()

    fun pushState(currentState: Timeline) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(currentState)
        redoStack.clear()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo(currentState: Timeline): Timeline? {
        if (undoStack.isEmpty()) return null
        val previousState = undoStack.removeLast()
        redoStack.addLast(currentState)
        return previousState
    }

    fun redo(currentState: Timeline): Timeline? {
        if (redoStack.isEmpty()) return null
        val nextState = redoStack.removeLast()
        undoStack.addLast(currentState)
        return nextState
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
