package com.example.network

import com.example.model.AnonymousSubmission
import com.example.model.GamePhase
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import com.example.model.RevealedSubmission
import com.example.model.RoomChatMessage
import com.example.model.RoundRevealData
import com.example.model.SavedSession
import com.example.model.ScoreDeltaEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

sealed interface LanClientEvent {
    data class Joined(
        val roomCode: String,
        val hostAddress: String,
        val playerId: String,
        val sessionToken: String,
        val nickname: String,
        val isHost: Boolean
    ) : LanClientEvent

    data class RoomStateUpdated(
        val roomCode: String,
        val phase: GamePhase,
        val phaseEndsAtMs: Long,
        val phaseDurationMs: Long,
        val roundNum: Int,
        val totalRounds: Int,
        val isHost: Boolean,
        val players: List<PlayerState>
    ) : LanClientEvent

    data class RoundStarted(
        val roundNum: Int,
        val prompt: PromptItem,
        val role: PlayerRole,
        val aiOptions: List<String>,
        val rerollUsed: Boolean
    ) : LanClientEvent

    data class GlitchOptionsUpdated(
        val aiOptions: List<String>,
        val rerollUsed: Boolean
    ) : LanClientEvent

    data class AnswerAck(val success: Boolean, val answerId: String, val message: String?) : LanClientEvent
    data class SubmissionsReceived(val submissions: List<AnonymousSubmission>) : LanClientEvent
    data class VoteAck(val success: Boolean, val targetAnswerId: String) : LanClientEvent
    data class RoundRevealed(val revealData: RoundRevealData) : LanClientEvent
    data class ChatMessageReceived(val message: RoomChatMessage) : LanClientEvent
    data class Error(val message: String) : LanClientEvent
    data class Disconnected(val reason: String) : LanClientEvent
}

class LanClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LanClientEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<LanClientEvent> = _events.asSharedFlow()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var readJob: Job? = null
    private var autoReconnectEnabled = false
    private var lastSession: SavedSession? = null

    fun connectAndJoin(hostAddress: String, roomCode: String, nickname: String, isHost: Boolean) {
        disconnect()
        autoReconnectEnabled = true
        scope.launch {
            openSocketAndSend(hostAddress) { w ->
                sendJson(w, JSONObject().apply {
                    put("type", "room:join")
                    put("roomCode", roomCode.trim().uppercase())
                    put("nickname", nickname.trim())
                    put("isHost", isHost)
                })
            }
        }
    }

    fun reconnect(session: SavedSession) {
        disconnect()
        lastSession = session
        autoReconnectEnabled = true
        scope.launch {
            openSocketAndSend(session.hostAddress) { w ->
                sendJson(w, JSONObject().apply {
                    put("type", "room:reconnect")
                    put("roomCode", session.roomCode)
                    put("playerId", session.playerId)
                    put("sessionToken", session.sessionToken)
                })
            }
        }
    }

    fun sendGameStart() = sendTypeOnly("game:start")
    fun sendHostSkip() = sendTypeOnly("phase:skip")
    fun sendGlitchReroll() = sendTypeOnly("glitch:reroll")
    fun sendGameRestart() = sendTypeOnly("game:restart")

    fun sendAnswer(answerText: String) {
        scope.launch {
            writer?.let { w ->
                sendJson(w, JSONObject().apply {
                    put("type", "answer:submit")
                    put("answerText", answerText)
                })
            }
        }
    }

    fun sendVote(targetAnswerId: String, elapsedMs: Long) {
        scope.launch {
            writer?.let { w ->
                sendJson(w, JSONObject().apply {
                    put("type", "vote:cast")
                    put("targetAnswerId", targetAnswerId)
                    put("elapsedMs", elapsedMs)
                })
            }
        }
    }

    fun sendChatMessage(text: String) {
        val cleanText = text.trim().take(200)
        if (cleanText.isEmpty()) return
        scope.launch {
            writer?.let { w ->
                sendJson(w, JSONObject().apply {
                    put("type", "chat:send")
                    put("text", cleanText)
                })
            }
        }
    }

    fun disconnect() {
        autoReconnectEnabled = false
        readJob?.cancel()
        readJob = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        writer = null
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun sendTypeOnly(type: String) {
        scope.launch {
            writer?.let { w ->
                sendJson(w, JSONObject().apply { put("type", type) })
            }
        }
    }

    private suspend fun openSocketAndSend(hostAddress: String, onConnected: (BufferedWriter) -> Unit) {
        val parts = hostAddress.trim().split(":")
        val ip = parts.getOrNull(0).orEmpty().ifBlank { "127.0.0.1" }
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 8765
        try {
            val s = Socket()
            s.connect(InetSocketAddress(ip, port), 5000)
            s.tcpNoDelay = true
            socket = s
            val w = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
            writer = w
            onConnected(w)

            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            readJob = scope.launch {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        parseAndEmit(line, "$ip:$port")
                    }
                } catch (_: Exception) {
                } finally {
                    if (autoReconnectEnabled && lastSession != null) {
                        _events.tryEmit(LanClientEvent.Disconnected("Wi-Fi hiccup — attempting auto-reconnect..."))
                        delay(1500L)
                        lastSession?.let { sess ->
                            openSocketAndSend(sess.hostAddress) { rw ->
                                sendJson(rw, JSONObject().apply {
                                    put("type", "room:reconnect")
                                    put("roomCode", sess.roomCode)
                                    put("playerId", sess.playerId)
                                    put("sessionToken", sess.sessionToken)
                                })
                            }
                        }
                    } else if (autoReconnectEnabled) {
                        _events.tryEmit(LanClientEvent.Disconnected("Connection to Host lost."))
                    }
                }
            }
        } catch (e: Exception) {
            _events.tryEmit(
                LanClientEvent.Error("Could not reach host at $ip:$port (${e.localizedMessage ?: "Connection failed"})")
            )
        }
    }

    private fun parseAndEmit(raw: String, hostAddress: String) {
        val obj = JSONObject(raw)
        when (obj.optString("type")) {
            "room:joined" -> {
                val joined = LanClientEvent.Joined(
                    roomCode = obj.getString("roomCode"),
                    hostAddress = hostAddress,
                    playerId = obj.getString("playerId"),
                    sessionToken = obj.getString("sessionToken"),
                    nickname = obj.getString("nickname"),
                    isHost = obj.optBoolean("isHost", false)
                )
                lastSession = SavedSession(
                    roomCode = joined.roomCode,
                    hostAddress = joined.hostAddress,
                    playerId = joined.playerId,
                    sessionToken = joined.sessionToken,
                    nickname = joined.nickname
                )
                _events.tryEmit(joined)
            }
            "room:state" -> {
                val pArr = obj.getJSONArray("players")
                val players = List(pArr.length()) { i ->
                    val p = pArr.getJSONObject(i)
                    PlayerState(
                        id = p.getString("id"),
                        nickname = p.getString("nickname"),
                        isHost = p.optBoolean("isHost", false),
                        isConnected = p.optBoolean("isConnected", true),
                        score = p.optInt("score", 0),
                        totalVoteTimeMs = p.optLong("totalVoteTimeMs", 0L),
                        hasSubmittedAnswer = p.optBoolean("hasSubmittedAnswer", false),
                        hasVoted = p.optBoolean("hasVoted", false)
                    )
                }
                val phase = enumValues<GamePhase>().firstOrNull { it.name == obj.optString("phase") }
                    ?: GamePhase.LOBBY
                _events.tryEmit(
                    LanClientEvent.RoomStateUpdated(
                        roomCode = obj.getString("roomCode"),
                        phase = phase,
                        phaseEndsAtMs = obj.optLong("phaseEndsAt", 0L),
                        phaseDurationMs = obj.optLong("phaseDurationMs", 0L),
                        roundNum = obj.optInt("roundNum", 1),
                        totalRounds = obj.optInt("totalRounds", 3),
                        isHost = obj.optBoolean("isHost", false),
                        players = players
                    )
                )
            }
            "round:started" -> {
                val role = enumValues<PlayerRole>().firstOrNull { it.name == obj.optString("role") }
                    ?: PlayerRole.HUMAN
                val aiArr = obj.optJSONArray("aiOptions")
                val aiOptions = if (aiArr != null) {
                    List(aiArr.length()) { idx -> aiArr.getString(idx) }
                } else {
                    emptyList()
                }
                _events.tryEmit(
                    LanClientEvent.RoundStarted(
                        roundNum = obj.optInt("roundNum", 1),
                        prompt = PromptItem(
                            id = obj.optString("promptId"),
                            category = obj.optString("category"),
                            prompt = obj.optString("prompt"),
                            aiLines = emptyList()
                        ),
                        role = role,
                        aiOptions = aiOptions,
                        rerollUsed = obj.optBoolean("rerollUsed", false)
                    )
                )
            }
            "glitch:options" -> {
                val aiArr = obj.getJSONArray("aiOptions")
                val opts = List(aiArr.length()) { idx -> aiArr.getString(idx) }
                _events.tryEmit(
                    LanClientEvent.GlitchOptionsUpdated(
                        aiOptions = opts,
                        rerollUsed = obj.optBoolean("rerollUsed", true)
                    )
                )
            }
            "answer:ack" -> {
                _events.tryEmit(
                    LanClientEvent.AnswerAck(
                        success = obj.optString("status") == "SUCCESS",
                        answerId = obj.optString("answerId"),
                        message = obj.optString("message").takeIf { it.isNotBlank() }
                    )
                )
            }
            "submissions:list" -> {
                val arr = obj.getJSONArray("submissions")
                val subs = List(arr.length()) { i ->
                    val s = arr.getJSONObject(i)
                    AnonymousSubmission(
                        answerId = s.getString("answerId"),
                        label = s.getString("label"),
                        text = s.getString("text"),
                        isOwnSubmission = s.optBoolean("isOwnSubmission", false)
                    )
                }
                _events.tryEmit(LanClientEvent.SubmissionsReceived(subs))
            }
            "vote:ack" -> {
                _events.tryEmit(
                    LanClientEvent.VoteAck(
                        success = obj.optString("status") == "SUCCESS",
                        targetAnswerId = obj.optString("targetAnswerId")
                    )
                )
            }
            "round:reveal" -> {
                val subsArr = obj.getJSONArray("revealedSubmissions")
                val subs = List(subsArr.length()) { i ->
                    val s = subsArr.getJSONObject(i)
                    val votersArr = s.getJSONArray("voterNicknames")
                    RevealedSubmission(
                        answerId = s.getString("answerId"),
                        label = s.getString("label"),
                        text = s.getString("text"),
                        authorId = s.getString("authorId"),
                        authorNickname = s.getString("authorNickname"),
                        role = enumValues<PlayerRole>().firstOrNull { it.name == s.optString("role") } ?: PlayerRole.HUMAN,
                        voterNicknames = List(votersArr.length()) { v -> votersArr.getString(v) },
                        voteCount = s.optInt("voteCount", 0)
                    )
                }
                val glitchArr = obj.getJSONArray("glitchPlayerIds")
                val glitchIds = List(glitchArr.length()) { i -> glitchArr.getString(i) }
                val deltaArr = obj.getJSONArray("scoreDeltas")
                val deltas = List(deltaArr.length()) { i ->
                    val d = deltaArr.getJSONObject(i)
                    val reasonArr = d.getJSONArray("reasonTags")
                    ScoreDeltaEntry(
                        playerId = d.getString("playerId"),
                        nickname = d.getString("nickname"),
                        role = enumValues<PlayerRole>().firstOrNull { it.name == d.optString("role") } ?: PlayerRole.HUMAN,
                        deltaPoints = d.optInt("deltaPoints", 0),
                        totalScore = d.optInt("totalScore", 0),
                        totalVoteTimeMs = d.optLong("totalVoteTimeMs", 0L),
                        reasonTags = List(reasonArr.length()) { r -> reasonArr.getString(r) }
                    )
                }
                _events.tryEmit(
                    LanClientEvent.RoundRevealed(
                        RoundRevealData(
                            roundNum = obj.optInt("roundNum", 1),
                            totalRounds = obj.optInt("totalRounds", 3),
                            promptText = obj.optString("promptText"),
                            category = obj.optString("category"),
                            revealedSubmissions = subs,
                            glitchPlayerIds = glitchIds,
                            scoreDeltas = deltas,
                            humansWonRound = obj.optBoolean("humansWonRound", false),
                            nextRoundAtMs = obj.optLong("nextRoundAt", 0L)
                        )
                    )
                )
            }
            "chat:message" -> {
                val msgPhase = enumValues<GamePhase>().firstOrNull { it.name == obj.optString("phase") }
                    ?: GamePhase.DEBATE
                _events.tryEmit(
                    LanClientEvent.ChatMessageReceived(
                        RoomChatMessage(
                            id = obj.optString("id"),
                            senderId = obj.optString("senderId"),
                            senderNickname = obj.optString("senderNickname", "Player"),
                            text = obj.optString("text"),
                            roundNum = obj.optInt("roundNum", 1),
                            phase = msgPhase,
                            sentAtMs = obj.optLong("sentAtMs", System.currentTimeMillis()),
                            isMine = obj.optString("senderId") == lastSession?.playerId
                        )
                    )
                )
            }
            "error" -> {
                _events.tryEmit(LanClientEvent.Error(obj.optString("message", "Unknown host error")))
            }
        }
    }

    private fun sendJson(writer: BufferedWriter, obj: JSONObject) {
        try {
            writer.write(obj.toString())
            writer.newLine()
            writer.flush()
        } catch (_: Exception) {
        }
    }
}
