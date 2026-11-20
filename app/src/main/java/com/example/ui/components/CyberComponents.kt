package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.GlobalLeaderboardEntry
import com.example.model.PlayerState
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
fun ProxyWarsHeroArtwork(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, SlateBorder, RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF061622),
                        ObsidianBg,
                        Color(0xFF230815)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val step = 28.dp.toPx()

            // Subtle tactical grid
            var x = 0f
            while (x < w) {
                drawLine(
                    color = SlateBorder.copy(alpha = 0.25f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.dp.toPx()
                )
                x += step
            }
            var y = 0f
            while (y < h) {
                drawLine(
                    color = SlateBorder.copy(alpha = 0.25f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }

            // Cyan & Magenta signal glows
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HumanCyan.copy(alpha = 0.28f * pulse), Color.Transparent),
                    center = Offset(w * 0.2f, h * 0.5f),
                    radius = h * 0.9f
                ),
                center = Offset(w * 0.2f, h * 0.5f),
                radius = h * 0.9f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlitchMagenta.copy(alpha = 0.28f * pulse), Color.Transparent),
                    center = Offset(w * 0.8f, h * 0.5f),
                    radius = h * 0.9f
                ),
                center = Offset(w * 0.8f, h * 0.5f),
                radius = h * 0.9f
            )

            // Center split cyber mask silhouette
            val cx = w * 0.5f
            val cy = h * 0.44f
            val r = h * 0.27f

            val leftMask = Path().apply {
                moveTo(cx - 2.dp.toPx(), cy - r)
                lineTo(cx - r * 0.95f, cy - r * 0.55f)
                lineTo(cx - r * 1.05f, cy + r * 0.25f)
                lineTo(cx - r * 0.65f, cy + r * 0.95f)
                lineTo(cx - 2.dp.toPx(), cy + r * 1.15f)
                close()
            }
            drawPath(leftMask, color = HumanCyan.copy(alpha = 0.92f))

            val rightMask = Path().apply {
                moveTo(cx + 2.dp.toPx(), cy - r)
                lineTo(cx + r * 0.95f, cy - r * 0.55f)
                lineTo(cx + r * 1.05f, cy + r * 0.25f)
                lineTo(cx + r * 0.65f, cy + r * 0.95f)
                lineTo(cx + 2.dp.toPx(), cy + r * 1.15f)
                close()
            }
            drawPath(rightMask, color = GlitchMagenta.copy(alpha = 0.92f))

            // Visor eye slits
            drawRect(
                color = ObsidianBg,
                topLeft = Offset(cx - r * 0.72f, cy - r * 0.1f),
                size = Size(r * 0.46f, r * 0.22f)
            )
            drawRect(
                color = ObsidianBg,
                topLeft = Offset(cx + r * 0.26f, cy - r * 0.1f),
                size = Size(r * 0.46f, r * 0.22f)
            )

            // Amber glitch spark slash
            drawLine(
                color = GlitchAmber,
                start = Offset(cx - r * 0.3f, cy - r * 1.25f),
                end = Offset(cx + r * 0.3f, cy + r * 1.35f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PROXY WARS",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Text(
                text = "WHO'S THE BOT?  •  CAMPUS SOCIAL DEDUCTION",
                style = MaterialTheme.typography.labelSmall,
                color = HumanCyanBright
            )
        }
    }
}

@Composable
fun GlitchEmblemBadge(
    isGlitch: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = if (isGlitch) GlitchMagenta else HumanCyan
    val secondary = if (isGlitch) GlitchAmber else HumanCyanBright
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(primary.copy(alpha = 0.16f))
            .border(2.dp, primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = primary,
                radius = w * 0.44f,
                style = Stroke(width = 3.dp.toPx())
            )
            if (isGlitch) {
                // Visor bar + glitch diamond
                drawRect(
                    color = secondary,
                    topLeft = Offset(w * 0.2f, h * 0.38f),
                    size = Size(w * 0.6f, h * 0.16f)
                )
                drawLine(
                    color = primary,
                    start = Offset(w * 0.15f, h * 0.72f),
                    end = Offset(w * 0.85f, h * 0.28f),
                    strokeWidth = 2.5.dp.toPx()
                )
            } else {
                // Human shield pulse
                drawCircle(color = secondary, radius = w * 0.16f)
            }
        }
    }
}

@Composable
fun PhaseTimerBar(
    phaseLabel: String,
    remainingSeconds: Int,
    totalDurationMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val totalSec = (totalDurationMs / 1000f).coerceAtLeast(1f)
    val targetFraction = (remainingSeconds / totalSec).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(),
        label = "timerBar"
    )
    val urgentColor = if (remainingSeconds <= 5 && remainingSeconds > 0) GlitchAmber else accentColor

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor
            )
            Surface(
                color = urgentColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, urgentColor)
            ) {
                Text(
                    text = "${remainingSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = urgentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SlateSurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(urgentColor)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerRosterGrid(
    players: List<PlayerState>,
    showSubmissionTicks: Boolean = false,
    showVoteTicks: Boolean = false,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2
    ) {
        players.forEach { player ->
            val avatarColors = listOf(
                HumanCyan, GlitchMagenta, GlitchAmber, SignalEmerald, Color(0xFF38BDF8), Color(0xFFF472B6)
            )
            val badgeColor = avatarColors[kotlin.math.abs(player.nickname.hashCode()) % avatarColors.size]
            val doneFlag = (showSubmissionTicks && player.hasSubmittedAnswer) ||
                (showVoteTicks && player.hasVoted)

            Surface(
                modifier = Modifier.weight(1f, fill = true),
                color = SlateSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (doneFlag) SignalEmerald else SlateBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.22f))
                            .border(1.5.dp, badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = player.nickname.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = player.nickname,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (player.isConnected) TextPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (player.isHost) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Host",
                                    tint = GlitchAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = when {
                                !player.isConnected -> "Reconnecting..."
                                doneFlag -> "Locked in"
                                player.score > 0 -> "${player.score} pts"
                                else -> "Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                !player.isConnected -> GlitchAmber
                                doneFlag -> SignalEmerald
                                else -> TextSecondary
                            }
                        )
                    }
                    if (doneFlag) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Locked",
                            tint = SignalEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyCurtainOverlay(
    playerNickname: String,
    actionHint: String,
    onUnlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(2.dp, HumanCyan),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(HumanCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "Private screen",
                        tint = HumanCyanBright,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "PASS PHONE TO",
                    style = MaterialTheme.typography.labelMedium,
                    color = HumanCyanBright
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = playerNickname,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = actionHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(26.dp))
                Button(
                    onClick = onUnlock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HumanCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("unlock_turn_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I'M ${playerNickname.uppercase()} — REVEAL",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SlateSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, HumanCyan)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "9:16 SHAREABLE RECEIPT",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "9:16 Match Receipt Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("CLOSE")
                    }
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HumanCyan,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_share_receipt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE")
                    }
                }
            }
        }
    }
}

@Composable
fun RolePillBadge(isGlitch: Boolean) {
    val bg = if (isGlitch) GlitchMagenta.copy(alpha = 0.2f) else HumanCyan.copy(alpha = 0.18f)
    val border = if (isGlitch) GlitchMagenta else HumanCyan
    val fg = if (isGlitch) GlitchMagentaBright else HumanCyanBright
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isGlitch) "THE GLITCH" else "HUMAN",
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@Composable
fun CampusGlobalLeaderboardCard(
    entries: List<GlobalLeaderboardEntry>,
    maxVisible: Int = 10,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_leaderboard_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = GlitchAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CAMPUS AI-HUNTER LEADERBOARD",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlitchAmber
                    )
                }
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No cloud scores recorded yet. Finish a Cloud round detecting the AI to claim #1!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                entries.take(maxVisible).forEachIndexed { idx, entry ->
                    val highlightBorder = if (entry.isCurrentUser) HumanCyan else SlateBorder
                    val bgColor = if (entry.isCurrentUser) {
                        HumanCyan.copy(alpha = 0.12f)
                    } else {
                        SlateSurfaceElevated
                    }
                    Surface(
                        color = bgColor,
                        border = BorderStroke(1.dp, highlightBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when (idx) {
                                        0 -> GlitchAmber
                                        1 -> HumanCyanBright
                                        else -> TextSecondary
                                    },
                                    modifier = Modifier.width(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.nickname,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (entry.isCurrentUser) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = HumanCyan.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(999.dp),
                                                border = BorderStroke(1.dp, HumanCyan)
                                            ) {
                                                Text(
                                                    text = "YOU",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = HumanCyanBright,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${entry.glitchesCaught} AI caught • ${entry.roundsPlayed} rds",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${entry.totalPoints} PTS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (entry.lastRoundDelta > 0) {
                                    Text(
                                        text = "+${entry.lastRoundDelta} last",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalEmerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
