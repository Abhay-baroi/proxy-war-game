package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.audio.GameAudioEngine
import com.example.audio.LyriaMusicRepository
import com.example.data.FirestoreGameRepository
import com.example.data.PromptRepository
import com.example.data.SessionStore
import com.example.data.configureAppCheck
import com.example.data.local.LocalSqlRepository
import com.example.data.local.ProxyWarsDatabase
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.ui.ProxyWarsViewModel
import com.example.ui.components.AudioStudioBottomSheet
import com.example.ui.components.GameOpeningOverlay
import com.example.ui.components.MatchHistorySqlSheet
import com.example.ui.components.RoomChatBottomSheet
import com.example.ui.components.RoomChatDockBar
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DebateVoteScreen
import com.example.ui.screens.JoinHubScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.PromptInputScreen
import com.example.ui.screens.RevealMatchEndScreen
import com.example.ui.screens.signOutFromGoogle
import com.example.ui.theme.GlitchAmber
import com.example.ui.theme.GlitchMagenta
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ProxyWarsTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private val deepLinkCode = mutableStateOf("")
    private val deepLinkHost = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleIncomingIntent(intent)

        setContent {
            ProxyWarsTheme {
                AppNavigation(
                    prefillCode = deepLinkCode.value,
                    prefillHost = deepLinkHost.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "proxywars" && uri.host == "join") {
            val code = uri.getQueryParameter("code").orEmpty().uppercase().take(4)
            val host = uri.getQueryParameter("host").orEmpty()
            if (code.isNotBlank()) {
                deepLinkCode.value = code
                deepLinkHost.value = host
            }
        }
    }
}

@Composable
private fun AppNavigation(
    prefillCode: String,
    prefillHost: String
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            val uid = user.uid
            val displayName = user.displayName?.split(" ")?.firstOrNull()?.take(18)?.ifBlank { "Player" } ?: "Player"
            key(uid) {
                val viewModel: ProxyWarsViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(databaseId)
                            val sqlDb = ProxyWarsDatabase.getInstance(app)
                            val sqlRepo = LocalSqlRepository(sqlDb.proxyWarsDao())
                            val audioEngine = GameAudioEngine(app)
                            val lyriaRepo = LyriaMusicRepository(app, sqlRepo)
                            ProxyWarsViewModel(
                                promptRepository = PromptRepository(app),
                                sessionStore = SessionStore(app),
                                firestoreRepo = FirestoreGameRepository(db),
                                sqlRepo = sqlRepo,
                                audioEngine = audioEngine,
                                lyriaRepo = lyriaRepo,
                                currentUserId = uid,
                                defaultDisplayName = displayName
                            )
                        }
                    }
                )
                ProxyWarsAuthenticatedRoot(
                    viewModel = viewModel,
                    prefillCode = prefillCode,
                    prefillHost = prefillHost,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ProxyWarsAuthenticatedRoot(
    viewModel: ProxyWarsViewModel,
    prefillCode: String,
    prefillHost: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isChatSheetOpen by rememberSaveable { mutableStateOf(false) }
    var showAudioStudioSheet by rememberSaveable { mutableStateOf(false) }
    var showMatchHistorySheet by rememberSaveable { mutableStateOf(false) }
    var seenMessageCount by rememberSaveable(state.roomCode) { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.onAppForegrounded(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val inActiveRoom = state.phase != GamePhase.JOIN_HUB && state.roomCode.isNotBlank()
    val privacyCurtainLocked = state.mode == GameMode.PASS_AND_PLAY &&
        state.passAndPlayTurn?.isPrivacyCurtainActive == true
    val showChatUi = inActiveRoom && !privacyCurtainLocked

    LaunchedEffect(state.chatMessages.size, isChatSheetOpen) {
        if (isChatSheetOpen) {
            seenMessageCount = state.chatMessages.size
        } else if (state.chatMessages.size < seenMessageCount) {
            seenMessageCount = state.chatMessages.size
        }
    }
    LaunchedEffect(showChatUi) {
        if (!showChatUi) {
            isChatSheetOpen = false
        }
    }

    val unreadCount = if (isChatSheetOpen) {
        0
    } else {
        (state.chatMessages.size - seenMessageCount).coerceAtLeast(0)
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = SlateSurface,
            title = { Text("Leave Active Match?", color = TextPrimary) },
            text = { Text("Exiting now will remove you from the current round.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leaveTable()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlitchMagenta, contentColor = TextPrimary)
                ) {
                    Text("LEAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("STAY")
                }
            }
        )
    }

    if (mediaState.showGameOpening && state.phase == GamePhase.JOIN_HUB) {
        GameOpeningOverlay(
            nickname = state.myNickname,
            musicEnabled = mediaState.musicEnabled,
            sfxEnabled = mediaState.sfxEnabled,
            onToggleMusic = { viewModel.toggleMusic(!mediaState.musicEnabled) },
            onToggleSfx = { viewModel.toggleSfx(!mediaState.sfxEnabled) },
            onEnterArena = viewModel::dismissGameOpening,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.statusBanner?.let { banner ->
            Surface(
                color = GlitchAmber.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, GlitchAmber),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.labelMedium,
                    color = GlitchAmber,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "phaseScreenTransition",
            modifier = Modifier.weight(1f)
        ) { phase ->
            when (phase) {
                GamePhase.JOIN_HUB -> JoinHubScreen(
                    state = state,
                    prefillCode = prefillCode,
                    prefillHost = prefillHost,
                    musicEnabled = mediaState.musicEnabled,
                    savedHistoryCount = mediaState.matchHistory.size,
                    bestVoteSpeedMs = mediaState.bestVoteSpeedMs,
                    onCreateCloudRoom = viewModel::createCloudRoom,
                    onJoinCloudRoom = viewModel::joinCloudRoom,
                    onJoinLan = viewModel::joinLanRoom,
                    onCreateLan = viewModel::createLanRoom,
                    onReconnect = viewModel::reconnectLastSession,
                    onStartPassAndPlay = viewModel::startPassAndPlay,
                    onOpenAudioStudio = { showAudioStudioSheet = true },
                    onOpenMatchHistory = { showMatchHistorySheet = true },
                    onReplayOpening = viewModel::replayGameOpening,
                    onSignOut = {
                        viewModel.leaveTable()
                        signOutFromGoogle(credentialManager, scope)
                    }
                )

                GamePhase.LOBBY -> LobbyScreen(
                    state = state,
                    musicEnabled = mediaState.musicEnabled,
                    onStartGame = viewModel::startTableMatch,
                    onLeaveTable = viewModel::leaveTable,
                    onToggleOpenTable = viewModel::toggleCloudOpenTable,
                    onOpenAudioStudio = { showAudioStudioSheet = true }
                )

                GamePhase.ROLE_REVEAL, GamePhase.PROMPT -> PromptInputScreen(
                    state = state,
                    aiReplySuggestion = mediaState.aiReplySuggestion,
                    isSuggestingReply = mediaState.isSuggestingReply,
                    aiReplyError = mediaState.aiReplyError,
                    onUnlockPassAndPlayTurn = viewModel::unlockPassAndPlayTurn,
                    onSubmitAnswer = viewModel::submitAnswer,
                    onRerollGlitch = viewModel::rerollGlitchOptions,
                    onHostSkipRoleReveal = viewModel::hostSkipPhase,
                    onSuggestAiReply = viewModel::suggestAiPromptReply,
                    onClearAiReplySuggestion = viewModel::clearAiReplySuggestion,
                    onLeaveTable = { showLeaveConfirm = true }
                )

                GamePhase.DEBATE, GamePhase.VOTE -> DebateVoteScreen(
                    state = state,
                    onUnlockPassAndPlayVote = viewModel::unlockPassAndPlayTurn,
                    onSelectCandidate = viewModel::selectVoteCandidate,
                    onLockVote = viewModel::lockSelectedVote,
                    onHostStartVoteNow = viewModel::hostSkipPhase,
                    onOpenRoomChat = {
                        seenMessageCount = state.chatMessages.size
                        isChatSheetOpen = true
                    },
                    onLeaveTable = { showLeaveConfirm = true }
                )

                GamePhase.REVEAL, GamePhase.MATCH_END -> RevealMatchEndScreen(
                    state = state,
                    onHostNextRound = viewModel::hostSkipPhase,
                    onPlayAgain = viewModel::playAgain,
                    onLeaveTable = viewModel::leaveTable
                )
            }
        }

        if (showChatUi) {
            RoomChatDockBar(
                roomCode = state.roomCode,
                messages = state.chatMessages,
                unreadCount = unreadCount,
                onOpenChat = {
                    seenMessageCount = state.chatMessages.size
                    isChatSheetOpen = true
                }
            )
        }
    }

    if (showChatUi && isChatSheetOpen) {
        RoomChatBottomSheet(
            roomCode = state.roomCode,
            playerCount = state.players.size,
            currentPhase = state.phase,
            currentRoundNum = state.roundNum,
            messages = state.chatMessages,
            isSending = state.isSendingChat,
            onSendMessage = viewModel::sendRoomChatMessage,
            onDismiss = {
                seenMessageCount = state.chatMessages.size
                isChatSheetOpen = false
            }
        )
    }

    if (showAudioStudioSheet) {
        AudioStudioBottomSheet(
            musicEnabled = mediaState.musicEnabled,
            sfxEnabled = mediaState.sfxEnabled,
            activePreset = mediaState.activePreset,
            selectedTrackId = mediaState.selectedTrackId,
            generatedTracks = mediaState.generatedTracks,
            isGeneratingTrack = mediaState.isGeneratingTrack,
            musicGenError = mediaState.musicGenError,
            onToggleMusic = viewModel::toggleMusic,
            onToggleSfx = viewModel::toggleSfx,
            onSelectPreset = viewModel::selectMusicPreset,
            onSelectGeneratedTrack = viewModel::selectGeneratedTrack,
            onDeleteTrack = viewModel::deleteGeneratedTrack,
            onGenerateTrack = viewModel::generateLyriaTrack,
            onPreviewSfx = viewModel::previewMoveSfx,
            onDismiss = {
                viewModel.clearMusicGenError()
                showAudioStudioSheet = false
            }
        )
    }

    if (showMatchHistorySheet) {
        MatchHistorySqlSheet(
            history = mediaState.matchHistory,
            bestVoteSpeedMs = mediaState.bestVoteSpeedMs,
            onDismiss = { showMatchHistorySheet = false }
        )
    }
}
