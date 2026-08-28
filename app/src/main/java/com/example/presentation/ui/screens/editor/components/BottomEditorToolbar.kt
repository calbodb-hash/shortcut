package com.example.presentation.ui.screens.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SelectionTarget
import com.example.presentation.viewmodel.EditorToolGroup
import com.example.ui.theme.*

@Composable
fun BottomEditorToolbar(
    selection: SelectionTarget?,
    activeToolGroup: EditorToolGroup,
    onToolClick: (EditorToolGroup) -> Unit,
    onSplitClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        when (selection) {
            is SelectionTarget.Video, is SelectionTarget.Overlay -> {
                // Video Clip / Overlay Selected Contextual Tools
                ToolbarItem(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    isSelected = false,
                    onClick = onSplitClick,
                    testTag = "toolbar_split_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Crop,
                    label = "Crop",
                    isSelected = activeToolGroup == EditorToolGroup.CROP,
                    onClick = { onToolClick(EditorToolGroup.CROP) },
                    testTag = "toolbar_crop_button"
                )
                ToolbarItem(
                    icon = Icons.Default.FitScreen,
                    label = "Framing",
                    isSelected = activeToolGroup == EditorToolGroup.FRAMING,
                    onClick = { onToolClick(EditorToolGroup.FRAMING) },
                    testTag = "toolbar_framing_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    isSelected = activeToolGroup == EditorToolGroup.SPEED,
                    onClick = { onToolClick(EditorToolGroup.SPEED) },
                    testTag = "toolbar_speed_button"
                )
                ToolbarItem(
                    icon = Icons.Default.CropRotate,
                    label = "Transform",
                    isSelected = activeToolGroup == EditorToolGroup.TRANSFORM,
                    onClick = { onToolClick(EditorToolGroup.TRANSFORM) },
                    testTag = "toolbar_transform_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Tune,
                    label = "Adjust",
                    isSelected = activeToolGroup == EditorToolGroup.ADJUST,
                    onClick = { onToolClick(EditorToolGroup.ADJUST) },
                    testTag = "toolbar_adjust_button"
                )
                ToolbarItem(
                    icon = Icons.Default.AutoAwesome,
                    label = "Filters",
                    isSelected = activeToolGroup == EditorToolGroup.FILTER,
                    onClick = { onToolClick(EditorToolGroup.FILTER) },
                    testTag = "toolbar_filter_button"
                )
                ToolbarItem(
                    icon = Icons.Default.VolumeUp,
                    label = "Volume",
                    isSelected = activeToolGroup == EditorToolGroup.AUDIO,
                    onClick = { onToolClick(EditorToolGroup.AUDIO) },
                    testTag = "toolbar_volume_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Layers,
                    label = "Arrange",
                    isSelected = activeToolGroup == EditorToolGroup.ARRANGE,
                    onClick = { onToolClick(EditorToolGroup.ARRANGE) },
                    testTag = "toolbar_arrange_button"
                )
                ToolbarItem(
                    icon = Icons.Default.ContentCopy,
                    label = "Duplicate",
                    isSelected = false,
                    onClick = onDuplicateClick,
                    testTag = "toolbar_duplicate_button"
                )
                ToolbarItem(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    isSelected = false,
                    tint = ShortCutAccent,
                    onClick = onDeleteClick,
                    testTag = "toolbar_delete_button"
                )
            }
            is SelectionTarget.Text -> {
                // Text Layer Selected Contextual Tools
                ToolbarItem(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    isSelected = activeToolGroup == EditorToolGroup.TEXT,
                    onClick = { onToolClick(EditorToolGroup.TEXT) },
                    testTag = "toolbar_text_edit_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Style,
                    label = "Style",
                    isSelected = activeToolGroup == EditorToolGroup.TEXT,
                    onClick = { onToolClick(EditorToolGroup.TEXT) },
                    testTag = "toolbar_text_style_button"
                )
                ToolbarItem(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    isSelected = false,
                    tint = ShortCutAccent,
                    onClick = onDeleteClick,
                    testTag = "toolbar_delete_text_button"
                )
            }
            is SelectionTarget.Audio -> {
                // Audio Clip Selected Contextual Tools
                ToolbarItem(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    isSelected = false,
                    onClick = onSplitClick,
                    testTag = "toolbar_split_button"
                )
                ToolbarItem(
                    icon = Icons.Default.VolumeUp,
                    label = "Volume",
                    isSelected = activeToolGroup == EditorToolGroup.AUDIO,
                    onClick = { onToolClick(EditorToolGroup.AUDIO) },
                    testTag = "toolbar_audio_volume_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    isSelected = activeToolGroup == EditorToolGroup.AUDIO,
                    onClick = { onToolClick(EditorToolGroup.AUDIO) },
                    testTag = "toolbar_audio_speed_button"
                )
                ToolbarItem(
                    icon = Icons.Default.ContentCopy,
                    label = "Duplicate",
                    isSelected = false,
                    onClick = onDuplicateClick,
                    testTag = "toolbar_duplicate_button"
                )
                ToolbarItem(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    isSelected = false,
                    tint = ShortCutAccent,
                    onClick = onDeleteClick,
                    testTag = "toolbar_delete_audio_button"
                )
            }
            null -> {
                // Root Contextual Tools (Timeline Level)
                ToolbarItem(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    isSelected = false,
                    onClick = onSplitClick,
                    testTag = "toolbar_split_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Audiotrack,
                    label = "Audio",
                    isSelected = activeToolGroup == EditorToolGroup.AUDIO,
                    onClick = { onToolClick(EditorToolGroup.AUDIO) },
                    testTag = "toolbar_audio_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Layers,
                    label = "Overlay",
                    isSelected = activeToolGroup == EditorToolGroup.OVERLAY,
                    onClick = { onToolClick(EditorToolGroup.OVERLAY) },
                    testTag = "toolbar_overlay_button"
                )
                ToolbarItem(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeToolGroup == EditorToolGroup.TEXT,
                    onClick = { onToolClick(EditorToolGroup.TEXT) },
                    testTag = "toolbar_text_button"
                )
                ToolbarItem(
                    icon = Icons.Default.Tune,
                    label = "Adjust",
                    isSelected = activeToolGroup == EditorToolGroup.ADJUST,
                    onClick = { onToolClick(EditorToolGroup.ADJUST) },
                    testTag = "toolbar_adjust_button"
                )
                ToolbarItem(
                    icon = Icons.Default.AutoAwesome,
                    label = "Filters",
                    isSelected = activeToolGroup == EditorToolGroup.FILTER,
                    onClick = { onToolClick(EditorToolGroup.FILTER) },
                    testTag = "toolbar_filter_button"
                )
                ToolbarItem(
                    icon = Icons.Default.AspectRatio,
                    label = "Canvas",
                    isSelected = activeToolGroup == EditorToolGroup.CANVAS,
                    onClick = { onToolClick(EditorToolGroup.CANVAS) },
                    testTag = "toolbar_canvas_button"
                )
            }
        }
    }
}

@Composable
private fun ToolbarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextHighContrast,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .width(62.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ShortCutAccent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) ShortCutAccent else tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) ShortCutAccent else TextMediumContrast
            )
        )
    }
}
