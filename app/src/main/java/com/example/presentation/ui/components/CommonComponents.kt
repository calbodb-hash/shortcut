package com.example.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.example.ui.theme.*

@Composable
fun ShortCutIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextHighContrast,
    containerColor: Color = Color.Transparent,
    isSelected: Boolean = false,
    testTag: String = ""
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) ShortCutAccent.copy(alpha = 0.2f) else containerColor)
            .then(
                if (isSelected) Modifier.border(1.dp, ShortCutAccent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) ShortCutAccent else tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun TimecodeBadge(
    currentMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier
) {
    val currentSec = currentMs / 1000f
    val totalSec = totalMs / 1000f

    val currentStr = String.format("%02d:%05.2f", (currentSec / 60).toInt(), currentSec % 60)
    val totalStr = String.format("%02d:%05.2f", (totalSec / 60).toInt(), totalSec % 60)

    Surface(
        color = DarkSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = currentStr,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ShortCutCyan
                )
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
            )
            Text(
                text = totalStr,
                style = MaterialTheme.typography.labelMedium.copy(color = TextMediumContrast)
            )
        }
    }
}

@Composable
fun StatusToast(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = DarkSurfaceHigh.copy(alpha = 0.95f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ShortCutAccent.copy(alpha = 0.4f)),
            shadowElevation = 8.dp
        ) {
            Text(
                text = message ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextHighContrast
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
