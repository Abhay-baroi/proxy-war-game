package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameUiState
import com.example.model.PlayerRole
import com.example.ui.components.GlitchEmblemBadge
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
fun PromptInputScreen(
    state: GameUiState,
    aiReplySuggestion: String? = null,
    isSuggestingReply: Boolean = false,
    aiReplyError: String? = null,
    onUnlockPassAndPlayTurn: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onRerollGlitch: () -> Unit,
    onHostSkipRoleReveal: () -> Unit,
    onSuggestAiReply: () -> Unit = {},
    onClearAiReplySuggestion: () -> Unit = {},
    onLeaveTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onLeaveTable() }

    val turn = state.passAndPlayTurn
    if (state.mode == GameMode.PASS_AND_PLAY && turn != null && turn.isPrivacyCurtainActive) {
        PrivacyCurtainOverlay(
            playerNickname = turn.currentPlayerNickname,
            actionHint = "Round ${state.roundNum}/${state.totalRounds}: View your secret role and submit your response.",
            onUnlock = onUnlockPassAndPlayTurn
        )
        return
    }

    val isGlitch = state.myRole == PlayerRole.GLITCH
    val accent = if (isGlitch) GlitchMagenta else HumanCyan
    val brightAccent = if (isGlitch) GlitchMagentaBright else HumanCyanBright

    if (state.phase == GamePhase.ROLE_REVEAL) {
        val pulseTransition = rememberInfiniteTransition(label = "playStartingPulse")
        val badgeScale by pulseTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "roleEmblemScale"
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(2.dp, accent),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "PLAY STARTING • ROUND ${state.roundNum} OF ${state.totalRounds}",
                            style = MaterialTheme.typography.labelMedium,
                            color = brightAccent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Box(modifier = Modifier.scale(badgeScale)) {
                        GlitchEmblemBadge(isGlitch = isGlitch)
                    }
                    Text(
                        text = if (isGlitch) "YOU ARE THE GLITCH" else "YOU ARE HUMAN",
                        style = MaterialTheme.typography.headlineLarge,
                        color = brightAccent,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isGlitch) {
                            "Blend in! Pick one of 3 pre-generated AI responses and convince the table you're human."
                        } else {
                            "Write a natural, human answer to the prompt. Then spot who sounds like a bot!"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    PhaseTimerBar(
                        phaseLabel = "ROLE ASSIGNMENT",
                        remainingSeconds = state.remainingSeconds,
                        totalDurationMs = state.phaseDurationMs,
                        accentColor = accent
                    )
                    if (state.isHost) {
                        OutlinedButton(
                            onClick = onHostSkipRoleReveal,
                            border = BorderStroke(1.dp, accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("skip_role_reveal_button")
                        ) {
                            Text("REVEAL PROMPT NOW", color = brightAccent)
                        }
                    }
                }
            }
        }
        return
    }

    // PROMPT & ANSWER INPUT PHASE
    val currentTurnKey = "${state.roundNum}_${turn?.currentPlayerId ?: state.myPlayerId}"
    var humanDraft by remember(currentTurnKey) { mutableStateOf("") }
    var selectedAiIndex by remember(currentTurnKey) { mutableStateOf(0) }

    LaunchedEffect(state.myAiOptions) {
        if (selectedAiIndex >= state.myAiOptions.size) {
            selectedAiIndex = 0
        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Dynamic Role Status Banner
            Surface(
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGlitch) Icons.Filled.AutoAwesome else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = brightAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isGlitch) "YOU ARE THE GLITCH" else "YOU ARE HUMAN",
                            style = MaterialTheme.typography.labelLarge,
                            color = brightAccent
                        )
                    }
                    Text(
                        text = if (turn != null) "${turn.currentPlayerNickname} • R${state.roundNum}/${state.totalRounds}"
                        else "R${state.roundNum}/${state.totalRounds}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }

            PhaseTimerBar(
                phaseLabel = "PROMPT & ANSWER",
                remainingSeconds = state.remainingSeconds,
                totalDurationMs = state.phaseDurationMs,
                accentColor = accent
            )

            // High-Contrast Prompt Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.5.dp, SlateBorder),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = (state.currentPrompt?.category ?: "CAMPUS").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = GlitchAmber
                    )
                    Text(
                        text = state.currentPrompt?.prompt ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
            }

            if (state.hasSubmittedCurrentRound && state.mode != GameMode.PASS_AND_PLAY) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SignalEmerald.copy(alpha = 0.14f)),
                    border = BorderStroke(1.5.dp, SignalEmerald),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = SignalEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "RESPONSE LOCKED IN",
                                style = MaterialTheme.typography.titleLarge,
                                color = SignalEmerald
                            )
                        }
                        Text(
                            text = "Waiting for remaining players to submit before anonymous debate begins...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                PlayerRosterGrid(players = state.players, showSubmissionTicks = true)
            } else if (!isGlitch) {
                // HUMAN INPUT STATE
                OutlinedTextField(
                    value = humanDraft,
                    onValueChange = { if (it.length <= 140) humanDraft = it },
                    label = { Text("Type your natural human answer") },
                    placeholder = { Text("Keep it casual — don't sound like a robot!") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Max 140 characters", color = TextSecondary)
                            Text(
                                text = "${humanDraft.length}/140",
                                color = if (humanDraft.length >= 130) GlitchAmber else HumanCyanBright
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HumanCyan,
                        unfocusedBorderColor = SlateBorder,
                        focusedLabelColor = HumanCyanBright
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("human_answer_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Need inspiration?",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    OutlinedButton(
                        onClick = onSuggestAiReply,
                        enabled = !isSuggestingReply,
                        border = BorderStroke(1.dp, HumanCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ai_reply_idea_button")
                    ) {
                        if (isSuggestingReply) {
                            CircularProgressIndicator(
                                color = HumanCyanBright,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("THINKING...", style = MaterialTheme.typography.labelSmall, color = HumanCyanBright)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = HumanCyanBright, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI REPLY IDEA", style = MaterialTheme.typography.labelSmall, color = HumanCyanBright)
                        }
                    }
                }

                aiReplyError?.let { err ->
                    Text(text = err, style = MaterialTheme.typography.bodySmall, color = GlitchMagentaBright)
                }

                aiReplySuggestion?.let { suggestion ->
                    Surface(
                        color = HumanCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, HumanCyan.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    humanDraft = suggestion.take(140)
                                    onClearAiReplySuggestion()
                                }
                            ) {
                                Text("USE", color = HumanCyanBright)
                            }
                        }
                    }
                }

                Button(
                    onClick = { onSubmitAnswer(humanDraft) },
                    enabled = humanDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HumanCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_answer_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SUBMIT ANSWER", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                // GLITCH INPUT STATE
                Surface(
                    color = GlitchMagenta.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, GlitchMagenta),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = GlitchAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Typing disabled for The Glitch. Pick 1 of 3 AI responses below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlitchMagentaBright
                        )
                    }
                }

                state.myAiOptions.forEachIndexed { index, option ->
                    val isSelected = selectedAiIndex == index
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) GlitchMagenta.copy(alpha = 0.22f) else SlateSurfaceElevated
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GlitchMagenta else SlateBorder
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAiIndex = index }
                            .testTag("glitch_option_$index")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI #${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) GlitchMagentaBright else GlitchAmber,
                                modifier = Modifier.width(52.dp)
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRerollGlitch,
                        enabled = !state.rerollUsed,
                        border = BorderStroke(1.dp, if (state.rerollUsed) SlateBorder else GlitchAmber),
                        modifier = Modifier
                            .weight(0.45f)
                            .height(54.dp)
                            .testTag("reroll_options_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.rerollUsed) "USED" else "REROLL (1)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = {
                            val chosen = state.myAiOptions.getOrNull(selectedAiIndex) ?: return@Button
                            onSubmitAnswer(chosen)
                        },
                        enabled = state.myAiOptions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GlitchMagenta,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.55f)
                            .height(54.dp)
                            .testTag("lock_selection_button")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LOCK SELECTION", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
