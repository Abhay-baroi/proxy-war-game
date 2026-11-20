package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameUiState
import com.example.model.PlayerRole
import com.example.model.RevealedSubmission
import com.example.ui.components.CampusGlobalLeaderboardCard
import com.example.ui.components.PhaseTimerBar
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.RolePillBadge
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
import com.example.util.ReceiptRenderer
import kotlinx.coroutines.delay

@Composable
fun RevealMatchEndScreen(
    state: GameUiState,
    onHostNextRound: () -> Unit,
    onPlayAgain: () -> Unit,
    onLeaveTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onLeaveTable() }

    val context = LocalContext.current
    val reveal = state.revealData
    val isMatchEnd = state.phase == GamePhase.MATCH_END
    var receiptBitmap by remember { mutableStateOf<Bitmap?>(null) }

    receiptBitmap?.let { bmp ->
        ReceiptPreviewDialog(
            bitmap = bmp,
            onDismiss = { receiptBitmap = null },
            onShare = {
                ReceiptRenderer.shareReceiptBitmap(context, bmp, state.roomCode)
                receiptBitmap = null
            }
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isMatchEnd) {
                val champion = state.players.firstOrNull()
                Card(
                    colors = CardDefaults.cardColors(containerColor = HumanCyan.copy(alpha = 0.16f)),
                    border = BorderStroke(2.dp, HumanCyan),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = "Table Champion",
                            tint = GlitchAmber,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "TABLE CHAMPION",
                            style = MaterialTheme.typography.labelMedium,
                            color = HumanCyanBright
                        )
                        Text(
                            text = champion?.nickname ?: "Champion",
                            style = MaterialTheme.typography.displayMedium,
                            color = TextPrimary
                        )
                        if (champion != null) {
                            Text(
                                text = "${champion.score} PTS  •  Vote Speed: ${champion.totalVoteTimeMs}ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = GlitchAmber
                            )
                        }
                    }
                }
            } else if (reveal != null) {
                val outcomeColor = if (reveal.humansWonRound) HumanCyan else GlitchMagenta
                PhaseTimerBar(
                    phaseLabel = if (reveal.roundNum < reveal.totalRounds) {
                        "ROUND ${reveal.roundNum} REVEAL • NEXT ROUND SOON"
                    } else {
                        "FINAL ROUND REVEAL"
                    },
                    remainingSeconds = state.remainingSeconds,
                    totalDurationMs = state.phaseDurationMs,
                    accentColor = outcomeColor
                )

                Surface(
                    color = outcomeColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.5.dp, outcomeColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!reveal.humansWonRound) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = GlitchAmber,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                text = if (reveal.humansWonRound) {
                                    "GLITCH UNMASKED!"
                                } else {
                                    "THE GLITCH EVADED CAPTURE!"
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = reveal.promptText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Animated Card Flip Reveal of Round Submissions
            if (reveal != null) {
                Text(
                    text = "ROUND ${reveal.roundNum} SUBMISSIONS UNMASKED",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                reveal.revealedSubmissions.forEachIndexed { idx, sub ->
                    FlipRevealSubmissionCard(submission = sub, staggerIndex = idx)
                }

                // Compact Scoreboard
                Text(
                    text = "LEADERBOARD & SCORE DELTAS",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        reveal.scoreDeltas.forEachIndexed { rank, entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "#${rank + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (rank == 0) GlitchAmber else TextSecondary,
                                        modifier = Modifier.width(30.dp)
                                    )
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = entry.nickname,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            RolePillBadge(isGlitch = entry.role == PlayerRole.GLITCH)
                                        }
                                         if (entry.reasonTags.isNotEmpty()) {
                                             Text(
                                                 text = entry.reasonTags.joinToString(" • "),
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = TextSecondary
                                             )
                                         }
                                     }
                                 }
                                 Column(horizontalAlignment = Alignment.End) {
                                     Text(
                                         text = "${entry.totalScore} PTS",
                                         style = MaterialTheme.typography.titleMedium,
                                         color = TextPrimary,
                                         fontWeight = FontWeight.Bold
                                     )
                                     val deltaLabel = if (entry.deltaPoints > 0) "+${entry.deltaPoints} Δ • ${entry.totalVoteTimeMs}ms" else "${entry.totalVoteTimeMs}ms"
                                     Text(
                                         text = deltaLabel,
                                         style = MaterialTheme.typography.labelSmall,
                                         color = if (entry.deltaPoints > 0) SignalEmerald else TextSecondary
                                     )
                                 }
                             }
                         }
                     }
                 }

                 if (state.mode == GameMode.CLOUD_HOST || state.mode == GameMode.CLOUD_CLIENT || state.globalLeaderboard.isNotEmpty()) {
                     CampusGlobalLeaderboardCard(
                         entries = state.globalLeaderboard,
                         maxVisible = 8
                     )
                 }

                // 9:16 Shareable Receipt Action
                OutlinedButton(
                    onClick = {
                        receiptBitmap = ReceiptRenderer.renderReceiptBitmap(
                            reveal = reveal,
                            roomCode = state.roomCode,
                            isMatchEnd = isMatchEnd
                        )
                    },
                    border = BorderStroke(1.5.dp, GlitchAmber),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_receipt_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = GlitchAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("SHARE 9:16 RECEIPT", color = GlitchAmber, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (!isMatchEnd && state.isHost) {
                Button(
                    onClick = onHostNextRound,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HumanCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("next_round_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if ((reveal?.roundNum ?: 1) < (reveal?.totalRounds ?: 3)) {
                            "START NEXT ROUND NOW"
                        } else {
                            "SHOW FINAL PODIUM"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else if (isMatchEnd) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onLeaveTable,
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("leave_table_button")
                    ) {
                        Text("LEAVE TABLE")
                    }
                    if (state.isHost) {
                        Button(
                            onClick = onPlayAgain,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HumanCyan,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("play_again_button")
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PLAY AGAIN")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipRevealSubmissionCard(
    submission: RevealedSubmission,
    staggerIndex: Int
) {
    var flipped by remember(submission.answerId) { mutableStateOf(false) }
    LaunchedEffect(submission.answerId) {
        delay(120L * staggerIndex)
        flipped = true
    }

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 0f else 90f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "cardFlip"
    )

    val isGlitch = submission.role == PlayerRole.GLITCH
    val borderColor = if (isGlitch) GlitchMagenta else SlateBorder
    val bgColor = if (isGlitch) GlitchMagenta.copy(alpha = 0.16f) else SlateSurfaceElevated

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isGlitch) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = rotation
                cameraDistance = 12f * density
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = submission.authorNickname,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isGlitch) GlitchMagentaBright else HumanCyanBright
                    )
                    Spacer(Modifier.width(8.dp))
                    RolePillBadge(isGlitch = isGlitch)
                }
                Text(
                    text = "${submission.voteCount} vote${if (submission.voteCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (submission.voteCount > 0) GlitchAmber else TextSecondary
                )
            }
            Text(
                text = "\"${submission.text}\"",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            if (submission.voterNicknames.isNotEmpty()) {
                Text(
                    text = "Suspected by: ${submission.voterNicknames.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
