package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.performance.DeviceProfiler
import com.example.core.performance.DeviceSpecs
import com.example.core.performance.PerformanceTier
import com.example.core.performance.SmartCacheEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val specs: DeviceSpecs,
    val selectedTier: PerformanceTier,
    val cacheMemoryUsedMb: Float = 0f,
    val proxyPreviewEnabled: Boolean = true,
    val hardwareEncodingEnabled: Boolean = true,
    val isCacheCleared: Boolean = false
)

class SettingsViewModel(
    context: Context,
    private val cacheEngine: SmartCacheEngine = SmartCacheEngine()
) : ViewModel() {

    private val profiler = DeviceProfiler(context)
    private val initialSpecs = profiler.getDeviceSpecs()

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            specs = initialSpecs,
            selectedTier = initialSpecs.detectedTier,
            cacheMemoryUsedMb = cacheEngine.getEstimatedMemoryUsedMb()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun selectTier(tier: PerformanceTier) {
        _uiState.update { it.copy(selectedTier = tier) }
    }

    fun toggleProxyPreview(enabled: Boolean) {
        _uiState.update { it.copy(proxyPreviewEnabled = enabled) }
    }

    fun toggleHardwareEncoding(enabled: Boolean) {
        _uiState.update { it.copy(hardwareEncodingEnabled = enabled) }
    }

    fun clearCache() {
        cacheEngine.clearAll()
        _uiState.update {
            it.copy(
                cacheMemoryUsedMb = 0f,
                isCacheCleared = true
            )
        }
    }
}
