package com.example.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

enum class LobbyMusicPreset(val label: String, val bpm: Double) {
    NEON_LOUNGE("Neon Lounge", 96.0),
    CYBER_PULSE("Cyber Pulse", 118.0),
    CAMPUS_CHILL("Campus Chill", 84.0)
}

enum class GameSfx {
    OPENING_STING,
    ROOM_JOIN,
    ROUND_START,
    MATCH_START,
    CHAT_SEND,
    CHAT_PING,
    MOVE_CONFIRM,
    VOTE_SELECT,
    VOTE_LOCK,
    URGENT_TICK,
    REVEAL_WIN,
    REVEAL_GLITCH
}

class GameAudioEngine(context: Context) {
    private val appContext = context.applicationContext

    private var boundService: LobbyMediaPlayerService? = null
    private var isBound: Boolean = false
    private var bindAttempted: Boolean = false

    @Volatile
    var musicEnabled: Boolean = true
        private set

    @Volatile
    var sfxEnabled: Boolean = true
        private set

    private var activePreset: LobbyMusicPreset = LobbyMusicPreset.NEON_LOUNGE
    private var customFilePath: String? = null
    private var customMimeType: String? = null
    private var lobbyMusicDesired: Boolean = false
    private var isDucked: Boolean = false
    private val pendingSfxQueue = ArrayDeque<GameSfx>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? LobbyMediaPlayerService.LocalBinder ?: return
            val svc = binder.getService()
            boundService = svc
            isBound = true
            svc.warmUpCache()
            svc.updateMusicSettings(
                musicEnabled = musicEnabled,
                sfxEnabled = sfxEnabled,
                preset = activePreset,
                customFilePath = customFilePath,
                customMimeType = customMimeType
            )
            svc.setLobbyMusicState(lobbyMusicDesired, isDucked)
            while (pendingSfxQueue.isNotEmpty()) {
                svc.playSfx(pendingSfxQueue.removeFirst())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
        }
    }

    fun warmUp() {
        ensureBound()
        boundService?.warmUpCache()
    }

    fun setSettings(
        musicEnabled: Boolean,
        sfxEnabled: Boolean,
        preset: LobbyMusicPreset,
        customFilePath: String? = null,
        customMimeType: String? = null
    ) {
        this.musicEnabled = musicEnabled
        this.sfxEnabled = sfxEnabled
        this.activePreset = preset
        this.customFilePath = customFilePath
        this.customMimeType = customMimeType
        ensureBound()
        boundService?.updateMusicSettings(
            musicEnabled = musicEnabled,
            sfxEnabled = sfxEnabled,
            preset = preset,
            customFilePath = customFilePath,
            customMimeType = customMimeType
        )
    }

    fun setLobbyMusicActive(active: Boolean, ducked: Boolean = false) {
        this.lobbyMusicDesired = active
        this.isDucked = ducked
        ensureBound()
        boundService?.setLobbyMusicState(active, ducked)
    }

    fun playSfx(sfx: GameSfx) {
        if (!sfxEnabled) return
        ensureBound()
        val svc = boundService
        if (svc != null) {
            svc.playSfx(sfx)
        } else {
            if (pendingSfxQueue.size >= 4) {
                pendingSfxQueue.removeFirst()
            }
            pendingSfxQueue.addLast(sfx)
        }
    }

    private fun ensureBound() {
        if (bindAttempted) return
        bindAttempted = true
        try {
            val intent = Intent(appContext, LobbyMediaPlayerService::class.java)
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
            bindAttempted = false
        }
    }

    fun release() {
        pendingSfxQueue.clear()
        if (isBound) {
            try {
                appContext.unbindService(connection)
            } catch (_: Exception) {
            }
            isBound = false
        }
        boundService = null
        bindAttempted = false
    }
}
