package com.example.model

import com.example.audio.LobbyMusicPreset
import com.example.data.local.GeneratedTrackEntity
import com.example.data.local.MatchHistoryEntity

enum class GameMode {
    CLOUD_HOST,
    CLOUD_CLIENT,
    LAN_HOST,
    LAN_CLIENT,
    PASS_AND_PLAY
}

enum class GamePhase {
    JOIN_HUB,
    LOBBY,
    ROLE_REVEAL,
    PROMPT,
    DEBATE,
    VOTE,
    REVEAL,
    MATCH_END
}

enum class PlayerRole {
    HUMAN,
    GLITCH
}

data class OpenTableSummary(
    val roomCode: String,
    val playerCount: Int,
    val hostId: String
)

data class GlobalLeaderboardEntry(
    val userId: String,
    val nickname: String,
    val totalPoints: Int,
    val lastRoundDelta: Int,
    val roundsPlayed: Int,
    val glitchesCaught: Int,
    val isCurrentUser: Boolean = false
)

data class PromptItem(
    val id: String,
    val category: String,
    val prompt: String,
    val aiLines: List<String>
)

data class PlayerState(
    val id: String,
    val nickname: String,
    val isHost: Boolean = false,
    val isConnected: Boolean = true,
    val score: Int = 0,
    val totalVoteTimeMs: Long = 0L,
    val hasSubmittedAnswer: Boolean = false,
    val hasVoted: Boolean = false
)

data class AnonymousSubmission(
    val answerId: String,
    val label: String,
    val text: String,
    val isOwnSubmission: Boolean = false
)

data class RevealedSubmission(
    val answerId: String,
    val label: String,
    val text: String,
    val authorId: String,
    val authorNickname: String,
    val role: PlayerRole,
    val voterNicknames: List<String>,
    val voteCount: Int
)

data class ScoreDeltaEntry(
    val playerId: String,
    val nickname: String,
    val role: PlayerRole,
    val deltaPoints: Int,
    val totalScore: Int,
    val totalVoteTimeMs: Long,
    val reasonTags: List<String>
)

data class RoundRevealData(
    val roundNum: Int,
    val totalRounds: Int,
    val promptText: String,
    val category: String,
    val revealedSubmissions: List<RevealedSubmission>,
    val glitchPlayerIds: List<String>,
    val scoreDeltas: List<ScoreDeltaEntry>,
    val humansWonRound: Boolean,
    val nextRoundAtMs: Long
)

data class SavedSession(
    val roomCode: String,
    val hostAddress: String,
    val playerId: String,
    val sessionToken: String,
    val nickname: String
)

data class PassAndPlayTurnState(
    val currentPlayerIndex: Int = 0,
    val isPrivacyCurtainActive: Boolean = true,
    val currentPlayerId: String = "",
    val currentPlayerNickname: String = "",
    val currentPlayerRole: PlayerRole = PlayerRole.HUMAN,
    val currentAiOptions: List<String> = emptyList(),
    val rerollUsed: Boolean = false
)

data class RoomChatMessage(
    val id: String,
    val senderId: String,
    val senderNickname: String,
    val text: String,
    val roundNum: Int,
    val phase: GamePhase,
    val sentAtMs: Long,
    val isMine: Boolean = false
)

data class GameUiState(
    val mode: GameMode = GameMode.CLOUD_CLIENT,
    val phase: GamePhase = GamePhase.JOIN_HUB,
    val roomCode: String = "",
    val hostAddress: String = "",
    val myPlayerId: String = "",
    val myNickname: String = "",
    val isHost: Boolean = false,
    val isOpenTable: Boolean = false,
    val players: List<PlayerState> = emptyList(),
    val openTables: List<OpenTableSummary> = emptyList(),
    val globalLeaderboard: List<GlobalLeaderboardEntry> = emptyList(),
    val roundNum: Int = 1,
    val totalRounds: Int = 3,
    val phaseEndsAtMs: Long = 0L,
    val phaseDurationMs: Long = 0L,
    val remainingSeconds: Int = 0,
    val currentPrompt: PromptItem? = null,
    val myRole: PlayerRole = PlayerRole.HUMAN,
    val myAiOptions: List<String> = emptyList(),
    val rerollUsed: Boolean = false,
    val hasSubmittedCurrentRound: Boolean = false,
    val mySubmittedAnswerId: String = "",
    val anonymousSubmissions: List<AnonymousSubmission> = emptyList(),
    val selectedVoteAnswerId: String? = null,
    val lockedVoteAnswerId: String? = null,
    val revealData: RoundRevealData? = null,
    val savedSession: SavedSession? = null,
    val passAndPlayTurn: PassAndPlayTurnState? = null,
    val chatMessages: List<RoomChatMessage> = emptyList(),
    val isSendingChat: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val statusBanner: String? = null
)

data class ArenaMediaUiState(
    val showGameOpening: Boolean = true,
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val activePreset: LobbyMusicPreset = LobbyMusicPreset.NEON_LOUNGE,
    val selectedTrackId: Long? = null,
    val generatedTracks: List<GeneratedTrackEntity> = emptyList(),
    val isGeneratingTrack: Boolean = false,
    val musicGenError: String? = null,
    val matchHistory: List<MatchHistoryEntity> = emptyList(),
    val bestVoteSpeedMs: Long? = null,
    val aiReplySuggestion: String? = null,
    val isSuggestingReply: Boolean = false,
    val aiReplyError: String? = null
)
