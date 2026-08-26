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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.media.MediaFrameLoader
import com.example.domain.engine.TimelineTimeMapper
import com.example.domain.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun TimelineView(
    timeline: Timeline,
    playheadMs: Long,
    isPlaying: Boolean = false,
    selectedClipId: String?,
    selectedTextLayerId: String?,
    selectedAudioClipId: String? = null,
    isCompact: Boolean = false,
    pxPerSecond: Float = 80f,
    isSnappingEnabled: Boolean = true,
    onPause: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    onScrubTo: (Long) -> Unit = onSeekTo,
    onSelectClip: (String?) -> Unit,
    onSelectTextLayer: (String?) -> Unit,
    onSelectAudioClip: (String?) -> Unit = {},
    onTrimClip: (clipId: String, newStartMs: Long, newEndMs: Long) -> Unit,
    onTrimAudioClip: (audioId: String, newStartMs: Long, newEndMs: Long) -> Unit = { _, _, _ -> },
    onMoveAudioClip: (audioId: String, newStartMs: Long) -> Unit = { _, _ -> },
    onUpdateTextLayerTime: (layerId: String, newStartMs: Long, newDurationMs: Long) -> Unit = { _, _, _ -> },
    onUpdateAudioClipTime: (audioId: String, newStartMs: Long, newDurationMs: Long) -> Unit = { _, _, _ -> },
    onToggleTrackMute: (trackId: String) -> Unit = {},
    onToggleTrackLock: (trackId: String) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    onToggleSnapping: () -> Unit = {},
    onSplitCurrent: () -> Unit = {},
    onAddText: () -> Unit = {},
    onAddAudio: () -> Unit = {},
    onAddMedia: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalDurationMs = timeline.totalDurationMs.coerceAtLeast(1000L)
    val totalWidthDp = ((totalDurationMs / 1000f) * pxPerSecond).dp + 240.dp

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val halfScreenWidthDp = screenWidthDp / 2

    val density = LocalDensity.current
    val pxPerSecondPx = with(density) { pxPerSecond.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    var isUserInteractingWithLiner by remember { mutableStateOf(false) }

    var isDraggingHandle by remember { mutableStateOf(false) }
    var snapActiveTimeMs by remember { mutableStateOf<Long?>(null) }

    // 1. Move timeline smoothly with playhead during playback
    LaunchedEffect(playheadMs, isPlaying) {
        if (isPlaying && !scrollState.isScrollInProgress && !isUserInteractingWithLiner) {
            val targetScrollPx = ((playheadMs / 1000f) * pxPerSecondPx).roundToInt()
            if (abs(scrollState.value - targetScrollPx) > 1) {
                scrollState.scrollTo(targetScrollPx)
            }
        }
    }

    // 2. If user touches and scrolls timeline, immediately pause video
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            if (isPlaying) {
                onPause()
            }
        }
    }

    // 3. Track scroll position to update playhead during manual scrub
    LaunchedEffect(scrollState.value) {
        if (scrollState.isScrollInProgress && !isPlaying) {
            val calculatedMs = ((scrollState.value / pxPerSecondPx) * 1000f).roundToLong()
                .coerceIn(0L, totalDurationMs)
            if (abs(calculatedMs - playheadMs) > 15L) {
                onScrubTo(calculatedMs)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TimelineBackground)
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, DarkSurfaceBorder))
    ) {
        // 1. Timeline Toolbar (Timecode, Snapping, Zoom, Split Shortcut)
        TimelineToolbar(
            playheadMs = playheadMs,
            totalDurationMs = totalDurationMs,
            pxPerSecond = pxPerSecond,
            isSnappingEnabled = isSnappingEnabled,
            canSplit = selectedClipId != null || selectedAudioClipId != null,
            onSplitCurrent = onSplitCurrent,
            onToggleSnapping = onToggleSnapping,
            onZoomIn = { onZoomChange((pxPerSecond * 1.25f).coerceIn(30f, 250f)) },
            onZoomOut = { onZoomChange((pxPerSecond / 1.25f).coerceIn(30f, 250f)) }
        )

        // 2. Main Timeline Multi-Track Scroll Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(pxPerSecond) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            val newPx = (pxPerSecond * zoom).coerceIn(30f, 250f)
                            onZoomChange(newPx)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(vertical = if (isCompact) 2.dp else 4.dp)
            ) {
                // Left spacer so playhead can reach 0:00 at center
                Spacer(modifier = Modifier.width(halfScreenWidthDp))

                Column(
                    modifier = Modifier.width(totalWidthDp)
                ) {
                    // Time Ruler
                    TimelineRuler(
                        totalDurationMs = totalDurationMs,
                        pxPerSecond = pxPerSecond,
                        pxPerSecondPx = pxPerSecondPx,
                        isSnappingEnabled = isSnappingEnabled,
                        timeline = timeline,
                        onSeek = { targetMs ->
                            if (isPlaying) onPause()
                            onScrubTo(targetMs)
                            coroutineScope.launch {
                                val targetScrollPx = ((targetMs / 1000f) * pxPerSecondPx).roundToInt()
                                scrollState.scrollTo(targetScrollPx)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompact) 20.dp else 26.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Video Track Row
                    VideoTrackRow(
                        videoClips = timeline.videoClips,
                        selectedClipId = selectedClipId,
                        pxPerSecond = pxPerSecond,
                        pxPerSecondPx = pxPerSecondPx,
                        isSnappingEnabled = isSnappingEnabled,
                        timeline = timeline,
                        playheadMs = playheadMs,
                        isCompact = isCompact,
                        onAddMedia = onAddMedia,
                        onSelectClip = {
                            onSelectAudioClip(null)
                            onSelectTextLayer(null)
                            onSelectClip(it)
                        },
                        onTrimClip = { clipId, startMs, endMs ->
                            onTrimClip(clipId, startMs, endMs)
                        },
                        onSnapTriggered = { snapActiveTimeMs = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isCompact) 48.dp else 58.dp)
                    )

                    // Text Track Row (if any)
                    if (timeline.textLayers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        TextTrackRow(
                            textLayers = timeline.textLayers,
                            selectedTextLayerId = selectedTextLayerId,
                            pxPerSecond = pxPerSecond,
                            onSelectTextLayer = {
                                onSelectClip(null)
                                onSelectAudioClip(null)
                                onSelectTextLayer(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 22.dp else 28.dp)
                        )
                    }

                    // Multi-Track Audio Rows (Music, Voice-over, SFX)
                    val musicClips = timeline.audioClips.filter { it.audioTrackType == AudioTrackType.MUSIC || it.audioTrackType == AudioTrackType.EXTRACTED || it.audioTrackType == AudioTrackType.ORIGINAL_VIDEO }
                    val voiceClips = timeline.audioClips.filter { it.audioTrackType == AudioTrackType.VOICE_OVER }
                    val sfxClips = timeline.audioClips.filter { it.audioTrackType == AudioTrackType.SFX }

                    if (musicClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        AudioTrackRow(
                            trackTitle = "Music",
                            audioClips = musicClips,
                            selectedAudioClipId = selectedAudioClipId,
                            pxPerSecond = pxPerSecond,
                            onSelectAudioClip = {
                                onSelectClip(null)
                                onSelectTextLayer(null)
                                onSelectAudioClip(it)
                            },
                            onTrimAudioClip = onTrimAudioClip,
                            onMoveAudioClip = onMoveAudioClip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 26.dp else 32.dp)
                        )
                    }

                    if (voiceClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        AudioTrackRow(
                            trackTitle = "Voice",
                            audioClips = voiceClips,
                            selectedAudioClipId = selectedAudioClipId,
                            pxPerSecond = pxPerSecond,
                            onSelectAudioClip = {
                                onSelectClip(null)
                                onSelectTextLayer(null)
                                onSelectAudioClip(it)
                            },
                            onTrimAudioClip = onTrimAudioClip,
                            onMoveAudioClip = onMoveAudioClip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 26.dp else 32.dp)
                        )
                    }

                    if (sfxClips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        AudioTrackRow(
                            trackTitle = "SFX",
                            audioClips = sfxClips,
                            selectedAudioClipId = selectedAudioClipId,
                            pxPerSecond = pxPerSecond,
                            onSelectAudioClip = {
                                onSelectClip(null)
                                onSelectTextLayer(null)
                                onSelectAudioClip(it)
                            },
                            onTrimAudioClip = onTrimAudioClip,
                            onMoveAudioClip = onMoveAudioClip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 26.dp else 32.dp)
                        )
                    }
                }

                // Right spacer so playhead can reach the end at center
                Spacer(modifier = Modifier.width(halfScreenWidthDp))
            }

            // Interactive Center Playhead Needle Indicator
            TimelinePlayheadNeedle(
                playheadMs = playheadMs,
                isHeld = isUserInteractingWithLiner,
                onHoldStart = {
                    isUserInteractingWithLiner = true
                    if (isPlaying) {
                        onPause()
                    }
                },
                onDragDelta = { deltaPx ->
                    coroutineScope.launch {
                        val deltaMs = (-deltaPx / pxPerSecondPx * 1000f).roundToLong()
                        val newMs = (playheadMs + deltaMs).coerceIn(0L, totalDurationMs)
                        onScrubTo(newMs)
                        val targetScrollPx = ((newMs / 1000f) * pxPerSecondPx).roundToInt()
                        scrollState.scrollTo(targetScrollPx)
                    }
                },
                onHoldEnd = {
                    isUserInteractingWithLiner = false
                    onSeekTo(playheadMs)
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TimelineToolbar(
    playheadMs: Long,
    totalDurationMs: Long,
    pxPerSecond: Float,
    isSnappingEnabled: Boolean,
    canSplit: Boolean,
    onSplitCurrent: () -> Unit,
    onToggleSnapping: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Surface(
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, DarkSurfaceBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Timecode Readout
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TimelineTimeUtils.formatTimecode(playheadMs),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = ShortCutAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = " / ${TimelineTimeUtils.formatTimecode(totalDurationMs, showMillis = false)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMediumContrast,
                        fontSize = 11.sp
                    )
                )
            }

            // Action Shortcuts
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Split Action Button
                if (canSplit) {
                    IconButton(
                        onClick = onSplitCurrent,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Split",
                            tint = ShortCutAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Snap Magnet Toggle
                IconButton(
                    onClick = onToggleSnapping,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isSnappingEnabled) Icons.Default.ElectricBolt else Icons.Default.FlashOff,
                        contentDescription = "Snapping",
                        tint = if (isSnappingEnabled) ShortCutCyan else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Zoom Out
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = TextMediumContrast,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Zoom In
                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = TextMediumContrast,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    totalDurationMs: Long,
    pxPerSecond: Float,
    pxPerSecondPx: Float,
    isSnappingEnabled: Boolean,
    timeline: Timeline,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(totalDurationMs, pxPerSecondPx, isSnappingEnabled) {
                detectDragGestures { change, _ ->
                    val x = change.position.x
                    val rawMs = if (pxPerSecondPx > 0f) ((x / pxPerSecondPx) * 1000f).roundToLong().coerceAtLeast(0L) else 0L
                    val targetMs = if (isSnappingEnabled) {
                        TimelineTimeMapper.findSnapPosition(rawMs, timeline).snappedTimeMs
                    } else rawMs
                    onSeek(targetMs)
                }
            }
    ) {
        val totalSec = (totalDurationMs / 1000f).toInt() + 10
        val stepSec = when {
            pxPerSecond > 120f -> 1
            pxPerSecond > 50f -> 2
            else -> 5
        }

        for (sec in 0..totalSec step stepSec) {
            val startX = sec * pxPerSecondPx

            // Major second tick
            drawLine(
                color = TimelinePlayhead,
                start = Offset(startX, size.height * 0.4f),
                end = Offset(startX, size.height),
                strokeWidth = 1.5.dp.toPx()
            )

            // Sub-second ticks
            val subStep = pxPerSecondPx / 4f
            for (sub in 1..3) {
                val subX = startX + (sub * subStep)
                if (subX < size.width) {
                    drawLine(
                        color = TimelineRulerTick,
                        start = Offset(subX, size.height * 0.75f),
                        end = Offset(subX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoTrackRow(
    videoClips: List<VideoClip>,
    selectedClipId: String?,
    pxPerSecond: Float,
    pxPerSecondPx: Float = pxPerSecond,
    isSnappingEnabled: Boolean,
    timeline: Timeline,
    playheadMs: Long,
    isCompact: Boolean,
    onAddMedia: () -> Unit,
    onSelectClip: (String?) -> Unit,
    onTrimClip: (clipId: String, newStartMs: Long, newEndMs: Long) -> Unit,
    onSnapTriggered: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (clip in videoClips) {
            val clipDurationSec = clip.trimmedDurationMs / 1000f
            val clipWidthDp = (clipDurationSec * pxPerSecond).dp
            val isSelected = clip.id == selectedClipId

            TimelineClipBlock(
                clip = clip,
                isSelected = isSelected,
                widthDp = clipWidthDp,
                pxPerSecond = pxPerSecond,
                pxPerSecondPx = pxPerSecondPx,
                isSnappingEnabled = isSnappingEnabled,
                timeline = timeline,
                playheadMs = playheadMs,
                onClick = {
                    onSelectClip(if (isSelected) null else clip.id)
                },
                onTrimLeft = { deltaPx ->
                    val deltaMs = (deltaPx / pxPerSecondPx * 1000f).roundToLong()
                    val candidateStartMs = (clip.sourceStartTimeMs + deltaMs).coerceIn(0L, clip.sourceEndTimeMs - TimelineTimeUtils.MIN_CLIP_DURATION_MS)
                    onTrimClip(clip.id, candidateStartMs, clip.sourceEndTimeMs)
                },
                onTrimRight = { deltaPx ->
                    val deltaMs = (deltaPx / pxPerSecondPx * 1000f).roundToLong()
                    val candidateEndMs = (clip.sourceEndTimeMs + deltaMs).coerceIn(clip.sourceStartTimeMs + TimelineTimeUtils.MIN_CLIP_DURATION_MS, clip.sourceDurationMs)
                    onTrimClip(clip.id, clip.sourceStartTimeMs, candidateEndMs)
                }
            )
        }

        // Add Media (+) block at the end of the video track
        Surface(
            color = DarkSurfaceHigh,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier
                .size(width = 36.dp, height = if (isCompact) 42.dp else 52.dp)
                .clickable { onAddMedia() }
                .testTag("timeline_add_media_button")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Media",
                    tint = ShortCutCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TimelineClipBlock(
    clip: VideoClip,
    isSelected: Boolean,
    widthDp: androidx.compose.ui.unit.Dp,
    pxPerSecond: Float,
    pxPerSecondPx: Float = pxPerSecond,
    isSnappingEnabled: Boolean,
    timeline: Timeline,
    playheadMs: Long,
    onClick: () -> Unit,
    onTrimLeft: (Float) -> Unit,
    onTrimRight: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbBitmap by remember(clip.sourceUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(clip.sourceUri) {
        if (clip.sourceUri.startsWith("content://") || clip.sourceUri.startsWith("file://") || clip.sourceUri.startsWith("android.resource://")) {
            thumbBitmap = MediaFrameLoader.loadFrame(context, clip.sourceUri, clip.sourceStartTimeMs, 120, 120)
        }
    }

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(clip.thumbnailColorSeed.toInt()))
            .then(
                if (isSelected) Modifier.border(2.dp, ShortCutAccent, RoundedCornerShape(6.dp))
                else Modifier.border(1.dp, DarkSurfaceBorder, RoundedCornerShape(6.dp))
            )
            .clickable(onClick = onClick)
            .testTag("timeline_clip_${clip.id}")
    ) {
        // Real thumbnail preview if available
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.65f)
            )
        } else {
            // Filmstrip preview cells fallback
            TimelineFilmstripCanvas(
                thumbnailColorSeed = clip.thumbnailColorSeed,
                clipDurationMs = clip.trimmedDurationMs,
                pxPerSecond = pxPerSecond,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Clip Info Overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = clip.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextHighContrast,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Speed or Filter Badge if modified
            if (clip.speed != 1.0f) {
                Surface(
                    color = ShortCutAccent.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(clip.speed)}x",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        ),
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Duration Label bottom right
        Text(
            text = "${"%.1f".format(clip.trimmedDurationMs / 1000f)}s",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 3.dp)
        )

        // Interactive Left & Right Trim Handles when selected
        if (isSelected) {
            // Left Handle (Draggable)
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(ShortCutAccent, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .pointerInput(clip.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onTrimLeft(dragAmount.x)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
            }

            // Right Handle (Draggable)
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(ShortCutAccent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .pointerInput(clip.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onTrimRight(dragAmount.x)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
private fun TextTrackRow(
    textLayers: List<TextLayer>,
    selectedTextLayerId: String?,
    pxPerSecond: Float,
    onSelectTextLayer: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (layer in textLayers) {
            val durationSec = layer.timelineDurationMs / 1000f
            val widthDp = (durationSec * pxPerSecond).dp.coerceAtLeast(36.dp)
            val isSelected = layer.id == selectedTextLayerId

            Surface(
                color = if (isSelected) ShortCutPurple else DarkSurfaceHigh,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) ShortCutCyan else DarkSurfaceBorder
                ),
                modifier = Modifier
                    .width(widthDp)
                    .fillMaxHeight()
                    .clickable { onSelectTextLayer(if (isSelected) null else layer.id) }
                    .padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "Text",
                        tint = TextHighContrast,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = layer.text,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextHighContrast,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    trackTitle: String,
    audioClips: List<AudioClip>,
    selectedAudioClipId: String?,
    pxPerSecond: Float,
    onSelectAudioClip: (String?) -> Unit,
    onTrimAudioClip: (audioId: String, newStartMs: Long, newEndMs: Long) -> Unit,
    onMoveAudioClip: (audioId: String, newStartMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (audio in audioClips) {
            val isSelected = audio.id == selectedAudioClipId
            val durationSec = audio.trimmedDurationMs / 1000f
            val widthDp = (durationSec * pxPerSecond).dp.coerceAtLeast(44.dp)

            // Category color theming
            val (baseBgColor, borderColor, accentColor, iconVector) = when (audio.audioTrackType) {
                AudioTrackType.MUSIC -> Quadruple(
                    Color(0xFF1E1035),
                    Color(0xFF8A2BE2),
                    Color(0xFFB388FF),
                    Icons.Default.MusicNote
                )
                AudioTrackType.VOICE_OVER -> Quadruple(
                    Color(0xFF332005),
                    Color(0xFFFF9800),
                    Color(0xFFFFB74D),
                    Icons.Default.Mic
                )
                AudioTrackType.SFX -> Quadruple(
                    Color(0xFF002B2E),
                    ShortCutCyan,
                    Color(0xFF80DEEA),
                    Icons.Default.Bolt
                )
                AudioTrackType.EXTRACTED -> Quadruple(
                    Color(0xFF0A2239),
                    Color(0xFF29B6F6),
                    Color(0xFF81D4FA),
                    Icons.Default.VolumeUp
                )
                AudioTrackType.ORIGINAL_VIDEO -> Quadruple(
                    Color(0xFF1B2838),
                    Color(0xFF4FC3F7),
                    Color(0xFFE1F5FE),
                    Icons.Default.Videocam
                )
            }

            Box(
                modifier = Modifier
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(baseBgColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ShortCutAccent else borderColor.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelectAudioClip(if (isSelected) null else audio.id) }
                    .padding(end = 4.dp)
            ) {
                // Waveform rendering with trim & fade envelope
                TimelineWaveformCanvas(
                    audioId = audio.id,
                    sourceUri = audio.sourceUri,
                    durationMs = audio.trimmedDurationMs,
                    sourceStartTimeMs = audio.sourceStartTimeMs,
                    sourceEndTimeMs = audio.sourceEndTimeMs,
                    sourceTotalDurationMs = audio.sourceDurationMs,
                    fadeInMs = audio.fadeInMs,
                    fadeOutMs = audio.fadeOutMs,
                    barColor = if (isSelected) ShortCutAccent.copy(alpha = 0.9f) else accentColor.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxSize()
                )

                // Track Label and Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = audio.audioTrackType.displayName,
                        tint = if (isSelected) ShortCutAccent else accentColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = audio.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) ShortCutAccent else accentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (audio.isMuted) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }

                // Interactive Trim Handles when selected
                if (isSelected) {
                    // Left Handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(10.dp)
                            .fillMaxHeight()
                            .background(ShortCutAccent.copy(alpha = 0.85f))
                            .pointerInput(audio.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaMs = (dragAmount.x / pxPerSecond * 1000f).toLong()
                                    val newStart = (audio.sourceStartTimeMs + deltaMs).coerceIn(0L, audio.sourceEndTimeMs - 100L)
                                    onTrimAudioClip(audio.id, newStart, audio.sourceEndTimeMs)
                                }
                            }
                    )

                    // Right Handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(10.dp)
                            .fillMaxHeight()
                            .background(ShortCutAccent.copy(alpha = 0.85f))
                            .pointerInput(audio.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaMs = (dragAmount.x / pxPerSecond * 1000f).toLong()
                                    val newEnd = (audio.sourceEndTimeMs + deltaMs).coerceIn(audio.sourceStartTimeMs + 100L, audio.sourceDurationMs)
                                    onTrimAudioClip(audio.id, audio.sourceStartTimeMs, newEnd)
                                }
                            }
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun TimelinePlayheadNeedle(
    playheadMs: Long,
    isHeld: Boolean,
    onHoldStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldStart()
                        tryAwaitRelease()
                        onHoldEnd()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onHoldStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x)
                    },
                    onDragEnd = {
                        onHoldEnd()
                    },
                    onDragCancel = {
                        onHoldEnd()
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // Needle Head (accented and enlarged when actively touched/held)
        Surface(
            color = if (isHeld) ShortCutAccent else TimelinePlayheadNeedle,
            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            border = if (isHeld) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White) else null,
            modifier = Modifier.size(
                width = if (isHeld) 16.dp else 12.dp,
                height = if (isHeld) 16.dp else 12.dp
            )
        ) {}

        // Needle Line (brightened and expanded when held)
        Box(
            modifier = Modifier
                .width(if (isHeld) 2.5.dp else 2.dp)
                .fillMaxHeight()
                .background(if (isHeld) Color.White else TimelinePlayheadNeedle)
        )
    }
}
