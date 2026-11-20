package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameUiState
import com.example.ui.components.DebateInlineChatPreviewCard
import com.example.ui.components.PhaseTimerBar
import com.example.ui.components.PlayerRosterGrid
import com.example.ui.components.PrivacyCurtainOverlay
import com.example.ui.theme.GlitchAmber
import com.example.ui.theme.GlitchMagenta
import com.example.ui.theme.GlitchMagentaBright
import com.example.ui.theme.HumanCyan
import com.example.ui.theme.HumanCyanBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DebateVoteScreen(
    state: GameUiState,
    onUnlockPassAndPlayVote: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onLockVote: () -> Unit,
    onHostStartVoteNow: () -> Unit,
    onOpenRoomChat: () -> Unit,
    onLeaveTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onLeaveTable() }

    val turn = state.passAndPlayTurn
    if (state.mode == GameMode.PASS_AND_PLAY && state.phase == GamePhase.VOTE && turn != null && turn.isPrivacyCurtainActive) {
        PrivacyCurtainOverlay(
            playerNickname = turn.currentPlayerNickname,
            actionHint = "Tap below to cast your secret vote for who submitted the AI Glitch line.",
            onUnlock = onUnlockPassAndPlayVote
        )
        return
    }

    val isVoting = state.phase == GamePhase.VOTE
    val accent = if (isVoting) GlitchMagenta else HumanCyan
    val selectedCandidate = state.anonymousSubmissions.firstOrNull { it.answerId == state.selectedVoteAnswerId }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PhaseTimerBar(
                phaseLabel = if (isVoting) {
                    if (turn != null) "VOTE PHASE • ${turn.currentPlayerNickname.uppercase()}" else "VOTING PHASE — TAP THE BOT"
                } else {
                    "ANONYMOUS DEBATE — READ ALOUD"
                },
                remainingSeconds = state.remainingSeconds,
                totalDurationMs = state.phaseDurationMs,
                accentColor = accent
            )

            // Prompt reminder
            Surface(
                color = SlateSurface,
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVoting) Icons.Filled.Gavel else Icons.Filled.Forum,
                        contentDescription = null,
                        tint = if (isVoting) GlitchMagentaBright else HumanCyanBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.currentPrompt?.prompt ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }

            state.errorMessage?.let { err ->
                Surface(
                    color = GlitchMagenta.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, GlitchMagenta),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = err,
                        color = GlitchMagentaBright,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            // Shuffled Anonymous Submission Cards
            state.anonymousSubmissions.forEachIndexed { idx, sub ->
                val isLockedOnThis = state.lockedVoteAnswerId == sub.answerId
                val isSelected = state.selectedVoteAnswerId == sub.answerId
                val canTap = isVoting && state.lockedVoteAnswerId == null && !sub.isOwnSubmission

                val borderColor = when {
                    isLockedOnThis -> SignalEmerald
                    isSelected -> GlitchMagenta
                    sub.isOwnSubmission && isVoting -> SlateBorder.copy(alpha = 0.45f)
                    else -> SlateBorder
                }
                val bgColor = when {
                    isLockedOnThis -> SignalEmerald.copy(alpha = 0.16f)
                    isSelected -> GlitchMagenta.copy(alpha = 0.18f)
                    else -> SlateSurfaceElevated
                }

                val cardScale by animateFloatAsState(
                    targetValue = if (isSelected || isLockedOnThis) 1.015f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 360f),
                    label = "voteCardScale"
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(if (isSelected || isLockedOnThis) 2.dp else 1.dp, borderColor),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(cardScale)
                        .then(
                            if (canTap) Modifier.clickable { onSelectCandidate(sub.answerId) } else Modifier
                        )
                        .testTag("submission_card_$idx")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isSelected) GlitchMagenta else HumanCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = sub.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) TextPrimary else HumanCyanBright,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (sub.isOwnSubmission && isVoting) {
                                Text(
                                    text = "YOUR ANSWER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlitchAmber
                                )
                            } else if (isLockedOnThis) {
                                Text(
                                    text = "VOTE LOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalEmerald
                                )
                            } else if (isSelected) {
                                Text(
                                    text = "SUSPECT SELECTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlitchMagentaBright
                                )
                            }
                        }
                        Text(
                            text = "\"${sub.text}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sub.isOwnSubmission && isVoting) TextSecondary else TextPrimary
                        )
                    }
                }
            }

            if (!isVoting) {
                if (state.isHost) {
                    OutlinedButton(
                        onClick = onHostStartVoteNow,
                        border = BorderStroke(1.5.dp, GlitchAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_vote_now_button")
                    ) {
                        Icon(Icons.Filled.HowToVote, contentDescription = null, tint = GlitchAmber)
                        Spacer(Modifier.width(8.dp))
                        Text("START VOTING EARLY", color = GlitchAmber, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                if (state.lockedVoteAnswerId == null) {
                    Button(
                        onClick = onLockVote,
                        enabled = selectedCandidate != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlitchMagenta,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("lock_vote_button")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (selectedCandidate != null) {
                                "LOCK VOTE FOR ${selectedCandidate.label}"
                            } else {
                                "SELECT AN OPTION TO VOTE"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Surface(
                        color = SignalEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.5.dp, SignalEmerald),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Vote locked! Waiting for remaining players...",
                            style = MaterialTheme.typography.titleMedium,
                            color = SignalEmerald,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                    PlayerRosterGrid(players = state.players, showVoteTicks = true)
                }
            }

            DebateInlineChatPreviewCard(
                messages = state.chatMessages,
                onOpenFullChat = onOpenRoomChat
            )
        }
    }
}
