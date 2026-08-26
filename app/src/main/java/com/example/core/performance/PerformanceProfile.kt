package com.example.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class PerformanceTier(
    val label: String,
    val maxTimelineResolutionPx: Int,
    val targetFps: Int,
    val maxConcurrentDecoders: Int,
    val thumbnailQuality: Int,
    val enableRealtimePreviewEffects: Boolean,
    val proxyRecommended: Boolean
) {
    LOW(
        label = "Battery & Low-RAM Mode",
        maxTimelineResolutionPx = 360,
        targetFps = 24,
        maxConcurrentDecoders = 1,
        thumbnailQuality = 50,
        enableRealtimePreviewEffects = false,
        proxyRecommended = true
    ),
    MEDIUM(
        label = "Standard Mobile Mode",
        maxTimelineResolutionPx = 540,
        targetFps = 30,
        maxConcurrentDecoders = 2,
        thumbnailQuality = 70,
        enableRealtimePreviewEffects = true,
        proxyRecommended = false
    ),
    HIGH(
        label = "High Performance Mode",
        maxTimelineResolutionPx = 720,
        targetFps = 60,
        maxConcurrentDecoders = 4,
        thumbnailQuality = 85,
        enableRealtimePreviewEffects = true,
        proxyRecommended = false
    ),
    ULTRA(
        label = "Studio Ultra Mode",
        maxTimelineResolutionPx = 1080,
        targetFps = 60,
        maxConcurrentDecoders = 6,
        thumbnailQuality = 100,
        enableRealtimePreviewEffects = true,
        proxyRecommended = false
    )
}

data class DeviceSpecs(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRamDevice: Boolean,
    val cpuCores: Int,
    val apiLevel: Int,
    val detectedTier: PerformanceTier
)

class DeviceProfiler(private val context: Context) {

    fun getDeviceSpecs(): DeviceSpecs {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)
        val isLowRam = activityManager?.isLowRamDevice ?: (totalRamMb < 3000)
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val apiLevel = Build.VERSION.SDK_INT

        val detectedTier = when {
            isLowRam || totalRamMb < 3000 -> PerformanceTier.LOW
            totalRamMb < 6000 || cpuCores <= 4 -> PerformanceTier.MEDIUM
            totalRamMb < 10000 -> PerformanceTier.HIGH
            else -> PerformanceTier.ULTRA
        }

        return DeviceSpecs(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRamDevice = isLowRam,
            cpuCores = cpuCores,
            apiLevel = apiLevel,
            detectedTier = detectedTier
        )
    }
}
