package com.example.presentation.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.AspectRatio
import com.example.domain.model.Project
import com.example.presentation.ui.components.ShortCutIconButton
import com.example.presentation.viewmodel.HomeViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by homeViewModel.projects.collectAsState()
    val drafts by homeViewModel.draftProjects.collectAsState()
    val createdId by homeViewModel.createdProjectId.collectAsState()

    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_9_16) }
    var showDismissedPickerOptions by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    // Required permissions based on Android version
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun hasMediaPermissions(): Boolean {
        return requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // System Media Picker for multiple photos and videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            homeViewModel.createProjectFromMediaUris(
                context = context,
                uris = uris,
                aspectRatio = selectedRatio
            )
        } else {
            showDismissedPickerOptions = true
        }
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val anyGranted = permissionsMap.values.any { it }
        if (anyGranted || permissionsMap.isEmpty()) {
            mediaPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        } else {
            showPermissionDeniedDialog = true
        }
    }

    val launchMediaPickerWithPermissionCheck = {
        if (hasMediaPermissions()) {
            mediaPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(createdId) {
        createdId?.let { id ->
            homeViewModel.onProjectOpened()
            onOpenProject(id)
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // App Header
                HomeTopBar(onOpenSettings = onOpenSettings)
            }

            item {
                // Hero "New Project" Card
                NewProjectHeroCard(
                    selectedRatio = selectedRatio,
                    onRatioSelect = { selectedRatio = it },
                    onStartProject = {
                        launchMediaPickerWithPermissionCheck()
                    },
                    onStartBlankProject = {
                        homeViewModel.createNewProject(
                            title = "Short Cut ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())}",
                            aspectRatio = selectedRatio,
                            useSampleMedia = false
                        )
                    },
                    onStartSampleProject = {
                        homeViewModel.createNewProject(
                            title = "Demo Beat ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())}",
                            aspectRatio = selectedRatio,
                            useSampleMedia = true
                        )
                    }
                )
            }

            // Quick Stock Templates
            item {
                Text(
                    text = "QUICK START TEMPLATES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                QuickTemplatesRow(
                    onTemplateClick = { templateRatio, title ->
                        homeViewModel.createNewProject(
                            title = title,
                            aspectRatio = templateRatio,
                            useSampleMedia = true
                        )
                    }
                )
            }

            // Recent Projects
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "RECENT PROJECTS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextMuted,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${projects.size} Projects",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            }

            if (projects.isEmpty()) {
                item {
                    EmptyProjectsCard(
                        onCreateProject = {
                            launchMediaPickerWithPermissionCheck()
                        }
                    )
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectItemCard(
                        project = project,
                        onClick = { onOpenProject(project.id) },
                        onDuplicate = { homeViewModel.duplicateProject(project) },
                        onDelete = { homeViewModel.deleteProject(project.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Permission Denied / Rationale Dialog
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            containerColor = DarkSurfaceHigh,
            icon = {
                Icon(
                    Icons.Default.PermMedia,
                    contentDescription = null,
                    tint = ShortCutAccent,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Media Access Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextHighContrast)
                )
            },
            text = {
                Text(
                    text = "Short Cut needs permission to access your videos and photos so you can select and edit them. You can also start with demo clips or a blank project.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMediumContrast)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        permissionLauncher.launch(requiredPermissions)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showPermissionDeniedDialog = false
                            homeViewModel.createNewProject(
                                title = "Sample Demo Project",
                                aspectRatio = selectedRatio,
                                useSampleMedia = true
                            )
                        }
                    ) {
                        Text("Use Demo Media", color = ShortCutCyan)
                    }
                    TextButton(
                        onClick = {
                            showPermissionDeniedDialog = false
                            homeViewModel.createNewProject(
                                title = "Blank Project",
                                aspectRatio = selectedRatio,
                                useSampleMedia = false
                            )
                        }
                    ) {
                        Text("Blank Project", color = TextMuted)
                    }
                }
            }
        )
    }

    // Dismissed Media Picker Dialog / Options
    if (showDismissedPickerOptions) {
        AlertDialog(
            onDismissRequest = { showDismissedPickerOptions = false },
            containerColor = DarkSurfaceHigh,
            title = {
                Text(
                    text = "No Media Selected",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextHighContrast)
                )
            },
            text = {
                Text(
                    text = "You did not pick any videos or images. Would you like to select media from your device, start with a blank timeline, or test with sample media?",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMediumContrast)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDismissedPickerOptions = false
                        launchMediaPickerWithPermissionCheck()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent)
                ) {
                    Text("Select Media")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDismissedPickerOptions = false
                            homeViewModel.createNewProject(
                                title = "Blank Project",
                                aspectRatio = selectedRatio,
                                useSampleMedia = false
                            )
                        }
                    ) {
                        Text("Blank Timeline", color = ShortCutCyan)
                    }
                    TextButton(
                        onClick = {
                            showDismissedPickerOptions = false
                            homeViewModel.createNewProject(
                                title = "Sample Demo Project",
                                aspectRatio = selectedRatio,
                                useSampleMedia = true
                            )
                        }
                    ) {
                        Text("Sample Media", color = TextMuted)
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShortCutAccent)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Short Cut Logo",
                    tint = TextHighContrast,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "SHORT CUT",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        fontSize = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pro Short Video Editor",
                    style = MaterialTheme.typography.bodySmall.copy(color = ShortCutCyan, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ShortCutIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
            containerColor = DarkSurface,
            testTag = "home_settings_button"
        )
    }
}

@Composable
private fun NewProjectHeroCard(
    selectedRatio: AspectRatio,
    onRatioSelect: (AspectRatio) -> Unit,
    onStartProject: () -> Unit,
    onStartBlankProject: () -> Unit,
    onStartSampleProject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Create Short",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Access device media or start editing",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMediumContrast, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = ShortCutCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "15-60s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ShortCutCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Aspect Ratio Selector Pills
            Text(
                text = "CANVAS FORMAT",
                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                for (ratio in AspectRatio.values()) {
                    val isSelected = ratio == selectedRatio
                    Surface(
                        color = if (isSelected) ShortCutAccent else DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .widthIn(min = 64.dp)
                            .clickable { onRatioSelect(ratio) }
                            .testTag("ratio_pill_${ratio.name}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = ratio.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextHighContrast else TextMediumContrast
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary CTA: Access Device Media
            Button(
                onClick = onStartProject,
                colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("new_project_button")
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Project (Pick Media)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStartBlankProject,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMediumContrast),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Blank Project", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onStartSampleProject,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMediumContrast),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Demo Clips", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun QuickTemplatesRow(onTemplateClick: (AspectRatio, String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TemplateCard(
                title = "TikTok / Reels",
                ratio = "9:16 Portrait",
                color = Color(0xFFE91E63),
                onClick = { onTemplateClick(AspectRatio.RATIO_9_16, "Reels Highlight") }
            )
        }
        item {
            TemplateCard(
                title = "YouTube Shorts",
                ratio = "9:16 Vertical",
                color = Color(0xFFFF3366),
                onClick = { onTemplateClick(AspectRatio.RATIO_9_16, "Shorts Viral Beat") }
            )
        }
        item {
            TemplateCard(
                title = "Instagram Post",
                ratio = "1:1 Square",
                color = Color(0xFF9C27B0),
                onClick = { onTemplateClick(AspectRatio.RATIO_1_1, "Square Promo") }
            )
        }
        item {
            TemplateCard(
                title = "Cinematic Landscape",
                ratio = "16:9 Wide",
                color = Color(0xFF00E5FF),
                onClick = { onTemplateClick(AspectRatio.RATIO_16_9, "Widescreen Cinema") }
            )
        }
    }
}

@Composable
private fun TemplateCard(
    title: String,
    ratio: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(color.copy(alpha = 0.8f), DarkSurfaceActive)
                        )
                    )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = ratio,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun ProjectItemCard(
    project: Project,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val durationSec = project.timeline.totalDurationMs / 1000f
    val durationStr = String.format("%02d:%02d", (durationSec / 60).toInt(), (durationSec % 60).toInt())
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(project.updatedAt))

    Surface(
        color = DarkSurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("project_item_${project.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Project Thumbnail Preview
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 64.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Color((project.timeline.videoClips.firstOrNull()?.thumbnailColorSeed ?: 0xFF2A2E44L).toInt())
                    )
            ) {
                Text(
                    text = project.canvasRatio.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ShortCutCyan,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${project.timeline.videoClips.size} clips",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMediumContrast)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 10.sp)
                )
            }

            // Quick Menu Actions
            Row {
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = TextMediumContrast,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = ShortCutAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsCard(onCreateProject: () -> Unit) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.MovieFilter,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No projects yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create your first short-form video in seconds",
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCreateProject,
                colors = ButtonDefaults.buttonColors(containerColor = ShortCutAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Start Editing")
            }
        }
    }
}
