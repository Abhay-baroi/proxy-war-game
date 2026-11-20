package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.GlitchMagenta
import com.example.ui.theme.HumanCyan
import com.example.ui.theme.HumanCyanBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreeDStageView(
    stageMode: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(stageMode, webViewRef) {
        val safeMode = stageMode.replace("'", "")
        webViewRef?.evaluateJavascript("window.setStageMode && window.setStageMode('$safeMode');", null)
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#070B14"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val safeMode = stageMode.replace("'", "")
                        view?.evaluateJavascript("window.setStageMode && window.setStageMode('$safeMode');", null)
                    }
                }
                loadUrl("file:///android_asset/three_stage.html")
                webViewRef = this
            }
        },
        update = { view ->
            webViewRef = view
        },
        modifier = modifier
    )
}

@Composable
fun GameOpeningOverlay(
    nickname: String,
    musicEnabled: Boolean,
    sfxEnabled: Boolean,
    onToggleMusic: () -> Unit,
    onToggleSfx: () -> Unit,
    onEnterArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Full-bleed interactive 3D Three.js stage
        ThreeDStageView(
            stageMode = "OPENING",
            modifier = Modifier.fillMaxSize()
        )

        // Top compact audio control pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = SlateSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewInAr,
                        contentDescription = null,
                        tint = HumanCyanBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.opening_3d_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                color = SlateSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleMusic,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                            contentDescription = "Toggle lobby music",
                            tint = if (musicEnabled) HumanCyanBright else TextMuted
                        )
                    }
                    IconButton(
                        onClick = onToggleSfx,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (sfxEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle sound effects",
                            tint = if (sfxEnabled) HumanCyanBright else TextMuted
                        )
                    }
                }
            }
        }

        // Bottom viewport-compact opening dock (solid rgba surface, <=15-22% footprint)
        Surface(
            color = SlateSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, HumanCyan.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PROXY WARS // NEURAL ARENA",
                    style = MaterialTheme.typography.labelLarge,
                    color = HumanCyanBright
                )
                Text(
                    text = "Welcome, $nickname. Detect the undercover AI before it outbluffs your table.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onEnterArena,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HumanCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("enter_arena_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.opening_enter_arena),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun LobbyThreeDShowcaseCard(
    stageMode: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface,
        border = BorderStroke(1.dp, if (stageMode == "GLITCH") GlitchMagenta else SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewInAr,
                        contentDescription = null,
                        tint = HumanCyanBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "3D PROXY CORE (DRAG TO ROTATE)",
                        style = MaterialTheme.typography.labelMedium,
                        color = HumanCyanBright
                    )
                }
                TextButton(onClick = onToggleExpand) {
                    Text(
                        text = if (expanded) "HIDE" else "VIEW 3D",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                ) {
                    ThreeDStageView(
                        stageMode = stageMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
