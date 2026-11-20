package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.GameAudioEngine
import com.example.audio.GameSfx
import com.example.audio.LobbyMusicPreset
import com.example.audio.LyriaModelTier
import com.example.audio.LyriaMusicRepository
import com.example.data.FirestoreGameRepository
import com.example.data.FsGameRoom
import com.example.data.FsPublicAnswer
import com.example.data.FsRoomPlayer
import com.example.data.FsRoundSecret
import com.example.data.PromptRepository
import com.example.data.SessionStore
import com.example.data.local.AudioSettingsEntity
import com.example.data.local.GeneratedTrackEntity
import com.example.data.local.LocalSqlRepository
import com.example.data.local.MatchHistoryEntity
import com.example.data.unwrapFirestoreStreamError
import com.example.engine.GameScoringEngine
import com.example.engine.PassAndPlayEngine
import com.example.model.AnonymousSubmission
import com.example.model.ArenaMediaUiState
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameUiState
import com.example.model.GlobalLeaderboardEntry
import com.example.model.OpenTableSummary
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import com.example.model.RevealedSubmission
import com.example.model.RoomChatMessage
import com.example.model.RoundRevealData
import com.example.model.SavedSession
import com.example.model.ScoreDeltaEntry
import com.example.network.LanClient
import com.example.network.LanClientEvent
import com.example.network.LanHostServer
import com.example.network.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

class ProxyWarsViewModel(
    private val promptRepository: PromptRepository,
    private val sessionStore: SessionStore,
    private val firestoreRepo: FirestoreGameRepository,
    private val sqlRepo: LocalSqlRepository,
    private val audioEngine: GameAudioEngine,
    private val lyriaRepo: LyriaMusicRepository,
    private val currentUserId: String,
    private val defaultDisplayName: String
) : ViewModel() {

    private val lanServer = LanHostServer(promptRepository)
    private val lanClient = LanClient()
    private val passAndPlayEngine = PassAndPlayEngine(promptRepository)

    private val _uiState = MutableStateFlow(
        GameUiState(
            myPlayerId = currentUserId,
            myNickname = defaultDisplayName
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _mediaState = MutableStateFlow(ArenaMediaUiState())
    val mediaState: StateFlow<ArenaMediaUiState> = _mediaState.asStateFlow()

    private var timerTickJob: Job? = null
    private var cloudRoomJobs: MutableList<Job> = mutableListOf()
    private var votePhaseStartedAtMs: Long = 0L

    // Cloud Host tracking
    private var cloudMatchPrompts: List<PromptItem> = emptyList()
    private var cloudHostRolesByPlayer: Map<String, PlayerRole> = emptyMap()
    private var latestFsRoom: FsGameRoom? = null
    private var latestFsPlayers: List<FsRoomPlayer> = emptyList()
    private var latestFsSecret: FsRoundSecret? = null
    private var latestFsPublicAnswers: List<FsPublicAnswer> = emptyList()
    private var scoresBeforeRound: Map<String, Int> = emptyMap()
    private var lastSyncedMemberSyncKey: String = ""
    private var hostTransitionLockPhaseKey: String = ""
    private var lastRecordedLeaderboardRoundKey: String = ""
    private var lastRecordedMatchEndKey: String = ""
    private var lastLocalMatchRecordedKey: String = ""
    private var lastPhaseSfxKey: String = ""
    private var lastRoundStartSfxKey: String = ""
    private var lastUrgentTickKey: String = ""
    private var lastKnownChatSize: Int = 0
    private var fastestVoteThisMatchMs: Long = 0L
    private var isAppForegrounded: Boolean = true

    init {
        audioEngine.warmUp()
        audioEngine.playSfx(GameSfx.OPENING_STING)
        observeLocalSqlData()
        ensureProfile()
        observeOpenTables()
        observeGlobalLeaderboard()
        observeSavedSession()
        observeLanEvents()
        syncAudioForCurrentState()
    }

    private fun observeLocalSqlData() {
        viewModelScope.launch {
            combine(
                sqlRepo.audioSettings,
                sqlRepo.generatedTracks,
                sqlRepo.matchHistory,
                sqlRepo.bestVoteSpeedMs
            ) { settings, tracks, history, bestSpeed ->
                val resolvedSettings = settings ?: AudioSettingsEntity()
                val preset = enumValues<LobbyMusicPreset>()
                    .firstOrNull { it.name == resolvedSettings.presetName }
                    ?: LobbyMusicPreset.NEON_LOUNGE
                val selectedTrack = resolvedSettings.selectedTrackId?.let { id ->
                    tracks.firstOrNull { it.id == id }
                }
                Triple(
                    Triple(resolvedSettings.musicEnabled, resolvedSettings.sfxEnabled, preset),
                    Pair(selectedTrack, tracks),
                    Pair(history, bestSpeed)
                )
            }.collect { (audioPair, trackPair, sqlPair) ->
                val (musicOn, sfxOn, preset) = audioPair
                val (selectedTrack, tracks) = trackPair
                val (history, bestSpeed) = sqlPair
                audioEngine.setSettings(
                    musicEnabled = musicOn,
                    sfxEnabled = sfxOn,
                    preset = preset,
                    customFilePath = selectedTrack?.filePath,
                    customMimeType = selectedTrack?.mimeType
                )
                _mediaState.update {
                    it.copy(
                        musicEnabled = musicOn,
                        sfxEnabled = sfxOn,
                        activePreset = preset,
                        selectedTrackId = selectedTrack?.id,
                        generatedTracks = tracks,
                        matchHistory = history,
                        bestVoteSpeedMs = bestSpeed
                    )
                }
                syncAudioForCurrentState()
            }
        }
    }

    private fun ensureProfile() {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile(defaultDisplayName)
        }
    }

    private fun observeOpenTables() {
        viewModelScope.launch {
            firestoreRepo.observeOpenTables()
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    Log.w(TAG, "Failed to observe open tables", root)
                }
                .collect { rooms ->
                    val summaries = rooms.map { r ->
                        OpenTableSummary(
                            roomCode = r.roomCode,
                            playerCount = r.memberUids.size,
                            hostId = r.hostId
                        )
                    }
                    _uiState.update { it.copy(openTables = summaries) }
                }
        }
    }

    private fun observeGlobalLeaderboard() {
        viewModelScope.launch {
            firestoreRepo.observeGlobalLeaderboard()
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    Log.w(TAG, "Failed to observe global leaderboard", root)
                }
                .collect { entries ->
                    val mapped = entries.map { e ->
                        GlobalLeaderboardEntry(
                            userId = e.userId,
                            nickname = e.nickname,
                            totalPoints = e.totalPoints.toInt(),
                            lastRoundDelta = e.lastRoundDelta.toInt(),
                            roundsPlayed = e.roundsPlayed.toInt(),
                            glitchesCaught = e.glitchesCaught.toInt(),
                            isCurrentUser = e.userId == currentUserId
                        )
                    }
                    _uiState.update { it.copy(globalLeaderboard = mapped) }
                }
        }
    }

    private fun observeSavedSession() {
        viewModelScope.launch {
            sessionStore.savedSessionFlow.collect { saved ->
                _uiState.update { it.copy(savedSession = saved) }
            }
        }
    }

    private fun observeLanEvents() {
        viewModelScope.launch {
            lanClient.events.collect { event ->
                when (event) {
                    is LanClientEvent.Joined -> {
                        val session = SavedSession(
                            roomCode = event.roomCode,
                            hostAddress = event.hostAddress,
                            playerId = event.playerId,
                            sessionToken = event.sessionToken,
                            nickname = event.nickname
                        )
                        sessionStore.saveSession(session)
                        _uiState.update { state ->
                            state.copy(
                                roomCode = event.roomCode,
                                myPlayerId = event.playerId,
                                myNickname = event.nickname,
                                isHost = event.isHost,
                                isConnecting = false,
                                errorMessage = null,
                                statusBanner = null
                            )
                        }
                        audioEngine.playSfx(GameSfx.ROOM_JOIN)
                    }

                    is LanClientEvent.RoomStateUpdated -> {
                        val previousPhase = _uiState.value.phase
                        if (event.phase == GamePhase.VOTE && previousPhase != GamePhase.VOTE) {
                            votePhaseStartedAtMs = System.currentTimeMillis()
                        }
                        _uiState.update { state ->
                            state.copy(
                                roomCode = event.roomCode,
                                phase = event.phase,
                                phaseEndsAtMs = event.phaseEndsAtMs,
                                phaseDurationMs = event.phaseDurationMs,
                                roundNum = event.roundNum,
                                totalRounds = event.totalRounds,
                                isHost = event.isHost,
                                players = event.players,
                                isConnecting = false,
                                statusBanner = null
                            )
                        }
                        syncAudioForCurrentState()
                        syncTimerTicker(event.phaseEndsAtMs)
                    }

                    is LanClientEvent.RoundStarted -> {
                        _uiState.update { state ->
                            state.copy(
                                roundNum = event.roundNum,
                                currentPrompt = event.prompt,
                                myRole = event.role,
                                myAiOptions = event.aiOptions,
                                rerollUsed = event.rerollUsed,
                                hasSubmittedCurrentRound = false,
                                mySubmittedAnswerId = "",
                                anonymousSubmissions = emptyList(),
                                selectedVoteAnswerId = null,
                                lockedVoteAnswerId = null,
                                revealData = null
                            )
                        }
                    }

                    is LanClientEvent.GlitchOptionsUpdated -> {
                        _uiState.update { state ->
                            state.copy(
                                myAiOptions = event.aiOptions,
                                rerollUsed = event.rerollUsed
                            )
                        }
                    }

                    is LanClientEvent.AnswerAck -> {
                        _uiState.update { state ->
                            if (event.success) {
                                state.copy(
                                    hasSubmittedCurrentRound = true,
                                    mySubmittedAnswerId = event.answerId,
                                    errorMessage = null
                                )
                            } else {
                                state.copy(errorMessage = event.message ?: "Could not submit answer.")
                            }
                        }
                    }

                    is LanClientEvent.SubmissionsReceived -> {
                        _uiState.update { state ->
                            state.copy(anonymousSubmissions = event.submissions)
                        }
                    }

                    is LanClientEvent.VoteAck -> {
                        _uiState.update { state ->
                            if (event.success) {
                                state.copy(
                                    lockedVoteAnswerId = event.targetAnswerId,
                                    errorMessage = null
                                )
                            } else {
                                state.copy(errorMessage = "Invalid vote selection.")
                            }
                        }
                    }

                    is LanClientEvent.RoundRevealed -> {
                        _uiState.update { state ->
                            state.copy(revealData = event.revealData)
                        }
                    }

                    is LanClientEvent.ChatMessageReceived -> {
                        val shouldPing = !event.message.isMine &&
                            _uiState.value.chatMessages.none { it.id == event.message.id }
                        _uiState.update { state ->
                            if (state.chatMessages.any { it.id == event.message.id }) {
                                state
                            } else {
                                val normalized = event.message.copy(
                                    isMine = event.message.senderId == state.myPlayerId
                                )
                                state.copy(chatMessages = state.chatMessages + normalized)
                            }
                        }
                        if (shouldPing) {
                            audioEngine.playSfx(GameSfx.CHAT_PING)
                        }
                    }

                    is LanClientEvent.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                isConnecting = false,
                                errorMessage = event.message
                            )
                        }
                    }

                    is LanClientEvent.Disconnected -> {
                        _uiState.update { state ->
                            state.copy(statusBanner = event.reason)
                        }
                    }
                }
            }
        }
    }

    // --- Cloud Firestore Room Flows ---

    fun createCloudRoom(nickname: String, openTable: Boolean) {
        val cleanNick = nickname.trim().take(18).ifBlank { defaultDisplayName }
        _uiState.update {
            it.copy(
                mode = GameMode.CLOUD_HOST,
                myNickname = cleanNick,
                isConnecting = true,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = firestoreRepo.createCloudRoom(cleanNick, openTable)
            result.onSuccess { code ->
                attachCloudRoomListeners(code, isHost = true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = err.localizedMessage ?: "Failed to create cloud room."
                    )
                }
            }
        }
    }

    fun joinCloudRoom(roomCode: String, nickname: String) {
        val cleanNick = nickname.trim().take(18).ifBlank { defaultDisplayName }
        val code = roomCode.trim().uppercase()
        if (code.length != 4) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 4-character room code.") }
            return
        }
        _uiState.update {
            it.copy(
                mode = GameMode.CLOUD_CLIENT,
                myNickname = cleanNick,
                isConnecting = true,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = firestoreRepo.joinCloudRoom(code, cleanNick)
            result.onSuccess { joinedCode ->
                attachCloudRoomListeners(joinedCode, isHost = false)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = err.localizedMessage ?: "Could not join Room $code."
                    )
                }
            }
        }
    }

    private fun attachCloudRoomListeners(roomCode: String, isHost: Boolean) {
        cancelCloudRoomListeners()
        hostTransitionLockPhaseKey = ""
        lastSyncedMemberSyncKey = ""
        lastRecordedLeaderboardRoundKey = ""
        lastRecordedMatchEndKey = ""
        lastLocalMatchRecordedKey = ""
        lastPhaseSfxKey = ""
        lastRoundStartSfxKey = ""
        lastUrgentTickKey = ""
        lastKnownChatSize = 0
        fastestVoteThisMatchMs = 0L
        audioEngine.playSfx(GameSfx.ROOM_JOIN)
        latestFsRoom = null
        latestFsPlayers = emptyList()
        latestFsSecret = null
        latestFsPublicAnswers = emptyList()

        _uiState.update {
            it.copy(
                roomCode = roomCode,
                hostAddress = "Cloud Sync",
                myPlayerId = currentUserId,
                isHost = isHost,
                phase = GamePhase.LOBBY,
                chatMessages = emptyList(),
                isSendingChat = false,
                isConnecting = false,
                errorMessage = null
            )
        }

        cloudRoomJobs += viewModelScope.launch {
            firestoreRepo.observeRoom(roomCode)
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    _uiState.update { it.copy(errorMessage = root.localizedMessage ?: "Room connection error") }
                }
                .collect { room ->
                    if (room == null) {
                        if (_uiState.value.phase != GamePhase.JOIN_HUB) {
                            cancelCloudRoomListeners()
                            _uiState.update {
                                GameUiState(
                                    myPlayerId = currentUserId,
                                    myNickname = it.myNickname,
                                    openTables = it.openTables,
                                    errorMessage = "Host closed Room $roomCode."
                                )
                            }
                        }
                        return@collect
                    }
                    latestFsRoom = room
                    reconcileCloudState()
                }
        }

        cloudRoomJobs += viewModelScope.launch {
            firestoreRepo.observeRoomPlayers(roomCode)
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    Log.w(TAG, "Error observing players", root)
                }
                .collect { players ->
                    latestFsPlayers = players
                    reconcileCloudState()
                }
        }

        cloudRoomJobs += viewModelScope.launch {
            firestoreRepo.observeMySecret(roomCode)
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    Log.w(TAG, "Error observing secret", root)
                }
                .collect { secret ->
                    latestFsSecret = secret
                    reconcileCloudState()
                }
        }

        cloudRoomJobs += viewModelScope.launch {
            firestoreRepo.observePublicAnswers(roomCode)
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    Log.w(TAG, "Error observing public answers", root)
                }
                .collect { answers ->
                    latestFsPublicAnswers = answers
                    reconcileCloudState()
                }
        }

        cloudRoomJobs += viewModelScope.launch {
            firestoreRepo.observeRoomMessages(roomCode)
                .catch { error ->
                    val root = unwrapFirestoreStreamError(error)
                    Log.w(TAG, "Error observing room messages", root)
                }
                .collect { fsMessages ->
                    val mapped = fsMessages.map { msg ->
                        val msgPhase = enumValues<GamePhase>().firstOrNull { it.name == msg.phase } ?: GamePhase.LOBBY
                        RoomChatMessage(
                            id = msg.id,
                            senderId = msg.userId,
                            senderNickname = msg.senderNickname,
                            text = msg.text,
                            roundNum = msg.roundNum.toInt(),
                            phase = msgPhase,
                            sentAtMs = msg.clientSentAtMs,
                            isMine = msg.userId == currentUserId
                        )
                    }
                    val previousSize = lastKnownChatSize
                    lastKnownChatSize = mapped.size
                    _uiState.update { it.copy(chatMessages = mapped) }
                    if (mapped.size > previousSize && previousSize >= 0 && mapped.lastOrNull()?.isMine == false) {
                        audioEngine.playSfx(GameSfx.CHAT_PING)
                    }
                }
        }
    }

    private fun reconcileCloudState() {
        val room = latestFsRoom ?: return
        val phase = enumValues<GamePhase>().firstOrNull { it.name == room.phase } ?: GamePhase.LOBBY
        val previousPhase = _uiState.value.phase
        val roundNum = room.roundNum.toInt()
        val isHost = room.hostId == currentUserId

        if (phase == GamePhase.VOTE && previousPhase != GamePhase.VOTE) {
            votePhaseStartedAtMs = System.currentTimeMillis()
        }

        if (phase == GamePhase.ROLE_REVEAL && previousPhase != GamePhase.ROLE_REVEAL) {
            scoresBeforeRound = latestFsPlayers.associate { it.userId to it.score.toInt() }
        }

        // Host keeps memberUids synced across player documents in LOBBY
        if (isHost && phase == GamePhase.LOBBY) {
            val hasOutOfSyncPlayer = latestFsPlayers.any { it.memberUids != room.memberUids }
            val syncKey = "${room.memberUids.joinToString(",")}|${latestFsPlayers.size}|$hasOutOfSyncPlayer"
            if (hasOutOfSyncPlayer && syncKey != lastSyncedMemberSyncKey) {
                lastSyncedMemberSyncKey = syncKey
                viewModelScope.launch {
                    firestoreRepo.syncPlayerMemberUids(room.roomCode, room.memberUids)
                }
            }
        }

        val domainPlayers = latestFsPlayers.map { p ->
            PlayerState(
                id = p.userId,
                nickname = p.nickname,
                isHost = p.hostFlag,
                isConnected = true,
                score = p.score.toInt(),
                totalVoteTimeMs = p.totalVoteTimeMs,
                hasSubmittedAnswer = p.hasSubmittedAnswer,
                hasVoted = p.hasVoted
            )
        }

        val mePlayer = latestFsPlayers.firstOrNull { it.userId == currentUserId }
        val secret = latestFsSecret?.takeIf { it.roundNum.toInt() == roundNum }
        val myRole = enumValues<PlayerRole>().firstOrNull { it.name == secret?.role } ?: PlayerRole.HUMAN

        val promptItem = if (!room.promptText.isNullOrBlank()) {
            PromptItem(
                id = room.promptId ?: "",
                category = room.promptCategory ?: "Campus",
                prompt = room.promptText,
                aiLines = emptyList()
            )
        } else null

        val roundPublicAnswers = latestFsPublicAnswers.filter { it.roundNum.toInt() == roundNum }
        val myAnswerId = "ans_r${roundNum}_$currentUserId"
        val anonSubs = roundPublicAnswers.map { ans ->
            AnonymousSubmission(
                answerId = ans.id,
                label = ans.label,
                text = ans.text,
                isOwnSubmission = ans.id == myAnswerId
            )
        }

        val revealData = if (phase == GamePhase.REVEAL || phase == GamePhase.MATCH_END) {
            val revealedSubs = roundPublicAnswers.map { ans ->
                RevealedSubmission(
                    answerId = ans.id,
                    label = ans.label,
                    text = ans.text,
                    authorId = ans.authorId ?: "",
                    authorNickname = ans.authorNickname ?: "Player",
                    role = enumValues<PlayerRole>().firstOrNull { it.name == ans.role } ?: PlayerRole.HUMAN,
                    voterNicknames = ans.voterNicknames,
                    voteCount = ans.voteCount.toInt()
                )
            }
            val fsPlayerById = latestFsPlayers.associateBy { it.userId }
            val scoreDeltas = domainPlayers.map { p ->
                val fsPlayer = fsPlayerById[p.id]
                val prev = scoresBeforeRound[p.id] ?: p.score
                val fallbackDelta = (p.score - prev).coerceAtLeast(0)
                val persistedDelta = fsPlayer?.lastRoundDelta?.toInt() ?: 0
                val delta = if (persistedDelta > 0) persistedDelta else fallbackDelta
                val pRole = if (p.id in room.glitchPlayerIds) PlayerRole.GLITCH else PlayerRole.HUMAN
                val tags = fsPlayer?.lastRoundTags?.takeIf { it.isNotEmpty() }
                    ?: if (delta > 0) listOf("+$delta pts") else emptyList()
                ScoreDeltaEntry(
                    playerId = p.id,
                    nickname = p.nickname,
                    role = pRole,
                    deltaPoints = delta,
                    totalScore = p.score,
                    totalVoteTimeMs = p.totalVoteTimeMs,
                    reasonTags = tags
                )
            }.sortedWith(compareByDescending<ScoreDeltaEntry> { it.totalScore }.thenBy { it.totalVoteTimeMs })
            RoundRevealData(
                roundNum = roundNum,
                totalRounds = room.totalRounds.toInt(),
                promptText = room.promptText ?: "",
                category = room.promptCategory ?: "Campus",
                revealedSubmissions = revealedSubs,
                glitchPlayerIds = room.glitchPlayerIds,
                scoreDeltas = scoreDeltas,
                humansWonRound = room.humansWonRound ?: false,
                nextRoundAtMs = room.phaseEndsAtMs
            )
        } else null

        if (phase == GamePhase.REVEAL && revealData != null && mePlayer != null) {
            val roundKey = "${room.roomCode}_r$roundNum"
            if (lastRecordedLeaderboardRoundKey != roundKey) {
                lastRecordedLeaderboardRoundKey = roundKey
                val myDelta = revealData.scoreDeltas.firstOrNull { it.playerId == currentUserId }?.deltaPoints
                    ?: mePlayer.lastRoundDelta.toInt()
                val votedTargetId = mePlayer.lockedVoteAnswerId
                val caughtGlitch = myRole == PlayerRole.HUMAN &&
                    votedTargetId != null &&
                    revealData.revealedSubmissions.any { it.answerId == votedTargetId && it.role == PlayerRole.GLITCH }
                viewModelScope.launch {
                    firestoreRepo.recordRoundToLeaderboard(
                        nickname = mePlayer.nickname,
                        roundDelta = myDelta,
                        caughtGlitch = caughtGlitch
                    )
                }
            }
        }

        if (phase == GamePhase.MATCH_END && mePlayer != null) {
            val endKey = "${room.roomCode}_end"
            if (lastRecordedMatchEndKey != endKey) {
                lastRecordedMatchEndKey = endKey
                viewModelScope.launch {
                    firestoreRepo.recordMatchCompleted()
                }
            }
        }

        _uiState.update { state ->
            state.copy(
                mode = if (isHost) GameMode.CLOUD_HOST else GameMode.CLOUD_CLIENT,
                roomCode = room.roomCode,
                phase = phase,
                isHost = isHost,
                isOpenTable = room.openTable,
                players = domainPlayers,
                roundNum = roundNum,
                totalRounds = room.totalRounds.toInt(),
                phaseEndsAtMs = room.phaseEndsAtMs,
                phaseDurationMs = room.phaseDurationMs,
                currentPrompt = promptItem,
                myRole = myRole,
                myAiOptions = secret?.aiOptions ?: emptyList(),
                rerollUsed = secret?.rerollUsed ?: false,
                hasSubmittedCurrentRound = mePlayer?.hasSubmittedAnswer ?: false,
                mySubmittedAnswerId = mePlayer?.mySubmittedAnswerId ?: "",
                anonymousSubmissions = anonSubs,
                lockedVoteAnswerId = mePlayer?.lockedVoteAnswerId,
                revealData = revealData ?: state.revealData?.takeIf { phase == GamePhase.MATCH_END }
            )
        }
        syncAudioForCurrentState()
        syncTimerTicker(room.phaseEndsAtMs)

        // Host fast-forward checks when 100% of players submitted or voted
        if (isHost && domainPlayers.isNotEmpty()) {
            if (phase == GamePhase.PROMPT && latestFsPlayers.all { it.hasSubmittedAnswer }) {
                triggerCloudHostTransition(room, GamePhase.DEBATE)
            } else if (phase == GamePhase.VOTE && latestFsPlayers.all { it.hasVoted }) {
                triggerCloudHostTransition(room, GamePhase.REVEAL)
            }
        }
    }

    private fun triggerCloudHostTransition(room: FsGameRoom, targetPhase: GamePhase) {
        val lockKey = "${room.roundNum}_${room.phase}->${targetPhase.name}"
        if (hostTransitionLockPhaseKey == lockKey) return
        hostTransitionLockPhaseKey = lockKey

        val roundNum = room.roundNum.toInt()
        val prompt = cloudMatchPrompts.getOrNull(roundNum - 1) ?: _uiState.value.currentPrompt

        viewModelScope.launch {
            when (targetPhase) {
                GamePhase.PROMPT -> {
                    firestoreRepo.advanceRoomPhase(room.roomCode, GamePhase.PROMPT, GameScoringEngine.PROMPT_DURATION_MS)
                }
                GamePhase.DEBATE -> {
                    firestoreRepo.hostPublishDebateAnswers(
                        roomCode = room.roomCode,
                        roundNum = roundNum,
                        players = latestFsPlayers,
                        fallbackPrompt = prompt,
                        roles = cloudHostRolesByPlayer
                    )
                }
                GamePhase.VOTE -> {
                    firestoreRepo.advanceRoomPhase(room.roomCode, GamePhase.VOTE, GameScoringEngine.VOTE_DURATION_MS)
                }
                GamePhase.REVEAL -> {
                    if (prompt != null) {
                        firestoreRepo.hostCommitReveal(
                            roomCode = room.roomCode,
                            roundNum = roundNum,
                            prompt = prompt,
                            players = latestFsPlayers,
                            publicAnswers = latestFsPublicAnswers.filter { it.roundNum.toInt() == roundNum },
                            roles = cloudHostRolesByPlayer
                        )
                    }
                }
                GamePhase.ROLE_REVEAL -> {
                    val nextRound = roundNum + 1
                    val nextPrompt = cloudMatchPrompts.getOrElse(nextRound - 1) {
                        promptRepository.loadAllPrompts().random()
                    }
                    firestoreRepo.startCloudRound(
                        roomCode = room.roomCode,
                        roundNum = nextRound,
                        prompt = nextPrompt,
                        players = latestFsPlayers,
                        resetScores = false
                    ).onSuccess { roles ->
                        cloudHostRolesByPlayer = roles
                    }
                }
                GamePhase.MATCH_END -> {
                    firestoreRepo.advanceRoomPhase(room.roomCode, GamePhase.MATCH_END, 0L)
                }
                else -> {}
            }
        }
    }

    private fun cancelCloudRoomListeners() {
        cloudRoomJobs.forEach { it.cancel() }
        cloudRoomJobs.clear()
    }

    // --- Shared Actions Across Cloud, LAN & Pass-and-Play ---

    fun parseDeepLink(uri: Uri?): Pair<String, String>? {
        if (uri == null) return null
        if (uri.scheme == "proxywars" && uri.host == "join") {
            val code = uri.getQueryParameter("code").orEmpty().uppercase().take(4)
            val host = uri.getQueryParameter("host").orEmpty()
            if (code.isNotBlank()) {
                return code to host
            }
        }
        return null
    }

    fun createLanRoom(nickname: String) {
        val cleanNick = nickname.trim().take(18).ifBlank { defaultDisplayName }
        _uiState.update {
            it.copy(
                mode = GameMode.LAN_HOST,
                chatMessages = emptyList(),
                isSendingChat = false,
                isConnecting = true,
                errorMessage = null,
                statusBanner = null
            )
        }
        viewModelScope.launch {
            val port = lanServer.startServer()
            val ip = NetworkUtils.getLocalIpv4Address()
            val displayHost = "$ip:$port"
            _uiState.update {
                it.copy(
                    hostAddress = displayHost,
                    roomCode = lanServer.roomCode
                )
            }
            lanClient.connectAndJoin(
                hostAddress = "127.0.0.1:$port",
                roomCode = lanServer.roomCode,
                nickname = cleanNick,
                isHost = true
            )
        }
    }

    fun joinLanRoom(roomCode: String, hostAddress: String, nickname: String) {
        val parsedDeepLink = if (hostAddress.startsWith("proxywars://") || roomCode.startsWith("proxywars://")) {
            val raw = if (hostAddress.startsWith("proxywars://")) hostAddress else roomCode
            parseDeepLink(Uri.parse(raw))
        } else null

        val finalCode = (parsedDeepLink?.first ?: roomCode).trim().uppercase()
        val finalHost = (parsedDeepLink?.second ?: hostAddress).trim()
        val cleanNick = nickname.trim().take(18).ifBlank { defaultDisplayName }

        if (finalCode.length != 4) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 4-character room code.") }
            return
        }
        if (finalHost.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter the Host IP:Port shown on the Host's Lobby screen.") }
            return
        }

        _uiState.update {
            it.copy(
                mode = GameMode.LAN_CLIENT,
                hostAddress = finalHost,
                chatMessages = emptyList(),
                isSendingChat = false,
                isConnecting = true,
                errorMessage = null,
                statusBanner = null
            )
        }
        lanClient.connectAndJoin(
            hostAddress = finalHost,
            roomCode = finalCode,
            nickname = cleanNick,
            isHost = false
        )
    }

    fun reconnectLastSession() {
        val saved = _uiState.value.savedSession ?: return
        _uiState.update {
            it.copy(
                mode = GameMode.LAN_CLIENT,
                hostAddress = saved.hostAddress,
                roomCode = saved.roomCode,
                isConnecting = true,
                errorMessage = null,
                statusBanner = "Reconnecting to Room ${saved.roomCode}..."
            )
        }
        lanClient.reconnect(saved)
    }

    fun startPassAndPlay(nicknames: List<String>) {
        if (nicknames.size < 4) {
            _uiState.update { it.copy(errorMessage = "Add at least 4 players to start Pass & Play.") }
            return
        }
        lastLocalMatchRecordedKey = ""
        lastPhaseSfxKey = ""
        lastRoundStartSfxKey = ""
        fastestVoteThisMatchMs = 0L
        audioEngine.playSfx(GameSfx.ROOM_JOIN)
        val snapshot = passAndPlayEngine.startMatch(nicknames)
        _uiState.update {
            it.copy(
                mode = GameMode.PASS_AND_PLAY,
                roomCode = "LOCAL",
                hostAddress = "Single Device",
                isHost = true,
                chatMessages = emptyList(),
                isSendingChat = false,
                errorMessage = null,
                statusBanner = null
            )
        }
        applyPassAndPlaySnapshot(snapshot)
    }

    fun toggleCloudOpenTable(openTable: Boolean) {
        val state = _uiState.value
        if (state.mode != GameMode.CLOUD_HOST || state.roomCode.isBlank()) return
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        viewModelScope.launch {
            firestoreRepo.setCloudRoomOpenTable(state.roomCode, openTable)
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Could not update room visibility.")
                    }
                }
        }
    }

    fun startTableMatch() {
        val state = _uiState.value
        if (state.isConnecting) return
        if (state.mode == GameMode.CLOUD_HOST) {
            if (latestFsPlayers.size < 4) {
                _uiState.update { it.copy(errorMessage = "Need at least 4 players to start.") }
                return
            }
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            cloudMatchPrompts = promptRepository.pickMatchPrompts(GameScoringEngine.TOTAL_ROUNDS)
            scoresBeforeRound = latestFsPlayers.associate { it.userId to 0 }
            viewModelScope.launch {
                firestoreRepo.startCloudRound(
                    roomCode = state.roomCode,
                    roundNum = 1,
                    prompt = cloudMatchPrompts.first(),
                    players = latestFsPlayers,
                    resetScores = true
                ).onSuccess { roles ->
                    cloudHostRolesByPlayer = roles
                    _uiState.update { it.copy(isConnecting = false) }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            errorMessage = err.localizedMessage ?: "Failed to start match."
                        )
                    }
                }
            }
        } else {
            lanClient.sendGameStart()
        }
    }

    fun hostSkipPhase() {
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        val state = _uiState.value
        when (state.mode) {
            GameMode.PASS_AND_PLAY -> {
                val snap = when (state.phase) {
                    GamePhase.DEBATE -> passAndPlayEngine.startVotingTurns()
                    GamePhase.REVEAL -> passAndPlayEngine.advanceAfterReveal()
                    else -> null
                }
                if (snap != null) applyPassAndPlaySnapshot(snap)
            }
            GameMode.CLOUD_HOST -> {
                val room = latestFsRoom ?: return
                val target = when (state.phase) {
                    GamePhase.ROLE_REVEAL -> GamePhase.PROMPT
                    GamePhase.DEBATE -> GamePhase.VOTE
                    GamePhase.REVEAL -> if (state.roundNum < state.totalRounds) GamePhase.ROLE_REVEAL else GamePhase.MATCH_END
                    else -> return
                }
                triggerCloudHostTransition(room, target)
            }
            else -> lanClient.sendHostSkip()
        }
    }

    fun unlockPassAndPlayTurn() {
        if (_uiState.value.mode != GameMode.PASS_AND_PLAY) return
        val snap = passAndPlayEngine.unlockCurrentTurn()
        applyPassAndPlaySnapshot(snap)
    }

    fun rerollGlitchOptions() {
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        val state = _uiState.value
        when (state.mode) {
            GameMode.PASS_AND_PLAY -> {
                val snap = passAndPlayEngine.rerollGlitchOptions()
                applyPassAndPlaySnapshot(snap)
            }
            GameMode.CLOUD_HOST, GameMode.CLOUD_CLIENT -> {
                val prompt = state.currentPrompt ?: return
                if (state.rerollUsed) return
                val fresh = promptRepository.buildRerollAiLines(prompt, state.myAiOptions)
                viewModelScope.launch {
                    firestoreRepo.rerollGlitchSecret(state.roomCode, fresh)
                }
            }
            else -> lanClient.sendGlitchReroll()
        }
    }

    fun submitAnswer(answerText: String) {
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        val state = _uiState.value
        when (state.mode) {
            GameMode.PASS_AND_PLAY -> {
                val snap = passAndPlayEngine.submitCurrentAnswer(answerText)
                applyPassAndPlaySnapshot(snap)
            }
            GameMode.CLOUD_HOST, GameMode.CLOUD_CLIENT -> {
                val hostId = latestFsRoom?.hostId ?: return
                viewModelScope.launch {
                    firestoreRepo.submitCloudAnswer(
                        roomCode = state.roomCode,
                        hostId = hostId,
                        roundNum = state.roundNum,
                        answerText = answerText
                    ).onFailure { err ->
                        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Failed to submit.") }
                    }
                }
            }
            else -> lanClient.sendAnswer(answerText)
        }
    }

    fun selectVoteCandidate(answerId: String) {
        val state = _uiState.value
        if (state.lockedVoteAnswerId != null) return
        val candidate = state.anonymousSubmissions.firstOrNull { it.answerId == answerId } ?: return
        if (candidate.isOwnSubmission) {
            _uiState.update { it.copy(errorMessage = "You cannot vote for your own response.") }
            return
        }
        audioEngine.playSfx(GameSfx.VOTE_SELECT)
        _uiState.update { it.copy(selectedVoteAnswerId = answerId, errorMessage = null) }
    }

    fun lockSelectedVote() {
        val state = _uiState.value
        val targetId = state.selectedVoteAnswerId ?: return
        audioEngine.playSfx(GameSfx.VOTE_LOCK)
        val elapsedMs = (System.currentTimeMillis() - votePhaseStartedAtMs)
            .coerceIn(0L, GameScoringEngine.VOTE_DURATION_MS)
        if (elapsedMs > 0L && (fastestVoteThisMatchMs == 0L || elapsedMs < fastestVoteThisMatchMs)) {
            fastestVoteThisMatchMs = elapsedMs
        }
        when (state.mode) {
            GameMode.PASS_AND_PLAY -> {
                val snap = passAndPlayEngine.submitCurrentVote(targetId)
                applyPassAndPlaySnapshot(snap)
            }
            GameMode.CLOUD_HOST, GameMode.CLOUD_CLIENT -> {
                val hostId = latestFsRoom?.hostId ?: return
                viewModelScope.launch {
                    firestoreRepo.submitCloudVote(
                        roomCode = state.roomCode,
                        hostId = hostId,
                        roundNum = state.roundNum,
                        targetAnswerId = targetId,
                        elapsedMs = elapsedMs
                    ).onFailure { err ->
                        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Vote failed.") }
                    }
                }
            }
            else -> lanClient.sendVote(targetId, elapsedMs)
        }
    }

    fun playAgain() {
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        lastLocalMatchRecordedKey = ""
        lastPhaseSfxKey = ""
        lastRoundStartSfxKey = ""
        fastestVoteThisMatchMs = 0L
        val state = _uiState.value
        when (state.mode) {
            GameMode.PASS_AND_PLAY -> {
                val snap = passAndPlayEngine.restartMatch()
                applyPassAndPlaySnapshot(snap)
            }
            GameMode.CLOUD_HOST -> {
                viewModelScope.launch {
                    firestoreRepo.advanceRoomPhase(state.roomCode, GamePhase.LOBBY, 0L)
                }
            }
            else -> lanClient.sendGameRestart()
        }
    }

    fun sendRoomChatMessage(rawText: String) {
        val cleanText = rawText.trim().take(200)
        if (cleanText.isEmpty()) return
        val state = _uiState.value
        if (state.phase == GamePhase.JOIN_HUB || state.roomCode.isBlank()) return
        audioEngine.playSfx(GameSfx.CHAT_SEND)

        when (state.mode) {
            GameMode.CLOUD_HOST, GameMode.CLOUD_CLIENT -> {
                val room = latestFsRoom ?: return
                if (state.isSendingChat) return
                val senderNick = state.players.firstOrNull { it.id == currentUserId }?.nickname
                    ?: state.myNickname.ifBlank { defaultDisplayName }
                val roundNum = if (state.phase == GamePhase.LOBBY) 0 else state.roundNum
                _uiState.update { it.copy(isSendingChat = true, errorMessage = null) }
                viewModelScope.launch {
                    firestoreRepo.sendRoomMessage(
                        roomCode = state.roomCode,
                        hostId = room.hostId,
                        memberUids = room.memberUids,
                        senderNickname = senderNick,
                        text = cleanText,
                        roundNum = roundNum,
                        phase = state.phase
                    ).onFailure { err ->
                        _uiState.update {
                            it.copy(errorMessage = err.localizedMessage ?: "Failed to send chat message.")
                        }
                    }
                    _uiState.update { it.copy(isSendingChat = false) }
                }
            }
            GameMode.LAN_HOST, GameMode.LAN_CLIENT -> {
                lanClient.sendChatMessage(cleanText)
            }
            GameMode.PASS_AND_PLAY -> {
                val turn = state.passAndPlayTurn
                val senderId = turn?.currentPlayerId?.takeIf { it.isNotBlank() } ?: currentUserId
                val senderNick = turn?.currentPlayerNickname?.takeIf { it.isNotBlank() }
                    ?: state.myNickname.ifBlank { "Table" }
                val msg = RoomChatMessage(
                    id = "local_${System.currentTimeMillis()}_${state.chatMessages.size}",
                    senderId = senderId,
                    senderNickname = senderNick,
                    text = cleanText,
                    roundNum = if (state.phase == GamePhase.LOBBY) 0 else state.roundNum,
                    phase = state.phase,
                    sentAtMs = System.currentTimeMillis(),
                    isMine = true
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }
            }
        }
    }

    fun leaveTable() {
        val state = _uiState.value
        timerTickJob?.cancel()
        cancelCloudRoomListeners()
        lanClient.disconnect()
        if (state.mode == GameMode.LAN_HOST) {
            lanServer.stopServer()
        }
        if (state.mode == GameMode.CLOUD_HOST || state.mode == GameMode.CLOUD_CLIENT) {
            val code = state.roomCode
            val isHost = state.isHost
            if (code.isNotBlank()) {
                viewModelScope.launch {
                    firestoreRepo.leaveCloudRoom(code, isHost)
                }
            }
        }
        viewModelScope.launch {
            sessionStore.clearSession()
        }
        lastLocalMatchRecordedKey = ""
        lastPhaseSfxKey = ""
        lastRoundStartSfxKey = ""
        lastUrgentTickKey = ""
        lastKnownChatSize = 0
        fastestVoteThisMatchMs = 0L
        _uiState.update { current ->
            GameUiState(
                myPlayerId = currentUserId,
                myNickname = current.myNickname,
                openTables = current.openTables,
                globalLeaderboard = current.globalLeaderboard,
                savedSession = null
            )
        }
        syncAudioForCurrentState()
    }

    // --- 3D Opening, Sound Studio, Lyria AI & Room SQLite Controls ---

    fun dismissGameOpening() {
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        _mediaState.update { it.copy(showGameOpening = false) }
        syncAudioForCurrentState()
    }

    fun replayGameOpening() {
        audioEngine.playSfx(GameSfx.OPENING_STING)
        _mediaState.update { it.copy(showGameOpening = true) }
        syncAudioForCurrentState()
    }

    fun previewMoveSfx() {
        audioEngine.playSfx(GameSfx.ROUND_START)
    }

    fun onAppForegrounded(foreground: Boolean) {
        isAppForegrounded = foreground
        syncAudioForCurrentState()
    }

    fun toggleMusic(enabled: Boolean) {
        val m = _mediaState.value
        viewModelScope.launch {
            sqlRepo.saveAudioSettings(
                AudioSettingsEntity(
                    musicEnabled = enabled,
                    sfxEnabled = m.sfxEnabled,
                    presetName = m.activePreset.name,
                    selectedTrackId = m.selectedTrackId
                )
            )
        }
    }

    fun toggleSfx(enabled: Boolean) {
        val m = _mediaState.value
        if (enabled) audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        viewModelScope.launch {
            sqlRepo.saveAudioSettings(
                AudioSettingsEntity(
                    musicEnabled = m.musicEnabled,
                    sfxEnabled = enabled,
                    presetName = m.activePreset.name,
                    selectedTrackId = m.selectedTrackId
                )
            )
        }
    }

    fun selectMusicPreset(preset: LobbyMusicPreset) {
        val m = _mediaState.value
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        viewModelScope.launch {
            sqlRepo.saveAudioSettings(
                AudioSettingsEntity(
                    musicEnabled = m.musicEnabled,
                    sfxEnabled = m.sfxEnabled,
                    presetName = preset.name,
                    selectedTrackId = null
                )
            )
        }
    }

    fun selectGeneratedTrack(track: GeneratedTrackEntity) {
        val m = _mediaState.value
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        viewModelScope.launch {
            sqlRepo.saveAudioSettings(
                AudioSettingsEntity(
                    musicEnabled = m.musicEnabled,
                    sfxEnabled = m.sfxEnabled,
                    presetName = m.activePreset.name,
                    selectedTrackId = track.id
                )
            )
        }
    }

    fun deleteGeneratedTrack(trackId: Long) {
        val m = _mediaState.value
        val track = m.generatedTracks.firstOrNull { it.id == trackId }
        viewModelScope.launch {
            sqlRepo.deleteTrack(trackId)
            if (track != null) {
                withContext(Dispatchers.IO) {
                    try {
                        File(track.filePath).delete()
                    } catch (_: Exception) {
                    }
                }
            }
            if (m.selectedTrackId == trackId) {
                sqlRepo.saveAudioSettings(
                    AudioSettingsEntity(
                        musicEnabled = m.musicEnabled,
                        sfxEnabled = m.sfxEnabled,
                        presetName = m.activePreset.name,
                        selectedTrackId = null
                    )
                )
            }
        }
    }

    fun generateLyriaTrack(prompt: String, tier: LyriaModelTier) {
        if (_mediaState.value.isGeneratingTrack) return
        audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
        _mediaState.update { it.copy(isGeneratingTrack = true, musicGenError = null) }
        viewModelScope.launch {
            lyriaRepo.generateLobbyTrack(prompt, tier)
                .onSuccess { entity ->
                    audioEngine.playSfx(GameSfx.REVEAL_WIN)
                    val m = _mediaState.value
                    sqlRepo.saveAudioSettings(
                        AudioSettingsEntity(
                            musicEnabled = true,
                            sfxEnabled = m.sfxEnabled,
                            presetName = m.activePreset.name,
                            selectedTrackId = entity.id
                        )
                    )
                    _mediaState.update { it.copy(isGeneratingTrack = false, musicGenError = null) }
                }
                .onFailure { err ->
                    _mediaState.update {
                        it.copy(
                            isGeneratingTrack = false,
                            musicGenError = err.localizedMessage ?: "Failed to generate AI music track."
                        )
                    }
                }
        }
    }

    fun clearMusicGenError() {
        _mediaState.update { it.copy(musicGenError = null) }
    }

    fun suggestAiPromptReply() {
        val state = _uiState.value
        val promptText = state.currentPrompt?.prompt ?: return
        if (_mediaState.value.isSuggestingReply) return
        _mediaState.update { it.copy(isSuggestingReply = true, aiReplyError = null) }
        viewModelScope.launch {
            lyriaRepo.suggestGlitchOrHumanReply(
                promptText = promptText,
                asGlitch = state.myRole == PlayerRole.GLITCH
            ).onSuccess { line ->
                audioEngine.playSfx(GameSfx.MOVE_CONFIRM)
                _mediaState.update {
                    it.copy(
                        isSuggestingReply = false,
                        aiReplySuggestion = line,
                        aiReplyError = null
                    )
                }
            }.onFailure { err ->
                _mediaState.update {
                    it.copy(
                        isSuggestingReply = false,
                        aiReplyError = err.localizedMessage ?: "AI reply suggestion failed."
                    )
                }
            }
        }
    }

    fun clearAiReplySuggestion() {
        _mediaState.update { it.copy(aiReplySuggestion = null, aiReplyError = null) }
    }

    private fun syncAudioForCurrentState() {
        val state = _uiState.value
        val showOpening = _mediaState.value.showGameOpening && state.phase == GamePhase.JOIN_HUB

        if (!isAppForegrounded) {
            audioEngine.setLobbyMusicActive(false, ducked = false)
            return
        }

        when {
            showOpening || state.phase == GamePhase.JOIN_HUB || state.phase == GamePhase.LOBBY ->
                audioEngine.setLobbyMusicActive(true, ducked = false)
            state.phase == GamePhase.ROLE_REVEAL || state.phase == GamePhase.REVEAL || state.phase == GamePhase.MATCH_END ->
                audioEngine.setLobbyMusicActive(true, ducked = true)
            else ->
                audioEngine.setLobbyMusicActive(false, ducked = false)
        }

        if (state.phase == GamePhase.ROLE_REVEAL || state.phase == GamePhase.PROMPT) {
            val roundKey = "${state.roomCode}_round_${state.roundNum}"
            if (roundKey != lastRoundStartSfxKey) {
                lastRoundStartSfxKey = roundKey
                audioEngine.playSfx(GameSfx.ROUND_START)
                _mediaState.update { it.copy(aiReplySuggestion = null, aiReplyError = null) }
            }
        }

        val phaseKey = "${state.roomCode}_${state.roundNum}_${state.phase.name}"
        if (phaseKey != lastPhaseSfxKey && state.phase != GamePhase.JOIN_HUB && state.phase != GamePhase.LOBBY) {
            lastPhaseSfxKey = phaseKey
            when (state.phase) {
                GamePhase.REVEAL -> {
                    val won = state.revealData?.humansWonRound == true
                    audioEngine.playSfx(if (won) GameSfx.REVEAL_WIN else GameSfx.REVEAL_GLITCH)
                }
                GamePhase.MATCH_END -> recordCompletedMatchToSql(state)
                else -> {}
            }
        }
    }

    private fun recordCompletedMatchToSql(state: GameUiState) {
        if (state.players.isEmpty()) return
        val matchKey = "${state.roomCode}_${state.roundNum}_${state.mode.name}"
        if (lastLocalMatchRecordedKey == matchKey) return
        lastLocalMatchRecordedKey = matchKey

        val sorted = state.players.sortedWith(
            compareByDescending<PlayerState> { it.score }.thenBy { it.totalVoteTimeMs }
        )
        val myIndex = sorted.indexOfFirst { it.id == state.myPlayerId }.let { if (it >= 0) it else 0 }
        val targetPlayer = sorted.getOrNull(myIndex) ?: return

        viewModelScope.launch {
            sqlRepo.recordMatch(
                MatchHistoryEntity(
                    roomCode = state.roomCode.ifBlank { "LOCAL" },
                    modeName = state.mode.name.replace('_', ' '),
                    finalScore = targetPlayer.score,
                    rank = myIndex + 1,
                    totalPlayers = sorted.size,
                    wonMatch = myIndex == 0,
                    fastestVoteMs = fastestVoteThisMatchMs
                )
            )
        }
    }

    private fun applyPassAndPlaySnapshot(snap: PassAndPlayEngine.Snapshot) {
        val endsAt = if (snap.keepTimer) {
            _uiState.value.phaseEndsAtMs
        } else if (snap.phaseDurationMs > 0L) {
            System.currentTimeMillis() + snap.phaseDurationMs
        } else {
            0L
        }
        val duration = if (snap.keepTimer) _uiState.value.phaseDurationMs else snap.phaseDurationMs

        _uiState.update { state ->
            state.copy(
                phase = snap.phase,
                players = snap.players,
                roundNum = snap.roundNum,
                totalRounds = snap.totalRounds,
                currentPrompt = snap.prompt,
                phaseEndsAtMs = endsAt,
                phaseDurationMs = duration,
                passAndPlayTurn = snap.turnState,
                myRole = snap.turnState?.currentPlayerRole ?: PlayerRole.HUMAN,
                myAiOptions = snap.turnState?.currentAiOptions ?: emptyList(),
                rerollUsed = snap.turnState?.rerollUsed ?: false,
                hasSubmittedCurrentRound = false,
                anonymousSubmissions = snap.anonymousSubmissions,
                selectedVoteAnswerId = null,
                lockedVoteAnswerId = null,
                revealData = snap.revealData ?: state.revealData?.takeIf { snap.phase == GamePhase.MATCH_END },
                errorMessage = null
            )
        }
        syncAudioForCurrentState()
        syncTimerTicker(endsAt)
    }

    private fun syncTimerTicker(phaseEndsAtMs: Long) {
        timerTickJob?.cancel()
        if (phaseEndsAtMs <= 0L) {
            _uiState.update { it.copy(remainingSeconds = 0) }
            return
        }
        timerTickJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val diffMs = (phaseEndsAtMs - now).coerceAtLeast(0L)
                val secs = ceil(diffMs / 1000.0).toInt()
                _uiState.update { it.copy(remainingSeconds = secs) }

                val currentState = _uiState.value
                if ((currentState.phase == GamePhase.PROMPT || currentState.phase == GamePhase.VOTE) && secs in 1..5) {
                    val tickKey = "${currentState.roomCode}_${currentState.roundNum}_${currentState.phase.name}_$secs"
                    if (tickKey != lastUrgentTickKey) {
                        lastUrgentTickKey = tickKey
                        audioEngine.playSfx(GameSfx.URGENT_TICK)
                    }
                }

                if (diffMs <= 0L) {
                    val state = _uiState.value
                    if (state.mode == GameMode.PASS_AND_PLAY) {
                        handlePassAndPlayTimeout()
                    } else if (state.mode == GameMode.CLOUD_HOST) {
                        handleCloudHostTimeout()
                    }
                    break
                }
                delay(200L)
            }
        }
    }

    private fun handlePassAndPlayTimeout() {
        val state = _uiState.value
        val snap = when (state.phase) {
            GamePhase.PROMPT -> passAndPlayEngine.submitCurrentAnswer("")
            GamePhase.DEBATE -> passAndPlayEngine.startVotingTurns()
            GamePhase.VOTE -> passAndPlayEngine.submitCurrentVote(null)
            GamePhase.REVEAL -> passAndPlayEngine.advanceAfterReveal()
            else -> null
        }
        if (snap != null) {
            applyPassAndPlaySnapshot(snap)
        }
    }

    private fun handleCloudHostTimeout() {
        val room = latestFsRoom ?: return
        val state = _uiState.value
        val target = when (state.phase) {
            GamePhase.ROLE_REVEAL -> GamePhase.PROMPT
            GamePhase.PROMPT -> GamePhase.DEBATE
            GamePhase.DEBATE -> GamePhase.VOTE
            GamePhase.VOTE -> GamePhase.REVEAL
            GamePhase.REVEAL -> if (state.roundNum < state.totalRounds) GamePhase.ROLE_REVEAL else GamePhase.MATCH_END
            else -> return
        }
        triggerCloudHostTransition(room, target)
    }

    override fun onCleared() {
        super.onCleared()
        timerTickJob?.cancel()
        cancelCloudRoomListeners()
        lanClient.destroy()
        lanServer.destroy()
        audioEngine.release()
    }

    private companion object {
        const val TAG = "ProxyWarsVM"
    }
}
