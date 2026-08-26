package com.example.presentation.ui.screens.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.presentation.ui.components.ShortCutIconButton
import com.example.ui.theme.*

@Composable
fun CropToolPanel(
    crop: CropState,
    onCropChange: (CropState) -> Unit,
    onResetCrop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Crop, contentDescription = "Crop", tint = ShortCutCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Crop & Framing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onResetCrop,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Reset", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                ShortCutIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onClose
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Aspect Ratio Presets",
            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            // Free / Custom
            val isFreeSelected = crop.targetRatio == null
            Surface(
                color = if (isFreeSelected) ShortCutAccent.copy(alpha = 0.25f) else DarkSurface,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isFreeSelected) ShortCutAccent else DarkSurfaceBorder
                ),
                modifier = Modifier.clickable {
                    onCropChange(crop.copy(isEnabled = true, targetRatio = null))
                }
            ) {
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isFreeSelected) ShortCutAccent else TextMediumContrast,
                        fontWeight = if (isFreeSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            for (ratio in AspectRatio.values()) {
                val isSelected = crop.targetRatio == ratio
                Surface(
                    color = if (isSelected) ShortCutAccent.copy(alpha = 0.25f) else DarkSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ShortCutAccent else DarkSurfaceBorder
                    ),
                    modifier = Modifier.clickable {
                        val ratioVal = ratio.aspectRatioFloat
                        val newCrop = if (ratioVal >= 1f) {
                            // Landscape / wider
                            val height = (1f / ratioVal).coerceIn(0.2f, 1f)
                            val top = (1f - height) / 2f
                            crop.copy(isEnabled = true, left = 0f, right = 1f, top = top, bottom = top + height, targetRatio = ratio)
                        } else {
                            // Portrait / taller
                            val width = ratioVal.coerceIn(0.2f, 1f)
                            val left = (1f - width) / 2f
                            crop.copy(isEnabled = true, left = left, right = left + width, top = 0f, bottom = 1f, targetRatio = ratio)
                        }
                        onCropChange(newCrop)
                    }
                ) {
                    Text(
                        text = ratio.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) ShortCutAccent else TextMediumContrast,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FramingToolPanel(
    framingMode: FramingMode,
    onFramingModeChange: (FramingMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitScreen, contentDescription = "Framing", tint = ShortCutCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Canvas Framing Mode",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }
            ShortCutIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (mode in FramingMode.values()) {
                val isSelected = mode == framingMode
                Surface(
                    color = if (isSelected) ShortCutAccent.copy(alpha = 0.2f) else DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSelected) ShortCutAccent else DarkSurfaceBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onFramingModeChange(mode) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (isSelected) ShortCutAccent else TextHighContrast,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (mode) {
                                FramingMode.FIT -> "Letterbox"
                                FramingMode.FILL -> "Crop to fill"
                                FramingMode.ORIGINAL -> "Actual size"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransformToolPanel(
    transform: Transform,
    onTransformChange: (Transform) -> Unit,
    onResetTransform: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Transform & Position",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onResetTransform,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Reset All", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                ShortCutIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onClose
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scale Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Scale",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextMediumContrast, fontSize = 12.sp),
                modifier = Modifier.width(54.dp)
            )
            Slider(
                value = transform.scale,
                onValueChange = { onTransformChange(transform.copy(scale = it)) },
                valueRange = 0.2f..3.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ShortCutAccent,
                    activeTrackColor = ShortCutAccent,
                    inactiveTrackColor = DarkSurfaceBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("scale_slider")
            )
            Text(
                text = "${"%.2f".format(transform.scale)}x",
                style = MaterialTheme.typography.labelMedium.copy(color = ShortCutCyan),
                modifier = Modifier.width(42.dp)
            )
        }

        // Rotation Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Rotate",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextMediumContrast, fontSize = 12.sp),
                modifier = Modifier.width(54.dp)
            )
            Slider(
                value = transform.rotation,
                onValueChange = { onTransformChange(transform.copy(rotation = it)) },
                valueRange = -180f..180f,
                colors = SliderDefaults.colors(
                    thumbColor = ShortCutCyan,
                    activeTrackColor = ShortCutCyan,
                    inactiveTrackColor = DarkSurfaceBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("rotate_slider")
            )
            Text(
                text = "${transform.rotation.toInt()}°",
                style = MaterialTheme.typography.labelMedium.copy(color = ShortCutCyan),
                modifier = Modifier.width(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Flip & Rotate Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Button(
                onClick = {
                    val nextRot = ((transform.rotation + 90f + 180f) % 360f) - 180f
                    onTransformChange(transform.copy(rotation = nextRot))
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90", tint = ShortCutCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rotate 90°", style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = {
                    onTransformChange(transform.copy(isFlippedHorizontal = !transform.isFlippedHorizontal))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transform.isFlippedHorizontal) ShortCutAccent.copy(alpha = 0.3f) else DarkSurface
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Flip, contentDescription = "Flip H", tint = TextHighContrast, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip H", style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = {
                    onTransformChange(transform.copy(isFlippedVertical = !transform.isFlippedVertical))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transform.isFlippedVertical) ShortCutAccent.copy(alpha = 0.3f) else DarkSurface
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip V", tint = TextHighContrast, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Flip V", style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = {
                    onTransformChange(transform.copy(translationX = 0f, translationY = 0f))
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center", tint = TextMediumContrast, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Center", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AdjustToolPanel(
    adjustments: ColorAdjustment,
    onAdjustmentsChange: (ColorAdjustment) -> Unit,
    onResetAdjustments: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedProperty by remember { mutableStateOf("Brightness") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Color Adjustments",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onResetAdjustments,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Reset All", color = ShortCutAccent, style = MaterialTheme.typography.labelSmall)
                }
                ShortCutIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onClose
                )
            }
        }

        // Active Slider for Selected Property
        val currentValue = when (selectedProperty) {
            "Brightness" -> adjustments.brightness
            "Contrast" -> adjustments.contrast
            "Saturation" -> adjustments.saturation
            "Exposure" -> adjustments.exposure
            "Temperature" -> adjustments.temperature
            "Tint" -> adjustments.tint
            "Opacity" -> adjustments.opacity * 100f
            else -> 0f
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedProperty,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextMediumContrast, fontSize = 12.sp),
                modifier = Modifier.width(78.dp),
                maxLines = 1
            )
            Slider(
                value = currentValue,
                onValueChange = { newVal ->
                    val updated = when (selectedProperty) {
                        "Brightness" -> adjustments.copy(brightness = newVal)
                        "Contrast" -> adjustments.copy(contrast = newVal)
                        "Saturation" -> adjustments.copy(saturation = newVal)
                        "Exposure" -> adjustments.copy(exposure = newVal)
                        "Temperature" -> adjustments.copy(temperature = newVal)
                        "Tint" -> adjustments.copy(tint = newVal)
                        "Opacity" -> adjustments.copy(opacity = (newVal / 100f).coerceIn(0f, 1f))
                        else -> adjustments
                    }
                    onAdjustmentsChange(updated)
                },
                valueRange = if (selectedProperty == "Opacity") 0f..100f else -100f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = ShortCutAccent,
                    activeTrackColor = ShortCutAccent,
                    inactiveTrackColor = DarkSurfaceBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("adjust_slider")
            )
            Text(
                text = if (selectedProperty == "Opacity") "${currentValue.toInt()}%" else "${currentValue.toInt()}",
                style = MaterialTheme.typography.labelMedium.copy(color = ShortCutCyan),
                modifier = Modifier.width(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Property Tabs
        val properties = listOf("Brightness", "Contrast", "Saturation", "Exposure", "Temperature", "Tint", "Opacity")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(properties) { prop ->
                val isSelected = prop == selectedProperty
                Surface(
                    color = if (isSelected) ShortCutAccent.copy(alpha = 0.2f) else DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ShortCutAccent else DarkSurfaceBorder
                    ),
                    modifier = Modifier.clickable { selectedProperty = prop }
                ) {
                    Text(
                        text = prop,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) ShortCutAccent else TextMediumContrast,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedToolPanel(
    currentSpeed: Float,
    sourceDurationMs: Long,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f)
    val effectiveDurationMs = (sourceDurationMs / currentSpeed).toLong()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Clip Speed (${"%.2f".format(currentSpeed)}x)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
                Text(
                    text = "Duration: ${"%.1f".format(effectiveDurationMs / 1000f)}s",
                    style = MaterialTheme.typography.labelSmall.copy(color = ShortCutCyan, fontSize = 10.sp)
                )
            }
            ShortCutIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Slider(
            value = currentSpeed,
            onValueChange = onSpeedChange,
            valueRange = 0.1f..5.0f,
            colors = SliderDefaults.colors(
                thumbColor = ShortCutAccent,
                activeTrackColor = ShortCutAccent,
                inactiveTrackColor = DarkSurfaceBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speed_slider")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            for (speed in presets) {
                val isSelected = Math.abs(currentSpeed - speed) < 0.05f
                Surface(
                    color = if (isSelected) ShortCutAccent else DarkSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onSpeedChange(speed) }
                ) {
                    Text(
                        text = "${speed}x",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) TextHighContrast else TextMediumContrast,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FilterToolPanel(
    activeFilter: FilterPreset,
    onSelectFilter: (FilterPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cinematic Filters",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            ShortCutIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FilterPreset.values()) { filter ->
                val isSelected = filter == activeFilter
                Surface(
                    color = if (isSelected) ShortCutPurple.copy(alpha = 0.3f) else DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSelected) ShortCutPurple else DarkSurfaceBorder
                    ),
                    modifier = Modifier
                        .width(76.dp)
                        .clickable { onSelectFilter(filter) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when (filter) {
                                        FilterPreset.CINEMATIC -> Color(0xFF1E88E5)
                                        FilterPreset.VINTAGE -> Color(0xFFD87D4A)
                                        FilterPreset.CYBERPUNK -> Color(0xFFD500F9)
                                        FilterPreset.WARM -> Color(0xFFFF9100)
                                        FilterPreset.COOL -> Color(0xFF00E5FF)
                                        FilterPreset.NOIR -> Color(0xFF757575)
                                        FilterPreset.VIVID -> Color(0xFFFF1744)
                                        FilterPreset.ORIGINAL -> DarkSurfaceBorder
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = filter.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) TextHighContrast else TextMediumContrast,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioToolPanel(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Audio & Volume",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            ShortCutIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { onVolumeChange(volume, !isMuted) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isMuted || volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute",
                    tint = if (isMuted) ShortCutAccent else ShortCutCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Slider(
                value = if (isMuted) 0f else volume,
                onValueChange = { onVolumeChange(it, false) },
                valueRange = 0f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ShortCutCyan,
                    activeTrackColor = ShortCutCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("volume_slider")
            )
            Text(
                text = "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(color = ShortCutCyan),
                modifier = Modifier.width(38.dp)
            )
        }
    }
}

@Composable
fun TextEditorPanel(
    selectedTextLayer: TextLayer?,
    onAddText: (String) -> Unit,
    onUpdateTextLayer: (TextLayer) -> Unit,
    onDeleteTextLayer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember(selectedTextLayer) {
        mutableStateOf(selectedTextLayer?.text ?: "")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedTextLayer != null) "Edit Text Layer" else "Add Text Overlay",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            Row {
                if (selectedTextLayer != null) {
                    IconButton(onClick = onDeleteTextLayer, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Text", tint = ShortCutAccent, modifier = Modifier.size(18.dp))
                    }
                }
                ShortCutIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = onClose
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                if (selectedTextLayer != null) {
                    onUpdateTextLayer(selectedTextLayer.copy(text = it))
                }
            },
            placeholder = { Text("Enter text for overlay...", color = TextMuted, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ShortCutAccent,
                unfocusedBorderColor = DarkSurfaceBorder,
                focusedTextColor = TextHighContrast,
                unfocusedTextColor = TextHighContrast
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("text_input_field")
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTextLayer == null) {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onAddText(textInput)
                        textInput = ""
                        onClose()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_text_button")
            ) {
                Text("Add Text to Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        } else {
            // Text styling options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedTextLayer.hasBackground,
                    onClick = {
                        onUpdateTextLayer(selectedTextLayer.copy(hasBackground = !selectedTextLayer.hasBackground))
                    },
                    label = { Text("Background Box", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedTextLayer.isBold,
                    onClick = {
                        onUpdateTextLayer(selectedTextLayer.copy(isBold = !selectedTextLayer.isBold))
                    },
                    label = { Text("Bold", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun CanvasRatioPanel(
    activeRatio: AspectRatio,
    onSelectRatio: (AspectRatio) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Canvas Aspect Ratio",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            ShortCutIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            for (ratio in AspectRatio.values()) {
                val isSelected = ratio == activeRatio
                Surface(
                    color = if (isSelected) ShortCutAccent.copy(alpha = 0.2f) else DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSelected) ShortCutAccent else DarkSurfaceBorder
                    ),
                    modifier = Modifier
                        .widthIn(min = 76.dp)
                        .clickable { onSelectRatio(ratio) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = ratio.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (isSelected) ShortCutAccent else TextHighContrast,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (ratio) {
                                AspectRatio.RATIO_9_16 -> "Shorts"
                                AspectRatio.RATIO_16_9 -> "16:9"
                                AspectRatio.RATIO_1_1 -> "Square"
                                AspectRatio.RATIO_4_5 -> "Feed"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                        )
                    }
                }
            }
        }
    }
}

