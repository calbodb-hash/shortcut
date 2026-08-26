package com.example.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.domain.model.AudioClip
import com.example.domain.model.AudioTrackType
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Real-time audio metering snapshot for UI visualizers.
 */
data class AudioMixState(
    val masterLeftLevel: Float = 0f, // 0.0 to 1.0
    val masterRightLevel: Float = 0f, // 0.0 to 1.0
    val activeMusicLevel: Float = 0f,
    val activeVoiceLevel: Float = 0f,
    val activeSfxLevel: Float = 0f,
    val activeVideoAudioLevel: Float = 0f
)

/**
 * Professional multi-track audio preview mixer.
 * Manages concurrent audio streams (Video original audio, Music, Voice-over, Sound Effects),
 * envelope calculations (fade-in, fade-out, volume, panning, ducking), and scrubbing synchronization.
 */
class AudioPreviewMixer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val TAG = "AudioPreviewMixer"

    private val _mixState = MutableStateFlow(AudioMixState())
    val mixState: StateFlow<AudioMixState> = _mixState.asStateFlow()

    private var activeMusicPlayer: MediaPlayer? = null
    private var activeVoicePlayer: MediaPlayer? = null
    private var activeSfxPlayer: MediaPlayer? = null
    private var activeVideoAudioPlayer: MediaPlayer? = null

    private var currentMusicUri: String? = null
    private var currentVoiceUri: String? = null
    private var currentSfxUri: String? = null
    private var currentVideoUri: String? = null

    /**
     * Updates mixed audio playback and calculates real-time gain/metering values at the given playhead.
     */
    fun syncMix(timeline: Timeline, playheadMs: Long, isPlaying: Boolean) {
        val tracksMap = timeline.tracks.associateBy { it.id }

        // 1. Calculate active video clip audio
        val activeVideo = timeline.videoClips.firstOrNull {
            playheadMs >= it.timelineStartMs && playheadMs < (it.timelineStartMs + it.trimmedDurationMs)
        }
        val isVideoTrackMuted = tracksMap["track_video_main"]?.isMuted == true
        val videoGain = if (activeVideo != null && !isVideoTrackMuted && !activeVideo.isMuted) {
            activeVideo.volume.coerceIn(0f, 2f)
        } else 0f

        // 2. Calculate active audio clips (Music, Voice-over, SFX, Extracted)
        val activeAudios = timeline.audioClips.filter {
            playheadMs >= it.timelineStartMs && playheadMs < (it.timelineStartMs + it.trimmedDurationMs)
        }

        var voiceGain = 0f
        var musicGain = 0f
        var sfxGain = 0f
        var hasActiveVoice = false

        // First pass: check if voice-over is active to trigger ducking on music
        for (audio in activeAudios) {
            val isTrackMuted = tracksMap[audio.trackId]?.isMuted == true
            if (audio.audioTrackType == AudioTrackType.VOICE_OVER && !audio.isMuted && !isTrackMuted) {
                val offset = (playheadMs - audio.timelineStartMs) * audio.speed
                val g = audio.calculateEffectiveVolumeAt(offset.toLong(), isTrackMuted)
                if (g > 0.05f) {
                    hasActiveVoice = true
                    voiceGain = max(voiceGain, g)
                }
            }
        }

        // Second pass: compute effective gain for music and SFX with ducking applied
        for (audio in activeAudios) {
            val isTrackMuted = tracksMap[audio.trackId]?.isMuted == true
            val offset = (playheadMs - audio.timelineStartMs) * audio.speed
            var effectiveGain = audio.calculateEffectiveVolumeAt(offset.toLong(), isTrackMuted)

            if (audio.audioTrackType == AudioTrackType.MUSIC || audio.audioTrackType == AudioTrackType.EXTRACTED) {
                if (hasActiveVoice && audio.duckingEnabled) {
                    effectiveGain *= audio.duckingLevel.coerceIn(0.1f, 1.0f)
                }
                musicGain = max(musicGain, effectiveGain)
            } else if (audio.audioTrackType == AudioTrackType.SFX) {
                sfxGain = max(sfxGain, effectiveGain)
            }
        }

        // 3. Compute master stereo levels with pan
        val totalLeft = (videoGain * 0.5f + musicGain * 0.5f + voiceGain * 0.6f + sfxGain * 0.5f).coerceIn(0f, 1f)
        val totalRight = totalLeft // Balanced master

        _mixState.value = AudioMixState(
            masterLeftLevel = totalLeft,
            masterRightLevel = totalRight,
            activeMusicLevel = (musicGain / 1.5f).coerceIn(0f, 1f),
            activeVoiceLevel = (voiceGain / 1.5f).coerceIn(0f, 1f),
            activeSfxLevel = (sfxGain / 1.5f).coerceIn(0f, 1f),
            activeVideoAudioLevel = (videoGain / 1.5f).coerceIn(0f, 1f)
        )

        // 4. Stream synchronization for playable local/content URI media
        if (isPlaying) {
            val primaryMusic = activeAudios.firstOrNull { it.audioTrackType == AudioTrackType.MUSIC }
            val primaryVoice = activeAudios.firstOrNull { it.audioTrackType == AudioTrackType.VOICE_OVER }
            val primarySfx = activeAudios.firstOrNull { it.audioTrackType == AudioTrackType.SFX }

            syncTrackPlayer(
                audioClip = primaryMusic,
                playheadMs = playheadMs,
                gain = musicGain,
                activePlayerGetter = { activeMusicPlayer },
                activePlayerSetter = { activeMusicPlayer = it },
                currentUriGetter = { currentMusicUri },
                currentUriSetter = { currentMusicUri = it }
            )

            syncTrackPlayer(
                audioClip = primaryVoice,
                playheadMs = playheadMs,
                gain = voiceGain,
                activePlayerGetter = { activeVoicePlayer },
                activePlayerSetter = { activeVoicePlayer = it },
                currentUriGetter = { currentVoiceUri },
                currentUriSetter = { currentVoiceUri = it }
            )

            syncTrackPlayer(
                audioClip = primarySfx,
                playheadMs = playheadMs,
                gain = sfxGain,
                activePlayerGetter = { activeSfxPlayer },
                activePlayerSetter = { activeSfxPlayer = it },
                currentUriGetter = { currentSfxUri },
                currentUriSetter = { currentSfxUri = it }
            )
        } else {
            pauseAllPlayers()
        }
    }

    private val preparedPlayers = mutableSetOf<MediaPlayer>()

    private fun syncTrackPlayer(
        audioClip: AudioClip?,
        playheadMs: Long,
        gain: Float,
        activePlayerGetter: () -> MediaPlayer?,
        activePlayerSetter: (MediaPlayer?) -> Unit,
        currentUriGetter: () -> String?,
        currentUriSetter: (String?) -> Unit
    ) {
        if (audioClip == null || audioClip.sourceUri.isBlank() || gain <= 0f) {
            val player = activePlayerGetter()
            try {
                if (player != null && preparedPlayers.contains(player) && player.isPlaying) {
                    player.pause()
                }
            } catch (e: Exception) {
                // Ignored
            }
            return
        }

        val uri = audioClip.sourceUri
        val isSupported = uri.startsWith("content://") || uri.startsWith("file://") ||
                uri.startsWith("android.resource://") || uri.startsWith("http://") || uri.startsWith("https://")
        if (!isSupported) {
            return
        }

        try {
            var player = activePlayerGetter()
            if (player == null || currentUriGetter() != uri) {
                player?.let {
                    preparedPlayers.remove(it)
                    try { it.reset(); it.release() } catch (e: Exception) {}
                }
                val newPlayer = MediaPlayer()
                newPlayer.setOnErrorListener { mp, what, extra ->
                    Log.d(TAG, "Audio player error: what=$what, extra=$extra")
                    preparedPlayers.remove(mp)
                    true
                }
                newPlayer.setOnPreparedListener { mp ->
                    preparedPlayers.add(mp)
                    val localOffset = ((playheadMs - audioClip.timelineStartMs) * audioClip.speed).toLong()
                    val targetMs = (audioClip.sourceStartTimeMs + localOffset).toInt().coerceAtLeast(0)
                    try {
                        mp.seekTo(targetMs)
                        mp.setVolume(gain.coerceIn(0f, 1f), gain.coerceIn(0f, 1f))
                        mp.start()
                    } catch (e: Exception) {
                        Log.d(TAG, "Audio player seek/start error: ${e.message}")
                    }
                }
                newPlayer.setDataSource(context, Uri.parse(uri))
                newPlayer.prepareAsync()
                activePlayerSetter(newPlayer)
                currentUriSetter(uri)
            } else if (preparedPlayers.contains(player)) {
                try {
                    player.setVolume(gain.coerceIn(0f, 1f), gain.coerceIn(0f, 1f))
                    if (!player.isPlaying) {
                        val localOffset = ((playheadMs - audioClip.timelineStartMs) * audioClip.speed).toLong()
                        val targetMs = (audioClip.sourceStartTimeMs + localOffset).toInt().coerceAtLeast(0)
                        player.seekTo(targetMs)
                        player.start()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Audio player error during playback: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Audio track sync notice: ${e.message}")
        }
    }

    fun pauseAllPlayers() {
        listOf(activeMusicPlayer, activeVoicePlayer, activeSfxPlayer, activeVideoAudioPlayer).forEach { player ->
            if (player != null && preparedPlayers.contains(player)) {
                try {
                    if (player.isPlaying) player.pause()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }

    fun release() {
        pauseAllPlayers()
        listOf(activeMusicPlayer, activeVoicePlayer, activeSfxPlayer, activeVideoAudioPlayer).forEach { player ->
            if (player != null) {
                preparedPlayers.remove(player)
                try {
                    player.reset()
                    player.release()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
        activeMusicPlayer = null
        activeVoicePlayer = null
        activeSfxPlayer = null
        activeVideoAudioPlayer = null
    }
}
