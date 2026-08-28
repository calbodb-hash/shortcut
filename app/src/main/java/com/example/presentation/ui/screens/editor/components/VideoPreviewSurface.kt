package com.example.presentation.ui.screens.editor.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.media.MediaFrameLoader
import com.example.domain.model.*
import com.example.presentation.ui.components.TimecodeBadge
import com.example.presentation.viewmodel.EditorToolGroup
import com.example.ui.theme.*

@Composable
fun VideoPreviewSurface(
    aspectRatio: AspectRatio,
    activeClip: VideoClip? = null,
    videoClips: List<VideoClip> = emptyList(),
    textLayers: List<TextLayer> = emptyList(),
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    activeToolGroup: EditorToolGroup,
    selection: SelectionTarget? = null,
    onTogglePlayPause: () -> Unit,
    onSelectTarget: (SelectionTarget?) -> Unit = {},
    onSelectTextLayer: (String?) -> Unit = { id -> onSelectTarget(id?.let { SelectionTarget.Text(it) }) },
    selectedTextLayerId: String? = (selection as? SelectionTarget.Text)?.layerId,
    onTransformChange: (Transform) -> Unit = {},
    onCropChange: (CropState) -> Unit = {},
    onGestureCommit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isClipSelected = selection is SelectionTarget.Video || selection is SelectionTarget.Overlay
    val effectiveSelectedClipId = when (selection) {
        is SelectionTarget.Video -> selection.clipId
        is SelectionTarget.Overlay -> selection.overlayId
        else -> null
    }

    // Determine all active video & image clips (main track and overlay tracks)
    val allClips = remember(videoClips, activeClip) {
        if (videoClips.isNotEmpty()) videoClips else listOfNotNull(activeClip)
    }

    val activeClips = remember(allClips, activeClip, playheadMs) {
        allClips.filter { clip ->
            if (clip.trackId == "track_video_main") {
                (activeClip != null && clip.id == activeClip.id) ||
                (playheadMs >= clip.timelineStartMs && playheadMs < clip.timelineStartMs + clip.trimmedDurationMs)
            } else {
                playheadMs >= clip.timelineStartMs && playheadMs < clip.timelineStartMs + clip.trimmedDurationMs
            }
        }.sortedBy { it.zIndex }
    }

    val activeTextLayers = remember(textLayers, playheadMs) {
        textLayers.filter { layer ->
            playheadMs >= layer.timelineStartMs &&
            playheadMs <= (layer.timelineStartMs + layer.timelineDurationMs)
        }.sortedBy { it.zIndex }
    }

    val selectedClip = remember(allClips, effectiveSelectedClipId) {
        allClips.find { it.id == effectiveSelectedClipId }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkCanvas)
            .clickable {
                // Tapping outer background clears selection
                onSelectTarget(null)
            }
    ) {
        // Video Preview Canvas fitted directly to aspect ratio
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio.aspectRatioFloat)
                .background(Color.Black)
                .clipToBounds()
                .testTag("preview_canvas_box")
        ) {
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()

            if (activeClips.isEmpty() && activeClip == null && textLayers.isEmpty()) {
                // Empty state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .clickable { onSelectTarget(null) }
                ) {
                    Text(
                        text = "SHORT CUT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = ShortCutAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add or select clips to preview",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            } else {
                // Multi-layer visual composition: All active video clips and overlays rendered in zIndex order
                for (clip in activeClips) {
                    val isThisClipSelected = clip.id == effectiveSelectedClipId
                    val transform = clip.transform
                    val crop = transform.crop

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(clip.id, activeToolGroup, selection) {
                                if (isThisClipSelected && (activeToolGroup == EditorToolGroup.TRANSFORM || activeToolGroup == EditorToolGroup.NONE)) {
                                    detectTransformGestures(
                                        onGesture = { _, pan, zoom, rotation ->
                                            val newScale = (transform.scale * zoom).coerceIn(0.15f, 6.0f)
                                            val newRot = ((transform.rotation + rotation + 180f) % 360f) - 180f
                                            val newTx = transform.translationX + pan.x
                                            val newTy = transform.translationY + pan.y
                                            onTransformChange(
                                                transform.copy(
                                                    scale = newScale,
                                                    rotation = newRot,
                                                    translationX = newTx,
                                                    translationY = newTy
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                    ) {
                        ClipRenderSurface(
                            clip = clip,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (clip.trackId == "track_video_main") {
                                        onSelectTarget(SelectionTarget.Video(clip.id))
                                    } else {
                                        onSelectTarget(SelectionTarget.Overlay(clip.id))
                                    }
                                }
                        )

                        // Transform Bounding Box Guide Overlay (when in TRANSFORM tool group or this clip is selected)
                        if (isThisClipSelected && (activeToolGroup == EditorToolGroup.TRANSFORM || isClipSelected)) {
                            TransformBoundingBoxOverlay(
                                transform = transform,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Interactive Crop Overlay (when in CROP tool group for selected clip)
                        if (isThisClipSelected && activeToolGroup == EditorToolGroup.CROP) {
                            InteractiveCropOverlay(
                                crop = crop,
                                onCropChange = onCropChange,
                                onCommit = onGestureCommit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Text Layers Overlay
            for (textLayer in activeTextLayers) {
                TextLayerOverlay(
                    layer = textLayer,
                    isSelected = textLayer.id == selectedTextLayerId,
                    onSelect = {
                        onSelectTarget(SelectionTarget.Text(textLayer.id))
                        onSelectTextLayer(textLayer.id)
                    }
                )
            }

            // Play / Pause Floating Overlay Indicator (when tapping preview or paused)
            AnimatedVisibility(
                visible = !isPlaying && activeToolGroup == EditorToolGroup.NONE && selection == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable { onTogglePlayPause() }
            ) {
                Surface(
                    color = DarkSurfaceHigh.copy(alpha = 0.75f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ShortCutAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = TextHighContrast,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipRenderSurface(
    clip: VideoClip,
    playheadMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transform = clip.transform
    val adjustments = clip.adjustments
    val crop = transform.crop
    val framing = transform.framingMode

    val isRealMedia = clip.sourceUri.startsWith("content://") ||
            clip.sourceUri.startsWith("file://") ||
            clip.sourceUri.startsWith("android.resource://") ||
            clip.sourceUri.startsWith("http")

    val localClipTimeMs = remember(playheadMs, clip.timelineStartMs, clip.sourceStartTimeMs, clip.speed, clip.sourceDurationMs) {
        val rel = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L)
        (clip.sourceStartTimeMs + (rel * clip.speed).toLong()).coerceIn(0L, clip.sourceDurationMs)
    }

    var frameBitmap by remember(clip.sourceUri) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(clip.sourceUri, localClipTimeMs / 60L) {
        if (isRealMedia) {
            val bitmap = MediaFrameLoader.loadFrame(context, clip.sourceUri, localClipTimeMs, 720, 720)
            if (bitmap != null) {
                frameBitmap = bitmap
            }
        }
    }

    // Construct Color Matrix for dynamic adjustments
    val matrix = remember(adjustments, clip.filter) {
        val cm = ColorMatrix()
        // Saturation
        val sat = (adjustments.saturation + 100f) / 100f
        cm.setToSaturation(sat.coerceIn(0f, 3f))

        // Apply filter preset matrix adjustments
        when (clip.filter) {
            FilterPreset.CINEMATIC -> {
                cm.timesAssign(
                    ColorMatrix(
                        floatArrayOf(
                            1.1f, 0f, 0f, 0f, 10f,
                            0f, 1.0f, 0f, 0f, 5f,
                            0f, 0f, 1.2f, 0f, 15f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.VINTAGE -> {
                cm.timesAssign(
                    ColorMatrix(
                        floatArrayOf(
                            1.2f, 0.1f, 0.1f, 0f, 20f,
                            0.2f, 1.0f, 0.1f, 0f, 10f,
                            0.1f, 0.1f, 0.8f, 0f, -10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.CYBERPUNK -> {
                cm.timesAssign(
                    ColorMatrix(
                        floatArrayOf(
                            1.3f, 0f, 0.2f, 0f, 10f,
                            0f, 1.1f, 0.3f, 0f, -5f,
                            0.4f, 0.2f, 1.4f, 0f, 20f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.WARM -> {
                cm.timesAssign(
                    ColorMatrix(
                        floatArrayOf(
                            1.2f, 0f, 0f, 0f, 15f,
                            0f, 1.05f, 0f, 0f, 10f,
                            0f, 0f, 0.9f, 0f, -10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.COOL -> {
                cm.timesAssign(
                    ColorMatrix(
                        floatArrayOf(
                            0.9f, 0f, 0f, 0f, -10f,
                            0f, 1.0f, 0f, 0f, 5f,
                            0f, 0f, 1.3f, 0f, 20f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.NOIR -> {
                cm.setToSaturation(0f)
            }
            FilterPreset.VIVID -> {
                cm.setToSaturation(1.6f)
            }
            FilterPreset.ORIGINAL -> {}
        }
        cm
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = transform.scale * (if (transform.isFlippedHorizontal) -1f else 1f)
                scaleY = transform.scale * (if (transform.isFlippedVertical) -1f else 1f)
                rotationZ = transform.rotation
                translationX = transform.translationX
                translationY = transform.translationY
                alpha = adjustments.opacity
            }
    ) {
        val cropModifier = if (crop.isEnabled) {
            Modifier.graphicsLayer {
                this.clip = true
                shape = object : Shape {
                    override fun createOutline(
                        size: Size,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        density: androidx.compose.ui.unit.Density
                    ): Outline {
                        val leftPx = crop.left * size.width
                        val topPx = crop.top * size.height
                        val rightPx = crop.right * size.width
                        val bottomPx = crop.bottom * size.height
                        return Outline.Rectangle(
                            Rect(leftPx, topPx, rightPx, bottomPx)
                        )
                    }
                }
            }
        } else Modifier

        val currentBitmap = frameBitmap
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = clip.name,
                contentScale = when (framing) {
                    FramingMode.FIT -> ContentScale.Fit
                    FramingMode.FILL -> ContentScale.Crop
                    FramingMode.ORIGINAL -> ContentScale.Inside
                },
                colorFilter = ColorFilter.colorMatrix(matrix),
                modifier = Modifier
                    .fillMaxSize()
                    .then(cropModifier)
            )
        } else {
            // High fidelity visual representation of the video frame
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(cropModifier)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(clip.thumbnailColorSeed.toInt()),
                                DarkSurface
                            )
                        )
                    )
            )

            // Clip Info Center Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = if (clip.mediaType == MediaType.IMAGE) Icons.Default.Image else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = TextHighContrast,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = clip.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextHighContrast
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0x66000000),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Speed ${"%.2f".format(clip.speed)}x",
                            style = MaterialTheme.typography.labelSmall.copy(color = ShortCutCyan),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (clip.filter != FilterPreset.ORIGINAL) {
                        Surface(
                            color = Color(0x66000000),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = clip.filter.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(color = ShortCutPurple),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (transform.crop.isEnabled) {
                        Surface(
                            color = Color(0x66000000),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Cropped",
                                style = MaterialTheme.typography.labelSmall.copy(color = ShortCutGreen),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformBoundingBoxOverlay(
    transform: Transform,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .graphicsLayer {
                scaleX = transform.scale * (if (transform.isFlippedHorizontal) -1f else 1f)
                scaleY = transform.scale * (if (transform.isFlippedVertical) -1f else 1f)
                rotationZ = transform.rotation
                translationX = transform.translationX
                translationY = transform.translationY
            }
    ) {
        val strokeWidth = 2.dp.toPx()
        val cornerSize = 14.dp.toPx()

        // Outer bounding box
        drawRect(
            color = ShortCutCyan,
            size = size,
            style = Stroke(width = strokeWidth)
        )

        // Corner handles
        val corners = listOf(
            Offset(0f, 0f),
            Offset(size.width, 0f),
            Offset(0f, size.height),
            Offset(size.width, size.height)
        )

        for (corner in corners) {
            drawCircle(
                color = TextHighContrast,
                radius = cornerSize / 2f,
                center = corner
            )
            drawCircle(
                color = ShortCutAccent,
                radius = cornerSize / 3f,
                center = corner
            )
        }
    }
}

@Composable
private fun InteractiveCropOverlay(
    crop: CropState,
    onCropChange: (CropState) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(crop) {
                    detectDragGestures(
                        onDragEnd = { onCommit() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dxNorm = dragAmount.x / w
                            val dyNorm = dragAmount.y / h

                            val newLeft = (crop.left + dxNorm).coerceIn(0f, crop.right - 0.1f)
                            val newRight = (crop.right + dxNorm).coerceIn(crop.left + 0.1f, 1f)
                            val newTop = (crop.top + dyNorm).coerceIn(0f, crop.bottom - 0.1f)
                            val newBottom = (crop.bottom + dyNorm).coerceIn(crop.top + 0.1f, 1f)

                            onCropChange(
                                crop.copy(
                                    isEnabled = true,
                                    left = newLeft,
                                    right = newRight,
                                    top = newTop,
                                    bottom = newBottom
                                )
                            )
                        }
                    )
                }
        ) {
            val leftPx = crop.left * size.width
            val topPx = crop.top * size.height
            val rightPx = crop.right * size.width
            val bottomPx = crop.bottom * size.height
            val cropW = rightPx - leftPx
            val cropH = bottomPx - topPx

            // 1. Darkened exterior mask
            val maskColor = Color(0x99000000)
            // Top
            drawRect(maskColor, topLeft = Offset(0f, 0f), size = Size(size.width, topPx))
            // Bottom
            drawRect(maskColor, topLeft = Offset(0f, bottomPx), size = Size(size.width, size.height - bottomPx))
            // Left
            drawRect(maskColor, topLeft = Offset(0f, topPx), size = Size(leftPx, cropH))
            // Right
            drawRect(maskColor, topLeft = Offset(rightPx, topPx), size = Size(size.width - rightPx, cropH))

            // 2. Crop border
            drawRect(
                color = ShortCutCyan,
                topLeft = Offset(leftPx, topPx),
                size = Size(cropW, cropH),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Rule of Thirds Grid Lines
            val gridColor = Color(0x6600E5FF)
            val gridStroke = Stroke(width = 1.dp.toPx())

            drawLine(gridColor, Offset(leftPx + cropW / 3f, topPx), Offset(leftPx + cropW / 3f, bottomPx), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(leftPx + 2f * cropW / 3f, topPx), Offset(leftPx + 2f * cropW / 3f, bottomPx), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(leftPx, topPx + cropH / 3f), Offset(rightPx, topPx + cropH / 3f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(leftPx, topPx + 2f * cropH / 3f), Offset(rightPx, topPx + 2f * cropH / 3f), strokeWidth = 1.dp.toPx())

            // 4. Corner bracket anchors
            val cornerLen = 16.dp.toPx()
            val bracketStroke = 3.dp.toPx()
            val bracketColor = TextHighContrast

            // Top-left
            drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx + cornerLen, topPx), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(leftPx, topPx), Offset(leftPx, topPx + cornerLen), strokeWidth = bracketStroke)
            // Top-right
            drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx - cornerLen, topPx), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(rightPx, topPx), Offset(rightPx, topPx + cornerLen), strokeWidth = bracketStroke)
            // Bottom-left
            drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx + cornerLen, bottomPx), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - cornerLen), strokeWidth = bracketStroke)
            // Bottom-right
            drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx - cornerLen, bottomPx), strokeWidth = bracketStroke)
            drawLine(bracketColor, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - cornerLen), strokeWidth = bracketStroke)
        }
    }
}

@Composable
private fun TextLayerOverlay(
    layer: TextLayer,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = layer.positionX * 200f
                    translationY = layer.positionY * 200f
                    scaleX = layer.scale
                    scaleY = layer.scale
                    rotationZ = layer.rotation
                }
                .then(
                    if (isSelected) Modifier.border(1.5.dp, ShortCutCyan, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .clickable { onSelect() }
                .then(
                    if (layer.hasBackground) Modifier
                        .background(
                            Color(layer.backgroundColorArgb.toInt()),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    else Modifier.padding(4.dp)
                )
        ) {
            Text(
                text = layer.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = layer.fontSizeSp.sp,
                    fontWeight = if (layer.isBold) FontWeight.Bold else FontWeight.Normal,
                    color = Color(layer.textColorArgb.toInt()),
                    textAlign = when (layer.alignment) {
                        0 -> TextAlign.Start
                        2 -> TextAlign.End
                        else -> TextAlign.Center
                    }
                )
            )

            // Corner anchor handles when selected
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .background(ShortCutCyan, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(ShortCutCyan, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-4).dp, y = 4.dp)
                        .background(ShortCutCyan, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(ShortCutCyan, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CanvasGuidelines() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Subtle vertical and horizontal center crosshair guides
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(0.5.dp)
                .align(Alignment.Center)
                .background(Color(0x10FFFFFF))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .align(Alignment.Center)
                .background(Color(0x10FFFFFF))
        )
    }
}

