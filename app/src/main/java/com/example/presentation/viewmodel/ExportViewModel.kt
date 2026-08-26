package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.engine.ExportEngine
import com.example.domain.engine.ExportState
import com.example.domain.engine.IExportEngine
import com.example.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    val settings: ExportSettings = ExportSettings(),
    val exportState: ExportState = ExportState.Idle,
    val estimatedSizeBytes: Long = 0L,
    val availableEncoders: List<String> = emptyList()
)

class ExportViewModel(
    private val exportEngine: IExportEngine = ExportEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(availableEncoders = exportEngine.getHardwareEncoders())
        }
        viewModelScope.launch {
            exportEngine.exportState.collect { state ->
                _uiState.update { it.copy(exportState = state) }
            }
        }
    }

    fun updateResolution(resolution: ExportResolution, timeline: Timeline) {
        _uiState.update {
            val newSettings = it.settings.copy(resolution = resolution)
            it.copy(
                settings = newSettings,
                estimatedSizeBytes = exportEngine.calculateEstimatedSizeBytes(timeline, newSettings)
            )
        }
    }

    fun updateFrameRate(frameRate: ExportFrameRate, timeline: Timeline) {
        _uiState.update {
            val newSettings = it.settings.copy(frameRate = frameRate)
            it.copy(
                settings = newSettings,
                estimatedSizeBytes = exportEngine.calculateEstimatedSizeBytes(timeline, newSettings)
            )
        }
    }

    fun updateCodec(codec: ExportCodec, timeline: Timeline) {
        _uiState.update {
            val newSettings = it.settings.copy(codec = codec)
            it.copy(
                settings = newSettings,
                estimatedSizeBytes = exportEngine.calculateEstimatedSizeBytes(timeline, newSettings)
            )
        }
    }

    fun startExport(context: Context, project: Project) {
        viewModelScope.launch {
            exportEngine.startExport(context, project, _uiState.value.settings)
        }
    }

    fun cancelExport() {
        exportEngine.cancelExport()
    }
}
