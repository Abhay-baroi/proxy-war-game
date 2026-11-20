package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "match_history")
data class MatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val roomCode: String,
    val modeName: String,
    val finalScore: Int,
    val rank: Int,
    val totalPlayers: Int,
    val wonMatch: Boolean,
    val fastestVoteMs: Long,
    val playedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "generated_tracks")
data class GeneratedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val prompt: String,
    val modelId: String,
    val filePath: String,
    val mimeType: String,
    val durationSec: Int,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "audio_settings")
data class AudioSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val presetName: String = "NEON_LOUNGE",
    val selectedTrackId: Long? = null
)

@Dao
interface ProxyWarsDao {
    @Query("SELECT * FROM match_history ORDER BY playedAtMs DESC LIMIT 20")
    fun observeMatchHistory(): Flow<List<MatchHistoryEntity>>

    @Query("SELECT MIN(fastestVoteMs) FROM match_history WHERE fastestVoteMs > 0")
    fun observeBestVoteSpeedMs(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchHistory(entry: MatchHistoryEntity)

    @Query("SELECT * FROM generated_tracks ORDER BY createdAtMs DESC LIMIT 15")
    fun observeGeneratedTracks(): Flow<List<GeneratedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedTrack(track: GeneratedTrackEntity): Long

    @Query("DELETE FROM generated_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("SELECT * FROM audio_settings WHERE id = 1")
    fun observeAudioSettings(): Flow<AudioSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudioSettings(settings: AudioSettingsEntity)
}

@Database(
    entities = [
        MatchHistoryEntity::class,
        GeneratedTrackEntity::class,
        AudioSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ProxyWarsDatabase : RoomDatabase() {
    abstract fun proxyWarsDao(): ProxyWarsDao

    companion object {
        @Volatile
        private var INSTANCE: ProxyWarsDatabase? = null

        fun getInstance(context: Context): ProxyWarsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProxyWarsDatabase::class.java,
                    "proxy_wars_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}

class LocalSqlRepository(private val dao: ProxyWarsDao) {
    val matchHistory: Flow<List<MatchHistoryEntity>> = dao.observeMatchHistory()
    val bestVoteSpeedMs: Flow<Long?> = dao.observeBestVoteSpeedMs()
    val generatedTracks: Flow<List<GeneratedTrackEntity>> = dao.observeGeneratedTracks()
    val audioSettings: Flow<AudioSettingsEntity?> = dao.observeAudioSettings()

    suspend fun recordMatch(entry: MatchHistoryEntity) = dao.insertMatchHistory(entry)

    suspend fun saveGeneratedTrack(track: GeneratedTrackEntity): Long = dao.insertGeneratedTrack(track)

    suspend fun deleteTrack(id: Long) = dao.deleteTrackById(id)

    suspend fun saveAudioSettings(settings: AudioSettingsEntity) = dao.upsertAudioSettings(settings)
}
