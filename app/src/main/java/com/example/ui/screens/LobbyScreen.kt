package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.GameUiState
import com.example.ui.components.LobbyThreeDShowcaseCard
import com.example.ui.components.PlayerRosterGrid
import com.example.ui.theme.GlitchAmber
import com.example.ui.theme.GlitchMagenta
import com.example.ui.theme.GlitchMagentaBright
import com.example.ui.theme.HumanCyan
import com.example.ui.theme.HumanCyanBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.QrCodeGenerator

@Composable
fun LobbyScreen(
    state: GameUiState,
    musicEnabled: Boolean = true,
    onStartGame: () -> Unit,
    onLeaveTable: () -> Unit,
    onToggleOpenTable: (Boolean) -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onLeaveTable() }
    var showThreeDCore by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val isCloud = state.mode == com.example.model.GameMode.CLOUD_HOST || state.mode == com.example.model.GameMode.CLOUD_CLIENT
    val deepLink = remember(state.roomCode, state.hostAddress, isCloud) {
        if (isCloud) {
            "proxywars://join?code=${state.roomCode}"
        } else {
            "proxywars://join?code=${state.roomCode}&host=${state.hostAddress}"
        }
    }
    val qrBitmap = remember(deepLink) {
        QrCodeGenerator.generateQrBitmap(deepLink, 480)
    }

    val glitchCount = if (state.players.size >= 8) 2 else 1

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
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WAITING LOBBY",
                        style = MaterialTheme.typography.labelMedium,
                        color = HumanCyanBright
                    )
                    Text(
                        text = "ROOM ${state.roomCode}",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenAudioStudio,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lobby_audio_studio_button")
                    ) {
                        Icon(
                            imageVector = if (musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                            contentDescription = "Open Sound & Lyria Studio",
                            tint = if (musicEnabled) HumanCyanBright else TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onLeaveTable,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("leave_lobby_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave Lobby",
                            tint = TextSecondary
                        )
                    }
                }
            }

            LobbyThreeDShowcaseCard(
                stageMode = "LOBBY",
                expanded = showThreeDCore,
                onToggleExpand = { showThreeDCore = !showThreeDCore }
            )

            // QR Code + Host Address Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.5.dp, HumanCyan.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Room QR Code for ${state.roomCode}",
                            modifier = Modifier
                                .size(124.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, HumanCyan, RoundedCornerShape(14.dp))
                                .background(ObsidianBg)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCloud) Icons.Filled.Cloud else Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = HumanCyanBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isCloud) "CLOUD ROOM CODE" else "HOST ADDRESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = HumanCyanBright
                            )
                        }
                        Text(
                            text = if (isCloud) state.roomCode else state.hostAddress,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isCloud) {
                                "Players scan QR or enter 4-letter code ${state.roomCode}."
                            } else {
                                "Same Wi-Fi players scan QR or enter Code + IP."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Proxy Wars Invite", deepLink))
                                Toast.makeText(context, "Invite link copied", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("copy_invite_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("COPY INVITE", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Roster Header & Glitch Allocation Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYERS JOINED (${state.players.size}/10)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Surface(
                    color = GlitchMagenta.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, GlitchMagenta)
                ) {
                    Text(
                        text = "$glitchCount GLITCH ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlitchMagentaBright,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (isCloud) {
                Surface(
                    color = SlateSurface,
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (state.isOpenTable) Icons.Filled.Public else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (state.isOpenTable) HumanCyanBright else GlitchAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (state.isOpenTable) "Open Table (Public)" else "Private Room (Code Only)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (state.isOpenTable) {
                                        "Listed in Open Tables matchmaker"
                                    } else {
                                        "Only players with code or QR can join"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (state.isHost) {
                            Switch(
                                checked = state.isOpenTable,
                                onCheckedChange = onToggleOpenTable,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = HumanCyan
                                ),
                                modifier = Modifier.testTag("lobby_open_table_switch")
                            )
                        }
                    }
                }
            }

            PlayerRosterGrid(players = state.players)

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

            Spacer(Modifier.height(4.dp))

            if (state.isHost) {
                val canStart = state.players.size >= 4 && !state.isConnecting
                Button(
                    onClick = onStartGame,
                    enabled = canStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canStart) HumanCyan else SlateSurfaceElevated,
                        contentColor = if (canStart) ObsidianBg else TextSecondary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_game_button")
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(
                            color = HumanCyanBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("STARTING MATCH…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.players.size >= 4) {
                                "START 3-ROUND MATCH"
                            } else {
                                "NEED ${4 - state.players.size} MORE TO START (MIN 4)"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                Surface(
                    color = SlateSurface,
                    border = BorderStroke(1.dp, GlitchAmber.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Waiting for Host to start the match...",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlitchAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    )
                }
            }
        }
    }
}
