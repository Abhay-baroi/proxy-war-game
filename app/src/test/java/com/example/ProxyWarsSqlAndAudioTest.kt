package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.GameSfx
import com.example.audio.LobbyMediaPlayerService
import com.example.audio.LobbyMusicPreset
import com.example.audio.LyriaModelTier
import com.example.audio.WavAudioSerializer
import com.example.data.local.AudioSettingsEntity
import com.example.data.local.GeneratedTrackEntity
import com.example.data.local.LocalSqlRepository
import com.example.data.local.MatchHistoryEntity
import com.example.data.local.ProxyWarsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProxyWarsSqlAndAudioTest {

    private lateinit var db: ProxyWarsDatabase
    private lateinit var repo: LocalSqlRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ProxyWarsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LocalSqlRepository(db.proxyWarsDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `match history and best vote speed ignore zero votes`() = runTest {
        repo.recordMatch(
            MatchHistoryEntity(
                roomCode = "AB12",
                modeName = "CLOUD HOST",
                finalScore = 350,
                rank = 1,
                totalPlayers = 5,
                wonMatch = true,
                fastestVoteMs = 0L
            )
        )
        assertNull(repo.bestVoteSpeedMs.first())

        repo.recordMatch(
            MatchHistoryEntity(
                roomCode = "CD34",
                modeName = "LAN CLIENT",
                finalScore = 250,
                rank = 2,
                totalPlayers = 6,
                wonMatch = false,
                fastestVoteMs = 2450L
            )
        )
        repo.recordMatch(
            MatchHistoryEntity(
                roomCode = "EF56",
                modeName = "PASS AND PLAY",
                finalScore = 400,
                rank = 1,
                totalPlayers = 5,
                wonMatch = true,
                fastestVoteMs = 1820L
            )
        )

        val history = repo.matchHistory.first()
        assertEquals(3, history.size)
        assertEquals(1820L, repo.bestVoteSpeedMs.first())
    }

    @Test
    fun `audio settings and generated tracks persist in Room SQLite`() = runTest {
        val trackId = repo.saveGeneratedTrack(
            GeneratedTrackEntity(
                title = "Campus Cyber Bass",
                prompt = "Dark neon campus lobby beat",
                modelId = LyriaModelTier.CLIP_30S.modelId,
                filePath = "/tmp/test_track.mp3",
                mimeType = "audio/mpeg",
                durationSec = 30
            )
        )

        repo.saveAudioSettings(
            AudioSettingsEntity(
                musicEnabled = true,
                sfxEnabled = false,
                presetName = LobbyMusicPreset.CYBER_PULSE.name,
                selectedTrackId = trackId
            )
        )

        val settings = repo.audioSettings.first()
        assertTrue(settings?.musicEnabled == true)
        assertFalse(settings?.sfxEnabled == true)
        assertEquals(LobbyMusicPreset.CYBER_PULSE.name, settings?.presetName)
        assertEquals(trackId, settings?.selectedTrackId)

        repo.deleteTrack(trackId)
        assertEquals(0, repo.generatedTracks.first().size)
    }

    @Test
    fun `lyria model tiers expose expected preview model ids`() {
        assertEquals("lyria-3-clip-preview", LyriaModelTier.CLIP_30S.modelId)
        assertEquals("lyria-3-pro-preview", LyriaModelTier.PRO_FULL.modelId)
    }

    @Test
    fun `wav serializer creates valid RIFF WAVE files for lobby music and SFX`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = File(context.cacheDir, "wav_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val lobbyWav = WavAudioSerializer.ensurePresetWav(cacheDir, LobbyMusicPreset.NEON_LOUNGE)
            assertTrue(lobbyWav.exists())
            val header = lobbyWav.readBytes().copyOfRange(0, 12)
            assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals("WAVE", String(header.copyOfRange(8, 12), Charsets.US_ASCII))

            listOf(GameSfx.ROOM_JOIN, GameSfx.CHAT_SEND, GameSfx.ROUND_START).forEach { sfx ->
                val sfxWav = WavAudioSerializer.ensureSfxWav(cacheDir, sfx)
                assertTrue(sfxWav.exists() && sfxWav.length() > 44L)
            }
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `lobby media player service binds and exposes local binder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(
            LobbyMediaPlayerService::class.java,
            Intent(context, LobbyMediaPlayerService::class.java)
        ).create().bind()
        val serviceInstance = controller.get()
        val binder = serviceInstance.onBind(Intent(context, LobbyMediaPlayerService::class.java)) as? LobbyMediaPlayerService.LocalBinder
        assertNotNull(binder?.getService())
        controller.unbind().destroy()
    }
}
