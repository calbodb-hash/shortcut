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
    EXPORT
}

data class EditorUiState(
    val project: Project? = null,
    val selectedClipId: String? = null,
    val selectedTextLayerId: String? = null,
    val selectedAudioClipId: String? = null,
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
)

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
                _uiState.update {
                    it.copy(
                        project = project,
                        selectedClipId = project.timeline.videoClips.firstOrNull()?.id
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

    private fun updateTimeline(newTimeline: Timeline, recordHistory: Boolean = true) {
        val currentProject = _uiState.value.project ?: return

        if (recordHistory) {
            history.pushState(currentProject.timeline)
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
    fun selectClip(clipId: String?) {
        _uiState.update {
            it.copy(
                selectedClipId = clipId,
                selectedTextLayerId = null,
                selectedAudioClipId = null
            )
        }
    }

    fun selectTextLayer(layerId: String?) {
        _uiState.update {
            it.copy(
                selectedTextLayerId = layerId,
                selectedClipId = null,
                selectedAudioClipId = null,
                activeToolGroup = if (layerId != null) EditorToolGroup.TEXT else it.activeToolGroup
            )
        }
    }

    fun selectAudioClip(audioId: String?) {
        _uiState.update {
            it.copy(
                selectedAudioClipId = audioId,
                selectedClipId = null,
                selectedTextLayerId = null,
                activeToolGroup = if (audioId != null) EditorToolGroup.AUDIO else it.activeToolGroup
            )
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
        val prevTimeline = history.undo(currentTimeline) ?: return
        updateTimeline(prevTimeline, recordHistory = false)
        showStatus("Undo")
    }

    fun redo() {
        val currentTimeline = _uiState.value.project?.timeline ?: return
        val nextTimeline = history.redo(currentTimeline) ?: return
        updateTimeline(nextTimeline, recordHistory = false)
        showStatus("Redo")
    }

    // Timeline Operations
    fun splitCurrentClip() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: _uiState.value.activeClip?.id ?: return
        val currentPos = _uiState.value.playheadMs

        val newTimeline = timelineEngine.splitClip(timeline, selectedId, currentPos)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline)
            showStatus("Split Clip")
        }
    }

    fun trimClip(clipId: String, newStartMs: Long, newEndMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.trimClip(timeline, clipId, newStartMs, newEndMs)
        updateTimeline(newTimeline)
    }

    fun deleteSelectedClip() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.deleteClip(timeline, selectedId)
        val nextSelected = newTimeline.videoClips.firstOrNull()?.id
        _uiState.update { it.copy(selectedClipId = nextSelected) }
        updateTimeline(newTimeline)
        showStatus("Deleted Clip")
    }

    fun duplicateSelectedClip() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.duplicateClip(timeline, selectedId)
        updateTimeline(newTimeline)
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
        updateTimeline(newTimeline)
        showStatus("Added ${newClips.size} media clip${if (newClips.size > 1) "s" else ""}")
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.reorderClips(timeline, fromIndex, toIndex)
        updateTimeline(newTimeline)
    }

    fun updateSelectedTransform(transform: Transform, recordHistory: Boolean = true) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipTransform(timeline, selectedId, transform)
        updateTimeline(newTimeline, recordHistory = recordHistory)
    }

    fun updateSelectedCrop(crop: CropState, recordHistory: Boolean = true) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipCrop(timeline, selectedId, crop)
        updateTimeline(newTimeline, recordHistory = recordHistory)
    }

    fun updateSelectedFramingMode(framingMode: FramingMode) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipFramingMode(timeline, selectedId, framingMode)
        updateTimeline(newTimeline)
        showStatus("Framing: ${framingMode.displayName}")
    }

    fun resetSelectedTransform() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.resetClipTransform(timeline, selectedId)
        updateTimeline(newTimeline)
        showStatus("Transform Reset")
    }

    fun resetSelectedCrop() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.resetClipCrop(timeline, selectedId)
        updateTimeline(newTimeline)
        showStatus("Crop Reset")
    }

    fun resetSelectedAdjustments() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.resetClipAdjustments(timeline, selectedId)
        updateTimeline(newTimeline)
        showStatus("Adjustments Reset")
    }

    fun updateSelectedAdjustments(adjustments: ColorAdjustment, recordHistory: Boolean = true) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipAdjustments(timeline, selectedId, adjustments)
        updateTimeline(newTimeline, recordHistory = recordHistory)
    }

    fun updateSelectedFilter(filter: FilterPreset) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipFilter(timeline, selectedId, filter)
        updateTimeline(newTimeline)
        showStatus("Filter: ${filter.displayName}")
    }

    fun updateSelectedEffect(effect: EffectPreset) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipEffect(timeline, selectedId, effect)
        updateTimeline(newTimeline)
        showStatus("Effect: ${effect.displayName}")
    }

    fun updateSelectedSpeed(speed: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipSpeed(timeline, selectedId, speed)
        updateTimeline(newTimeline)
        showStatus("Speed: ${"%.1f".format(speed)}x")
    }

    fun updateSelectedVolume(volume: Float, isMuted: Boolean) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.updateClipVolume(timeline, selectedId, volume, isMuted)
        updateTimeline(newTimeline)
    }

    fun replaceSelectedClipMedia(newUri: String, newName: String, durationMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val newTimeline = timelineEngine.replaceClipSource(timeline, selectedId, newUri, newName, durationMs)
        updateTimeline(newTimeline)
        showStatus("Media Replaced")
    }

    fun createFreezeFrame() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedClipId ?: return
        val currentPos = _uiState.value.playheadMs
        val newTimeline = timelineEngine.freezeFrame(timeline, selectedId, currentPos)
        updateTimeline(newTimeline)
        showStatus("Freeze Frame Created")
    }

    fun commitGestureHistory() {
        val currentTimeline = _uiState.value.project?.timeline ?: return
        history.pushState(currentTimeline)
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
        _uiState.update { it.copy(selectedTextLayerId = newLayer.id) }
        updateTimeline(newTimeline)
        showStatus("Added Text Layer")
    }

    fun updateTextLayer(textLayer: TextLayer) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateTextLayer(timeline, textLayer)
        updateTimeline(newTimeline)
    }

    fun updateTextLayerTime(layerId: String, newStartMs: Long, newDurationMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateTextLayerTime(timeline, layerId, newStartMs, newDurationMs)
        updateTimeline(newTimeline)
    }

    fun deleteSelectedTextLayer() {
        val timeline = _uiState.value.project?.timeline ?: return
        val selectedId = _uiState.value.selectedTextLayerId ?: return
        val newTimeline = timelineEngine.deleteTextLayer(timeline, selectedId)
        _uiState.update { it.copy(selectedTextLayerId = null) }
        updateTimeline(newTimeline)
        showStatus("Deleted Text Layer")
    }

    // Audio Operations
    fun addAudioClip(clip: AudioClip) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.addAudioClip(timeline, clip)
        updateTimeline(newTimeline)
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
            updateTimeline(newTimeline)
            showStatus("Split Audio Clip")
        }
    }

    fun trimAudioClip(audioId: String, newSourceStartMs: Long, newSourceEndMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.trimAudioClip(timeline, audioId, newSourceStartMs, newSourceEndMs)
        updateTimeline(newTimeline)
    }

    fun moveAudioClip(audioId: String, newTimelineStartMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.moveAudioClip(timeline, audioId, newTimelineStartMs)
        updateTimeline(newTimeline)
    }

    fun duplicateAudioClip(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.duplicateAudioClip(timeline, audioId)
        updateTimeline(newTimeline)
        showStatus("Duplicated Audio")
    }

    fun deleteAudioClip(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.deleteAudioClip(timeline, audioId)
        if (_uiState.value.selectedAudioClipId == audioId) {
            _uiState.update { it.copy(selectedAudioClipId = null, activeToolGroup = EditorToolGroup.NONE) }
        }
        updateTimeline(newTimeline)
        showStatus("Deleted Audio Clip")
    }

    fun updateAudioClipVolume(audioId: String, volume: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val currentClip = timeline.audioClips.firstOrNull { it.id == audioId }
        val newTimeline = timelineEngine.updateAudioClipVolume(timeline, audioId, volume, currentClip?.isMuted ?: false)
        updateTimeline(newTimeline)
    }

    fun updateAudioClipFade(audioId: String, fadeInMs: Long, fadeOutMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipFade(timeline, audioId, fadeInMs, fadeOutMs)
        updateTimeline(newTimeline)
    }

    fun updateAudioClipSpeed(audioId: String, speed: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipSpeed(timeline, audioId, speed)
        updateTimeline(newTimeline)
    }

    fun updateAudioClipPan(audioId: String, pan: Float) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipPan(timeline, audioId, pan)
        updateTimeline(newTimeline)
    }

    fun toggleAudioClipDucking(audioId: String, enabled: Boolean, level: Float = 0.3f) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.toggleAudioClipDucking(timeline, audioId, enabled, level)
        updateTimeline(newTimeline)
        showStatus(if (enabled) "Smart Ducking ON" else "Ducking OFF")
    }

    fun toggleAudioClipMute(audioId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val clip = timeline.audioClips.firstOrNull { it.id == audioId } ?: return
        val nextMuted = !clip.isMuted
        val nextVolume = if (nextMuted) 0f else (if (clip.volume > 0f) clip.volume else 1.0f)
        val newTimeline = timelineEngine.updateAudioClipVolume(timeline, audioId, nextVolume, nextMuted)
        updateTimeline(newTimeline)
        showStatus(if (nextMuted) "Muted Audio" else "Unmuted Audio")
    }

    fun extractAudioFromVideo(clipId: String) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.extractAudioFromVideo(timeline, clipId)
        if (newTimeline != timeline) {
            updateTimeline(newTimeline)
            val extractedClip = newTimeline.audioClips.lastOrNull { it.audioTrackType == AudioTrackType.EXTRACTED }
            selectAudioClip(extractedClip?.id)
            showStatus("Extracted Audio from Video")
        }
    }

    fun updateAudioClipTime(audioId: String, newStartMs: Long, newDurationMs: Long) {
        val timeline = _uiState.value.project?.timeline ?: return
        val newTimeline = timelineEngine.updateAudioClipTime(timeline, audioId, newStartMs, newDurationMs)
        updateTimeline(newTimeline)
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
