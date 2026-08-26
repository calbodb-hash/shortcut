package com.example.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface RecordingState {
    object Idle : RecordingState
    data class Recording(val durationMs: Long, val currentAmplitudeNormalized: Float) : RecordingState
    data class Paused(val durationMs: Long) : RecordingState
    object Stopped : RecordingState
    data class Completed(val outputFile: File, val durationMs: Long) : RecordingState
    data class Failed(val reason: String) : RecordingState
}

interface IAudioRecordingManager {
    val recordingState: StateFlow<RecordingState>
    val amplitudeHistory: StateFlow<List<Float>>
    fun startRecording(targetFile: File): Boolean
    fun pauseRecording()
    fun resumeRecording()
    fun stopRecording(): File?
    fun cancelRecording()
    fun getRecordingDuration(): Long
    fun release()
}

class AudioRecordingManager(
    private val appContext: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : IAudioRecordingManager {

    private val TAG = "AudioRecordingManager"
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _amplitudeHistory = MutableStateFlow<List<Float>>(emptyList())
    override val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var recordStartTimeMs: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private var tickerJob: Job? = null

    override fun startRecording(targetFile: File): Boolean {
        try {
            stopRecording() // Clean up any active session
            _amplitudeHistory.value = emptyList()
            accumulatedDurationMs = 0L

            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appContext != null) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(targetFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentFile = targetFile
            recordStartTimeMs = System.currentTimeMillis()

            startTicker()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _recordingState.value = RecordingState.Failed(e.localizedMessage ?: "Failed to initialize microphone")
            releaseRecorder()
            return false
        }
    }

    override fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                accumulatedDurationMs += (System.currentTimeMillis() - recordStartTimeMs)
                tickerJob?.cancel()
                _recordingState.value = RecordingState.Paused(accumulatedDurationMs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause recorder", e)
            }
        }
    }

    override fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                recordStartTimeMs = System.currentTimeMillis()
                startTicker()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume recorder", e)
            }
        }
    }

    override fun getRecordingDuration(): Long {
        val currentRunning = if (_recordingState.value is RecordingState.Recording) {
            System.currentTimeMillis() - recordStartTimeMs
        } else {
            0L
        }
        return accumulatedDurationMs + currentRunning
    }

    override fun stopRecording(): File? {
        tickerJob?.cancel()
        tickerJob = null

        val recorder = mediaRecorder ?: return null
        val file = currentFile
        val totalDuration = getRecordingDuration()

        try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            currentFile = null

            if (file != null && file.exists() && file.length() > 0 && totalDuration >= 300L) {
                _recordingState.value = RecordingState.Completed(file, totalDuration)
                return file
            } else {
                file?.delete()
                _recordingState.value = RecordingState.Stopped
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
            file?.delete()
            _recordingState.value = RecordingState.Failed(e.localizedMessage ?: "Failed to stop recording")
            releaseRecorder()
            return null
        }
    }

    override fun cancelRecording() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignored
        }
        releaseRecorder()
        currentFile?.delete()
        currentFile = null
        _recordingState.value = RecordingState.Idle
        _amplitudeHistory.value = emptyList()
    }

    override fun release() {
        cancelRecording()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && mediaRecorder != null) {
                val currentRunning = System.currentTimeMillis() - recordStartTimeMs
                val totalMs = accumulatedDurationMs + currentRunning
                val maxAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                val normalizedAmp = (maxAmp.toFloat() / 25000f).coerceIn(0.05f, 1.0f)
                _recordingState.value = RecordingState.Recording(totalMs, normalizedAmp)

                val updatedList = (_amplitudeHistory.value + normalizedAmp).takeLast(60)
                _amplitudeHistory.value = updatedList

                delay(80L)
            }
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignored
        }
        mediaRecorder = null
    }
}

