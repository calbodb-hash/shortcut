package com.example.presentation.ui.screens.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.audio.AudioWaveformEngine
import com.example.core.audio.WaveformLod

/**
 * High-performance lightweight waveform visualizer for audio and video tracks on the timeline.
 * Integrated with AudioWaveformEngine for cached amplitude extraction, trim slicing, and fade curves.
 */
@Composable
fun TimelineWaveformCanvas(
    audioId: String,
    sourceUri: String = "",
    durationMs: Long,
    sourceStartTimeMs: Long = 0L,
    sourceEndTimeMs: Long = durationMs,
    sourceTotalDurationMs: Long = durationMs,
    fadeInMs: Long = 0L,
    fadeOutMs: Long = 0L,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val amplitudes = remember(audioId, sourceUri, sourceStartTimeMs, sourceEndTimeMs, sourceTotalDurationMs) {
        val raw = AudioWaveformEngine.getFastAmplitudes(
            audioId = audioId,
            sourceUri = sourceUri.ifBlank { audioId },
            durationMs = sourceTotalDurationMs.coerceAtLeast(1000L),
            lod = WaveformLod.MEDIUM
        )
        if (sourceTotalDurationMs > 0L && (sourceStartTimeMs > 0L || sourceEndTimeMs < sourceTotalDurationMs)) {
            val startRatio = (sourceStartTimeMs.toFloat() / sourceTotalDurationMs).coerceIn(0f, 1f)
            val endRatio = (sourceEndTimeMs.toFloat() / sourceTotalDurationMs).coerceIn(startRatio, 1f)
            val startIndex = (startRatio * raw.size).toInt().coerceIn(0, raw.size - 1)
            val endIndex = (endRatio * raw.size).toInt().coerceIn(startIndex + 1, raw.size)
            raw.copyOfRange(startIndex, endIndex)
        } else {
            raw
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val totalBars = amplitudes.size
        if (totalBars == 0) return@Canvas

        val barSpacing = 2.5.dp.toPx()
        val barWidth = 1.8.dp.toPx()
        val centerY = size.height / 2f
        val maxAmplitudeHeight = size.height * 0.85f

        val step = (size.width / totalBars).coerceAtLeast(barSpacing)
        var currentX = 0f

        val visibleDurationMs = (sourceEndTimeMs - sourceStartTimeMs).coerceAtLeast(100L)

        for (i in 0 until totalBars) {
            if (currentX > size.width) break

            var amp = amplitudes[i]

            // Apply fade envelope to waveform visualization
            val pointMs = (i.toFloat() / totalBars.toFloat()) * visibleDurationMs
            if (fadeInMs > 0L && pointMs < fadeInMs) {
                amp *= (pointMs / fadeInMs.toFloat()).coerceIn(0.1f, 1f)
            }
            val timeToEnd = visibleDurationMs - pointMs
            if (fadeOutMs > 0L && timeToEnd < fadeOutMs) {
                amp *= (timeToEnd / fadeOutMs.toFloat()).coerceIn(0.1f, 1f)
            }

            val barHalfHeight = (amp * (maxAmplitudeHeight / 2f)).coerceAtLeast(1.5.dp.toPx())

            drawLine(
                color = barColor,
                start = Offset(currentX, centerY - barHalfHeight),
                end = Offset(currentX, centerY + barHalfHeight),
                strokeWidth = barWidth
            )

            currentX += step
        }
    }
}

