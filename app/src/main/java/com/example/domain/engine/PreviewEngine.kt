package com.example.domain.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.core.audio.AudioMixState
import com.example.core.audio.AudioPreviewMixer
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IPreviewEngine {
    val playheadMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val activeClip: StateFlow<VideoClip?>
    val audioMixState: StateFlow<AudioMixState>

    fun setTimeline(timeline: Timeline)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun scrubTo(positionMs: Long)
    fun release()
}

class PreviewEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : IPreviewEngine {

    private val _playheadMs = MutableStateFlow(0L)
    override val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeClip = MutableStateFlow<VideoClip?>(null)
    override val activeClip: StateFlow<VideoClip?> = _activeClip.asStateFlow()

    private val audioPreviewMixer = AudioPreviewMixer(context, scope)
    override val audioMixState: StateFlow<AudioMixState> = audioPreviewMixer.mixState

    private var currentTimeline: Timeline = Timeline()
    private var playbackJob: Job? = null
    private var scrubSyncJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayerPrepared: Boolean = false
    private var currentPlayingUri: String? = null

    override fun setTimeline(timeline: Timeline) {
        this.currentTimeline = timeline
        updateActiveClipForPosition(_playheadMs.value)
        audioPreviewMixer.syncMix(timeline, _playheadMs.value, _isPlaying.value)
    }

    override fun play() {
        if (currentTimeline.totalDurationMs == 0L) return
        if (_playheadMs.value >= currentTimeline.totalDurationMs) {
            _playheadMs.value = 0L
        }
        _isPlaying.value = true
        audioPreviewMixer.syncMix(currentTimeline, _playheadMs.value, true)
        if (isPlayerPrepared) {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.d("PreviewEngine", "MediaPlayer start error: ${e.message}")
            }
        }
        startPlaybackLoop()
    }

    override fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        audioPreviewMixer.pauseAllPlayers()
        if (isPlayerPrepared) {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (e: Exception) {
                Log.d("PreviewEngine", "MediaPlayer pause error: ${e.message}")
            }
        }
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
        _playheadMs.value = clamped
        updateActiveClipForPosition(clamped)
        syncMediaPlayer(clamped)
        audioPreviewMixer.syncMix(currentTimeline, clamped, _isPlaying.value)
    }

    override fun scrubTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
        // Immediate UI playhead update
        _playheadMs.value = clamped
        updateActiveClipForPosition(clamped)
        audioPreviewMixer.syncMix(currentTimeline, clamped, false)

        // Debounce media player synchronization to keep 60 FPS touch responsiveness on low-end devices
        scrubSyncJob?.cancel()
        scrubSyncJob = scope.launch {
            delay(40L)
            syncMediaPlayer(clamped)
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val frameIntervalMs = 33L // ~30fps preview loop
            var lastTime = System.currentTimeMillis()

            while (isActive && _isPlaying.value) {
                delay(frameIntervalMs)
                val now = System.currentTimeMillis()
                val delta = now - lastTime
                lastTime = now

                val newPos = _playheadMs.value + delta
                if (newPos >= currentTimeline.totalDurationMs) {
                    _playheadMs.value = currentTimeline.totalDurationMs
                    audioPreviewMixer.syncMix(currentTimeline, currentTimeline.totalDurationMs, false)
                    pause()
                    break
                } else {
                    _playheadMs.value = newPos
                    updateActiveClipForPosition(newPos)
                    audioPreviewMixer.syncMix(currentTimeline, newPos, true)
                }
            }
        }
    }

    private fun updateActiveClipForPosition(posMs: Long) {
        val clip = currentTimeline.videoClips.firstOrNull {
            posMs >= it.timelineStartMs && posMs < (it.timelineStartMs + it.trimmedDurationMs)
        } ?: currentTimeline.videoClips.lastOrNull()

        _activeClip.value = clip
    }

    private fun syncMediaPlayer(posMs: Long) {
        val clip = _activeClip.value ?: return
        if (clip.sourceUri.isBlank() || clip.mediaType == com.example.domain.model.MediaType.IMAGE) {
            releaseMediaPlayer()
            return
        }

        val uriStr = clip.sourceUri
        val isValidPlayableUri = uriStr.startsWith("content://") || uriStr.startsWith("file://") ||
                uriStr.startsWith("android.resource://") || uriStr.startsWith("http://") || uriStr.startsWith("https://")

        if (!isValidPlayableUri) {
            releaseMediaPlayer()
            return
        }

        try {
            if (currentPlayingUri != uriStr || mediaPlayer == null) {
                releaseMediaPlayer()
                currentPlayingUri = uriStr
                val player = MediaPlayer()
                mediaPlayer = player
                player.setOnErrorListener { _, what, extra ->
                    Log.d("PreviewEngine", "MediaPlayer error handled: what=$what, extra=$extra")
                    isPlayerPrepared = false
                    true
                }
                player.setOnPreparedListener { mp ->
                    if (mediaPlayer === mp) {
                        isPlayerPrepared = true
                        val clipLocalMs = (posMs - clip.timelineStartMs) * clip.speed
                        val targetMs = (clip.sourceStartTimeMs + clipLocalMs).toInt().coerceAtLeast(0)
                        try {
                            mp.seekTo(targetMs)
                            if (_isPlaying.value) {
                                mp.start()
                            }
                        } catch (e: Exception) {
                            Log.d("PreviewEngine", "Error seeking after prepare: ${e.message}")
                        }
                    }
                }
                player.setDataSource(context, Uri.parse(uriStr))
                player.prepareAsync()
            } else if (isPlayerPrepared && mediaPlayer != null) {
                val clipLocalMs = (posMs - clip.timelineStartMs) * clip.speed
                val targetMs = (clip.sourceStartTimeMs + clipLocalMs).toInt().coerceAtLeast(0)
                try {
                    mediaPlayer?.seekTo(targetMs)
                } catch (e: Exception) {
                    Log.d("PreviewEngine", "MediaPlayer seekTo error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d("PreviewEngine", "Audio/Video streaming sync notice: ${e.message}")
            releaseMediaPlayer()
        }
    }

    private fun releaseMediaPlayer() {
        isPlayerPrepared = false
        currentPlayingUri = null
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignored
        }
        mediaPlayer = null
    }

    override fun release() {
        pause()
        audioPreviewMixer.release()
        releaseMediaPlayer()
    }
}

