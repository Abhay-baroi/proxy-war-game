package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GamePhase
import com.example.model.RoomChatMessage
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QUICK_DEBATE_CHIPS = listOf(
    "Too robotic!",
    "No human writes that",
    "Prove you're real",
    "Wait, that sounds legit"
)

@Composable
fun RoomChatDockBar(
    roomCode: String,
    messages: List<RoomChatMessage>,
    unreadCount: Int,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = messages.lastOrNull()
    val hasUnread = unreadCount > 0

    Surface(
        onClick = onOpenChat,
        color = SlateSurface,
        border = BorderStroke(1.dp, if (hasUnread) GlitchMagenta else SlateBorder),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("open_room_chat_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = if (hasUnread) GlitchMagenta.copy(alpha = 0.2f) else HumanCyan.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = stringResource(R.string.chat_open_desc),
                        tint = if (hasUnread) GlitchMagentaBright else HumanCyanBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.chat_room_label, roomCode),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasUnread) GlitchMagentaBright else HumanCyanBright
                    )
                    if (hasUnread) {
                        Surface(
                            color = GlitchMagenta,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "$unreadCount NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (latest != null) {
                        val sender = if (latest.isMine) "You" else latest.senderNickname
                        "$sender: ${latest.text}"
                    } else {
                        stringResource(R.string.chat_dock_empty_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (latest != null) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.chat_open_desc),
                tint = TextSecondary
            )
        }
    }
}

@Composable
fun DebateInlineChatPreviewCard(
    messages: List<RoomChatMessage>,
    onOpenFullChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recent = messages.takeLast(3)

    Surface(
        color = SlateSurface,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = HumanCyanBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_table_feed_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = HumanCyanBright
                    )
                    if (messages.isNotEmpty()) {
                        Surface(
                            color = HumanCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${messages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = HumanCyanBright,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onOpenFullChat,
                    modifier = Modifier.testTag("debate_open_chat_button")
                ) {
                    Text(
                        text = stringResource(R.string.chat_reply_button),
                        color = HumanCyanBright,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_debate_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = if (msg.isMine) "You:" else "${msg.senderNickname}:",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (msg.isMine) HumanCyanBright else GlitchAmber
                            )
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomChatBottomSheet(
    roomCode: String,
    playerCount: Int,
    currentPhase: GamePhase,
    currentRoundNum: Int,
    messages: List<RoomChatMessage>,
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        RoomChatPanel(
            roomCode = roomCode,
            playerCount = playerCount,
            currentPhase = currentPhase,
            currentRoundNum = currentRoundNum,
            messages = messages,
            isSending = isSending,
            onSendMessage = onSendMessage,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        )
    }
}

@Composable
fun RoomChatPanel(
    roomCode: String,
    playerCount: Int,
    currentPhase: GamePhase,
    currentRoundNum: Int,
    messages: List<RoomChatMessage>,
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var draftText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val submitDraft: () -> Unit = {
        val clean = draftText.trim()
        if (clean.isNotEmpty() && !isSending) {
            onSendMessage(clean)
            draftText = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = HumanCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = HumanCyanBright,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.chat_room_title, roomCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(
                            R.string.chat_status_line,
                            playerCount,
                            formatPhaseTag(currentRoundNum, currentPhase)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald
                    )
                }
            }
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_room_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_close_desc),
                        tint = TextSecondary
                    )
                }
            }
        }

        HorizontalDivider(color = SlateBorder)

        // Messages list
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = HumanCyanBright.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.chat_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.chat_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 340.dp)
                    .testTag("chat_messages_list")
            ) {
                items(items = messages, key = { it.id }) { msg ->
                    ChatMessageBubble(msg = msg)
                }
            }
        }

        // Quick quips during debate/voting/reveal
        if (currentPhase == GamePhase.DEBATE || currentPhase == GamePhase.VOTE || currentPhase == GamePhase.REVEAL) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QUICK_DEBATE_CHIPS) { chip ->
                    SuggestionChip(
                        onClick = { draftText = chip },
                        label = {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = SlateSurfaceElevated
                        ),
                        border = BorderStroke(1.dp, SlateBorder)
                    )
                }
            }
        }

        // Input Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = { if (it.length <= 200) draftText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_placeholder),
                        color = TextMuted
                    )
                },
                supportingText = {
                    Text(
                        text = "${draftText.length}/200",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draftText.length >= 180) GlitchAmber else TextMuted
                    )
                },
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HumanCyan,
                    unfocusedBorderColor = SlateBorder,
                    focusedContainerColor = ObsidianBg,
                    unfocusedContainerColor = ObsidianBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            FilledIconButton(
                onClick = submitDraft,
                enabled = draftText.isNotBlank() && !isSending,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = HumanCyan,
                    contentColor = ObsidianBg,
                    disabledContainerColor = SlateSurfaceElevated,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(52.dp)
                    .padding(bottom = 12.dp)
                    .testTag("send_chat_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send_desc)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(msg: RoomChatMessage) {
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val containerColor = if (msg.isMine) HumanCyan.copy(alpha = 0.14f) else SlateSurfaceElevated
    val borderColor = if (msg.isMine) HumanCyan.copy(alpha = 0.6f) else SlateBorder
    val timeText = formatChatTime(msg.sentAtMs)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (msg.isMine) 14.dp else 4.dp,
                bottomEnd = if (msg.isMine) 4.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (msg.isMine) "You" else msg.senderNickname,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (msg.isMine) HumanCyanBright else GlitchAmber
                    )
                    Text(
                        text = "${formatPhaseTag(msg.roundNum, msg.phase)} • $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

private fun formatPhaseTag(roundNum: Int, phase: GamePhase): String {
    if (roundNum <= 0 || phase == GamePhase.LOBBY) return "LOBBY"
    val phaseLabel = when (phase) {
        GamePhase.ROLE_REVEAL -> "ROLES"
        GamePhase.PROMPT -> "PROMPT"
        GamePhase.DEBATE -> "DEBATE"
        GamePhase.VOTE -> "VOTE"
        GamePhase.REVEAL -> "REVEAL"
        GamePhase.MATCH_END -> "FINAL"
        else -> phase.name
    }
    return "R$roundNum • $phaseLabel"
}

private fun formatChatTime(epochMs: Long): String {
    if (epochMs <= 0L) return "now"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}
