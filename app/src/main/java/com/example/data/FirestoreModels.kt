package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FsUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val matchesPlayed: Long = 0L,
    val totalPoints: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsGameRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostId: String = "",
    val memberUids: List<String> = emptyList(),
    val phase: String = "LOBBY",
    val phaseEndsAtMs: Long = 0L,
    val phaseDurationMs: Long = 0L,
    val roundNum: Long = 1L,
    val totalRounds: Long = 3L,
    val openTable: Boolean = false,
    val promptId: String? = null,
    val promptCategory: String? = null,
    val promptText: String? = null,
    val humansWonRound: Boolean? = null,
    val glitchPlayerIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRoomPlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val memberUids: List<String> = emptyList(),
    val nickname: String = "",
    val hostFlag: Boolean = false,
    val score: Long = 0L,
    val totalVoteTimeMs: Long = 0L,
    val hasSubmittedAnswer: Boolean = false,
    val hasVoted: Boolean = false,
    val mySubmittedAnswerId: String? = null,
    val lockedVoteAnswerId: String? = null,
    val lastRoundDelta: Long = 0L,
    val lastRoundTags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRoundSecret(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val memberUids: List<String> = emptyList(),
    val roundNum: Long = 1L,
    val role: String = "HUMAN",
    val aiOptions: List<String> = emptyList(),
    val rerollUsed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsAnswerDraft(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val roundNum: Long = 1L,
    val answerText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsVoteRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val roundNum: Long = 1L,
    val targetAnswerId: String = "",
    val elapsedMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsPublicAnswer(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val memberUids: List<String> = emptyList(),
    val roundNum: Long = 1L,
    val label: String = "",
    val text: String = "",
    val revealed: Boolean = false,
    val authorId: String? = null,
    val authorNickname: String? = null,
    val role: String? = null,
    val voterNicknames: List<String> = emptyList(),
    val voteCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsLeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nickname: String = "",
    val totalPoints: Long = 0L,
    val lastRoundDelta: Long = 0L,
    val roundsPlayed: Long = 0L,
    val glitchesCaught: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FsRoomMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val hostId: String = "",
    val memberUids: List<String> = emptyList(),
    val senderNickname: String = "",
    val text: String = "",
    val roundNum: Long = 0L,
    val phase: String = "LOBBY",
    val clientSentAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
