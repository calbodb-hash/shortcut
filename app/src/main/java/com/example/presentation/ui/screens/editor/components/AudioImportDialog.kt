package com.example.presentation.ui.screens.editor.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.audio.AudioImportHelper
import com.example.core.audio.toAudioClip
import com.example.core.audio.*
import com.example.domain.model.AudioClip
import com.example.domain.model.AudioTrackType
import com.example.ui.theme.*

/**
 * Modal dialog for importing audio:
 * - Device storage (Music/Voice/SFX)
 * - Royalty-Free Stock Music library
 * - Sound Effects library
 * - Extract Audio from Video
 */
@Composable
fun AudioImportDialog(
    onImportAudioClip: (AudioClip) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(AudioCategory.STOCK_MUSIC) }
    var previewingAudioId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                // Ignored
            }
            mediaPlayer = null
        }
    }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val audioClip = AudioImportHelper.createAudioClipFromUri(context, uri, AudioTrackType.MUSIC)
            onImportAudioClip(audioClip)
            onDismiss()
        }
    }

    // Video extractor picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val audioClip = AudioImportHelper.extractAudioFromVideoUri(context, uri)
            onImportAudioClip(audioClip)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Audio Library",
                            tint = ShortCutAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Audio",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextHighContrast,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMediumContrast)
                    }
                }

                Divider(color = DarkSurfaceBorder, thickness = 0.5.dp)

                // Category Tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AudioCategory.values()) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            color = if (isSelected) ShortCutAccent.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ShortCutAccent else DarkSurfaceBorder
                            ),
                            modifier = Modifier.clickable { selectedCategory = category }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = category.label,
                                    tint = if (isSelected) ShortCutAccent else TextMediumContrast,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) ShortCutAccent else TextMediumContrast,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }

                Divider(color = DarkSurfaceBorder, thickness = 0.5.dp)

                // Category Body Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedCategory) {
                        AudioCategory.STOCK_MUSIC -> {
                            StockAudioList(
                                items = AudioImportHelper.getStockMusicTracks(),
                                previewingId = previewingAudioId,
                                onPreview = { stock ->
                                    if (previewingAudioId == stock.id) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        previewingAudioId = null
                                    } else {
                                        mediaPlayer?.release()
                                        previewingAudioId = stock.id
                                    }
                                },
                                onSelect = { stock ->
                                    onImportAudioClip(stock.toAudioClip())
                                    onDismiss()
                                }
                            )
                        }
                        AudioCategory.SFX -> {
                            StockAudioList(
                                items = AudioImportHelper.getStockSoundEffects(),
                                previewingId = previewingAudioId,
                                onPreview = { stock ->
                                    if (previewingAudioId == stock.id) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        previewingAudioId = null
                                    } else {
                                        mediaPlayer?.release()
                                        previewingAudioId = stock.id
                                    }
                                },
                                onSelect = { stock ->
                                    onImportAudioClip(stock.toAudioClip())
                                    onDismiss()
                                }
                            )
                        }
                        AudioCategory.DEVICE -> {
                            DeviceAudioImporter(
                                onPickFromStorage = { audioPickerLauncher.launch("audio/*") }
                            )
                        }
                        AudioCategory.EXTRACT -> {
                            ExtractAudioImporter(
                                onPickVideo = { videoPickerLauncher.launch("video/*") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockAudioList(
    items: List<com.example.core.audio.StockAudioItem>,
    previewingId: String?,
    onPreview: (com.example.core.audio.StockAudioItem) -> Unit,
    onSelect: (com.example.core.audio.StockAudioItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            val isPlaying = previewingId == item.id
            Surface(
                color = DarkSurfaceHigh,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isPlaying) ShortCutAccent else DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { onPreview(item) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) ShortCutAccent else DarkSurface)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isPlaying) Color.Black else TextHighContrast,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextHighContrast,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = ShortCutCyan.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.category.displayName,
                                        color = ShortCutCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format("%d:%02d", (item.durationMs / 1000) / 60, (item.durationMs / 1000) % 60),
                                    color = TextMediumContrast,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onSelect(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Add", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceAudioImporter(
    onPickFromStorage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = ShortCutAccent.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Device Storage",
                    tint = ShortCutAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Import from Device",
            style = MaterialTheme.typography.titleMedium.copy(color = TextHighContrast, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Select any audio file from your device storage.\nSupports MP3, WAV, AAC, M4A, OGG.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPickFromStorage,
            colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Browse Audio Files", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExtractAudioImporter(
    onPickVideo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = ShortCutCyan.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Extract from Video",
                    tint = ShortCutCyan,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Extract Audio from Video",
            style = MaterialTheme.typography.titleMedium.copy(color = TextHighContrast, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Extract the soundtrack from any video file without modifying or re-encoding the original media.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPickVideo,
            colors = ButtonDefaults.buttonColors(containerColor = ShortCutCyan),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Choose Video to Extract", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

private enum class AudioCategory(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    STOCK_MUSIC("Music", Icons.Default.MusicNote),
    SFX("Effects", Icons.Default.Bolt),
    DEVICE("My Audio", Icons.Default.Smartphone),
    EXTRACT("Extract", Icons.Default.VolumeDown)
}
