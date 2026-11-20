package com.example.audio

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LobbyMediaPlayerService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): LobbyMediaPlayerService = this@LobbyMediaPlayerService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var lobbyMusicPlayer: MediaPlayer? = null
    private var currentMusicSourceKey: String? = null
    private val activeSfxPlayers = mutableListOf<MediaPlayer>()

    private var musicEnabled: Boolean = true
    private var sfxEnabled: Boolean = true
    private var activePreset: LobbyMusicPreset = LobbyMusicPreset.NEON_LOUNGE
    private var customFilePath: String? = null
    private var customMimeType: String? = null
    private var isMusicDesired: Boolean = false
    private var isDucked: Boolean = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    fun warmUpCache() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                WavAudioSerializer.ensurePresetWav(cacheDir, LobbyMusicPreset.NEON_LOUNGE)
                listOf(
                    GameSfx.OPENING_STING,
                    GameSfx.ROOM_JOIN,
                    GameSfx.CHAT_SEND,
                    GameSfx.ROUND_START,
                    GameSfx.MOVE_CONFIRM
                ).forEach { sfx ->
                    WavAudioSerializer.ensureSfxWav(cacheDir, sfx)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio cache warm-up skipped", e)
            }
        }
    }

    fun updateMusicSettings(
        musicEnabled: Boolean,
        sfxEnabled: Boolean,
        preset: LobbyMusicPreset,
        customFilePath: String?,
        customMimeType: String?
    ) {
        val sourceChanged = this.activePreset != preset || this.customFilePath != customFilePath
        this.musicEnabled = musicEnabled
        this.sfxEnabled = sfxEnabled
        this.activePreset = preset
        this.customFilePath = customFilePath
        this.customMimeType = customMimeType

        if (!musicEnabled) {
            pauseOrStopMusic(release = true)
        } else if (isMusicDesired) {
            if (sourceChanged || lobbyMusicPlayer == null) {
                startOrSwitchMusicTrack()
            } else {
                resumeMusicIfPrepared()
            }
        }
    }

    fun setLobbyMusicState(active: Boolean, ducked: Boolean) {
        this.isMusicDesired = active
        this.isDucked = ducked
        updateLobbyVolume()

        if (!active || !musicEnabled) {
            pauseOrStopMusic(release = false)
        } else {
            val player = lobbyMusicPlayer
            if (player == null) {
                startOrSwitchMusicTrack()
            } else {
                resumeMusicIfPrepared()
            }
        }
    }

    fun playSfx(sfx: GameSfx) {
        if (!sfxEnabled) return
        serviceScope.launch {
            val wavFile = withContext(Dispatchers.IO) {
                try {
                    WavAudioSerializer.ensureSfxWav(cacheDir, sfx)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to prepare SFX file for $sfx", e)
                    null
                }
            } ?: return@launch

            if (!sfxEnabled) return@launch
            launchSfxMediaPlayer(wavFile)
        }
    }

    private fun startOrSwitchMusicTrack() {
        val presetSnapshot = activePreset
        val customPathSnapshot = customFilePath
        val customMimeSnapshot = customMimeType

        serviceScope.launch {
            val sourceFile = withContext(Dispatchers.IO) {
                resolveMusicSourceFile(presetSnapshot, customPathSnapshot, customMimeSnapshot)
            } ?: return@launch

            if (!musicEnabled || !isMusicDesired) return@launch
            val sourceKey = sourceFile.absolutePath
            if (sourceKey == currentMusicSourceKey && lobbyMusicPlayer != null) {
                resumeMusicIfPrepared()
                return@launch
            }

            releaseLobbyMusicPlayer()
            currentMusicSourceKey = sourceKey

            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(sourceFile.absolutePath)
                    isLooping = true
                    val vol = currentMusicVolume()
                    setVolume(vol, vol)
                    setOnErrorListener { mp, what, extra ->
                        Log.w(TAG, "Lobby MediaPlayer error what=$what extra=$extra")
                        if (lobbyMusicPlayer == mp) {
                            releaseLobbyMusicPlayer()
                        }
                        true
                    }
                    prepare()
                    start()
                }
                lobbyMusicPlayer = player
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer failed for $sourceKey", e)
                releaseLobbyMusicPlayer()
            }
        }
    }

    private fun resolveMusicSourceFile(
        preset: LobbyMusicPreset,
        customPath: String?,
        customMime: String?
    ): File? {
        if (!customPath.isNullOrBlank()) {
            val customFile = File(customPath)
            if (customFile.exists() && customFile.length() > 0L) {
                val mime = customMime.orEmpty().lowercase()
                return if (mime.contains("pcm") || mime.contains("l16")) {
                    WavAudioSerializer.wrapRawPcmAsWav(cacheDir, customFile.readBytes(), customPath)
                } else {
                    customFile
                }
            }
        }
        return try {
            WavAudioSerializer.ensurePresetWav(cacheDir, preset)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build preset WAV for $preset", e)
            null
        }
    }

    private fun resumeMusicIfPrepared() {
        val player = lobbyMusicPlayer ?: return
        try {
            updateLobbyVolume()
            if (!player.isPlaying) {
                player.start()
            }
        } catch (_: Exception) {
            startOrSwitchMusicTrack()
        }
    }

    private fun pauseOrStopMusic(release: Boolean) {
        if (release) {
            releaseLobbyMusicPlayer()
            return
        }
        try {
            lobbyMusicPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun updateLobbyVolume() {
        val vol = currentMusicVolume()
        try {
            lobbyMusicPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {
        }
    }

    private fun currentMusicVolume(): Float = if (isDucked) 0.16f else 0.42f

    private fun launchSfxMediaPlayer(file: File) {
        // Keep active SFX pool bounded so rapid ticks never exhaust MediaPlayer resources
        while (activeSfxPlayers.size >= MAX_CONCURRENT_SFX) {
            val oldest = activeSfxPlayers.removeAt(0)
            safeReleasePlayer(oldest)
        }

        try {
            val sfxPlayer = MediaPlayer()
            activeSfxPlayers.add(sfxPlayer)
            sfxPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(0.72f, 0.72f)
                setOnCompletionListener { mp ->
                    activeSfxPlayers.remove(mp)
                    safeReleasePlayer(mp)
                }
                setOnErrorListener { mp, _, _ ->
                    activeSfxPlayers.remove(mp)
                    safeReleasePlayer(mp)
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SFX MediaPlayer playback failed for ${file.name}", e)
        }
    }

    private fun releaseLobbyMusicPlayer() {
        val mp = lobbyMusicPlayer
        lobbyMusicPlayer = null
        currentMusicSourceKey = null
        safeReleasePlayer(mp)
    }

    private fun safeReleasePlayer(mp: MediaPlayer?) {
        if (mp == null) return
        try {
            if (mp.isPlaying) {
                mp.stop()
            }
        } catch (_: Exception) {
        }
        try {
            mp.release()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        releaseLobbyMusicPlayer()
        activeSfxPlayers.toList().forEach { safeReleasePlayer(it) }
        activeSfxPlayers.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LobbyMediaService"
        const val MAX_CONCURRENT_SFX = 6
    }
}
