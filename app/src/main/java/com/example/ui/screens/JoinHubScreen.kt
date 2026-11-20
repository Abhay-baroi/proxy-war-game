package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.model.GameUiState
import com.example.ui.components.CampusGlobalLeaderboardCard
import com.example.ui.components.ProxyWarsHeroArtwork
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JoinHubScreen(
    state: GameUiState,
    prefillCode: String,
    prefillHost: String,
    musicEnabled: Boolean = true,
    savedHistoryCount: Int = 0,
    bestVoteSpeedMs: Long? = null,
    onCreateCloudRoom: (nickname: String, openTable: Boolean) -> Unit,
    onJoinCloudRoom: (roomCode: String, nickname: String) -> Unit,
    onJoinLan: (roomCode: String, hostAddress: String, nickname: String) -> Unit,
    onCreateLan: (nickname: String) -> Unit,
    onReconnect: () -> Unit,
    onStartPassAndPlay: (List<String>) -> Unit,
    onOpenAudioStudio: () -> Unit = {},
    onOpenMatchHistory: () -> Unit = {},
    onReplayOpening: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = Cloud Online, 1 = LAN Wi-Fi, 2 = Pass & Play
    var selectedTab by remember { mutableIntStateOf(0) }
    var roomCode by remember { mutableStateOf(prefillCode) }
    var hostAddress by remember { mutableStateOf(prefillHost) }
    var nickname by remember { mutableStateOf(state.myNickname.ifBlank { state.savedSession?.nickname ?: "" }) }
    var openTableToggle by remember { mutableStateOf(true) }

    LaunchedEffect(prefillCode, prefillHost) {
        if (prefillCode.isNotBlank()) {
            roomCode = prefillCode
            selectedTab = if (prefillHost.isNotBlank()) 1 else 0
        }
        if (prefillHost.isNotBlank()) {
            hostAddress = prefillHost
            selectedTab = 1
        }
    }

    var newPlayerName by remember { mutableStateOf("") }
    val passPlayPlayers = remember { mutableStateListOf<String>() }

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
            // Top bar with signed-in player tag & sign out
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Text(
                        text = "SIGNED IN: ${state.myNickname.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HumanCyanBright,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = TextSecondary
                    )
                }
            }

            ProxyWarsHeroArtwork()

            // Quick utility bar: Sound & Lyria Studio | Room SQLite Archive | 3D Neural Core Replay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenAudioStudio,
                    border = BorderStroke(1.dp, if (musicEnabled) HumanCyan.copy(alpha = 0.65f) else SlateBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_audio_studio_button")
                ) {
                    Icon(
                        imageVector = if (musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                        contentDescription = "Sound and Lyria Music Studio",
                        tint = if (musicEnabled) HumanCyanBright else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (musicEnabled) "Sound & Lyria" else "Sound Muted",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = onOpenMatchHistory,
                    border = BorderStroke(1.dp, GlitchAmber.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_sql_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Local SQL Match Archive",
                        tint = GlitchAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    val prText = bestVoteSpeedMs?.let {
                        " • ${String.format(java.util.Locale.US, "%.1f", it / 1000.0)}s"
                    } ?: ""
                    Text(
                        text = if (savedHistoryCount > 0) "SQL ($savedHistoryCount)$prText" else "SQL Archive",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = SlateSurface,
                    border = BorderStroke(1.dp, HumanCyan.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    IconButton(
                        onClick = onReplayOpening,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("replay_3d_opening_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ViewInAr,
                            contentDescription = "Inspect 3D Neural Core",
                            tint = HumanCyanBright
                        )
                    }
                }
            }

            // Reconnect Banner for LAN session if present
            state.savedSession?.let { saved ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = HumanCyan.copy(alpha = 0.14f)),
                    border = BorderStroke(1.5.dp, HumanCyan),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PREVIOUS LAN SESSION",
                                style = MaterialTheme.typography.labelSmall,
                                color = HumanCyanBright
                            )
                            Text(
                                text = "Room ${saved.roomCode} as ${saved.nickname}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = onReconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HumanCyan,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("reconnect_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("REJOIN")
                        }
                    }
                }
            }

            // 3-Mode Switcher: Cloud Online | LAN Wi-Fi | Pass & Play
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Cloud", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HumanCyan.copy(alpha = 0.22f),
                        selectedLabelColor = HumanCyanBright,
                        selectedLeadingIconColor = HumanCyanBright
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tab_cloud_mode")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("LAN Wi-Fi", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HumanCyan.copy(alpha = 0.22f),
                        selectedLabelColor = HumanCyanBright,
                        selectedLeadingIconColor = HumanCyanBright
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tab_lan_mode")
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Pass & Play", style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GlitchMagenta.copy(alpha = 0.22f),
                        selectedLabelColor = GlitchMagentaBright,
                        selectedLeadingIconColor = GlitchMagentaBright
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tab_pass_play_mode")
                )
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

            when (selectedTab) {
                0 -> {
                    // CLOUD ROOMS + OPEN TABLES
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it.take(18) },
                                label = { Text("Display Nickname") },
                                placeholder = { Text("e.g. Alex") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HumanCyan,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedLabelColor = HumanCyanBright
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("nickname_input")
                            )

                            OutlinedTextField(
                                value = roomCode,
                                onValueChange = { raw ->
                                    roomCode = raw.uppercase().filter { it.isLetterOrDigit() }.take(4)
                                },
                                label = { Text("4-Letter Room Code") },
                                placeholder = { Text("X7K9") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HumanCyan,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedLabelColor = HumanCyanBright
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("room_code_input")
                            )

                            Button(
                                onClick = { onJoinCloudRoom(roomCode, nickname) },
                                enabled = !state.isConnecting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HumanCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("join_table_button")
                            ) {
                                if (state.isConnecting) {
                                    CircularProgressIndicator(color = ObsidianBg, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                } else {
                                    Text("JOIN CLOUD TABLE", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Open Table Matchmaking", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("Let solo passersby discover & join", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                Switch(
                                    checked = openTableToggle,
                                    onCheckedChange = { openTableToggle = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = HumanCyan)
                                )
                            }

                            OutlinedButton(
                                onClick = { onCreateCloudRoom(nickname, openTableToggle) },
                                enabled = !state.isConnecting,
                                border = BorderStroke(1.5.dp, GlitchMagenta),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlitchMagentaBright),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("create_table_button")
                            ) {
                                Text("CREATE CLOUD TABLE", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Live Open Tables Matchmaker
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Public, contentDescription = null, tint = SignalEmerald, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("OPEN TABLES (${state.openTables.size})", style = MaterialTheme.typography.labelLarge, color = SignalEmerald)
                            }
                            if (state.openTables.isEmpty()) {
                                Text(
                                    text = "No open tables active right now — create one above with Open Table enabled!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            } else {
                                state.openTables.forEach { table ->
                                    Surface(
                                        color = SlateSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, SlateBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("ROOM ${table.roomCode}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                                Text("${table.playerCount}/10 players waiting", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            }
                                            Button(
                                                onClick = { onJoinCloudRoom(table.roomCode, nickname) },
                                                colors = ButtonDefaults.buttonColors(containerColor = SignalEmerald, contentColor = ObsidianBg),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Text("JOIN")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    CampusGlobalLeaderboardCard(
                        entries = state.globalLeaderboard,
                        maxVisible = 5
                    )
                }

                1 -> {
                    // LAN WI-FI MODE
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it.take(18) },
                                label = { Text("Your Nickname") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = roomCode,
                                    onValueChange = { raw ->
                                        roomCode = raw.uppercase().filter { it.isLetterOrDigit() }.take(4)
                                    },
                                    label = { Text("4-Letter Code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    modifier = Modifier.weight(0.44f)
                                )
                                OutlinedTextField(
                                    value = hostAddress,
                                    onValueChange = { hostAddress = it },
                                    label = { Text("Host IP:Port") },
                                    placeholder = { Text("192.168.1.10:8765") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.56f)
                                )
                            }
                            Button(
                                onClick = { onJoinLan(roomCode, hostAddress, nickname) },
                                enabled = !state.isConnecting,
                                colors = ButtonDefaults.buttonColors(containerColor = HumanCyan, contentColor = ObsidianBg),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Text("JOIN LAN TABLE", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { onCreateLan(nickname) },
                                enabled = !state.isConnecting,
                                border = BorderStroke(1.5.dp, GlitchMagenta),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlitchMagentaBright),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text("CREATE LAN TABLE (HOST)", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                else -> {
                    // PASS & PLAY MODE
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TABLE ROSTER (${passPlayPlayers.size}/10)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GlitchMagentaBright
                                )
                                TextButton(
                                    onClick = {
                                        passPlayPlayers.clear()
                                        passPlayPlayers.addAll(listOf("Alex", "Jordan", "Sam", "Taylor", "Morgan"))
                                    },
                                    modifier = Modifier.testTag("prefill_demo_seats_button")
                                ) {
                                    Text("Quick-Fill 5 Seats", color = HumanCyanBright)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newPlayerName,
                                    onValueChange = { newPlayerName = it.take(18) },
                                    label = { Text("Add Player Nickname") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pass_play_name_input")
                                )
                                Button(
                                    onClick = {
                                        val trimmed = newPlayerName.trim()
                                        if (trimmed.isNotEmpty() && passPlayPlayers.size < 10) {
                                            passPlayPlayers.add(trimmed)
                                            newPlayerName = ""
                                        }
                                    },
                                    enabled = newPlayerName.isNotBlank() && passPlayPlayers.size < 10,
                                    colors = ButtonDefaults.buttonColors(containerColor = HumanCyan, contentColor = ObsidianBg),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("add_player_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add player")
                                }
                            }

                            if (passPlayPlayers.isEmpty()) {
                                Text(
                                    text = "Add 4 to 10 players passing this phone around the table. Privacy curtains hide each player's secret role and vote.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    passPlayPlayers.forEachIndexed { idx, name ->
                                        InputChip(
                                            selected = false,
                                            onClick = { passPlayPlayers.removeAt(idx) },
                                            label = { Text(name) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Remove $name",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = InputChipDefaults.inputChipColors(
                                                containerColor = SlateSurfaceElevated,
                                                labelColor = TextPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { onStartPassAndPlay(passPlayPlayers.toList()) },
                                enabled = passPlayPlayers.size >= 4,
                                colors = ButtonDefaults.buttonColors(containerColor = GlitchMagenta, contentColor = TextPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("start_pass_play_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (passPlayPlayers.size < 4) {
                                        "NEED ${4 - passPlayPlayers.size} MORE PLAYERS"
                                    } else {
                                        "START PASS & PLAY (${passPlayPlayers.size})"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface.copy(alpha = 0.7f)),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "165-SECOND ROUND CADENCE • 3 ROUNDS",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlitchAmber
                    )
                    Text(
                        text = "• Humans type a natural answer (≤140 chars).\n• The Glitch picks 1 of 3 AI lines (with 1 reroll).\n• 50s live debate → 20s vote → Card flip & 9:16 Receipt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
