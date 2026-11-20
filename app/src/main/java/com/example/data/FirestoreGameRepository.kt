package com.example.data

import com.example.engine.GameScoringEngine
import com.example.model.GamePhase
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class FirestoreGameRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(defaultNickname: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val cleanNick = defaultNickname.trim().take(18).ifBlank { "Player" }
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "nickname" to cleanNick,
                    "matchesPlayed" to 0L,
                    "totalPoints" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeGlobalLeaderboard(limit: Long = 20L): Flow<List<FsLeaderboardEntry>> = flow {
        requireUserId()
        val path = "leaderboard"
        emitAll(
            db.collection("leaderboard")
                .orderBy("totalPoints", Query.Direction.DESCENDING)
                .limit(limit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FsLeaderboardEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser != null) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordRoundToLeaderboard(
        nickname: String,
        roundDelta: Int,
        caughtGlitch: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "leaderboard/$uid"
        return try {
            val safeDelta = roundDelta.toLong().coerceIn(0L, 1000L)
            val glitchDelta = if (caughtGlitch) 1L else 0L
            val cleanNick = nickname.trim().take(18).ifBlank { "Player" }

            val lbRef = db.collection("leaderboard").document(uid)
            val userRef = db.collection("users").document(uid)

            db.runTransaction { tx ->
                val lbSnap = tx.get(lbRef)
                val userSnap = tx.get(userRef)

                if (!lbSnap.exists()) {
                    tx.set(
                        lbRef,
                        mapOf(
                            "userId" to uid,
                            "nickname" to cleanNick,
                            "totalPoints" to safeDelta,
                            "lastRoundDelta" to safeDelta,
                            "roundsPlayed" to 1L,
                            "glitchesCaught" to glitchDelta,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.update(
                        lbRef,
                        mapOf(
                            "nickname" to cleanNick,
                            "totalPoints" to FieldValue.increment(safeDelta),
                            "lastRoundDelta" to safeDelta,
                            "roundsPlayed" to FieldValue.increment(1L),
                            "glitchesCaught" to FieldValue.increment(glitchDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (userSnap.exists()) {
                    tx.update(
                        userRef,
                        mapOf(
                            "nickname" to cleanNick,
                            "totalPoints" to FieldValue.increment(safeDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchCompleted(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            if (snap.exists()) {
                userRef.update(
                    mapOf(
                        "matchesPlayed" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeOpenTables(): Flow<List<FsGameRoom>> = flow {
        requireUserId()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("openTable", true)
                .whereEqualTo("phase", GamePhase.LOBBY.name)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FsGameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.memberUids.size < 10 }
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser != null) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomCode: String): Flow<FsGameRoom?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomCode"
        emitAll(
            db.collection("rooms").document(roomCode)
                .snapshots()
                .map { snap ->
                    snap.toObject(FsGameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoomPlayers(roomCode: String): Flow<List<FsRoomPlayer>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomCode/players"
        emitAll(
            db.collection("rooms").document(roomCode).collection("players")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FsRoomPlayer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<FsRoomPlayer> { it.score }.thenBy { it.totalVoteTimeMs })
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMySecret(roomCode: String): Flow<FsRoundSecret?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomCode/secrets/$uid"
        emitAll(
            db.collection("rooms").document(roomCode).collection("secrets").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(FsRoundSecret::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicAnswers(roomCode: String): Flow<List<FsPublicAnswer>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomCode/public_answers"
        emitAll(
            db.collection("rooms").document(roomCode).collection("public_answers")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FsPublicAnswer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.label }
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoomMessages(roomCode: String): Flow<List<FsRoomMessage>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomCode/messages"
        emitAll(
            db.collection("rooms").document(roomCode).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FsRoomMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.clientSentAtMs }
                }
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendRoomMessage(
        roomCode: String,
        hostId: String,
        memberUids: List<String>,
        senderNickname: String,
        text: String,
        roundNum: Int,
        phase: GamePhase
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val cleanText = text.trim().take(200)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }
        val cleanNick = senderNickname.trim().take(18).ifBlank { "Player" }
        val safeMembers = if (uid in memberUids) memberUids else (memberUids + uid).distinct().take(10)
        val safeRound = roundNum.coerceIn(0, 3).toLong()
        val path = "rooms/$roomCode/messages"
        return try {
            val msgRef = db.collection("rooms").document(roomCode).collection("messages").document()
            val payload = mapOf(
                "userId" to uid,
                "roomId" to roomCode,
                "hostId" to hostId,
                "memberUids" to safeMembers,
                "senderNickname" to cleanNick,
                "text" to cleanText,
                "roundNum" to safeRound,
                "phase" to phase.name,
                "clientSentAtMs" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createCloudRoom(nickname: String, openTable: Boolean): Result<String> {
        var path = "rooms"
        return try {
            val uid = requireUserId()
            val cleanNick = nickname.trim().take(18).ifBlank { "Host" }
            var roomCode = generateRoomCode()
            var attempts = 0
            while (attempts < 5) {
                val existing = db.collection("rooms").document(roomCode).get().await()
                if (!existing.exists()) break
                roomCode = generateRoomCode()
                attempts++
            }
            path = "rooms/$roomCode"

            val roomRef = db.collection("rooms").document(roomCode)
            val playerRef = roomRef.collection("players").document(uid)

            val roomPayload = mapOf(
                "roomCode" to roomCode,
                "hostId" to uid,
                "memberUids" to listOf(uid),
                "phase" to GamePhase.LOBBY.name,
                "phaseEndsAtMs" to 0L,
                "phaseDurationMs" to 0L,
                "roundNum" to 1L,
                "totalRounds" to GameScoringEngine.TOTAL_ROUNDS.toLong(),
                "openTable" to openTable,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(roomPayload).await()

            val playerPayload = mapOf(
                "userId" to uid,
                "roomId" to roomCode,
                "hostId" to uid,
                "memberUids" to listOf(uid),
                "nickname" to cleanNick,
                "hostFlag" to true,
                "score" to 0L,
                "totalVoteTimeMs" to 0L,
                "hasSubmittedAnswer" to false,
                "hasVoted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            playerRef.set(playerPayload).await()
            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinCloudRoom(roomCode: String, nickname: String): Result<String> {
        val code = roomCode.trim().uppercase()
        val path = "rooms/$code"
        return try {
            val uid = requireUserId()
            require(code.length == 4 && code.all { it.isLetterOrDigit() }) {
                "Room code must be 4 letters or digits."
            }
            val cleanNick = nickname.trim().take(18).ifBlank { "Player" }

            val roomRef = db.collection("rooms").document(code)
            val playerRef = roomRef.collection("players").document(uid)

            db.runTransaction { transaction ->
                val snap = transaction.get(roomRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Room $code not found.")
                }
                val room = snap.toObject(FsGameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Room $code could not be read.")

                if (uid !in room.memberUids) {
                    if (room.phase != GamePhase.LOBBY.name) {
                        throw IllegalStateException("Match already in progress in Room $code.")
                    }
                    if (room.memberUids.size >= 10) {
                        throw IllegalStateException("Room $code is full (max 10 players).")
                    }
                    val newMembers = room.memberUids + uid
                    transaction.update(
                        roomRef,
                        mapOf(
                            "memberUids" to newMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.set(
                        playerRef,
                        mapOf(
                            "userId" to uid,
                            "roomId" to code,
                            "hostId" to room.hostId,
                            "memberUids" to newMembers,
                            "nickname" to cleanNick,
                            "hostFlag" to false,
                            "score" to 0L,
                            "totalVoteTimeMs" to 0L,
                            "hasSubmittedAnswer" to false,
                            "hasVoted" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val pSnap = transaction.get(playerRef)
                    if (pSnap.exists()) {
                        transaction.update(
                            playerRef,
                            mapOf(
                                "nickname" to cleanNick,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        transaction.set(
                            playerRef,
                            mapOf(
                                "userId" to uid,
                                "roomId" to code,
                                "hostId" to room.hostId,
                                "memberUids" to room.memberUids,
                                "nickname" to cleanNick,
                                "hostFlag" to (room.hostId == uid),
                                "score" to 0L,
                                "totalVoteTimeMs" to 0L,
                                "hasSubmittedAnswer" to false,
                                "hasVoted" to false,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()

            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setCloudRoomOpenTable(roomCode: String, openTable: Boolean): Result<Unit> {
        val path = "rooms/$roomCode"
        return try {
            requireUserId()
            db.collection("rooms").document(roomCode).update(
                mapOf(
                    "openTable" to openTable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncPlayerMemberUids(roomCode: String, memberUids: List<String>): Result<Unit> {
        val path = "rooms/$roomCode/players"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(roomCode)
            val snaps = roomRef.collection("players")
                .whereArrayContains("memberUids", uid)
                .get().await()
            val msgSnaps = roomRef.collection("messages")
                .whereArrayContains("memberUids", uid)
                .get().await()
            val batch = db.batch()
            snaps.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            msgSnaps.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun startCloudRound(
        roomCode: String,
        roundNum: Int,
        prompt: PromptItem,
        players: List<FsRoomPlayer>,
        resetScores: Boolean
    ): Result<Map<String, PlayerRole>> {
        val path = "rooms/$roomCode"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(roomCode)
            val memberUids = players.map { it.userId }
            val roles = GameScoringEngine.assignRoles(memberUids)

            // Delete previous public answers
            val prevPublic = roomRef.collection("public_answers")
                .whereArrayContains("memberUids", uid)
                .get().await()
            if (!prevPublic.isEmpty) {
                val delBatch = db.batch()
                prevPublic.documents.forEach { delBatch.delete(it.reference) }
                delBatch.commit().await()
            }

            // Write isolated per-player secrets and reset player round flags
            val batch = db.batch()
            players.forEach { p ->
                val role = roles[p.userId] ?: PlayerRole.HUMAN
                val aiOpts = if (role == PlayerRole.GLITCH) prompt.aiLines.take(3) else emptyList()
                val secretRef = roomRef.collection("secrets").document(p.userId)
                val existingSecret = secretRef.get().await()
                if (existingSecret.exists()) {
                    batch.update(
                        secretRef,
                        mapOf(
                            "memberUids" to memberUids,
                            "roundNum" to roundNum.toLong(),
                            "role" to role.name,
                            "aiOptions" to aiOpts,
                            "rerollUsed" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        secretRef,
                        mapOf(
                            "userId" to p.userId,
                            "roomId" to roomCode,
                            "hostId" to uid,
                            "memberUids" to memberUids,
                            "roundNum" to roundNum.toLong(),
                            "role" to role.name,
                            "aiOptions" to aiOpts,
                            "rerollUsed" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val pRef = roomRef.collection("players").document(p.userId)
                val pUpdates = mutableMapOf<String, Any>(
                    "memberUids" to memberUids,
                    "hasSubmittedAnswer" to false,
                    "hasVoted" to false,
                    "lastRoundDelta" to 0L,
                    "lastRoundTags" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (resetScores) {
                    pUpdates["score"] = 0L
                    pUpdates["totalVoteTimeMs"] = 0L
                }
                batch.update(pRef, pUpdates)
            }

            val duration = GameScoringEngine.ROLE_REVEAL_DURATION_MS
            val endsAt = System.currentTimeMillis() + duration
            batch.update(
                roomRef,
                mapOf(
                    "phase" to GamePhase.ROLE_REVEAL.name,
                    "phaseEndsAtMs" to endsAt,
                    "phaseDurationMs" to duration,
                    "roundNum" to roundNum.toLong(),
                    "promptId" to prompt.id,
                    "promptCategory" to prompt.category,
                    "promptText" to prompt.prompt,
                    "glitchPlayerIds" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(roles)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun advanceRoomPhase(
        roomCode: String,
        nextPhase: GamePhase,
        durationMs: Long
    ): Result<Unit> {
        val path = "rooms/$roomCode"
        return try {
            requireUserId()
            val endsAt = if (durationMs > 0L) System.currentTimeMillis() + durationMs else 0L
            db.collection("rooms").document(roomCode).update(
                mapOf(
                    "phase" to nextPhase.name,
                    "phaseEndsAtMs" to endsAt,
                    "phaseDurationMs" to durationMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rerollGlitchSecret(roomCode: String, freshOptions: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "rooms/$roomCode/secrets/$uid"
        return try {
            db.collection("rooms").document(roomCode)
                .collection("secrets").document(uid)
                .update(
                    mapOf(
                        "aiOptions" to freshOptions,
                        "rerollUsed" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitCloudAnswer(
        roomCode: String,
        hostId: String,
        roundNum: Int,
        answerText: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "rooms/$roomCode/drafts/$uid"
        return try {
            val cleanText = answerText.trim().take(140).ifBlank { GameScoringEngine.HUMAN_TIMEOUT_FALLBACK }
            val roomRef = db.collection("rooms").document(roomCode)
            val draftRef = roomRef.collection("drafts").document(uid)
            val existing = draftRef.get().await()
            if (existing.exists()) {
                draftRef.update(
                    mapOf(
                        "roundNum" to roundNum.toLong(),
                        "answerText" to cleanText,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                draftRef.set(
                    mapOf(
                        "userId" to uid,
                        "roomId" to roomCode,
                        "hostId" to hostId,
                        "roundNum" to roundNum.toLong(),
                        "answerText" to cleanText,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val answerId = "ans_r${roundNum}_$uid"
            roomRef.collection("players").document(uid).update(
                mapOf(
                    "hasSubmittedAnswer" to true,
                    "mySubmittedAnswerId" to answerId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(answerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun hostPublishDebateAnswers(
        roomCode: String,
        roundNum: Int,
        players: List<FsRoomPlayer>,
        fallbackPrompt: PromptItem?,
        roles: Map<String, PlayerRole>
    ): Result<Unit> {
        val path = "rooms/$roomCode/public_answers"
        return try {
            val hostUid = requireUserId()
            val roomRef = db.collection("rooms").document(roomCode)
            val draftsSnap = roomRef.collection("drafts")
                .whereEqualTo("hostId", hostUid)
                .get().await()
            val draftsByUid = draftsSnap.toObjects(FsAnswerDraft::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.roundNum.toInt() == roundNum }
                .associateBy { it.userId }

            val stored = players.map { p ->
                val isGlitch = roles[p.userId] == PlayerRole.GLITCH
                val text = draftsByUid[p.userId]?.answerText
                    ?: if (isGlitch) {
                        fallbackPrompt?.aiLines?.firstOrNull() ?: "Optimal execution."
                    } else {
                        GameScoringEngine.HUMAN_TIMEOUT_FALLBACK
                    }
                GameScoringEngine.StoredSubmission(
                    answerId = "ans_r${roundNum}_${p.userId}",
                    authorId = p.userId,
                    text = text
                )
            }
            val labeled = GameScoringEngine.shuffleSubmissions(stored, Random.nextLong())
            val memberUids = players.map { it.userId }

            val batch = db.batch()
            labeled.forEach { (label, sub) ->
                val docRef = roomRef.collection("public_answers").document(sub.answerId)
                batch.set(
                    docRef,
                    mapOf(
                        "roomId" to roomCode,
                        "hostId" to hostUid,
                        "memberUids" to memberUids,
                        "roundNum" to roundNum.toLong(),
                        "label" to label,
                        "text" to sub.text,
                        "revealed" to false,
                        "voteCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val duration = GameScoringEngine.DEBATE_DURATION_MS
            val endsAt = System.currentTimeMillis() + duration
            batch.update(
                roomRef,
                mapOf(
                    "phase" to GamePhase.DEBATE.name,
                    "phaseEndsAtMs" to endsAt,
                    "phaseDurationMs" to duration,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun submitCloudVote(
        roomCode: String,
        hostId: String,
        roundNum: Int,
        targetAnswerId: String,
        elapsedMs: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "rooms/$roomCode/votes/$uid"
        return try {
            val roomRef = db.collection("rooms").document(roomCode)
            val voteRef = roomRef.collection("votes").document(uid)
            val existing = voteRef.get().await()
            if (existing.exists()) {
                voteRef.update(
                    mapOf(
                        "roundNum" to roundNum.toLong(),
                        "targetAnswerId" to targetAnswerId,
                        "elapsedMs" to elapsedMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                voteRef.set(
                    mapOf(
                        "userId" to uid,
                        "roomId" to roomCode,
                        "hostId" to hostId,
                        "roundNum" to roundNum.toLong(),
                        "targetAnswerId" to targetAnswerId,
                        "elapsedMs" to elapsedMs,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            roomRef.collection("players").document(uid).update(
                mapOf(
                    "hasVoted" to true,
                    "lockedVoteAnswerId" to targetAnswerId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun hostCommitReveal(
        roomCode: String,
        roundNum: Int,
        prompt: PromptItem,
        players: List<FsRoomPlayer>,
        publicAnswers: List<FsPublicAnswer>,
        roles: Map<String, PlayerRole>
    ): Result<Unit> {
        val path = "rooms/$roomCode"
        return try {
            val hostUid = requireUserId()
            val roomRef = db.collection("rooms").document(roomCode)

            val votesSnap = roomRef.collection("votes")
                .whereEqualTo("hostId", hostUid)
                .get().await()
            val voteMap = votesSnap.toObjects(FsVoteRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.roundNum.toInt() == roundNum }
                .associate { v ->
                    v.userId to GameScoringEngine.StoredVote(
                        voterId = v.userId,
                        targetAnswerId = v.targetAnswerId,
                        elapsedMs = v.elapsedMs
                    )
                }

            val labeled = publicAnswers.map { ans ->
                val authorId = ans.id.removePrefix("ans_r${roundNum}_")
                ans.label to GameScoringEngine.StoredSubmission(
                    answerId = ans.id,
                    authorId = authorId,
                    text = ans.text
                )
            }

            val domainPlayers = players.map {
                PlayerState(
                    id = it.userId,
                    nickname = it.nickname,
                    isHost = it.hostFlag,
                    score = it.score.toInt(),
                    totalVoteTimeMs = it.totalVoteTimeMs
                )
            }

            val nextRoundAt = System.currentTimeMillis() + GameScoringEngine.REVEAL_DURATION_MS
            val (revealData, updatedPlayers) = GameScoringEngine.resolveRound(
                roundNum = roundNum,
                totalRounds = GameScoringEngine.TOTAL_ROUNDS,
                prompt = prompt,
                players = domainPlayers,
                roles = roles,
                labeledSubmissions = labeled,
                votes = voteMap,
                nextRoundAtMs = nextRoundAt
            )

            val batch = db.batch()
            revealData.revealedSubmissions.forEach { rev ->
                val ansRef = roomRef.collection("public_answers").document(rev.answerId)
                batch.update(
                    ansRef,
                    mapOf(
                        "revealed" to true,
                        "authorId" to rev.authorId,
                        "authorNickname" to rev.authorNickname,
                        "role" to rev.role.name,
                        "voterNicknames" to rev.voterNicknames,
                        "voteCount" to rev.voteCount.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val deltaByPlayer = revealData.scoreDeltas.associateBy { it.playerId }
            updatedPlayers.forEach { p ->
                val pRef = roomRef.collection("players").document(p.id)
                val deltaEntry = deltaByPlayer[p.id]
                batch.update(
                    pRef,
                    mapOf(
                        "score" to p.score.toLong(),
                        "totalVoteTimeMs" to p.totalVoteTimeMs,
                        "lastRoundDelta" to (deltaEntry?.deltaPoints?.toLong()?.coerceIn(0L, 1000L) ?: 0L),
                        "lastRoundTags" to (deltaEntry?.reasonTags?.take(5) ?: emptyList()),
                        "hasSubmittedAnswer" to false,
                        "hasVoted" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                roomRef,
                mapOf(
                    "phase" to GamePhase.REVEAL.name,
                    "phaseEndsAtMs" to nextRoundAt,
                    "phaseDurationMs" to GameScoringEngine.REVEAL_DURATION_MS,
                    "humansWonRound" to revealData.humansWonRound,
                    "glitchPlayerIds" to revealData.glitchPlayerIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveCloudRoom(roomCode: String, isHost: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "rooms/$roomCode"
        return try {
            val roomRef = db.collection("rooms").document(roomCode)
            if (isHost) {
                roomRef.delete().await()
            } else {
                val snap = roomRef.get().await()
                val room = snap.toObject(FsGameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (room != null && uid in room.memberUids) {
                    roomRef.update(
                        mapOf(
                            "memberUids" to room.memberUids.filter { it != uid },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                roomRef.collection("players").document(uid).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(4) { append(chars[Random.nextInt(chars.length)]) }
        }
    }
}
