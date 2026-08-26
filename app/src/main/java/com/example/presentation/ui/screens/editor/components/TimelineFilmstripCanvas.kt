package com.example.presentation.ui.screens.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Lightweight, GPU-rendered filmstrip representation for video clips on the timeline.
 * Renders thumbnail cells and filmstrip perforations without heavy bitmap allocations.
 */
@Composable
fun TimelineFilmstripCanvas(
    thumbnailColorSeed: Long,
    clipDurationMs: Long,
    pxPerSecond: Float,
    modifier: Modifier = Modifier
) {
    val baseColor = Color(thumbnailColorSeed.toInt())

    Canvas(modifier = modifier.fillMaxSize()) {
        val cellWidth = (size.height * 0.75f).coerceAtLeast(30.dp.toPx())
        val numCells = (size.width / cellWidth).toInt().coerceAtLeast(1)

        // Draw individual filmstrip frames with subtle color variations
        for (i in 0 until numCells) {
            val cellLeft = i * cellWidth
            val shadeFactor = 0.85f + ((i % 3) * 0.08f)
            val cellColor = Color(
                red = (baseColor.red * shadeFactor).coerceIn(0f, 1f),
                green = (baseColor.green * shadeFactor).coerceIn(0f, 1f),
                blue = (baseColor.blue * shadeFactor).coerceIn(0f, 1f),
                alpha = 0.65f
            )

            drawRoundRect(
                color = cellColor,
                topLeft = Offset(cellLeft + 1.dp.toPx(), 2.dp.toPx()),
                size = Size(cellWidth - 2.dp.toPx(), size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Film perforation divider line
            if (i > 0) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(cellLeft, 0f),
                    end = Offset(cellLeft, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
