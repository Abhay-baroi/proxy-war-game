package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SavedSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "proxy_wars_session")

class SessionStore(private val context: Context) {

    private object Keys {
        val ROOM_CODE = stringPreferencesKey("room_code")
        val HOST_ADDRESS = stringPreferencesKey("host_address")
        val PLAYER_ID = stringPreferencesKey("player_id")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val NICKNAME = stringPreferencesKey("nickname")
    }

    val savedSessionFlow: Flow<SavedSession?> = context.sessionDataStore.data.map { prefs ->
        val roomCode = prefs[Keys.ROOM_CODE] ?: ""
        val hostAddress = prefs[Keys.HOST_ADDRESS] ?: ""
        val playerId = prefs[Keys.PLAYER_ID] ?: ""
        val sessionToken = prefs[Keys.SESSION_TOKEN] ?: ""
        val nickname = prefs[Keys.NICKNAME] ?: ""
        if (roomCode.isNotBlank() && hostAddress.isNotBlank() && playerId.isNotBlank() && sessionToken.isNotBlank()) {
            SavedSession(
                roomCode = roomCode,
                hostAddress = hostAddress,
                playerId = playerId,
                sessionToken = sessionToken,
                nickname = nickname
            )
        } else {
            null
        }
    }

    suspend fun saveSession(session: SavedSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ROOM_CODE] = session.roomCode
            prefs[Keys.HOST_ADDRESS] = session.hostAddress
            prefs[Keys.PLAYER_ID] = session.playerId
            prefs[Keys.SESSION_TOKEN] = session.sessionToken
            prefs[Keys.NICKNAME] = session.nickname
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
