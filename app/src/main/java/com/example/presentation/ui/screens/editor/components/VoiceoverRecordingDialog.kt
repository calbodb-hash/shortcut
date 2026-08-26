package com.example.presentation.ui.screens.editor.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.core.audio.AudioRecordingManager
import com.example.core.audio.RecordingState
import com.example.domain.model.AudioClip
import com.example.domain.model.AudioTrackType
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * Professional voice-over recording studio dialog.
 * Features 3-2-1 countdown, real-time waveform & VU amplitude monitor, and seamless timeline attachment.
 */
@Composable
fun VoiceoverRecordingDialog(
    playheadMs: Long,
    onRecordingComplete: (AudioClip) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recordingManager = remember { AudioRecordingManager(context) }
    val recordingState by recordingManager.recordingState.collectAsState()
    val amplitudeHistory by recordingManager.amplitudeHistory.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    var countdownValue by remember { mutableStateOf<Int?>(null) }
    var currentOutputFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recordingManager.release()
        }
    }

    Dialog(onDismissRequest = {
        if (recordingState is RecordingState.Recording) {
            recordingManager.stopRecording()
        }
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFFF9800).copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice-over",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Record Voice-over",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextHighContrast,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(
                        onClick = {
                            if (recordingState is RecordingState.Recording) {
                                recordingManager.stopRecording()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMediumContrast)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!hasAudioPermission) {
                    // Permission Request Prompt
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Microphone Access Required",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextHighContrast,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Grant microphone permission to record studio-quality voice-overs directly into your video timeline.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent)
                        ) {
                            Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (countdownValue != null) {
                    // 3-2-1 Countdown Animation
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(ShortCutAccent.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = countdownValue.toString(),
                            style = MaterialTheme.typography.displayMedium.copy(
                                color = ShortCutAccent,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                    LaunchedEffect(countdownValue) {
                        if (countdownValue!! > 1) {
                            delay(800)
                            countdownValue = countdownValue!! - 1
                        } else {
                            delay(800)
                            countdownValue = null
                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a")
                            currentOutputFile = file
                            recordingManager.startRecording(file)
                        }
                    }
                } else {
                    // Live Amplitude VU Visualizer Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF14141E))
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val history = amplitudeHistory
                            if (history.isEmpty()) return@Canvas

                            val barWidth = 3.dp.toPx()
                            val barSpacing = 4.dp.toPx()
                            val centerY = size.height / 2f
                            val maxHeight = size.height * 0.9f

                            var currentX = size.width - (history.size * barSpacing)

                            for (amp in history) {
                                val barHalfHeight = (amp * (maxHeight / 2f)).coerceAtLeast(2.dp.toPx())
                                val isHigh = amp > 0.75f
                                drawLine(
                                    color = if (isHigh) Color(0xFFFF5252) else Color(0xFFFFB74D),
                                    start = Offset(currentX, centerY - barHalfHeight),
                                    end = Offset(currentX, centerY + barHalfHeight),
                                    strokeWidth = barWidth
                                )
                                currentX += barSpacing
                            }
                        }

                        // Duration Display
                        val durationMs = when (val s = recordingState) {
                            is RecordingState.Recording -> s.durationMs
                            is RecordingState.Paused -> s.durationMs
                            else -> 0L
                        }
                        Text(
                            text = String.format("%02d:%02d.%01d", (durationMs / 1000) / 60, (durationMs / 1000) % 60, (durationMs % 1000) / 100),
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Controls Row
                    when (recordingState) {
                        is RecordingState.Idle, is RecordingState.Stopped -> {
                            Button(
                                onClick = { countdownValue = 3 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = "Record", tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Recording", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        is RecordingState.Recording -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { recordingManager.pauseRecording() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause")
                                }

                                Button(
                                    onClick = {
                                        val finalFile = currentOutputFile
                                        recordingManager.stopRecording()
                                        if (finalFile != null && finalFile.exists() && finalFile.length() > 0) {
                                            val dur = recordingManager.getRecordingDuration()
                                            val clip = AudioClip(
                                                id = "voice_${UUID.randomUUID().toString().take(8)}",
                                                title = "Voice-over",
                                                sourceUri = finalFile.absolutePath,
                                                sourceDurationMs = dur.coerceAtLeast(1000L),
                                                sourceStartTimeMs = 0L,
                                                sourceEndTimeMs = dur.coerceAtLeast(1000L),
                                                timelineStartMs = playheadMs,
                                                timelineDurationMs = dur.coerceAtLeast(1000L),
                                                audioTrackType = AudioTrackType.VOICE_OVER,
                                                trackId = "track_audio_voice"
                                            )
                                            onRecordingComplete(clip)
                                            onDismiss()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is RecordingState.Paused -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { recordingManager.resumeRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val finalFile = currentOutputFile
                                        recordingManager.stopRecording()
                                        if (finalFile != null && finalFile.exists() && finalFile.length() > 0) {
                                            val dur = recordingManager.getRecordingDuration()
                                            val clip = AudioClip(
                                                id = "voice_${UUID.randomUUID().toString().take(8)}",
                                                title = "Voice-over",
                                                sourceUri = finalFile.absolutePath,
                                                sourceDurationMs = dur.coerceAtLeast(1000L),
                                                sourceStartTimeMs = 0L,
                                                sourceEndTimeMs = dur.coerceAtLeast(1000L),
                                                timelineStartMs = playheadMs,
                                                timelineDurationMs = dur.coerceAtLeast(1000L),
                                                audioTrackType = AudioTrackType.VOICE_OVER,
                                                trackId = "track_audio_voice"
                                            )
                                            onRecordingComplete(clip)
                                            onDismiss()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is RecordingState.Completed, is RecordingState.Failed -> {
                            Button(
                                onClick = { countdownValue = 3 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Record Again", tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record Again", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
