package com.example.engine

import com.example.data.PromptRepository
import com.example.model.AnonymousSubmission
import com.example.model.GamePhase
import com.example.model.PassAndPlayTurnState
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import com.example.model.RoundRevealData
import kotlin.random.Random

class PassAndPlayEngine(
    private val promptRepository: PromptRepository
) {
    private var players: MutableList<PlayerState> = mutableListOf()
    private var matchPrompts: List<PromptItem> = emptyList()
    private var currentRoundNum: Int = 1
    private var currentPrompt: PromptItem? = null
    private var roundRoles: Map<String, PlayerRole> = emptyMap()
    private var glitchOptionsByPlayer: MutableMap<String, List<String>> = mutableMapOf()
    private var glitchRerollUsed: MutableSet<String> = mutableSetOf()
    private var submissionsByPlayer: MutableMap<String, GameScoringEngine.StoredSubmission> = mutableMapOf()
    private var labeledSubmissions: List<Pair<String, GameScoringEngine.StoredSubmission>> = emptyList()
    private var votesByPlayer: MutableMap<String, GameScoringEngine.StoredVote> = mutableMapOf()

    private var turnIndex: Int = 0
    private var isPrivacyCurtainActive: Boolean = true
    private var turnStartedAtMs: Long = 0L

    fun startMatch(nicknames: List<String>): Snapshot {
        require(nicknames.size in 4..10) { "Pass & Play requires 4 to 10 players." }
        players = nicknames.mapIndexed { idx, raw ->
            PlayerState(
                id = "pp_${idx + 1}",
                nickname = raw.trim().take(18).ifBlank { "Player ${idx + 1}" },
                isHost = idx == 0,
                isConnected = true
            )
        }.toMutableList()
        matchPrompts = promptRepository.pickMatchPrompts(GameScoringEngine.TOTAL_ROUNDS)
        currentRoundNum = 1
        return beginRound()
    }

    fun restartMatch(): Snapshot {
        players = players.map {
            it.copy(score = 0, totalVoteTimeMs = 0L, hasSubmittedAnswer = false, hasVoted = false)
        }.toMutableList()
        matchPrompts = promptRepository.pickMatchPrompts(GameScoringEngine.TOTAL_ROUNDS)
        currentRoundNum = 1
        return beginRound()
    }

    private fun beginRound(): Snapshot {
        val prompt = matchPrompts.getOrElse(currentRoundNum - 1) {
            promptRepository.loadAllPrompts().random()
        }
        currentPrompt = prompt
        roundRoles = GameScoringEngine.assignRoles(players.map { it.id })
        glitchOptionsByPlayer.clear()
        glitchRerollUsed.clear()
        submissionsByPlayer.clear()
        labeledSubmissions = emptyList()
        votesByPlayer.clear()

        players = players.map { p ->
            if (roundRoles[p.id] == PlayerRole.GLITCH) {
                glitchOptionsByPlayer[p.id] = prompt.aiLines.take(3)
            }
            p.copy(hasSubmittedAnswer = false, hasVoted = false)
        }.toMutableList()

        turnIndex = 0
        isPrivacyCurtainActive = true
        return buildSnapshot(
            phase = GamePhase.PROMPT,
            phaseDurationMs = 0L,
            revealData = null
        )
    }

    fun unlockCurrentTurn(): Snapshot {
        isPrivacyCurtainActive = false
        turnStartedAtMs = System.currentTimeMillis()
        val currentPhase = if (submissionsByPlayer.size < players.size) GamePhase.PROMPT else GamePhase.VOTE
        val duration = if (currentPhase == GamePhase.PROMPT) {
            GameScoringEngine.PROMPT_DURATION_MS
        } else {
            GameScoringEngine.VOTE_DURATION_MS
        }
        return buildSnapshot(phase = currentPhase, phaseDurationMs = duration, revealData = null)
    }

    fun rerollGlitchOptions(): Snapshot {
        val currentPlayer = players.getOrNull(turnIndex) ?: return buildSnapshot(GamePhase.PROMPT, 0L, null)
        val prompt = currentPrompt ?: return buildSnapshot(GamePhase.PROMPT, 0L, null)
        if (roundRoles[currentPlayer.id] == PlayerRole.GLITCH && currentPlayer.id !in glitchRerollUsed) {
            val currentOpts = glitchOptionsByPlayer[currentPlayer.id].orEmpty()
            glitchOptionsByPlayer[currentPlayer.id] = promptRepository.buildRerollAiLines(prompt, currentOpts)
            glitchRerollUsed.add(currentPlayer.id)
        }
        return buildSnapshot(phase = GamePhase.PROMPT, phaseDurationMs = 0L, revealData = null, keepTimer = true)
    }

    fun submitCurrentAnswer(answerText: String): Snapshot {
        val currentPlayer = players.getOrNull(turnIndex)
            ?: return transitionToDebate()
        val role = roundRoles[currentPlayer.id] ?: PlayerRole.HUMAN
        val cleanText = answerText.trim().take(140).ifBlank {
            if (role == PlayerRole.GLITCH) {
                glitchOptionsByPlayer[currentPlayer.id]?.firstOrNull() ?: "Optimal execution."
            } else {
                GameScoringEngine.HUMAN_TIMEOUT_FALLBACK
            }
        }
        submissionsByPlayer[currentPlayer.id] = GameScoringEngine.StoredSubmission(
            answerId = "ans_${currentRoundNum}_${currentPlayer.id}",
            authorId = currentPlayer.id,
            text = cleanText
        )
        players[turnIndex] = currentPlayer.copy(hasSubmittedAnswer = true)

        if (turnIndex + 1 < players.size) {
            turnIndex += 1
            isPrivacyCurtainActive = true
            return buildSnapshot(phase = GamePhase.PROMPT, phaseDurationMs = 0L, revealData = null)
        }
        return transitionToDebate()
    }

    fun transitionToDebate(): Snapshot {
        labeledSubmissions = GameScoringEngine.shuffleSubmissions(
            submissionsByPlayer.values.toList(),
            Random.nextLong()
        )
        return buildSnapshot(
            phase = GamePhase.DEBATE,
            phaseDurationMs = GameScoringEngine.DEBATE_DURATION_MS,
            revealData = null
        )
    }

    fun startVotingTurns(): Snapshot {
        turnIndex = 0
        isPrivacyCurtainActive = true
        return buildSnapshot(
            phase = GamePhase.VOTE,
            phaseDurationMs = 0L,
            revealData = null
        )
    }

    fun submitCurrentVote(targetAnswerId: String?): Snapshot {
        val currentPlayer = players.getOrNull(turnIndex)
            ?: return transitionToReveal()
        if (targetAnswerId != null) {
            val targetSub = labeledSubmissions.firstOrNull { it.second.answerId == targetAnswerId }?.second
            if (targetSub != null && targetSub.authorId != currentPlayer.id) {
                val elapsed = (System.currentTimeMillis() - turnStartedAtMs).coerceIn(0L, GameScoringEngine.VOTE_DURATION_MS)
                votesByPlayer[currentPlayer.id] = GameScoringEngine.StoredVote(
                    voterId = currentPlayer.id,
                    targetAnswerId = targetAnswerId,
                    elapsedMs = elapsed
                )
            }
        }
        players[turnIndex] = currentPlayer.copy(hasVoted = true)

        if (turnIndex + 1 < players.size) {
            turnIndex += 1
            isPrivacyCurtainActive = true
            return buildSnapshot(phase = GamePhase.VOTE, phaseDurationMs = 0L, revealData = null)
        }
        return transitionToReveal()
    }

    private fun transitionToReveal(): Snapshot {
        val prompt = currentPrompt!!
        val nextAt = System.currentTimeMillis() + GameScoringEngine.REVEAL_DURATION_MS
        val (revealData, updatedPlayers) = GameScoringEngine.resolveRound(
            roundNum = currentRoundNum,
            totalRounds = GameScoringEngine.TOTAL_ROUNDS,
            prompt = prompt,
            players = players,
            roles = roundRoles,
            labeledSubmissions = labeledSubmissions,
            votes = votesByPlayer,
            nextRoundAtMs = nextAt
        )
        players = updatedPlayers.toMutableList()
        return buildSnapshot(
            phase = GamePhase.REVEAL,
            phaseDurationMs = GameScoringEngine.REVEAL_DURATION_MS,
            revealData = revealData
        )
    }

    fun advanceAfterReveal(): Snapshot {
        return if (currentRoundNum < GameScoringEngine.TOTAL_ROUNDS) {
            currentRoundNum += 1
            beginRound()
        } else {
            buildSnapshot(phase = GamePhase.MATCH_END, phaseDurationMs = 0L, revealData = null)
        }
    }

    private fun buildSnapshot(
        phase: GamePhase,
        phaseDurationMs: Long,
        revealData: RoundRevealData?,
        keepTimer: Boolean = false
    ): Snapshot {
        val currentPlayer = players.getOrNull(turnIndex)
        val turnState = if ((phase == GamePhase.PROMPT || phase == GamePhase.VOTE) && currentPlayer != null) {
            val role = roundRoles[currentPlayer.id] ?: PlayerRole.HUMAN
            PassAndPlayTurnState(
                currentPlayerIndex = turnIndex,
                isPrivacyCurtainActive = isPrivacyCurtainActive,
                currentPlayerId = currentPlayer.id,
                currentPlayerNickname = currentPlayer.nickname,
                currentPlayerRole = role,
                currentAiOptions = if (role == PlayerRole.GLITCH) glitchOptionsByPlayer[currentPlayer.id].orEmpty() else emptyList(),
                rerollUsed = currentPlayer.id in glitchRerollUsed
            )
        } else {
            null
        }

        val viewerId = currentPlayer?.id ?: ""
        val anonSubs = if (phase == GamePhase.DEBATE) {
            GameScoringEngine.toAnonymousSubmissions(labeledSubmissions, viewerPlayerId = "")
        } else if (phase == GamePhase.VOTE) {
            GameScoringEngine.toAnonymousSubmissions(labeledSubmissions, viewerPlayerId = viewerId)
        } else {
            emptyList()
        }

        return Snapshot(
            phase = phase,
            players = players.toList(),
            roundNum = currentRoundNum,
            totalRounds = GameScoringEngine.TOTAL_ROUNDS,
            prompt = currentPrompt,
            phaseDurationMs = phaseDurationMs,
            keepTimer = keepTimer,
            turnState = turnState,
            anonymousSubmissions = anonSubs,
            revealData = revealData
        )
    }

    data class Snapshot(
        val phase: GamePhase,
        val players: List<PlayerState>,
        val roundNum: Int,
        val totalRounds: Int,
        val prompt: PromptItem?,
        val phaseDurationMs: Long,
        val keepTimer: Boolean = false,
        val turnState: PassAndPlayTurnState?,
        val anonymousSubmissions: List<AnonymousSubmission>,
        val revealData: RoundRevealData?
    )
}
