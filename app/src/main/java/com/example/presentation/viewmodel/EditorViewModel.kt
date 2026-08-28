package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioMixState
import com.example.core.performance.SmartCacheEngine
import com.example.data.repository.IProjectRepository
import com.example.domain.engine.IPreviewEngine
import com.example.domain.engine.ITimelineEngine
import com.example.domain.engine.PreviewEngine
import com.example.domain.engine.TimelineEngine
import com.example.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class EditorToolGroup {
    NONE,
    SPLIT,
    TRIM,
    SPEED,
    TRANSFORM,
    CROP,
    ADJUST,
    FRAMING,
    AUDIO,
    TEXT,
    FILTER,
    EFFECT,
    CANVAS,
    ARRANGE,
    OVERLAY,
    EXPORT
}

data class EditorUiState(
    val project: Project? = null,
    val selection: SelectionTarget? = null,
    val activeToolGroup: EditorToolGroup = EditorToolGroup.NONE,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isAutoSaving: Boolean = false,
    val activeClip: VideoClip? = null,
    val showExportDialog: Boolean = false,
    val showAudioImportDialog: Boolean = false,
    val showVoiceoverDialog: Boolean = false,
    val audioMixState: AudioMixState = AudioMixState(),
    val statusMessage: String? = null,
    val pxPerSecond: Float = 80f,
    val isSnappingEnabled: Boolean = true
) {
    val selectedClipId: String? get() = when (selection) {
        is SelectionTarget.Video -> selection.clipId
        is SelectionTarget.Overlay -> selection.overlayId
        else -> null
    }
    val selectedTextLayerId: String? get() = (selection as? SelectionTarget.Text)?.layerId
    val selectedAudioClipId: String? get() = (selection as? SelectionTarget.Audio)?.audioId
    val isOverlaySelected: Boolean get() = selection is SelectionTarget.Overlay ||
        (selectedClipId != null && project?.timeline?.videoClips?.find { it.id == selectedClipId }?.trackId != "track_video_main")
}

class EditorViewModel(
    private val projectId: String,
    private val repository: IProjectRepository,
    context: Context,
    private val timelineEngine: ITimelineEngine = TimelineEngine(),
    private val previewEngine: IPreviewEngine = PreviewEngine(context),
    val cacheEngine: SmartCacheEngine = SmartCacheEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val history = TimelineHistory()
    private var autoSaveJob: Job? = null

    init {
        loadProject()
        observePreviewEngine()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val project = repository.getProject(projectId)
            if (project != null) {
                previewEngine.setTimeline(project.timeline)
                val initialClip = project.timeline.videoClips.firstOrNull()
                _uiState.update {
                    it.copy(
                        project = project,
                        selection = initialClip?.let { c -> SelectionTarget.Video(c.id) }
                    )
                }
            }
        }
    }

    private fun observePreviewEngine() {
        viewModelScope.launch {
            previewEngine.playheadMs.collect { pos ->
                _uiState.update { it.copy(playheadMs = pos) }
            }
        }
        viewModelScope.launch {
            previewEngine.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            previewEngine.activeClip.collect { clip ->
                _uiState.update { it.copy(activeClip = clip) }
            }
        }
        viewModelScope.launch {
            previewEngine.audioMixState.collect { mix ->
                _uiState.update { it.copy(audioMixState = mix) }
            }
        }
    }

    private fun updateTimeline(
        newTimeline: Timeline,
        action: TimelineAction? = null,
        recordHistory: Boolean = (action != null)
    ) {
        val currentProject = _uiState.value.project ?: return

        if (recordHistory && action != null) {
            history.pushState(
                currentState = currentProject.timeline,
                action = action,
                selection = _uiState.value.selection
            )
        }

        val updatedProject = currentProject.copy(
            timeline = newTimeline,
            updatedAt = System.currentTimeMillis()
        )

        previewEngine.setTimeline(newTimeline)

        _uiState.update {
            it.copy(
                project = updatedProject,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }

        triggerAutoSave(updatedProject)
    }

    private fun triggerAutoSave(project: Project) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            _uiState.update { it.copy(isAutoSaving = true) }
            delay(1000L) // Debounce auto-save
            repository.saveProject(project)
            _uiState.update { it.copy(isAutoSaving = false) }
        }
    }

    // Playback Controls
    fun togglePlayPause() = previewEngine.togglePlayPause()
    fun play() = previewEngine.play()
    fun pause() = previewEngine.pause()
    fun seekTo(positionMs: Long) = previewEngine.seekTo(positionMs)
    fun scrubTo(positionMs: Long) = previewEngine.scrubTo(positionMs)

    // Zoom & Viewport Controls
    fun setZoom(pxPerSecond: Float) {
        val clamped = pxPerSecond.coerceIn(30f, 250f)
        _uiState.update { it.copy(pxPerSecond = clamped) }
    }

    fun zoomIn() {
        val current = _uiState.value.pxPerSecond
        setZoom(current * 1.3f)
    }

    fun zoomOut() {
        val current = _uiState.value.pxPerSecond
        setZoom(current / 1.3f)
    }

    fun toggleSnapping() {
        _uiState.update {
            val nextState = !it.isSnappingEnabled
            showStatus(if (nextState) "Snapping ON" else "Snapping OFF")
            it.copy(isSnappingEnabled = nextState)
        }
    }

    // Selection
    fun select(target: SelectionTarget?) {
        if (target == null) {
            clearSelection()
            return
        }

        // Selection focus logic: if target is outside current playhead range, seek to its start time
        val timeline = _uiState.value.project?.timeline
        if (timeline != null) {
            val currentPos = _uiState.value.playheadMs
            when (target) {
                is SelectionTarget.Video -> {
                    val clip = timeline.videoClips.find { it.id == target.clipId }
                    if (clip != null && (currentPos < clip.timelineStartMs || currentPos > clip.timelineStartMs + clip.trimmedDurationMs)) {
                        seekTo(clip.timelineStartMs)
                    }
                }
                is SelectionTarget.Text -> {
                    val layer = timeline.textLayers.find { it.id == target.layerId }
                    if (layer != null && (currentPos < layer.timelineStartMs || currentPos > layer.timelineStartMs + layer.timelineDurationMs)) {
                        seekTo(layer.timelineStartMs)
                    }
                }
                is SelectionTarget.Audio -> {
                    val audio = timeline.audioClips.find { it.id == target.audioId }
                    if (audio != null && (currentPos < audio.timelineStartMs || currentPos > audio.timelineStartMs + audio.trimmedDurationMs)) {
                        seekTo(audio.timelineStartMs)
                    }
                }
                is SelectionTarget.Overlay -> {
                    val clip = timeline.videoClips.find { it.id == target.overlayId }
                    if (clip != null && (currentPos < clip.timelineStartMs || currentPos > clip.timelineStartMs + clip.trimmedDurationMs)) {
                        seekTo(clip.timelineStartMs)
                    }
                }
            }
        }

        _uiState.update { current ->
            val nextToolGroup = when (target) {
                is SelectionTarget.Text -> if (current.activeToolGroup != EditorToolGroup.TEXT) EditorToolGroup.TEXT else current.activeToolGroup
                is SelectionTarget.Audio -> if (current.activeToolGroup != EditorToolGroup.AUDIO) EditorToolGroup.AUDIO else current.activeToolGroup
                else -> {
                    if (current.activeToolGroup == EditorToolGroup.TEXT || current.activeToolGroup == EditorToolGroup.AUDIO) {
                        EditorToolGroup.NONE
                    } else {
                        current.activeToolGroup
                    }
                }
            }
            current.copy(
                selection = target,
                activeToolGroup = nextToolGroup
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selection = null,
                activeToolGroup = EditorToolGroup.NONE
            )
        }
    }

    fun selectClip(clipId: String?) {
        if (clipId != null) {
            select(SelectionTarget.Video(clipId))
        } else {
            clearSelection()
        }
    }

    fun selectTextLayer(layerId: String?) {
        if (layerId != null) {
            select(SelectionTarget.Text(layerId))
        } else {
            clearSelection()
        }
    }

    fun selectAudioClip(audioId: String?) {
        if (audioId != null) {
            select(SelectionTarget.Audio(audioId))
        } else {
            clearSelection()
        }
    }

    fun setToolGroup(toolGroup: EditorToolGroup) {
        _uiState.update {
            it.copy(activeToolGroup = if (it.activeToolGroup == toolGroup) EditorToolGroup.NONE else toolGroup)
        }
    }

    fun closeToolPanel() {
        _uiState.update { it.copy(activeToolGroup = EditorToolGroup.NONE) }
    }

    // Undo / Redo
    fun undo() {
        val currentTimeline = _uiState.value.project?.timeline ?: return
        val currentSelection = _uiState.value.selection
        val result = history.undo(currentTimeline, currentSelection) ?: return
        
        updateTimeline(result.timeline, action = null, recordHistory = false)
        
        _uiState.update {
            it.copy(
                selection = result.selection,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        showStatus("Undid: ${result.action.description}")
    }

    fun redo() {
        val currentTimeline = _uiState.value.project?.timeline ?: return
        val currentSelection = _uiState.value.selection
        val result = history.redo(currentTimeline, currentSelection) ?: return
        
        updateTimeline(result.timeline, action = null, recordHistory = false)
        
        _uiState.update {
            it.copy(
                selection = result.selection,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        showStatus("Redid: ${result.action.description}")
    }

    // Timeline Operations
    fun splitCurrentClip() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: _uiState.value.activeClip?.id ?: return
        val currentPos = _uiState.value.playheadMs

        val newTimeline = timelineEngine.splitClip(timeline, selectedId, currentPos)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.SplitClip(selectedId, currentPos))
            showStatus("Split Clip")
        }
    }

    fun trimClip(clipId: String, newStartMs: Long, newEndMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.trimClip(timeline, clipId, newStartMs, newEndMs)
        updateTimeline(newTimeline, action = TimelineAction.TrimClip(clipId, newStartMs, newEndMs))
    }

    fun deleteSelectedClip() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.deleteClip(timeline, selectedId)
        val nextSelected = newTimeline.videoClips.firstOrNull()?.id
        _uiState.update { it.copy(selection = nextSelected?.let { id -> SelectionTarget.Video(id) }) }
        updateTimeline(newTimeline, action = TimelineAction.DeleteClip(selectedId))
        showStatus("Deleted Clip")
    }

    fun duplicateSelectedClip() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.duplicateClip(timeline, selectedId)
        updateTimeline(newTimeline, action = TimelineAction.DuplicateClip(selectedId))
        showStatus("Duplicated Clip")
    }

    fun addMediaUris(context: Context, uris: List<android.net.Uri>) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newClips = uris.map { uri ->
            com.example.data.repository.MediaPickerHelper.parseMediaUri(context, uri)
        }
        val updatedClips = (timeline.videoClips + newClips)
        val newTimeline = timeline.copy(videoClips = updatedClips).let { tl ->
            tl.copy(videoClips = tl.recalculateClipTimelineStarts())
        }
        updateTimeline(newTimeline, action = TimelineAction.GenericAction("Add Media (${newClips.size})"))
        showStatus("Added ${newClips.size} media clip${if (newClips.size > 1) "s" else ""}")
    }

    fun addOverlayMediaUris(context: Context, uris: List<android.net.Uri>) {
        val timeline = _uiState.value.project?.timeline ?: return
        val currentPlayhead = _uiState.value.playheadMs
        var updatedTimeline = timeline
        var firstAddedId: String? = null

        uris.forEachIndexed { index, uri ->
            val parsed = com.example.data.repository.MediaPickerHelper.parseMediaUri(context, uri)
            val isImage = parsed.mediaType == MediaType.IMAGE
            val duration = if (isImage) 4000L else parsed.sourceDurationMs
            val overlayTrackId = "track_video_overlay"
            val newZ = (updatedTimeline.videoClips.maxOfOrNull { it.zIndex } ?: 0) + 1

            val overlayClip = parsed.copy(
                id = "overlay_${UUID.randomUUID().toString().take(8)}",
                timelineStartMs = currentPlayhead + (index * 500L),
                sourceDurationMs = duration,
                sourceEndTimeMs = duration,
                trackId = overlayTrackId,
                zIndex = newZ,
                transform = Transform(scale = 0.55f)
            )
            if (firstAddedId == null) firstAddedId = overlayClip.id

            val hasTrack = updatedTimeline.tracks.any { it.id == overlayTrackId }
            val newTracks = if (!hasTrack) {
                updatedTimeline.tracks + TimelineTrack(
                    id = overlayTrackId,
                    type = if (isImage) TrackType.GRAPHIC else TrackType.VIDEO_OVERLAY,
                    name = if (isImage) "Image Overlay" else "Video Overlay",
                    zIndex = newZ
                )
            } else updatedTimeline.tracks

            updatedTimeline = updatedTimeline.copy(
                videoClips = updatedTimeline.videoClips + overlayClip,
                tracks = newTracks
            )
        }

        updateTimeline(updatedTimeline, action = TimelineAction.AddOverlay(firstAddedId ?: "", "Overlay"))
        if (firstAddedId != null) {
            select(SelectionTarget.Overlay(firstAddedId!!))
        }
        showStatus("Added Overlay")
    }

    fun addOverlayClip(
        sourceUri: String,
        mediaType: MediaType = MediaType.VIDEO,
        name: String = "Overlay",
        durationMs: Long = 4000L
    ) {
        val timeline = _uiState.value.project?.timeline ?: return
        val currentPlayhead = _uiState.value.playheadMs
        val newTimeline = timelineEngine.addOverlayClip(
            timeline = timeline,
            sourceUri = sourceUri,
            mediaType = mediaType,
            name = name,
            durationMs = durationMs,
            timelineStartMs = currentPlayhead
        )
        val added = newTimeline.videoClips.lastOrNull()
        updateTimeline(newTimeline, action = TimelineAction.AddOverlay(added?.id ?: "", name))
        if (added != null) {
            select(SelectionTarget.Overlay(added.id))
        }
        showStatus("Added ${if (mediaType == MediaType.IMAGE) "Image" else "Video"} Overlay")
    }

    fun moveClip(clipId: String, newTimelineStartMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.moveClip(timeline, clipId, newTimelineStartMs)
        updateTimeline(newTimeline, action = TimelineAction.MoveClip(clipId, newTimelineStartMs))
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.reorderClips(timeline, fromIndex, toIndex)
        updateTimeline(newTimeline, action = TimelineAction.ReorderClips(fromIndex, toIndex))
    }

    fun updateSelectedTransform(transform: Transform, recordHistory: Boolean = true) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipTransform(timeline, selectedId, transform)
        updateTimeline(
            newTimeline,
            action = if (recordHistory) TimelineAction.UpdateTransform(selectedId, newTransform = transform) else null,
            recordHistory = recordHistory
        )
    }

    fun updateSelectedCrop(crop: CropState, recordHistory: Boolean = true) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipCrop(timeline, selectedId, crop)
        updateTimeline(
            newTimeline,
            action = if (recordHistory) TimelineAction.GenericAction("Crop Clip") else null,
            recordHistory = recordHistory
        )
    }

    fun updateSelectedFramingMode(framingMode: FramingMode) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipFramingMode(timeline, selectedId, framingMode)
        updateTimeline(newTimeline, action = TimelineAction.GenericAction("Framing: ${framingMode.displayName}"))
        showStatus("Framing: ${framingMode.displayName}")
    }

    fun resetSelectedTransform() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.resetClipTransform(timeline, selectedId)
        updateTimeline(newTimeline, action = TimelineAction.UpdateTransform(selectedId))
        showStatus("Transform Reset")
    }

    fun resetSelectedCrop() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.resetClipCrop(timeline, selectedId)
        updateTimeline(newTimeline, action = TimelineAction.GenericAction("Reset Crop"))
        showStatus("Crop Reset")
    }

    fun resetSelectedAdjustments() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.resetClipAdjustments(timeline, selectedId)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAdjustment(selectedId))
        showStatus("Adjustments Reset")
    }

    fun updateSelectedAdjustments(adjustments: ColorAdjustment, recordHistory: Boolean = true) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipAdjustments(timeline, selectedId, adjustments)
        updateTimeline(
            newTimeline,
            action = if (recordHistory) TimelineAction.UpdateAdjustment(selectedId, newAdjustment = adjustments) else null,
            recordHistory = recordHistory
        )
    }

    fun updateSelectedFilter(filter: FilterPreset) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipFilter(timeline, selectedId, filter)
        updateTimeline(newTimeline, action = TimelineAction.UpdateFilter(selectedId, filter.displayName))
        showStatus("Filter: ${filter.displayName}")
    }

    fun updateSelectedEffect(effect: EffectPreset) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipEffect(timeline, selectedId, effect)
        updateTimeline(newTimeline, action = TimelineAction.GenericAction("Effect: ${effect.displayName}"))
        showStatus("Effect: ${effect.displayName}")
    }

    fun updateSelectedSpeed(speed: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipSpeed(timeline, selectedId, speed)
        updateTimeline(newTimeline, action = TimelineAction.UpdateSpeed(selectedId, newSpeed = speed))
        showStatus("Speed: ${"%.1f".format(speed)}x")
    }

    fun updateSelectedVolume(volume: Float, isMuted: Boolean) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipVolume(timeline, selectedId, volume, isMuted)
        updateTimeline(newTimeline, action = TimelineAction.UpdateVolume(selectedId, volume))
    }

    fun replaceSelectedClipMedia(newUri: String, newName: String, durationMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.replaceClipSource(timeline, selectedId, newUri, newName, durationMs)
        updateTimeline(newTimeline, action = TimelineAction.ReplaceMedia(selectedId))
        showStatus("Media Replaced")
    }

    fun createFreezeFrame() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val currentPos = _uiState.value.playheadMs
        val newTimeline = timelineEngine.freezeFrame(timeline, selectedId, currentPos)
        updateTimeline(newTimeline, action = TimelineAction.FreezeFrame(selectedId))
        showStatus("Freeze Frame Created")
    }

    fun commitGestureHistory(action: TimelineAction = TimelineAction.GenericAction("Edit Layer")) {
        val currentTimeline = _uiState.value.project?.timeline ?: return
        history.pushState(
            currentState = currentTimeline,
            action = action,
            selection = _uiState.value.selection
        )
        _uiState.update {
            it.copy(
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    fun updateCanvasRatio(aspectRatio: AspectRatio) {
        val currentProject = _uiState.value.project ?: return
        val updatedProject = currentProject.copy(
            canvasRatio = aspectRatio,
            updatedAt = System.currentTimeMillis()
        )
        _uiState.update { it.copy(project = updatedProject) }
        triggerAutoSave(updatedProject)
        showStatus("Canvas Ratio: ${aspectRatio.label}")
    }

    // Layer Stacking (Z-Order) Operations
    fun bringSelectedForward() {
        val selectedId = getSelectedVisualItemId() ?: return
        bringItemForward(selectedId)
    }

    fun sendSelectedBackward() {
        val selectedId = getSelectedVisualItemId() ?: return
        sendItemBackward(selectedId)
    }

    fun bringSelectedToFront() {
        val selectedId = getSelectedVisualItemId() ?: return
        bringItemToFront(selectedId)
    }

    fun sendSelectedToBack() {
        val selectedId = getSelectedVisualItemId() ?: return
        sendItemToBack(selectedId)
    }

    fun setSelectedZIndex(newZIndex: Int) {
        val selectedId = getSelectedVisualItemId() ?: return
        setItemZIndex(selectedId, newZIndex)
    }

    fun bringItemForward(itemId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.bringForward(timeline, itemId)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.ReorderLayers(itemId, "Move Layer Forward"))
            showStatus("Layer Moved Forward")
        }
    }

    fun sendItemBackward(itemId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.sendBackward(timeline, itemId)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.ReorderLayers(itemId, "Move Layer Backward"))
            showStatus("Layer Moved Backward")
        }
    }

    fun bringItemToFront(itemId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.bringToFront(timeline, itemId)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.ReorderLayers(itemId, "Bring Layer to Front"))
            showStatus("Layer Moved to Top")
        }
    }

    fun sendItemToBack(itemId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.sendToBack(timeline, itemId)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.ReorderLayers(itemId, "Send Layer to Back"))
            showStatus("Layer Moved to Bottom")
        }
    }

    fun setItemZIndex(itemId: String, newZIndex: Int) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.setItemZIndex(timeline, itemId, newZIndex)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.ReorderLayers(itemId, "Set Layer Order ($newZIndex)"))
        }
    }

    private fun getSelectedVisualItemId(): String? {
        return when (val sel = _uiState.value.selection) {
            is SelectionTarget.Video -> sel.clipId
            is SelectionTarget.Overlay -> sel.overlayId
            is SelectionTarget.Text -> sel.layerId
            else -> _uiState.value.selectedClipId ?: _uiState.value.selectedTextLayerId
        }
    }

    // Text Layers
    fun addTextLayer(text: String = "Short Cut Title") {
        val timeline = _uiState.value.project?.timeline ?: return
        val newLayer = TextLayer(
            id = UUID.randomUUID().toString(),
            text = text,
            timelineStartMs = _uiState.value.playheadMs,
            timelineDurationMs = 3000L
        )
        val newTimeline = timelineEngine.addTextLayer(timeline, newLayer)
        _uiState.update { it.copy(selection = SelectionTarget.Text(newLayer.id), activeToolGroup = EditorToolGroup.TEXT) }
        updateTimeline(newTimeline, action = TimelineAction.AddText(newLayer.id, text))
        showStatus("Added Text Layer")
    }

    fun updateTextLayer(textLayer: TextLayer) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateTextLayer(timeline, textLayer)
        updateTimeline(newTimeline, action = TimelineAction.UpdateText(textLayer.id))
    }

    fun updateTextLayerTime(layerId: String, newStartMs: Long, newDurationMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateTextLayerTime(timeline, layerId, newStartMs, newDurationMs)
        updateTimeline(newTimeline, action = TimelineAction.UpdateText(layerId))
    }

    fun deleteSelectedTextLayer() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedTextLayerId ?: return
        val newTimeline = timelineEngine.deleteTextLayer(timeline, selectedId)
        _uiState.update { it.copy(selection = null, activeToolGroup = EditorToolGroup.NONE) }
        updateTimeline(newTimeline, action = TimelineAction.DeleteText(selectedId))
        showStatus("Deleted Text Layer")
    }

    // Audio Operations
    fun addAudioClip(clip: AudioClip) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.addAudioClip(timeline, clip)
        updateTimeline(newTimeline, action = TimelineAction.AddAudio(clip.id, clip.title))
        selectAudioClip(clip.id)
        showStatus("Added ${clip.audioTrackType.displayName}")
    }

    fun addAudioTrack(title: String = "Background Music") {
        val timeline = _uiState.value.project?.timeline ?: return
        val newAudio = AudioClip(
            id = "audio_${UUID.randomUUID().toString().take(8)}",
            sourceUri = "stock://music/energetic_beat",
            title = title,
            sourceDurationMs = 15000L,
            sourceStartTimeMs = 0L,
            sourceEndTimeMs = 15000L,
            timelineStartMs = _uiState.value.playheadMs,
            timelineDurationMs = 15000L,
            audioTrackType = AudioTrackType.MUSIC,
            trackId = "track_audio_music"
        )
        addAudioClip(newAudio)
    }

    fun splitAudioClip(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val currentPos = _uiState.value.playheadMs
        val newTimeline = timelineEngine.splitAudioClip(timeline, audioId, currentPos)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.SplitAudio(audioId))
            showStatus("Split Audio Clip")
        }
    }

    fun trimAudioClip(audioId: String, newSourceStartMs: Long, newSourceEndMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.trimAudioClip(timeline, audioId, newSourceStartMs, newSourceEndMs)
        updateTimeline(newTimeline, action = TimelineAction.TrimAudio(audioId))
    }

    fun moveAudioClip(audioId: String, newTimelineStartMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.moveAudioClip(timeline, audioId, newTimelineStartMs)
        updateTimeline(newTimeline, action = TimelineAction.MoveAudio(audioId))
    }

    fun duplicateAudioClip(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.duplicateAudioClip(timeline, audioId)
        updateTimeline(newTimeline, action = TimelineAction.DuplicateAudio(audioId))
        showStatus("Duplicated Audio")
    }

    fun deleteAudioClip(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.deleteAudioClip(timeline, audioId)
        if (_uiState.value.selectedAudioClipId == audioId) {
            _uiState.update { it.copy(selection = null, activeToolGroup = EditorToolGroup.NONE) }
        }
        updateTimeline(newTimeline, action = TimelineAction.DeleteAudio(audioId))
        showStatus("Deleted Audio Clip")
    }

    fun updateAudioClipVolume(audioId: String, volume: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val currentClip = timeline.audioClips.firstOrNull { it.id == audioId }
        val newTimeline = timelineEngine.updateAudioClipVolume(timeline, audioId, volume, currentClip?.isMuted ?: false)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAudioParams(audioId, "Volume"))
    }

    fun updateAudioClipFade(audioId: String, fadeInMs: Long, fadeOutMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipFade(timeline, audioId, fadeInMs, fadeOutMs)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAudioParams(audioId, "Fade"))
    }

    fun updateAudioClipSpeed(audioId: String, speed: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipSpeed(timeline, audioId, speed)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAudioParams(audioId, "Speed"))
    }

    fun updateAudioClipPan(audioId: String, pan: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipPan(timeline, audioId, pan)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAudioParams(audioId, "Pan"))
    }

    fun toggleAudioClipDucking(audioId: String, enabled: Boolean, level: Float = 0.3f) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.toggleAudioClipDucking(timeline, audioId, enabled, level)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAudioParams(audioId, "Ducking"))
        showStatus(if (enabled) "Smart Ducking ON" else "Ducking OFF")
    }

    fun toggleAudioClipMute(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val clip = timeline.audioClips.firstOrNull { it.id == audioId } ?: return
        val nextMuted = !clip.isMuted
        val nextVolume = if (nextMuted) 0f else (if (clip.volume > 0f) clip.volume else 1.0f)
        val newTimeline = timelineEngine.updateAudioClipVolume(timeline, audioId, nextVolume, nextMuted)
        updateTimeline(newTimeline, action = TimelineAction.UpdateAudioParams(audioId, "Mute"))
        showStatus(if (nextMuted) "Muted Audio" else "Unmuted Audio")
    }

    fun extractAudioFromVideo(clipId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.extractAudioFromVideo(timeline, clipId)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline, action = TimelineAction.ExtractAudio(clipId))
            val extractedClip = newTimeline.audioClips.lastOrNull { it.audioTrackType == AudioTrackType.EXTRACTED }
            selectAudioClip(extractedClip?.id)
            showStatus("Extracted Audio from Video")
        }
    }

    fun updateAudioClipTime(audioId: String, newStartMs: Long, newDurationMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipTime(timeline, audioId, newStartMs, newDurationMs)
        updateTimeline(newTimeline, action = TimelineAction.MoveAudio(audioId))
    }

    fun setAudioImportDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAudioImportDialog = visible) }
    }

    fun setVoiceoverDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showVoiceoverDialog = visible) }
    }

    // Track Toggles
    fun toggleTrackMute(trackId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.toggleTrackMute(timeline, trackId)
        updateTimeline(newTimeline, recordHistory = false)
    }

    fun toggleTrackLock(trackId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.toggleTrackLock(timeline, trackId)
        updateTimeline(newTimeline, recordHistory = false)
    }

    fun toggleTrackVisibility(trackId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.toggleTrackVisibility(timeline, trackId)
        updateTimeline(newTimeline, recordHistory = false)
    }

    fun setExportDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showExportDialog = visible) }
    }

    private fun showStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
        viewModelScope.launch {
            delay(1800L)
            _uiState.update { if (it.statusMessage == message) it.copy(statusMessage = null) else it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        previewEngine.release()
    }
}
