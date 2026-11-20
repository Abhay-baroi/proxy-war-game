package com.example.network

import com.example.data.PromptRepository
import com.example.engine.GameScoringEngine
import com.example.model.GamePhase
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import com.example.model.RoundRevealData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.random.Random

class LanHostServer(
    private val promptRepository: PromptRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var timerJob: Job? = null

    var roomCode: String = ""
        private set
    var port: Int = 8765
        private set

    private data class ClientRecord(
        val playerId: String,
        val sessionToken: String,
        var nickname: String,
        val isHost: Boolean,
        var isConnected: Boolean = true,
        var score: Int = 0,
        var totalVoteTimeMs: Long = 0L,
        var hasSubmittedAnswer: Boolean = false,
        var hasVoted: Boolean = false,
        var writer: BufferedWriter? = null
    )

    private val clients = mutableMapOf<String, ClientRecord>()
    private val socketToPlayerId = mutableMapOf<Socket, String>()

    private var currentPhase: GamePhase = GamePhase.LOBBY
    private var phaseEndsAtMs: Long = 0L
    private var phaseDurationMs: Long = 0L
    private var currentRoundNum: Int = 1
    private var matchPrompts: List<PromptItem> = emptyList()
    private var currentPrompt: PromptItem? = null

    private var roundRoles: Map<String, PlayerRole> = emptyMap()
    private var glitchOptionsByPlayer: MutableMap<String, List<String>> = mutableMapOf()
    private var glitchRerollUsed: MutableSet<String> = mutableSetOf()
    private var submissionsByPlayer: MutableMap<String, GameScoringEngine.StoredSubmission> = mutableMapOf()
    private var labeledSubmissions: List<Pair<String, GameScoringEngine.StoredSubmission>> = emptyList()
    private var votesByPlayer: MutableMap<String, GameScoringEngine.StoredVote> = mutableMapOf()
    private var lastRevealData: RoundRevealData? = null

    fun startServer(): Int {
        stopServer()
        roomCode = generateRoomCode()
        val socket = try {
            ServerSocket(8765)
        } catch (_: Exception) {
            ServerSocket(0)
        }
        serverSocket = socket
        port = socket.localPort
        currentPhase = GamePhase.LOBBY
        acceptJob = scope.launch {
            while (!socket.isClosed) {
                try {
                    val clientSocket = socket.accept()
                    launch { handleConnection(clientSocket) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return port
    }

    fun stopServer() {
        timerJob?.cancel()
        acceptJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        synchronized(socketToPlayerId) {
            socketToPlayerId.keys.forEach { sock ->
                try {
                    sock.close()
                } catch (_: Exception) {
                }
            }
            socketToPlayerId.clear()
        }
        clients.clear()
    }

    fun destroy() {
        stopServer()
        scope.cancel()
    }

    private suspend fun handleConnection(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val msg = JSONObject(line)
                val type = msg.optString("type")
                when (type) {
                    "room:join" -> handleJoin(socket, writer, msg)
                    "room:reconnect" -> handleReconnect(socket, writer, msg)
                    "game:start" -> handleGameStart(socket)
                    "phase:skip" -> handleHostSkip(socket)
                    "answer:submit" -> handleAnswerSubmit(socket, writer, msg)
                    "glitch:reroll" -> handleGlitchReroll(socket, writer)
                    "vote:cast" -> handleVoteCast(socket, writer, msg)
                    "chat:send" -> handleChatSend(socket, msg)
                    "game:restart" -> handleGameRestart(socket)
                }
            }
        } catch (_: Exception) {
        } finally {
            stateMutex.withLock {
                val pId = synchronized(socketToPlayerId) { socketToPlayerId.remove(socket) }
                if (pId != null) {
                    clients[pId]?.let { rec ->
                        if (rec.writer == writer) {
                            rec.isConnected = false
                            rec.writer = null
                            broadcastRoomStateLocked()
                        }
                    }
                }
            }
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun handleJoin(socket: Socket, writer: BufferedWriter, msg: JSONObject) {
        stateMutex.withLock {
            val requestedCode = msg.optString("roomCode").trim().uppercase()
            val nickname = msg.optString("nickname").trim().take(18).ifBlank { "Player" }
            val isHostClaim = msg.optBoolean("isHost", false)

            if (requestedCode != roomCode) {
                sendLine(writer, JSONObject().apply {
                    put("type", "error")
                    put("message", "Room $requestedCode not found on this host (active: $roomCode).")
                })
                return
            }
            if (currentPhase != GamePhase.LOBBY) {
                sendLine(writer, JSONObject().apply {
                    put("type", "error")
                    put("message", "Match already in progress in Room $roomCode.")
                })
                return
            }
            if (clients.size >= 10) {
                sendLine(writer, JSONObject().apply {
                    put("type", "error")
                    put("message", "Room $roomCode is full (max 10 players).")
                })
                return
            }

            val playerId = "usr_${UUID.randomUUID().toString().take(8)}"
            val sessionToken = "tok_${UUID.randomUUID().toString().replace("-", "")}"
            val isHost = isHostClaim && clients.values.none { it.isHost } || clients.isEmpty()

            val uniqueName = makeUniqueNickname(nickname)
            val record = ClientRecord(
                playerId = playerId,
                sessionToken = sessionToken,
                nickname = uniqueName,
                isHost = isHost,
                isConnected = true,
                writer = writer
            )
            clients[playerId] = record
            synchronized(socketToPlayerId) {
                socketToPlayerId[socket] = playerId
            }

            sendLine(writer, JSONObject().apply {
                put("type", "room:joined")
                put("roomCode", roomCode)
                put("playerId", playerId)
                put("sessionToken", sessionToken)
                put("nickname", uniqueName)
                put("isHost", isHost)
            })
            broadcastRoomStateLocked()
        }
    }

    private suspend fun handleReconnect(socket: Socket, writer: BufferedWriter, msg: JSONObject) {
        stateMutex.withLock {
            val requestedCode = msg.optString("roomCode").trim().uppercase()
            val playerId = msg.optString("playerId")
            val token = msg.optString("sessionToken")

            val record = clients[playerId]
            if (requestedCode != roomCode || record == null || record.sessionToken != token) {
                sendLine(writer, JSONObject().apply {
                    put("type", "error")
                    put("message", "Session expired or room closed.")
                })
                return
            }

            record.writer = writer
            record.isConnected = true
            synchronized(socketToPlayerId) {
                socketToPlayerId[socket] = playerId
            }

            sendLine(writer, JSONObject().apply {
                put("type", "room:joined")
                put("roomCode", roomCode)
                put("playerId", record.playerId)
                put("sessionToken", record.sessionToken)
                put("nickname", record.nickname)
                put("isHost", record.isHost)
            })
            sendRoomStateTo(record)
            rehydratePhaseTo(record)
            broadcastRoomStateLocked()
        }
    }

    private suspend fun handleGameStart(socket: Socket) {
        stateMutex.withLock {
            val callerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            val caller = clients[callerId] ?: return
            if (!caller.isHost) return
            if (clients.size < 4) {
                caller.writer?.let {
                    sendLine(it, JSONObject().apply {
                        put("type", "error")
                        put("message", "Need at least 4 players to start.")
                    })
                }
                return
            }

            matchPrompts = promptRepository.pickMatchPrompts(GameScoringEngine.TOTAL_ROUNDS)
            currentRoundNum = 1
            clients.values.forEach {
                it.score = 0
                it.totalVoteTimeMs = 0L
                it.hasSubmittedAnswer = false
                it.hasVoted = false
            }
            startRoundLocked()
        }
    }

    private suspend fun handleHostSkip(socket: Socket) {
        stateMutex.withLock {
            val callerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            val caller = clients[callerId] ?: return
            if (!caller.isHost) return
            when (currentPhase) {
                GamePhase.ROLE_REVEAL -> transitionToPromptLocked()
                GamePhase.DEBATE -> transitionToVoteLocked()
                GamePhase.REVEAL -> advanceAfterRevealLocked()
                else -> {}
            }
        }
    }

    private suspend fun handleGameRestart(socket: Socket) {
        stateMutex.withLock {
            val callerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            val caller = clients[callerId] ?: return
            if (!caller.isHost) return

            timerJob?.cancel()
            currentPhase = GamePhase.LOBBY
            phaseEndsAtMs = 0L
            phaseDurationMs = 0L
            currentRoundNum = 1
            lastRevealData = null
            clients.values.forEach {
                it.score = 0
                it.totalVoteTimeMs = 0L
                it.hasSubmittedAnswer = false
                it.hasVoted = false
            }
            broadcastRoomStateLocked()
        }
    }

    private suspend fun handleAnswerSubmit(socket: Socket, writer: BufferedWriter, msg: JSONObject) {
        stateMutex.withLock {
            val playerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            val record = clients[playerId] ?: return
            if (currentPhase != GamePhase.PROMPT || record.hasSubmittedAnswer) {
                sendLine(writer, JSONObject().apply {
                    put("type", "answer:ack")
                    put("status", "ERROR")
                    put("message", "Submission window closed.")
                })
                return
            }

            val rawText = msg.optString("answerText").trim()
            val cleanText = rawText.take(140).ifBlank {
                if (roundRoles[playerId] == PlayerRole.GLITCH) {
                    glitchOptionsByPlayer[playerId]?.firstOrNull() ?: "Optimal execution."
                } else {
                    GameScoringEngine.HUMAN_TIMEOUT_FALLBACK
                }
            }

            val answerId = "ans_${currentRoundNum}_$playerId"
            submissionsByPlayer[playerId] = GameScoringEngine.StoredSubmission(
                answerId = answerId,
                authorId = playerId,
                text = cleanText
            )
            record.hasSubmittedAnswer = true

            sendLine(writer, JSONObject().apply {
                put("type", "answer:ack")
                put("status", "SUCCESS")
                put("answerId", answerId)
            })
            broadcastRoomStateLocked()

            val activePlayers = clients.values.filter { it.isConnected }
            if (activePlayers.isNotEmpty() && activePlayers.all { it.hasSubmittedAnswer }) {
                transitionToDebateLocked()
            }
        }
    }

    private suspend fun handleGlitchReroll(socket: Socket, writer: BufferedWriter) {
        stateMutex.withLock {
            val playerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            if (currentPhase != GamePhase.PROMPT) return
            if (roundRoles[playerId] != PlayerRole.GLITCH) return
            if (playerId in glitchRerollUsed) return
            val prompt = currentPrompt ?: return

            val currentOpts = glitchOptionsByPlayer[playerId].orEmpty()
            val fresh = promptRepository.buildRerollAiLines(prompt, currentOpts)
            glitchOptionsByPlayer[playerId] = fresh
            glitchRerollUsed.add(playerId)

            sendLine(writer, JSONObject().apply {
                put("type", "glitch:options")
                put("aiOptions", JSONArray(fresh))
                put("rerollUsed", true)
            })
        }
    }

    private suspend fun handleVoteCast(socket: Socket, writer: BufferedWriter, msg: JSONObject) {
        stateMutex.withLock {
            val playerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            val record = clients[playerId] ?: return
            if (currentPhase != GamePhase.VOTE || record.hasVoted) {
                sendLine(writer, JSONObject().apply {
                    put("type", "vote:ack")
                    put("status", "ERROR")
                })
                return
            }

            val targetAnswerId = msg.optString("targetAnswerId")
            val targetSub = labeledSubmissions.firstOrNull { it.second.answerId == targetAnswerId }?.second
            if (targetSub == null || targetSub.authorId == playerId) {
                sendLine(writer, JSONObject().apply {
                    put("type", "vote:ack")
                    put("status", "ERROR")
                })
                return
            }

            val elapsedMs = msg.optLong("elapsedMs", 0L).coerceIn(0L, GameScoringEngine.VOTE_DURATION_MS)
            votesByPlayer[playerId] = GameScoringEngine.StoredVote(
                voterId = playerId,
                targetAnswerId = targetAnswerId,
                elapsedMs = elapsedMs
            )
            record.hasVoted = true

            sendLine(writer, JSONObject().apply {
                put("type", "vote:ack")
                put("status", "SUCCESS")
                put("targetAnswerId", targetAnswerId)
            })
            broadcastRoomStateLocked()

            val activePlayers = clients.values.filter { it.isConnected }
            if (activePlayers.isNotEmpty() && activePlayers.all { it.hasVoted }) {
                transitionToRevealLocked()
            }
        }
    }

    private suspend fun handleChatSend(socket: Socket, msg: JSONObject) {
        stateMutex.withLock {
            val playerId = synchronized(socketToPlayerId) { socketToPlayerId[socket] } ?: return
            val record = clients[playerId] ?: return
            val cleanText = msg.optString("text").trim().take(200)
            if (cleanText.isEmpty()) return

            val messageObj = JSONObject().apply {
                put("type", "chat:message")
                put("id", "lan_${UUID.randomUUID().toString().take(12)}")
                put("senderId", record.playerId)
                put("senderNickname", record.nickname)
                put("text", cleanText)
                put("roundNum", if (currentPhase == GamePhase.LOBBY) 0 else currentRoundNum)
                put("phase", currentPhase.name)
                put("sentAtMs", System.currentTimeMillis())
            }
            clients.values.forEach { target ->
                target.writer?.let { sendLine(it, messageObj) }
            }
        }
    }

    private fun startRoundLocked() {
        val prompt = matchPrompts.getOrElse(currentRoundNum - 1) {
            promptRepository.loadAllPrompts().random()
        }
        currentPrompt = prompt
        roundRoles = GameScoringEngine.assignRoles(clients.keys.toList())
        glitchOptionsByPlayer.clear()
        glitchRerollUsed.clear()
        submissionsByPlayer.clear()
        labeledSubmissions = emptyList()
        votesByPlayer.clear()
        lastRevealData = null

        clients.values.forEach { rec ->
            rec.hasSubmittedAnswer = false
            rec.hasVoted = false
            if (roundRoles[rec.playerId] == PlayerRole.GLITCH) {
                glitchOptionsByPlayer[rec.playerId] = prompt.aiLines.take(3)
            }
        }

        currentPhase = GamePhase.ROLE_REVEAL
        phaseDurationMs = GameScoringEngine.ROLE_REVEAL_DURATION_MS
        phaseEndsAtMs = System.currentTimeMillis() + phaseDurationMs

        broadcastRoomStateLocked()
        broadcastRoundStartedLocked()

        schedulePhaseTimeout(phaseDurationMs, GamePhase.ROLE_REVEAL) {
            transitionToPromptLocked()
        }
    }

    private fun transitionToPromptLocked() {
        if (currentPhase != GamePhase.ROLE_REVEAL) return
        currentPhase = GamePhase.PROMPT
        phaseDurationMs = GameScoringEngine.PROMPT_DURATION_MS
        phaseEndsAtMs = System.currentTimeMillis() + phaseDurationMs

        broadcastRoomStateLocked()
        schedulePhaseTimeout(phaseDurationMs, GamePhase.PROMPT) {
            transitionToDebateLocked()
        }
    }

    private fun transitionToDebateLocked() {
        if (currentPhase != GamePhase.PROMPT) return
        // Auto-submit fallback for any missing players
        clients.values.forEach { rec ->
            if (rec.playerId !in submissionsByPlayer) {
                val role = roundRoles[rec.playerId] ?: PlayerRole.HUMAN
                val fallbackText = if (role == PlayerRole.GLITCH) {
                    glitchOptionsByPlayer[rec.playerId]?.firstOrNull()
                        ?: currentPrompt?.aiLines?.firstOrNull()
                        ?: "System latency detected."
                } else {
                    GameScoringEngine.HUMAN_TIMEOUT_FALLBACK
                }
                submissionsByPlayer[rec.playerId] = GameScoringEngine.StoredSubmission(
                    answerId = "ans_${currentRoundNum}_${rec.playerId}",
                    authorId = rec.playerId,
                    text = fallbackText
                )
                rec.hasSubmittedAnswer = true
            }
        }

        labeledSubmissions = GameScoringEngine.shuffleSubmissions(
            submissionsByPlayer.values.toList(),
            Random.nextLong()
        )

        currentPhase = GamePhase.DEBATE
        phaseDurationMs = GameScoringEngine.DEBATE_DURATION_MS
        phaseEndsAtMs = System.currentTimeMillis() + phaseDurationMs

        broadcastRoomStateLocked()
        broadcastSubmissionsLocked()

        schedulePhaseTimeout(phaseDurationMs, GamePhase.DEBATE) {
            transitionToVoteLocked()
        }
    }

    private fun transitionToVoteLocked() {
        if (currentPhase != GamePhase.DEBATE) return
        currentPhase = GamePhase.VOTE
        phaseDurationMs = GameScoringEngine.VOTE_DURATION_MS
        phaseEndsAtMs = System.currentTimeMillis() + phaseDurationMs

        broadcastRoomStateLocked()
        broadcastSubmissionsLocked()

        schedulePhaseTimeout(phaseDurationMs, GamePhase.VOTE) {
            transitionToRevealLocked()
        }
    }

    private fun transitionToRevealLocked() {
        if (currentPhase != GamePhase.VOTE) return
        val prompt = currentPrompt ?: return
        val playerStates = clients.values.map { it.toPlayerState() }
        val nextRoundAt = System.currentTimeMillis() + GameScoringEngine.REVEAL_DURATION_MS

        val (revealData, updatedPlayers) = GameScoringEngine.resolveRound(
            roundNum = currentRoundNum,
            totalRounds = GameScoringEngine.TOTAL_ROUNDS,
            prompt = prompt,
            players = playerStates,
            roles = roundRoles,
            labeledSubmissions = labeledSubmissions,
            votes = votesByPlayer,
            nextRoundAtMs = nextRoundAt
        )

        updatedPlayers.forEach { p ->
            clients[p.id]?.let { rec ->
                rec.score = p.score
                rec.totalVoteTimeMs = p.totalVoteTimeMs
                rec.hasSubmittedAnswer = false
                rec.hasVoted = false
            }
        }

        lastRevealData = revealData
        currentPhase = GamePhase.REVEAL
        phaseDurationMs = GameScoringEngine.REVEAL_DURATION_MS
        phaseEndsAtMs = nextRoundAt

        broadcastRoomStateLocked()
        broadcastRevealLocked(revealData)

        schedulePhaseTimeout(phaseDurationMs, GamePhase.REVEAL) {
            advanceAfterRevealLocked()
        }
    }

    private fun advanceAfterRevealLocked() {
        if (currentPhase != GamePhase.REVEAL) return
        if (currentRoundNum < GameScoringEngine.TOTAL_ROUNDS) {
            currentRoundNum += 1
            startRoundLocked()
        } else {
            currentPhase = GamePhase.MATCH_END
            phaseDurationMs = 0L
            phaseEndsAtMs = 0L
            broadcastRoomStateLocked()
        }
    }

    private fun schedulePhaseTimeout(
        delayMs: Long,
        expectedPhase: GamePhase,
        onTimeout: suspend () -> Unit
    ) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(delayMs)
            stateMutex.withLock {
                if (currentPhase == expectedPhase) {
                    onTimeout()
                }
            }
        }
    }

    private fun broadcastRoomStateLocked() {
        clients.values.forEach { sendRoomStateTo(it) }
    }

    private fun sendRoomStateTo(target: ClientRecord) {
        val writer = target.writer ?: return
        val playersArr = JSONArray()
        clients.values
            .sortedWith(compareByDescending<ClientRecord> { it.score }.thenBy { it.totalVoteTimeMs })
            .forEach { p ->
                playersArr.put(JSONObject().apply {
                    put("id", p.playerId)
                    put("nickname", p.nickname)
                    put("isHost", p.isHost)
                    put("isConnected", p.isConnected)
                    put("score", p.score)
                    put("totalVoteTimeMs", p.totalVoteTimeMs)
                    put("hasSubmittedAnswer", p.hasSubmittedAnswer)
                    put("hasVoted", p.hasVoted)
                })
            }

        sendLine(writer, JSONObject().apply {
            put("type", "room:state")
            put("roomCode", roomCode)
            put("phase", currentPhase.name)
            put("phaseEndsAt", phaseEndsAtMs)
            put("phaseDurationMs", phaseDurationMs)
            put("roundNum", currentRoundNum)
            put("totalRounds", GameScoringEngine.TOTAL_ROUNDS)
            put("isHost", target.isHost)
            put("players", playersArr)
        })
    }

    private fun broadcastRoundStartedLocked() {
        clients.values.forEach { sendRoundStartedTo(it) }
    }

    private fun sendRoundStartedTo(target: ClientRecord) {
        val writer = target.writer ?: return
        val prompt = currentPrompt ?: return
        val role = roundRoles[target.playerId] ?: PlayerRole.HUMAN
        val payload = JSONObject().apply {
            put("type", "round:started")
            put("roundNum", currentRoundNum)
            put("promptId", prompt.id)
            put("category", prompt.category)
            put("prompt", prompt.prompt)
            put("role", role.name)
            // Strict payload isolation: aiOptions sent ONLY to Glitch sockets
            if (role == PlayerRole.GLITCH) {
                put("aiOptions", JSONArray(glitchOptionsByPlayer[target.playerId].orEmpty()))
                put("rerollUsed", target.playerId in glitchRerollUsed)
            } else {
                put("aiOptions", JSONObject.NULL)
                put("rerollUsed", false)
            }
        }
        sendLine(writer, payload)
    }

    private fun broadcastSubmissionsLocked() {
        clients.values.forEach { sendSubmissionsTo(it) }
    }

    private fun sendSubmissionsTo(target: ClientRecord) {
        val writer = target.writer ?: return
        val arr = JSONArray()
        labeledSubmissions.forEach { (label, sub) ->
            arr.put(JSONObject().apply {
                put("answerId", sub.answerId)
                put("label", label)
                put("text", sub.text)
                put("isOwnSubmission", sub.authorId == target.playerId)
            })
        }
        sendLine(writer, JSONObject().apply {
            put("type", "submissions:list")
            put("submissions", arr)
        })
    }

    private fun broadcastRevealLocked(reveal: RoundRevealData) {
        clients.values.forEach { sendRevealTo(it, reveal) }
    }

    private fun sendRevealTo(target: ClientRecord, reveal: RoundRevealData) {
        val writer = target.writer ?: return
        val subsArr = JSONArray()
        reveal.revealedSubmissions.forEach { sub ->
            subsArr.put(JSONObject().apply {
                put("answerId", sub.answerId)
                put("label", sub.label)
                put("text", sub.text)
                put("authorId", sub.authorId)
                put("authorNickname", sub.authorNickname)
                put("role", sub.role.name)
                put("voterNicknames", JSONArray(sub.voterNicknames))
                put("voteCount", sub.voteCount)
            })
        }

        val deltasArr = JSONArray()
        reveal.scoreDeltas.forEach { d ->
            deltasArr.put(JSONObject().apply {
                put("playerId", d.playerId)
                put("nickname", d.nickname)
                put("role", d.role.name)
                put("deltaPoints", d.deltaPoints)
                put("totalScore", d.totalScore)
                put("totalVoteTimeMs", d.totalVoteTimeMs)
                put("reasonTags", JSONArray(d.reasonTags))
            })
        }

        sendLine(writer, JSONObject().apply {
            put("type", "round:reveal")
            put("roundNum", reveal.roundNum)
            put("totalRounds", reveal.totalRounds)
            put("promptText", reveal.promptText)
            put("category", reveal.category)
            put("revealedSubmissions", subsArr)
            put("glitchPlayerIds", JSONArray(reveal.glitchPlayerIds))
            put("scoreDeltas", deltasArr)
            put("humansWonRound", reveal.humansWonRound)
            put("nextRoundAt", reveal.nextRoundAtMs)
        })
    }

    private fun rehydratePhaseTo(target: ClientRecord) {
        when (currentPhase) {
            GamePhase.ROLE_REVEAL, GamePhase.PROMPT -> sendRoundStartedTo(target)
            GamePhase.DEBATE, GamePhase.VOTE -> {
                sendRoundStartedTo(target)
                sendSubmissionsTo(target)
            }
            GamePhase.REVEAL, GamePhase.MATCH_END -> {
                lastRevealData?.let { sendRevealTo(target, it) }
            }
            else -> {}
        }
    }

    private fun ClientRecord.toPlayerState() = PlayerState(
        id = playerId,
        nickname = nickname,
        isHost = isHost,
        isConnected = isConnected,
        score = score,
        totalVoteTimeMs = totalVoteTimeMs,
        hasSubmittedAnswer = hasSubmittedAnswer,
        hasVoted = hasVoted
    )

    private fun makeUniqueNickname(base: String): String {
        val existing = clients.values.map { it.nickname.lowercase() }.toSet()
        if (base.lowercase() !in existing) return base
        var idx = 2
        while ("$base $idx".lowercase() in existing) idx++
        return "$base $idx"
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(4) { append(chars[Random.nextInt(chars.length)]) }
        }
    }

    private fun sendLine(writer: BufferedWriter, obj: JSONObject) {
        try {
            writer.write(obj.toString())
            writer.newLine()
            writer.flush()
        } catch (_: Exception) {
        }
    }
}
