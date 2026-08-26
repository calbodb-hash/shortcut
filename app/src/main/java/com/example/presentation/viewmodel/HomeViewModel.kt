package com.example.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.IProjectRepository
import com.example.data.repository.MediaPickerHelper
import com.example.domain.model.AspectRatio
import com.example.domain.model.Project
import com.example.domain.model.Timeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HomeViewModel(
    private val repository: IProjectRepository
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftProjects: StateFlow<List<Project>> = repository.getDraftProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _createdProjectId = MutableStateFlow<String?>(null)
    val createdProjectId: StateFlow<String?> = _createdProjectId.asStateFlow()

    fun createProjectFromMediaUris(
        context: Context,
        uris: List<Uri>,
        aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
        title: String? = null
    ) {
        viewModelScope.launch {
            val clips = uris.map { uri ->
                MediaPickerHelper.parseMediaUri(context, uri)
            }
            val defaultTitle = if (!title.isNullOrBlank()) title else {
                clips.firstOrNull()?.name?.substringBeforeLast('.') ?: "Short Cut ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())}"
            }
            val initialTimeline = Timeline(videoClips = clips).let { tl ->
                tl.copy(videoClips = tl.recalculateClipTimelineStarts())
            }

            val project = Project(
                id = UUID.randomUUID().toString(),
                title = defaultTitle,
                canvasRatio = aspectRatio,
                timeline = initialTimeline,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isDraft = true
            )

            repository.saveProject(project)
            _createdProjectId.value = project.id
        }
    }

    fun createNewProject(
        title: String = "Untitled Short",
        aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
        useSampleMedia: Boolean = false
    ) {
        viewModelScope.launch {
            val initialClips = if (useSampleMedia) MediaPickerHelper.createStockSampleClips() else emptyList()
            val initialTimeline = Timeline(videoClips = initialClips).let { tl ->
                tl.copy(videoClips = tl.recalculateClipTimelineStarts())
            }

            val project = Project(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank { "Short Cut Project" },
                canvasRatio = aspectRatio,
                timeline = initialTimeline,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isDraft = true
            )

            repository.saveProject(project)
            _createdProjectId.value = project.id
        }
    }

    fun onProjectOpened() {
        _createdProjectId.value = null
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun duplicateProject(project: Project) {
        viewModelScope.launch {
            val copy = project.copy(
                id = UUID.randomUUID().toString(),
                title = "${project.title} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveProject(copy)
        }
    }
}

