package com.example

import com.example.engine.GameScoringEngine
import com.example.model.PlayerRole
import com.example.model.PlayerState
import com.example.model.PromptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScoringEngineTest {

    private val samplePrompt = PromptItem(
        id = "p_test",
        category = "Lectures & Labs",
        prompt = "Why were you late?",
        aiLines = listOf("Traffic protocols.", "Alarm malfunction.", "Beverage priority.")
    )

    private val players = listOf(
        PlayerState(id = "p1", nickname = "Alex", isHost = true),
        PlayerState(id = "p2", nickname = "Jordan"),
        PlayerState(id = "p3", nickname = "Sam"),
        PlayerState(id = "p4", nickname = "Taylor"),
        PlayerState(id = "p5", nickname = "Morgan")
    )

    @Test
    fun `majority vote catches glitch and awards +100 to correct humans`() {
        val roles = mapOf(
            "p1" to PlayerRole.GLITCH,
            "p2" to PlayerRole.HUMAN,
            "p3" to PlayerRole.HUMAN,
            "p4" to PlayerRole.HUMAN,
            "p5" to PlayerRole.HUMAN
        )
        val subs = listOf(
            "OPTION A" to GameScoringEngine.StoredSubmission("a1", "p1", "Traffic protocols."),
            "OPTION B" to GameScoringEngine.StoredSubmission("a2", "p2", "overslept lol"),
            "OPTION C" to GameScoringEngine.StoredSubmission("a3", "p3", "bus was late"),
            "OPTION D" to GameScoringEngine.StoredSubmission("a4", "p4", "coffee line"),
            "OPTION E" to GameScoringEngine.StoredSubmission("a5", "p5", "forgot my bag")
        )
        // 3 out of 4 humans vote for p1 (majority)
        val votes = mapOf(
            "p2" to GameScoringEngine.StoredVote("p2", "a1", 1200L),
            "p3" to GameScoringEngine.StoredVote("p3", "a1", 1500L),
            "p4" to GameScoringEngine.StoredVote("p4", "a1", 2000L),
            "p5" to GameScoringEngine.StoredVote("p5", "a2", 2500L)
        )

        val (reveal, updated) = GameScoringEngine.resolveRound(
            roundNum = 1,
            totalRounds = 3,
            prompt = samplePrompt,
            players = players,
            roles = roles,
            labeledSubmissions = subs,
            votes = votes,
            nextRoundAtMs = 1000L
        )

        assertTrue(reveal.humansWonRound)
        val scores = updated.associateBy { it.id }
        assertEquals(0, scores["p1"]?.score)
        assertEquals(100, scores["p2"]?.score)
        assertEquals(100, scores["p3"]?.score)
        assertEquals(100, scores["p4"]?.score)
        assertEquals(0, scores["p5"]?.score)
    }

    @Test
    fun `glitch evades capture and gets +150 plus +50 per innocent human vote and honorary bot triggers`() {
        val roles = mapOf(
            "p1" to PlayerRole.GLITCH,
            "p2" to PlayerRole.HUMAN,
            "p3" to PlayerRole.HUMAN,
            "p4" to PlayerRole.HUMAN,
            "p5" to PlayerRole.HUMAN
        )
        val subs = listOf(
            "OPTION A" to GameScoringEngine.StoredSubmission("a1", "p1", "Traffic protocols."),
            "OPTION B" to GameScoringEngine.StoredSubmission("a2", "p2", "I initiated transit late."),
            "OPTION C" to GameScoringEngine.StoredSubmission("a3", "p3", "missed alarm"),
            "OPTION D" to GameScoringEngine.StoredSubmission("a4", "p4", "coffee run"),
            "OPTION E" to GameScoringEngine.StoredSubmission("a5", "p5", "rain delay")
        )
        // 2 humans accidentally vote for p2 (Honorary Bot!), 1 votes for p3, 1 votes for p1 -> Glitch evades!
        val votes = mapOf(
            "p3" to GameScoringEngine.StoredVote("p3", "a2", 1000L),
            "p4" to GameScoringEngine.StoredVote("p4", "a2", 1200L),
            "p5" to GameScoringEngine.StoredVote("p5", "a3", 1300L),
            "p2" to GameScoringEngine.StoredVote("p2", "a1", 900L)
        )

        val (reveal, updated) = GameScoringEngine.resolveRound(
            roundNum = 1,
            totalRounds = 3,
            prompt = samplePrompt,
            players = players,
            roles = roles,
            labeledSubmissions = subs,
            votes = votes,
            nextRoundAtMs = 1000L
        )

        assertFalse(reveal.humansWonRound)
        val scores = updated.associateBy { it.id }
        // Glitch gets 150 base + 3 innocent votes * 50 = 300
        assertEquals(300, scores["p1"]?.score)
        // p2 got >= 2 fellow human votes -> Honorary Bot +50
        assertEquals(50, scores["p2"]?.score)
    }

    @Test
    fun `role assignment picks 1 glitch for 4 to 7 players and 2 glitches for 8 to 10 players`() {
        val seven = (1..7).map { "p$it" }
        val eight = (1..8).map { "p$it" }

        assertEquals(1, GameScoringEngine.assignRoles(seven).values.count { it == PlayerRole.GLITCH })
        assertEquals(2, GameScoringEngine.assignRoles(eight).values.count { it == PlayerRole.GLITCH })
    }
}
