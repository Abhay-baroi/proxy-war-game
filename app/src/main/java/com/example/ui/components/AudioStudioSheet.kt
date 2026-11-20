package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.LobbyMusicPreset
import com.example.audio.LyriaModelTier
import com.example.data.local.GeneratedTrackEntity
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioBottomSheet(
    musicEnabled: Boolean,
    sfxEnabled: Boolean,
    activePreset: LobbyMusicPreset,
    selectedTrackId: Long?,
    generatedTracks: List<GeneratedTrackEntity>,
    isGeneratingTrack: Boolean,
    musicGenError: String?,
    onToggleMusic: (Boolean) -> Unit,
    onToggleSfx: (Boolean) -> Unit,
    onSelectPreset: (LobbyMusicPreset) -> Unit,
    onSelectGeneratedTrack: (GeneratedTrackEntity) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onGenerateTrack: (prompt: String, tier: LyriaModelTier) -> Unit,
    onPreviewSfx: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var promptText by rememberSaveable {
        mutableStateOf("Dark cyberpunk campus lounge beat with analog synth bass and crisp percussion")
    }
    var selectedTier by rememberSaveable { mutableStateOf(LyriaModelTier.CLIP_30S) }

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
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = HumanCyanBright)
                    Text("SOUND & LYRIA MUSIC STUDIO", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close audio studio", tint = TextSecondary)
                }
            }

            // Music & SFX toggles
            Surface(
                color = SlateSurfaceElevated,
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lobby & Arena Music", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Plays synthesized or Lyria tracks in Lobby & Opening", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Switch(checked = musicEnabled, onCheckedChange = onToggleMusic)
                    }
                    HorizontalDivider(color = SlateBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("In-Match Move SFX", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Prompts, votes, chat chirps, countdown ticks & reveals", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        if (sfxEnabled) {
                            TextButton(
                                onClick = onPreviewSfx,
                                modifier = Modifier.testTag("preview_sfx_button")
                            ) {
                                Text("TEST", style = MaterialTheme.typography.labelSmall, color = HumanCyanBright)
                            }
                        }
                        Switch(checked = sfxEnabled, onCheckedChange = onToggleSfx)
                    }
                }
            }

            // Built-in Synth Presets
            Text("BUILT-IN SYNTH PRESETS (INSTANT)", style = MaterialTheme.typography.labelSmall, color = HumanCyanBright)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LobbyMusicPreset.entries.forEach { preset ->
                    val active = selectedTrackId == null && activePreset == preset
                    FilterChip(
                        selected = active,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HumanCyan.copy(alpha = 0.22f),
                            selectedLabelColor = HumanCyanBright
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Lyria Music Generator (Firebase AI Logic)
            Text("GENERATE AI LOBBY TRACK (FIREBASE AI LOGIC)", style = MaterialTheme.typography.labelSmall, color = GlitchMagentaBright)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyriaModelTier.entries.forEach { tier ->
                    FilterChip(
                        selected = selectedTier == tier,
                        onClick = { selectedTier = tier },
                        label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GlitchMagenta.copy(alpha = 0.22f),
                            selectedLabelColor = GlitchMagentaBright
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { if (it.length <= 240) promptText = it },
                label = { Text("Music Style Prompt") },
                singleLine = false,
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlitchMagenta,
                    unfocusedBorderColor = SlateBorder,
                    focusedContainerColor = ObsidianBg,
                    unfocusedContainerColor = ObsidianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyria_prompt_input")
            )

            musicGenError?.let { err ->
                Surface(
                    color = GlitchMagenta.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, GlitchMagenta),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = GlitchMagentaBright,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }

            Button(
                onClick = { onGenerateTrack(promptText, selectedTier) },
                enabled = !isGeneratingTrack && promptText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GlitchMagenta, contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_lyria_track_button")
            ) {
                if (isGeneratingTrack) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("COMPOSING (${selectedTier.modelId})...", style = MaterialTheme.typography.labelMedium)
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GENERATE TRACK WITH ${selectedTier.modelId.uppercase()}", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (generatedTracks.isNotEmpty()) {
                Text("SAVED SQL TRACK LIBRARY", style = MaterialTheme.typography.labelSmall, color = SignalEmerald)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                ) {
                    items(generatedTracks, key = { it.id }) { track ->
                        val isPlayingThis = selectedTrackId == track.id
                        Surface(
                            onClick = { onSelectGeneratedTrack(track) },
                            color = if (isPlayingThis) SignalEmerald.copy(alpha = 0.14f) else SlateSurfaceElevated,
                            border = BorderStroke(1.dp, if (isPlayingThis) SignalEmerald else SlateBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Filled.CheckCircle else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = if (isPlayingThis) SignalEmerald else HumanCyanBright,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("${track.modelId} • ${track.durationSec}s", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                                IconButton(
                                    onClick = { onDeleteTrack(track.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete saved track", tint = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
