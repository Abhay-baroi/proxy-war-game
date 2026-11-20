package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.MatchHistoryEntity
import com.example.ui.theme.GlitchAmber
import com.example.ui.theme.HumanCyanBright
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchHistorySqlSheet(
    history: List<MatchHistoryEntity>,
    bestVoteSpeedMs: Long?,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(Icons.Filled.History, contentDescription = null, tint = HumanCyanBright)
                    Text("LOCAL SQL MATCH ARCHIVE", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close match history", tint = TextSecondary)
                }
            }

            Surface(
                color = SlateSurfaceElevated,
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("MATCHES SAVED", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${history.size}", style = MaterialTheme.typography.titleLarge, color = HumanCyanBright)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("FASTEST VOTE PR", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = bestVoteSpeedMs?.let { "${String.format(Locale.US, "%.2f", it / 1000.0)}s" } ?: "--",
                            style = MaterialTheme.typography.titleLarge,
                            color = GlitchAmber
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                Surface(
                    color = SlateSurfaceElevated,
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Finish any Cloud, LAN, or Pass & Play match to archive scores and vote speed telemetry locally in Room SQLite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.playedAtMs))
                        Surface(
                            color = SlateSurfaceElevated,
                            border = BorderStroke(1.dp, if (item.wonMatch) SignalEmerald else SlateBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (item.wonMatch) {
                                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = GlitchAmber, modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text(
                                            text = "Room ${item.roomCode} • Rank #${item.rank}/${item.totalPlayers}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${item.modeName} • $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                                Text(
                                    text = "${item.finalScore} pts",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (item.wonMatch) SignalEmerald else HumanCyanBright
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
