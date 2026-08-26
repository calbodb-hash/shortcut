package com.example.presentation.ui.screens.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AudioClip
import com.example.domain.model.AudioTrackType
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Contextual editing panel for selected AudioClip.
 * Allows adjusting volume, fade in/out, speed, pan, ducking, split, duplicate, and deletion.
 */
@Composable
fun AudioEditorPanel(
    audioClip: AudioClip,
    playheadMs: Long,
    onUpdateVolume: (Float) -> Unit,
    onUpdateFade: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onUpdatePan: (Float) -> Unit,
    onToggleDucking: (Boolean, Float) -> Unit,
    onToggleMute: () -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(AudioTab.VOLUME) }

    Surface(
        color = DarkSurface,
        modifier = modifier
            .fillMaxWidth()
            .border(androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            // Header: Title, Track badge, Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (audioClip.audioTrackType) {
                        AudioTrackType.MUSIC -> Color(0xFFB388FF)
                        AudioTrackType.VOICE_OVER -> Color(0xFFFFB74D)
                        AudioTrackType.SFX -> Color(0xFF80DEEA)
                        AudioTrackType.EXTRACTED -> Color(0xFF81D4FA)
                        AudioTrackType.ORIGINAL_VIDEO -> Color(0xFF4FC3F7)
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = audioClip.audioTrackType.displayName.uppercase(),
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = audioClip.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = TextHighContrast,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (audioClip.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute Toggle",
                            tint = if (audioClip.isMuted) Color(0xFFFF5252) else ShortCutAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMediumContrast,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider(color = DarkSurfaceBorder, thickness = 0.5.dp)

            // Sub-tabs navigation
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AudioTab.values()) { tab ->
                    val isSelected = selectedSubTab == tab
                    Surface(
                        color = if (isSelected) ShortCutAccent.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ShortCutAccent else DarkSurfaceBorder
                        ),
                        modifier = Modifier.clickable { selectedSubTab = tab }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) ShortCutAccent else TextMediumContrast,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) ShortCutAccent else TextMediumContrast,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                when (selectedSubTab) {
                    AudioTab.VOLUME -> {
                        VolumeControl(
                            volume = audioClip.volume,
                            onVolumeChanged = onUpdateVolume
                        )
                    }
                    AudioTab.FADE -> {
                        FadeControl(
                            fadeInMs = audioClip.fadeInMs,
                            fadeOutMs = audioClip.fadeOutMs,
                            clipDurationMs = audioClip.trimmedDurationMs,
                            onFadeChanged = onUpdateFade
                        )
                    }
                    AudioTab.SPEED -> {
                        SpeedControl(
                            speed = audioClip.speed,
                            onSpeedChanged = onUpdateSpeed
                        )
                    }
                    AudioTab.PAN -> {
                        PanControl(
                            pan = audioClip.pan,
                            onPanChanged = onUpdatePan
                        )
                    }
                    AudioTab.DUCKING -> {
                        DuckingControl(
                            duckingEnabled = audioClip.duckingEnabled,
                            duckingLevel = audioClip.duckingLevel,
                            onDuckingChanged = onToggleDucking
                        )
                    }
                    AudioTab.ACTIONS -> {
                        ActionsControl(
                            canSplit = playheadMs > audioClip.timelineStartMs && playheadMs < (audioClip.timelineStartMs + audioClip.trimmedDurationMs),
                            onSplit = onSplitAtPlayhead,
                            onDuplicate = onDuplicate,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeControl(
    volume: Float,
    onVolumeChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Track Volume", color = TextMediumContrast, fontSize = 12.sp)
            Text(
                "${(volume * 100).roundToInt()}%",
                color = ShortCutAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            valueRange = 0f..2.0f,
            colors = SliderDefaults.colors(
                thumbColor = ShortCutAccent,
                activeTrackColor = ShortCutAccent,
                inactiveTrackColor = DarkSurfaceBorder
            )
        )
    }
}

@Composable
private fun FadeControl(
    fadeInMs: Long,
    fadeOutMs: Long,
    clipDurationMs: Long,
    onFadeChanged: (Long, Long) -> Unit
) {
    val maxFadeSec = (clipDurationMs / 2000f).coerceIn(1f, 5f)
    var inSec by remember(fadeInMs) { mutableStateOf(fadeInMs / 1000f) }
    var outSec by remember(fadeOutMs) { mutableStateOf(fadeOutMs / 1000f) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fade In Duration", color = TextMediumContrast, fontSize = 12.sp)
                Text(String.format("%.1fs", inSec), color = ShortCutAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = inSec,
                onValueChange = {
                    inSec = it
                    onFadeChanged((it * 1000).toLong(), (outSec * 1000).toLong())
                },
                valueRange = 0f..maxFadeSec,
                colors = SliderDefaults.colors(thumbColor = ShortCutAccent, activeTrackColor = ShortCutAccent)
            )
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fade Out Duration", color = TextMediumContrast, fontSize = 12.sp)
                Text(String.format("%.1fs", outSec), color = ShortCutAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = outSec,
                onValueChange = {
                    outSec = it
                    onFadeChanged((inSec * 1000).toLong(), (it * 1000).toLong())
                },
                valueRange = 0f..maxFadeSec,
                colors = SliderDefaults.colors(thumbColor = ShortCutAccent, activeTrackColor = ShortCutAccent)
            )
        }
    }
}

@Composable
private fun SpeedControl(
    speed: Float,
    onSpeedChanged: (Float) -> Unit
) {
    val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Audio Pitch & Speed", color = TextMediumContrast, fontSize = 12.sp)
            Text("${speed}x", color = ShortCutAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            speedPresets.forEach { preset ->
                val isSelected = speed == preset
                Surface(
                    color = if (isSelected) ShortCutAccent else DarkSurfaceBorder,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onSpeedChanged(preset) }
                ) {
                    Text(
                        text = "${preset}x",
                        color = if (isSelected) Color.Black else TextHighContrast,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PanControl(
    pan: Float,
    onPanChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Stereo Balance", color = TextMediumContrast, fontSize = 12.sp)
            val panText = when {
                pan < -0.1f -> "L ${(( -pan ) * 100).roundToInt()}%"
                pan > 0.1f -> "R ${(pan * 100).roundToInt()}%"
                else -> "Center"
            }
            Text(panText, color = ShortCutAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Slider(
            value = pan,
            onValueChange = onPanChanged,
            valueRange = -1.0f..1.0f,
            colors = SliderDefaults.colors(thumbColor = ShortCutAccent, activeTrackColor = ShortCutAccent)
        )
    }
}

@Composable
private fun DuckingControl(
    duckingEnabled: Boolean,
    duckingLevel: Float,
    onDuckingChanged: (Boolean, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Smart Audio Ducking", color = TextHighContrast, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Automatically lowers music when voice-over speaks",
                    color = TextMediumContrast,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = duckingEnabled,
                onCheckedChange = { onDuckingChanged(it, duckingLevel) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = ShortCutAccent)
            )
        }

        if (duckingEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ducked Music Volume", color = TextMediumContrast, fontSize = 12.sp)
                Text("${(duckingLevel * 100).roundToInt()}%", color = ShortCutAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = duckingLevel,
                onValueChange = { onDuckingChanged(true, it) },
                valueRange = 0.1f..0.8f,
                colors = SliderDefaults.colors(thumbColor = ShortCutAccent, activeTrackColor = ShortCutAccent)
            )
        }
    }
}

@Composable
private fun ActionsControl(
    canSplit: Boolean,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onSplit,
            enabled = canSplit,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = "Split", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Split", fontSize = 12.sp)
        }

        OutlinedButton(
            onClick = onDuplicate,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Duplicate", fontSize = 12.sp)
        }

        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Delete", fontSize = 12.sp, color = Color.White)
        }
    }
}

private enum class AudioTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    VOLUME("Volume", Icons.Default.VolumeUp),
    FADE("Fade", Icons.Default.LinearScale),
    SPEED("Speed", Icons.Default.Speed),
    PAN("Pan", Icons.Default.Tune),
    DUCKING("Ducking", Icons.Default.RecordVoiceOver),
    ACTIONS("Actions", Icons.Default.MoreHoriz)
}
