package com.example.engine

import com.example.model.AnonymousSubmission
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import com.example.model.RevealedSubmission
import com.example.model.RoundRevealData
import com.example.model.ScoreDeltaEntry
import kotlin.math.ceil
import kotlin.random.Random

object GameScoringEngine {

    const val ROLE_REVEAL_DURATION_MS = 8_000L
    const val PROMPT_DURATION_MS = 35_000L
    const val DEBATE_DURATION_MS = 50_000L
    const val VOTE_DURATION_MS = 20_000L
    const val REVEAL_DURATION_MS = 15_000L
    const val TOTAL_ROUNDS = 3

    const val HUMAN_TIMEOUT_FALLBACK = "User took too long to respond..."

    data class StoredSubmission(
        val answerId: String,
        val authorId: String,
        val text: String
    )

    data class StoredVote(
        val voterId: String,
        val targetAnswerId: String,
        val elapsedMs: Long
    )

    fun assignRoles(playerIds: List<String>, random: Random = Random.Default): Map<String, PlayerRole> {
        val glitchCount = if (playerIds.size >= 8) 2 else 1
        val shuffled = playerIds.shuffled(random)
        val glitchSet = shuffled.take(glitchCount).toSet()
        return playerIds.associateWith { id ->
            if (id in glitchSet) PlayerRole.GLITCH else PlayerRole.HUMAN
        }
    }

    fun shuffleSubmissions(
        submissions: List<StoredSubmission>,
        seed: Long
    ): List<Pair<String, StoredSubmission>> {
        val shuffled = submissions.shuffled(Random(seed))
        return shuffled.mapIndexed { index, sub ->
            val letter = ('A' + index).toString()
            "OPTION $letter" to sub
        }
    }

    fun toAnonymousSubmissions(
        labeled: List<Pair<String, StoredSubmission>>,
        viewerPlayerId: String
    ): List<AnonymousSubmission> {
        return labeled.map { (label, sub) ->
            AnonymousSubmission(
                answerId = sub.answerId,
                label = label,
                text = sub.text,
                isOwnSubmission = sub.authorId == viewerPlayerId
            )
        }
    }

    fun resolveRound(
        roundNum: Int,
        totalRounds: Int,
        prompt: PromptItem,
        players: List<PlayerState>,
        roles: Map<String, PlayerRole>,
        labeledSubmissions: List<Pair<String, StoredSubmission>>,
        votes: Map<String, StoredVote>,
        nextRoundAtMs: Long
    ): Pair<RoundRevealData, List<PlayerState>> {
        val playerMap = players.associateBy { it.id }
        val submissionByAnswerId = labeledSubmissions.associate { it.second.answerId to it }
        val glitchIds = roles.filterValues { it == PlayerRole.GLITCH }.keys.toList()
        val glitchIdSet = glitchIds.toSet()

        // Votes cast by Humans (Glitches can also cast votes as cover, but scoring evaluates Human votes)
        val validVotes = votes.values.filter { vote ->
            val target = submissionByAnswerId[vote.targetAnswerId]?.second
            target != null && target.authorId != vote.voterId
        }

        val votesByAnswerId = validVotes.groupBy { it.targetAnswerId }
        val humanVotes = validVotes.filter { roles[it.voterId] == PlayerRole.HUMAN }
        val castHumanVoteCount = humanVotes.size

        // Count votes per author
        val totalVotesForAuthor = mutableMapOf<String, Int>()
        val humanVotesForAuthor = mutableMapOf<String, Int>()
        for (vote in validVotes) {
            val authorId = submissionByAnswerId[vote.targetAnswerId]?.second?.authorId ?: continue
            totalVotesForAuthor[authorId] = (totalVotesForAuthor[authorId] ?: 0) + 1
            if (roles[vote.voterId] == PlayerRole.HUMAN) {
                humanVotesForAuthor[authorId] = (humanVotesForAuthor[authorId] ?: 0) + 1
            }
        }

        val innocentHumanVotesCount = humanVotes.count { vote ->
            val authorId = submissionByAnswerId[vote.targetAnswerId]?.second?.authorId
            authorId != null && authorId !in glitchIdSet
        }

        // Determine caught Glitches
        val caughtGlitchIds: Set<String> = if (glitchIds.size <= 1) {
            glitchIds.filter { gId ->
                val votesOnG = humanVotesForAuthor[gId] ?: 0
                castHumanVoteCount > 0 && votesOnG * 2 > castHumanVoteCount
            }.toSet()
        } else {
            // In 8-10 player (2 Glitch) tables, a Glitch is caught if they receive at least ceil(castHumanVotes / 3) >= 2 votes
            val threshold = maxOf(2, ceil(castHumanVoteCount / 3.0).toInt())
            glitchIds.filter { gId ->
                val votesOnG = humanVotesForAuthor[gId] ?: 0
                castHumanVoteCount > 0 && votesOnG >= threshold
            }.toSet()
        }

        val humansWonRound = caughtGlitchIds.isNotEmpty()

        val scoreDeltas = mutableListOf<ScoreDeltaEntry>()
        val updatedPlayers = players.map { player ->
            val role = roles[player.id] ?: PlayerRole.HUMAN
            var delta = 0
            val reasons = mutableListOf<String>()

            if (role == PlayerRole.HUMAN) {
                val myVote = votes[player.id]
                val votedAuthorId = myVote?.let { submissionByAnswerId[it.targetAnswerId]?.second?.authorId }
                if (votedAuthorId != null && votedAuthorId in caughtGlitchIds) {
                    delta += 100
                    reasons += "+100 Caught Glitch"
                }
                val fellowHumanVotesOnMe = humanVotesForAuthor[player.id] ?: 0
                if (fellowHumanVotesOnMe >= 2) {
                    delta += 50
                    reasons += "+50 Honorary Bot"
                }
            } else {
                // GLITCH
                if (player.id !in caughtGlitchIds) {
                    delta += 150
                    reasons += "+150 Evaded"
                    if (innocentHumanVotesCount > 0) {
                        val misdirectBonus = 50 * innocentHumanVotesCount
                        delta += misdirectBonus
                        reasons += "+$misdirectBonus Misdirect x$innocentHumanVotesCount"
                    }
                } else {
                    reasons += "Caught by Table"
                }
            }

            val voteElapsed = votes[player.id]?.elapsedMs ?: 0L
            val newTotalScore = player.score + delta
            val newTotalVoteTime = player.totalVoteTimeMs + voteElapsed

            scoreDeltas += ScoreDeltaEntry(
                playerId = player.id,
                nickname = player.nickname,
                role = role,
                deltaPoints = delta,
                totalScore = newTotalScore,
                totalVoteTimeMs = newTotalVoteTime,
                reasonTags = reasons
            )

            player.copy(
                score = newTotalScore,
                totalVoteTimeMs = newTotalVoteTime,
                hasSubmittedAnswer = false,
                hasVoted = false
            )
        }

        val sortedDeltas = scoreDeltas.sortedWith(
            compareByDescending<ScoreDeltaEntry> { it.totalScore }
                .thenBy { it.totalVoteTimeMs }
        )

        val sortedPlayers = updatedPlayers.sortedWith(
            compareByDescending<PlayerState> { it.score }
                .thenBy { it.totalVoteTimeMs }
        )

        val revealedSubmissions = labeledSubmissions.map { (label, sub) ->
            val author = playerMap[sub.authorId]
            val votersForThis = votesByAnswerId[sub.answerId].orEmpty().mapNotNull { vote ->
                playerMap[vote.voterId]?.nickname
            }
            RevealedSubmission(
                answerId = sub.answerId,
                label = label,
                text = sub.text,
                authorId = sub.authorId,
                authorNickname = author?.nickname ?: "Unknown",
                role = roles[sub.authorId] ?: PlayerRole.HUMAN,
                voterNicknames = votersForThis,
                voteCount = votersForThis.size
            )
        }

        val revealData = RoundRevealData(
            roundNum = roundNum,
            totalRounds = totalRounds,
            promptText = prompt.prompt,
            category = prompt.category,
            revealedSubmissions = revealedSubmissions,
            glitchPlayerIds = glitchIds,
            scoreDeltas = sortedDeltas,
            humansWonRound = humansWonRound,
            nextRoundAtMs = nextRoundAtMs
        )

        return revealData to sortedPlayers
    }
}
