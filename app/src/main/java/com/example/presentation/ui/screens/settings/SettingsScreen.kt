package com.example.presentation.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.performance.PerformanceTier
import com.example.presentation.ui.components.ShortCutIconButton
import com.example.presentation.viewmodel.SettingsViewModel
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas),
        containerColor = DarkCanvas,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Top Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ShortCutIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onNavigateBack,
                        testTag = "settings_back_button"
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Engine Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Device Performance Specs Card
            item {
                DeviceSpecsCard(uiState = uiState)
            }

            // Performance Tier Selection
            item {
                Text(
                    text = "PERFORMANCE OPTIMIZATION PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                PerformanceTiersCard(
                    selectedTier = uiState.selectedTier,
                    onSelectTier = { settingsViewModel.selectTier(it) }
                )
            }

            // Optimization Toggles
            item {
                Text(
                    text = "HARDWARE & RENDERING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                RenderingTogglesCard(
                    proxyEnabled = uiState.proxyPreviewEnabled,
                    onToggleProxy = { settingsViewModel.toggleProxyPreview(it) },
                    hardwareEnabled = uiState.hardwareEncodingEnabled,
                    onToggleHardware = { settingsViewModel.toggleHardwareEncoding(it) }
                )
            }

            // Cache Management
            item {
                Text(
                    text = "SMART CACHE MANAGEMENT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                CacheManagerCard(
                    memoryUsedMb = uiState.cacheMemoryUsedMb,
                    isCleared = uiState.isCacheCleared,
                    onClearCache = { settingsViewModel.clearCache() }
                )
            }

            // Architecture Info
            item {
                AboutArchitectureCard()
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DeviceSpecsCard(uiState: com.example.presentation.viewmodel.SettingsUiState) {
    Surface(
        color = DarkSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Device Capability",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Surface(
                    color = ShortCutCyan.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = uiState.specs.detectedTier.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ShortCutCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SpecItem(
                    label = "Total RAM",
                    value = "${uiState.specs.totalRamMb} MB",
                    modifier = Modifier.weight(1f)
                )
                SpecItem(
                    label = "Free RAM",
                    value = "${uiState.specs.availableRamMb} MB",
                    modifier = Modifier.weight(1f)
                )
                SpecItem(
                    label = "CPU Cores",
                    value = "${uiState.specs.cpuCores}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpecItem(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun PerformanceTiersCard(
    selectedTier: PerformanceTier,
    onSelectTier: (PerformanceTier) -> Unit
) {
    Surface(
        color = DarkSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            for (tier in PerformanceTier.values()) {
                val isSelected = tier == selectedTier
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectTier(tier) }
                        .padding(10.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectTier(tier) },
                        colors = RadioButtonDefaults.colors(selectedColor = ShortCutAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = tier.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ShortCutAccent else TextHighContrast
                            )
                        )
                        Text(
                            text = "Max Preview: ${tier.maxTimelineResolutionPx}p @ ${tier.targetFps} FPS",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderingTogglesCard(
    proxyEnabled: Boolean,
    onToggleProxy: (Boolean) -> Unit,
    hardwareEnabled: Boolean,
    onToggleHardware: (Boolean) -> Unit
) {
    Surface(
        color = DarkSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Proxy Preview",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Decodes fast lightweight proxy frames to prevent RAM crashes during editing.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast)
                    )
                }
                Switch(
                    checked = proxyEnabled,
                    onCheckedChange = onToggleProxy,
                    colors = SwitchDefaults.colors(checkedThumbColor = ShortCutCyan, checkedTrackColor = DarkSurfaceActive)
                )
            }

            HorizontalDivider(color = DarkSurfaceBorder, modifier = Modifier.padding(vertical = 12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hardware MediaCodec Acceleration",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Utilizes device hardware encoder for fast export.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast)
                    )
                }
                Switch(
                    checked = hardwareEnabled,
                    onCheckedChange = onToggleHardware,
                    colors = SwitchDefaults.colors(checkedThumbColor = ShortCutAccent, checkedTrackColor = DarkSurfaceActive)
                )
            }
        }
    }
}

@Composable
private fun CacheManagerCard(
    memoryUsedMb: Float,
    isCleared: Boolean,
    onClearCache: () -> Unit
) {
    Surface(
        color = DarkSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Thumbnail & Waveform Cache",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (isCleared) "Cache Cleared!" else "Using ${"%.1f".format(memoryUsedMb)} MB in RAM",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isCleared) ShortCutGreen else TextMediumContrast
                    )
                )
            }

            Button(
                onClick = onClearCache,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Clear Cache", color = ShortCutAccent, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AboutArchitectureCard() {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Short Cut Engine — Phase 01",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Built with Clean Architecture, Jetpack Compose, Room local persistence, Non-destructive timeline instructions, and zero-full-decode streaming preview architecture.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
        }
    }
}
