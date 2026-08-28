package com.example.presentation.ui.screens.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.AspectRatio
import com.example.domain.model.TextLayer
import com.example.presentation.ui.components.ShortCutIconButton
import com.example.presentation.ui.components.StatusToast
import com.example.presentation.ui.screens.editor.components.*
import com.example.presentation.ui.screens.export.ExportDialog
import com.example.presentation.viewmodel.EditorToolGroup
import com.example.presentation.viewmodel.EditorViewModel
import com.example.presentation.viewmodel.ExportViewModel
import com.example.ui.theme.*

@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    exportViewModel: ExportViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by editorViewModel.uiState.collectAsState()
    val project = uiState.project

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    val addMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            editorViewModel.addMediaUris(context, uris)
        }
    }

    val addOverlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            editorViewModel.addOverlayMediaUris(context, uris)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val anyGranted = permissionsMap.values.any { it }
        if (anyGranted || permissionsMap.isEmpty()) {
            addMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val anyGranted = permissionsMap.values.any { it }
        if (anyGranted || permissionsMap.isEmpty()) {
            addOverlayLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
    }

    val launchAddMediaWithPermissionCheck = {
        val allGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            addMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    val launchAddOverlayWithPermissionCheck = {
        val allGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            addOverlayLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        } else {
            overlayPermissionLauncher.launch(requiredPermissions)
        }
    }

    if (project == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCanvas)
        ) {
            CircularProgressIndicator(color = ShortCutAccent)
        }
        return
    }

    val selectedClip = project.timeline.videoClips.firstOrNull { it.id == uiState.selectedClipId }
    val selectedTextLayer = project.timeline.textLayers.firstOrNull { it.id == uiState.selectedTextLayerId }
    val selectedAudioClip = project.timeline.audioClips.firstOrNull { it.id == uiState.selectedAudioClipId }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas),
        containerColor = DarkCanvas,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompactHeight = maxHeight < 640.dp
                val isNarrowWidth = maxWidth < 360.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Top Editor Navigation Bar
                    EditorTopBar(
                        title = project.title,
                        canvasRatio = project.canvasRatio,
                        isAutoSaving = uiState.isAutoSaving,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        isNarrow = isNarrowWidth,
                        onNavigateBack = onNavigateBack,
                        onUndo = { editorViewModel.undo() },
                        onRedo = { editorViewModel.redo() },
                        onExportClick = { editorViewModel.setExportDialogVisible(true) },
                        modifier = Modifier.height(if (isCompactHeight) 46.dp else 52.dp)
                    )

                    // 2. Center Video Preview Surface
                    VideoPreviewSurface(
                        aspectRatio = project.canvasRatio,
                        activeClip = uiState.activeClip ?: selectedClip,
                        videoClips = project.timeline.videoClips,
                        textLayers = project.timeline.textLayers,
                        playheadMs = uiState.playheadMs,
                        totalDurationMs = project.timeline.totalDurationMs,
                        isPlaying = uiState.isPlaying,
                        activeToolGroup = uiState.activeToolGroup,
                        selection = uiState.selection,
                        onTogglePlayPause = { editorViewModel.togglePlayPause() },
                        onSelectTarget = { editorViewModel.select(it) },
                        onTransformChange = { editorViewModel.updateSelectedTransform(it, recordHistory = false) },
                        onCropChange = { editorViewModel.updateSelectedCrop(it, recordHistory = false) },
                        onGestureCommit = { editorViewModel.commitGestureHistory() },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    // 3. Multi-Track Timeline Scrubber
                    TimelineView(
                        timeline = project.timeline,
                        playheadMs = uiState.playheadMs,
                        isPlaying = uiState.isPlaying,
                        selectedClipId = uiState.selectedClipId,
                        selectedTextLayerId = uiState.selectedTextLayerId,
                        selectedAudioClipId = uiState.selectedAudioClipId,
                        isCompact = isCompactHeight,
                        pxPerSecond = uiState.pxPerSecond,
                        isSnappingEnabled = uiState.isSnappingEnabled,
                        onPause = { editorViewModel.pause() },
                        onSeekTo = { editorViewModel.seekTo(it) },
                        onScrubTo = { editorViewModel.scrubTo(it) },
                        onSelectClip = { editorViewModel.selectClip(it) },
                        onSelectTextLayer = { editorViewModel.selectTextLayer(it) },
                        onSelectAudioClip = { editorViewModel.selectAudioClip(it) },
                        onTrimClip = { clipId, startMs, endMs ->
                            editorViewModel.trimClip(clipId, startMs, endMs)
                        },
                        onMoveClip = { clipId, startMs ->
                            editorViewModel.moveClip(clipId, startMs)
                        },
                        onTrimAudioClip = { audioId, startMs, endMs ->
                            editorViewModel.trimAudioClip(audioId, startMs, endMs)
                        },
                        onMoveAudioClip = { audioId, startMs ->
                            editorViewModel.moveAudioClip(audioId, startMs)
                        },
                        onUpdateTextLayerTime = { layerId, startMs, durMs ->
                            editorViewModel.updateTextLayerTime(layerId, startMs, durMs)
                        },
                        onUpdateAudioClipTime = { audioId, startMs, durMs ->
                            editorViewModel.updateAudioClipTime(audioId, startMs, durMs)
                        },
                        onToggleTrackMute = { editorViewModel.toggleTrackMute(it) },
                        onToggleTrackLock = { editorViewModel.toggleTrackLock(it) },
                        onZoomChange = { editorViewModel.setZoom(it) },
                        onToggleSnapping = { editorViewModel.toggleSnapping() },
                        onSplitCurrent = {
                            if (uiState.selectedAudioClipId != null) {
                                editorViewModel.splitAudioClip(uiState.selectedAudioClipId!!)
                            } else {
                                editorViewModel.splitCurrentClip()
                            }
                        },
                        onAddText = { editorViewModel.setToolGroup(EditorToolGroup.TEXT) },
                        onAddAudio = { editorViewModel.setAudioImportDialogVisible(true) },
                        onAddMedia = {
                            launchAddMediaWithPermissionCheck()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompactHeight) 140.dp else 175.dp)
                    )

                    // 4. Expandable Active Tool Sheet / Panel
                    AnimatedVisibility(
                        visible = uiState.activeToolGroup != EditorToolGroup.NONE,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        val maxPanelHeight = if (isCompactHeight) 180.dp else 230.dp
                        when (uiState.activeToolGroup) {
                            EditorToolGroup.CROP -> {
                                selectedClip?.let { clip ->
                                    CropToolPanel(
                                        crop = clip.transform.crop,
                                        onCropChange = { editorViewModel.updateSelectedCrop(it) },
                                        onResetCrop = { editorViewModel.resetSelectedCrop() },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                }
                            }
                            EditorToolGroup.FRAMING -> {
                                selectedClip?.let { clip ->
                                    FramingToolPanel(
                                        framingMode = clip.transform.framingMode,
                                        onFramingModeChange = { editorViewModel.updateSelectedFramingMode(it) },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                }
                            }
                            EditorToolGroup.TRANSFORM -> {
                                selectedClip?.let { clip ->
                                    TransformToolPanel(
                                        transform = clip.transform,
                                        onTransformChange = { editorViewModel.updateSelectedTransform(it) },
                                        onResetTransform = { editorViewModel.resetSelectedTransform() },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                }
                            }
                            EditorToolGroup.ADJUST -> {
                                selectedClip?.let { clip ->
                                    AdjustToolPanel(
                                        adjustments = clip.adjustments,
                                        onAdjustmentsChange = { editorViewModel.updateSelectedAdjustments(it) },
                                        onResetAdjustments = { editorViewModel.resetSelectedAdjustments() },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                }
                            }
                            EditorToolGroup.SPEED -> {
                                selectedClip?.let { clip ->
                                    SpeedToolPanel(
                                        currentSpeed = clip.speed,
                                        sourceDurationMs = clip.sourceDurationMs,
                                        onSpeedChange = { editorViewModel.updateSelectedSpeed(it) },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                }
                            }
                            EditorToolGroup.FILTER -> {
                                selectedClip?.let { clip ->
                                    FilterToolPanel(
                                        activeFilter = clip.filter,
                                        onSelectFilter = { editorViewModel.updateSelectedFilter(it) },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                }
                            }
                            EditorToolGroup.AUDIO -> {
                                if (selectedAudioClip != null) {
                                    AudioEditorPanel(
                                        audioClip = selectedAudioClip,
                                        playheadMs = uiState.playheadMs,
                                        onUpdateVolume = { editorViewModel.updateAudioClipVolume(selectedAudioClip.id, it) },
                                        onUpdateFade = { inMs, outMs -> editorViewModel.updateAudioClipFade(selectedAudioClip.id, inMs, outMs) },
                                        onUpdateSpeed = { editorViewModel.updateAudioClipSpeed(selectedAudioClip.id, it) },
                                        onUpdatePan = { editorViewModel.updateAudioClipPan(selectedAudioClip.id, it) },
                                        onToggleDucking = { enabled, lvl -> editorViewModel.toggleAudioClipDucking(selectedAudioClip.id, enabled, lvl) },
                                        onToggleMute = { editorViewModel.toggleAudioClipMute(selectedAudioClip.id) },
                                        onSplitAtPlayhead = { editorViewModel.splitAudioClip(selectedAudioClip.id) },
                                        onDuplicate = { editorViewModel.duplicateAudioClip(selectedAudioClip.id) },
                                        onDelete = { editorViewModel.deleteAudioClip(selectedAudioClip.id) },
                                        onClose = { editorViewModel.closeToolPanel() },
                                        modifier = Modifier.heightIn(max = maxPanelHeight)
                                    )
                                } else if (selectedClip != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkSurfaceHigh)
                                    ) {
                                        AudioToolPanel(
                                            volume = selectedClip.volume,
                                            isMuted = selectedClip.isMuted,
                                            onVolumeChange = { vol, muted ->
                                                editorViewModel.updateSelectedVolume(vol, muted)
                                            },
                                            onClose = { editorViewModel.closeToolPanel() },
                                            modifier = Modifier.heightIn(max = maxPanelHeight)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    editorViewModel.extractAudioFromVideo(selectedClip.id)
                                                    editorViewModel.closeToolPanel()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.VolumeDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Extract Audio", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                } else {
                                    // Audio Hub: Add music, record voice-over, extract audio
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkSurfaceHigh)
                                            .padding(14.dp)
                                    ) {
                                        Text(
                                            "Audio Tracks & Tools",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = TextHighContrast,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    editorViewModel.setAudioImportDialogVisible(true)
                                                    editorViewModel.closeToolPanel()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Music / SFX", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    editorViewModel.setVoiceoverDialogVisible(true)
                                                    editorViewModel.closeToolPanel()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Voice-over", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            EditorToolGroup.TEXT -> {
                                TextEditorPanel(
                                    selectedTextLayer = selectedTextLayer,
                                    onAddText = { editorViewModel.addTextLayer(it) },
                                    onUpdateTextLayer = { editorViewModel.updateTextLayer(it) },
                                    onDeleteTextLayer = { editorViewModel.deleteSelectedTextLayer() },
                                    onClose = { editorViewModel.closeToolPanel() },
                                    modifier = Modifier.heightIn(max = maxPanelHeight)
                                )
                            }
                            EditorToolGroup.CANVAS -> {
                                CanvasRatioPanel(
                                    activeRatio = project.canvasRatio,
                                    onSelectRatio = { editorViewModel.updateCanvasRatio(it) },
                                    onClose = { editorViewModel.closeToolPanel() },
                                    modifier = Modifier.heightIn(max = maxPanelHeight)
                                )
                            }
                            EditorToolGroup.ARRANGE -> {
                                val currentZ = selectedClip?.zIndex ?: selectedTextLayer?.zIndex ?: 0
                                ArrangeToolPanel(
                                    currentZIndex = currentZ,
                                    onBringToFront = { editorViewModel.bringSelectedToFront() },
                                    onSendToBack = { editorViewModel.sendSelectedToBack() },
                                    onBringForward = { editorViewModel.bringSelectedForward() },
                                    onSendBackward = { editorViewModel.sendSelectedBackward() },
                                    onClose = { editorViewModel.closeToolPanel() },
                                    modifier = Modifier.heightIn(max = maxPanelHeight)
                                )
                            }
                            EditorToolGroup.OVERLAY -> {
                                OverlayToolPanel(
                                    onAddOverlayMedia = {
                                        launchAddOverlayWithPermissionCheck()
                                        editorViewModel.closeToolPanel()
                                    },
                                    onClose = { editorViewModel.closeToolPanel() },
                                    modifier = Modifier.heightIn(max = maxPanelHeight)
                                )
                            }
                            else -> {}
                        }
                    }

                    // 5. Contextual Bottom Toolbar
                    BottomEditorToolbar(
                        selection = uiState.selection,
                        activeToolGroup = uiState.activeToolGroup,
                        onToolClick = { editorViewModel.setToolGroup(it) },
                        onSplitClick = {
                            if (uiState.selectedAudioClipId != null) {
                                editorViewModel.splitAudioClip(uiState.selectedAudioClipId!!)
                            } else {
                                editorViewModel.splitCurrentClip()
                            }
                        },
                        onDeleteClick = {
                            when (val sel = uiState.selection) {
                                is com.example.domain.model.SelectionTarget.Video, is com.example.domain.model.SelectionTarget.Overlay -> {
                                    editorViewModel.deleteSelectedClip()
                                }
                                is com.example.domain.model.SelectionTarget.Audio -> {
                                    editorViewModel.deleteAudioClip(sel.audioId)
                                }
                                is com.example.domain.model.SelectionTarget.Text -> {
                                    editorViewModel.deleteSelectedTextLayer()
                                }
                                null -> {}
                            }
                        },
                        onDuplicateClick = {
                            when (val sel = uiState.selection) {
                                is com.example.domain.model.SelectionTarget.Video, is com.example.domain.model.SelectionTarget.Overlay -> {
                                    editorViewModel.duplicateSelectedClip()
                                }
                                is com.example.domain.model.SelectionTarget.Audio -> {
                                    editorViewModel.duplicateAudioClip(sel.audioId)
                                }
                                is com.example.domain.model.SelectionTarget.Text -> {
                                    // Duplicate text layer
                                    selectedTextLayer?.let { tl ->
                                        editorViewModel.addTextLayer(tl.text)
                                    }
                                }
                                null -> {}
                            }
                        }
                    )
                }
            }

            // Status Floating Toast Notification
            StatusToast(
                message = uiState.statusMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
            )

            // Audio Import Dialog
            if (uiState.showAudioImportDialog) {
                AudioImportDialog(
                    onImportAudioClip = { clip ->
                        editorViewModel.addAudioClip(clip.copy(timelineStartMs = uiState.playheadMs))
                    },
                    onDismiss = { editorViewModel.setAudioImportDialogVisible(false) }
                )
            }

            // Voice-over Recording Studio Dialog
            if (uiState.showVoiceoverDialog) {
                VoiceoverRecordingDialog(
                    playheadMs = uiState.playheadMs,
                    onRecordingComplete = { clip ->
                        editorViewModel.addAudioClip(clip)
                    },
                    onDismiss = { editorViewModel.setVoiceoverDialogVisible(false) }
                )
            }

            // Dedicated Export Dialog Sheet
            if (uiState.showExportDialog) {
                ExportDialog(
                    project = project,
                    exportViewModel = exportViewModel,
                    onDismiss = { editorViewModel.setExportDialogVisible(false) }
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    canvasRatio: AspectRatio,
    isAutoSaving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isNarrow: Boolean,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkCanvas)
            .padding(horizontal = if (isNarrow) 6.dp else 12.dp)
    ) {
        // Back action & Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            ShortCutIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Projects",
                onClick = onNavigateBack,
                testTag = "editor_back_button"
            )
            Spacer(modifier = Modifier.width(if (isNarrow) 4.dp else 6.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isNarrow) 13.sp else 15.sp
                    ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (isAutoSaving) {
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.labelSmall.copy(color = ShortCutCyan, fontSize = 9.sp)
                    )
                }
            }
        }

        // Actions: Undo, Redo, Export
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            ShortCutIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                onClick = onUndo,
                tint = if (canUndo) TextHighContrast else TextDisabled,
                testTag = "undo_button"
            )

            ShortCutIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                onClick = onRedo,
                tint = if (canRedo) TextHighContrast else TextDisabled,
                testTag = "redo_button"
            )

            Spacer(modifier = Modifier.width(if (isNarrow) 4.dp else 8.dp))

            // Vibrant Export Action CTA
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(
                    horizontal = if (isNarrow) 8.dp else 12.dp,
                    vertical = 4.dp
                ),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                if (!isNarrow) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Export",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
