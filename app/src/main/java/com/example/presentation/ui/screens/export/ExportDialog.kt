package com.example.presentation.ui.screens.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.engine.ExportState
import com.example.domain.model.*
import com.example.presentation.ui.components.ShortCutIconButton
import com.example.presentation.viewmodel.ExportViewModel
import com.example.ui.theme.*

@Composable
fun ExportDialog(
    project: Project,
    exportViewModel: ExportViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by exportViewModel.uiState.collectAsState()
    val context = LocalContext.current

    Dialog(onDismissRequest = {
        if (uiState.exportState !is ExportState.Rendering) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Export Short",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    )
                    if (uiState.exportState !is ExportState.Rendering) {
                        ShortCutIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close",
                            onClick = onDismiss
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (val state = uiState.exportState) {
                    is ExportState.Idle, is ExportState.Failed, is ExportState.Cancelled -> {
                        // Configuration Form
                        Text(
                            text = "RESOLUTION",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (res in ExportResolution.values()) {
                                val isSelected = res == uiState.settings.resolution
                                Surface(
                                    color = if (isSelected) ShortCutAccent else DarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { exportViewModel.updateResolution(res, project.timeline) }
                                ) {
                                    Text(
                                        text = res.label.split(" ")[0],
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (isSelected) TextHighContrast else TextMediumContrast,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Frame Rate
                        Text(
                            text = "FRAME RATE",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (fps in ExportFrameRate.values()) {
                                val isSelected = fps == uiState.settings.frameRate
                                Surface(
                                    color = if (isSelected) ShortCutCyan else DarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { exportViewModel.updateFrameRate(fps, project.timeline) }
                                ) {
                                    Text(
                                        text = fps.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (isSelected) DarkCanvas else TextMediumContrast,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hardware Acceleration Badge & Estimated Size
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.FlashOn,
                                        contentDescription = "Hardware",
                                        tint = ShortCutAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Hardware Accelerated",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast)
                                    )
                                }

                                val estimatedMb = (uiState.estimatedSizeBytes / (1024f * 1024f)).coerceAtLeast(1.5f)
                                Text(
                                    text = "~${"%.1f".format(estimatedMb)} MB",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = ShortCutCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        if (state is ExportState.Failed) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Export Error: ${state.message}",
                                style = MaterialTheme.typography.bodySmall.copy(color = ShortCutAccent)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Start Export Button
                        Button(
                            onClick = { exportViewModel.startExport(context, project) },
                            colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_export_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Fast Export",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    is ExportState.Preparing, is ExportState.Rendering, is ExportState.Finalizing -> {
                        // Progress Rendering View
                        val progress = if (state is ExportState.Rendering) state.progress else 0.95f
                        val percent = (progress * 100).toInt()

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = if (state is ExportState.Finalizing) "Finalizing MP4 Container..." else "Rendering Frames...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                color = ShortCutAccent,
                                trackColor = DarkSurfaceBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = ShortCutCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )

                                if (state is ExportState.Rendering) {
                                    Text(
                                        text = "Frame ${state.currentFrame}/${state.totalFrames} (${state.estimatedRemainingSeconds}s remaining)",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedButton(
                                onClick = { exportViewModel.cancelExport() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ShortCutAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel Export")
                            }
                        }
                    }

                    is ExportState.Completed -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Done",
                                tint = ShortCutGreen,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Export Completed!",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Saved in Movies: ${state.outputPath.takeLast(28)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
