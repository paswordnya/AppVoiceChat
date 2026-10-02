package com.pip.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pip.app.audio.AudioCapture
import com.pip.app.audio.AudioPlayback
import com.pip.app.ui.components.BlobShape
import com.pip.app.ui.components.PipBlobFace
import com.pip.app.ui.components.rememberBlinkState
import com.pip.app.ui.theme.BuddyStore
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import com.pip.shared.domain.model.MessageRole
import com.pip.shared.network.dto.VoiceServerEvent
import com.pip.shared.viewmodel.VoiceSessionViewModel
import com.pip.shared.voice.ListeningMode
import com.pip.shared.voice.VoiceTurnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Full-screen voice session, ported from iOS's `VoiceSessionView.swift`:
 * animated mascot reflecting the shared `VoiceTurnState`, transcript/reply
 * display, feedback buttons, buddy switcher, listening-mode/voice-mode
 * pickers, and real mic capture / TTS playback (`AudioRecord`/`AudioTrack`,
 * PRD §7 Native/Android) (PRD §7/§13a). In PTT mode, press-and-hold the
 * mascot to talk — mirrors iOS's `beginTalking`/`endTalking` gating in
 * [com.pip.app.audio.AudioCapture] (the tap stays installed; PTT just
 * gates whether captured bytes are actually sent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSessionScreen(onEnd: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<VoiceSessionViewModel>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var transcript by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var lastRequestId by remember { mutableStateOf<String?>(null) }
    var feedbackSent by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState()
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var listeningMode by remember { mutableStateOf(ListeningMode.RESPONSIVE) }
    var voiceMode by remember { mutableStateOf("a") }
    var isTalking by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()

    LaunchedEffect(showHistory) {
        // Loaded on demand rather than eagerly on screen entry — this is
        // read-only past-turn context, not needed for the live session to
        // function, so there's no reason to pay the round trip unless the
        // user actually opens the sheet.
        if (showHistory) viewModel.loadHistory()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted = granted
            permissionDenied = !granted
        }
    LaunchedEffect(Unit) {
        if (!micGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val audioCapture = remember { AudioCapture(onChunk = { bytes -> viewModel.sendAudio(bytes) }) }
    val audioPlayback = remember { AudioPlayback() }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    // Continuous listening modes always send; PTT only sends while held.
    LaunchedEffect(listeningMode) {
        isTalking = false
        audioCapture.setSendingEnabled(listeningMode != ListeningMode.PTT)
    }

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(micGranted) {
        if (micGranted) {
            // `USAGE_VOICE_COMMUNICATION` audio (both capture and playback)
            // routes to the earpiece by default — very quiet — unless
            // speakerphone is forced on. Mirrors iOS's AVAudioSession
            // `.defaultToSpeaker` option (PRD 11.1).
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            // `STREAM_VOICE_CALL`'s own volume level isn't the one the
            // physical volume rocker shows/controls outside an active
            // session — it can sit at a quiet default regardless of
            // speakerphone routing, so force it up explicitly too.
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                0,
            )
            audioPlayback.start()
            audioCapture.start()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioCapture.stop()
            audioPlayback.stop()
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    LaunchedEffect(viewModel) {
        launch(Dispatchers.IO) {
            viewModel.audioFrames.collect { bytes -> audioPlayback.play(bytes) }
        }
        viewModel.events.collect { event ->
            when (event) {
                is VoiceServerEvent.Transcript -> {
                    transcript = event.text
                    if (event.final) lastRequestId = null
                }
                is VoiceServerEvent.Reply -> {
                    reply = event.text
                    if (event.final) {
                        lastRequestId = event.requestId
                        feedbackSent = false
                    }
                }
                is VoiceServerEvent.Interrupt -> {
                    audioPlayback.clearQueued()
                    transcript = ""
                    reply = ""
                    lastRequestId = null
                }
                is VoiceServerEvent.Error -> notice = event.message
                is VoiceServerEvent.Notice -> notice = event.text
                else -> Unit
            }
        }
    }

    val breathing by animateFloatAsState(
        targetValue = if (state == VoiceTurnState.LISTENING) 1.11f else 1.03f,
        animationSpec = infiniteRepeatable(tween(if (state == VoiceTurnState.LISTENING) 700 else 2000), RepeatMode.Reverse),
        label = "breathing",
    )
    val blink = rememberBlinkState()

    fun endSession() {
        audioCapture.stop()
        audioPlayback.stop()
        viewModel.stop()
        onEnd()
    }

    fun tapBlob() {
        if (state == VoiceTurnState.SPEAKING) {
            audioPlayback.clearQueued()
            reply = ""
            transcript = ""
            lastRequestId = null
        }
    }

    fun beginTalking() {
        if (listeningMode != ListeningMode.PTT || isTalking) return
        isTalking = true
        audioCapture.setSendingEnabled(true)
        viewModel.sendPushToTalkStart()
    }

    fun endTalking() {
        if (listeningMode != ListeningMode.PTT || !isTalking) return
        isTalking = false
        audioCapture.setSendingEnabled(false)
        viewModel.sendPushToTalkEnd()
    }

    fun switchListeningMode(mode: ListeningMode) {
        if (mode == listeningMode) return
        listeningMode = mode
        transcript = ""
        reply = ""
        scope.launch {
            viewModel.stop()
            viewModel.updateListeningMode(mode)
            viewModel.start()
        }
    }

    fun switchVoiceMode(mode: String) {
        if (mode == voiceMode) return
        voiceMode = mode
        transcript = ""
        reply = ""
        scope.launch {
            viewModel.stop()
            viewModel.updateVoiceMode(mode)
            viewModel.start()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = BuddyStore::cycleNext),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(BuddyStore.selected.name, style = pipDisplay(16, FontWeight.SemiBold), color = PipTheme.ink)
                Text(
                    if (permissionDenied) "mikrofon dimatikan" else phaseLabel(state),
                    style = pipBody(12),
                    color = PipTheme.ink.copy(alpha = 0.45f),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PipTheme.ink.copy(alpha = 0.06f))
                        .clickable { showHistory = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.History, contentDescription = "Riwayat", tint = PipTheme.ink, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PipTheme.ink.copy(alpha = 0.06f))
                        .clickable(onClick = ::endSession),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = PipTheme.ink, modifier = Modifier.size(16.dp))
            }
        }

        VoiceModePicker(
            current = voiceMode,
            onSelect = ::switchVoiceMode,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp),
        )
        ListeningModePicker(
            current = listeningMode,
            onSelect = ::switchListeningMode,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
        )

        notice?.let {
            Text(
                it,
                style = pipBody(12),
                color = PipTheme.accent,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PipTheme.accent.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (permissionDenied) {
                Text(
                    "Akses mikrofon belum diizinkan. Aktifkan lewat Pengaturan.",
                    style = pipBody(14),
                    color = PipTheme.ink.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            } else {
                Text(
                    if (state == VoiceTurnState.LISTENING && transcript.isEmpty()) "Mendengarkan…" else transcript,
                    style = pipBody(14),
                    color = PipTheme.ink.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            val glowAlpha =
                when (state) {
                    VoiceTurnState.LISTENING -> 0.2f
                    VoiceTurnState.THINKING, VoiceTurnState.PROCESSING -> 0.22f
                    VoiceTurnState.SPEAKING -> 0.3f
                    else -> 0.05f
                }

            val blobGesture =
                if (listeningMode == ListeningMode.PTT) {
                    Modifier.pointerInput(listeningMode) {
                        detectTapGestures(
                            onPress = {
                                beginTalking()
                                tryAwaitRelease()
                                endTalking()
                            },
                        )
                    }
                } else {
                    Modifier.clickable(onClick = ::tapBlob)
                }

            Box(
                modifier = Modifier.size(190.dp).then(blobGesture),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(BuddyStore.selected.colorTo.copy(alpha = glowAlpha), Color.Transparent),
                                ),
                            ),
                )

                Box(
                    modifier =
                        Modifier
                            .size((180 * breathing).dp)
                            .clip(BlobShape())
                            .background(Brush.linearGradient(colors = listOf(BuddyStore.selected.colorFrom, BuddyStore.selected.colorTo))),
                )
                PipBlobFace(
                    modifier = Modifier.size((180 * breathing).dp),
                    talkLevel = if (state == VoiceTurnState.SPEAKING) 0.5f else 0f,
                    eyeOpen = blink.eyeOpen.value,
                )
            }

            Spacer(Modifier.height(20.dp))

            if ((state == VoiceTurnState.SPEAKING || state == VoiceTurnState.THINKING) && reply.isNotEmpty()) {
                Text(
                    reply,
                    style = pipBody(15, FontWeight.Medium),
                    color = PipTheme.ink,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(PipTheme.ink.copy(alpha = 0.05f))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                )
                if (lastRequestId != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(
                            Icons.Filled.ThumbUp,
                            contentDescription = "Thumbs up",
                            tint = PipTheme.ink.copy(alpha = if (feedbackSent) 0.25f else 0.55f),
                            modifier =
                                Modifier.size(20.dp).clickable(enabled = !feedbackSent) {
                                    val requestId = lastRequestId ?: return@clickable
                                    feedbackSent = true
                                    scope.launch { viewModel.sendFeedback(requestId, true) }
                                },
                        )
                        Icon(
                            Icons.Filled.ThumbDown,
                            contentDescription = "Thumbs down",
                            tint = PipTheme.ink.copy(alpha = if (feedbackSent) 0.25f else 0.55f),
                            modifier =
                                Modifier.size(20.dp).clickable(enabled = !feedbackSent) {
                                    val requestId = lastRequestId ?: return@clickable
                                    feedbackSent = true
                                    scope.launch { viewModel.sendFeedback(requestId, false) }
                                },
                        )
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFD64545).copy(alpha = 0.08f))
                    .clickable(onClick = ::endSession)
                    .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Akhiri obrolan", style = pipDisplay(15, FontWeight.SemiBold), color = Color(0xFFD64545))
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }, sheetState = historySheetState) {
            VoiceHistoryList(messages = history?.messages.orEmpty())
        }
    }
}

/**
 * Read-only past turns for this voice session — same `messages` table/
 * session_id as the Chat screen's history (both mode_b_pipeline.py and
 * voice_mode_a.py persist voice turns via `session_store.add_message`), so
 * this reuses [com.pip.shared.viewmodel.VoiceSessionViewModel.history]
 * rather than a voice-specific fetch. Already scoped to the logged-in user:
 * `GET /api/session/{id}/history` 403s on a session_id owned by someone
 * else, so there's nothing further to filter here.
 */
@Composable
private fun VoiceHistoryList(messages: List<com.pip.shared.domain.model.Message>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Riwayat obrolan suara", style = pipDisplay(15, FontWeight.SemiBold), color = PipTheme.ink)
        Spacer(Modifier.height(12.dp))
        if (messages.isEmpty()) {
            Text(
                "Belum ada riwayat untuk sesi ini.",
                style = pipBody(13),
                color = PipTheme.ink.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { message -> VoiceHistoryBubble(message) }
            }
        }
    }
}

@Composable
private fun VoiceHistoryBubble(message: com.pip.shared.domain.model.Message) {
    val isUser = message.role == MessageRole.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isUser) PipTheme.accent else PipTheme.ink.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(message.content, style = pipBody(14), color = if (isUser) Color.White else PipTheme.ink)
        }
    }
}

private fun phaseLabel(state: VoiceTurnState): String =
    when (state) {
        VoiceTurnState.PAUSED -> "dijeda"
        VoiceTurnState.THINKING, VoiceTurnState.PROCESSING -> "mikir…"
        VoiceTurnState.SPEAKING -> "nih jawabannya"
        else -> "siap dengerin"
    }

/** "a" (Gemini Live) / "b" (custom cascaded pipeline) — mirrors iOS's dev-facing toggle. */
@Composable
private fun VoiceModePicker(
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedPicker(
        options = listOf("a" to "Mode A", "b" to "Mode B"),
        current = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun ListeningModePicker(
    current: ListeningMode,
    onSelect: (ListeningMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedPicker(
        options =
            listOf(
                ListeningMode.RESPONSIVE to "Responsif",
                ListeningMode.PATIENT to "Sabar",
                ListeningMode.PTT to "Tahan bicara",
            ),
        current = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun <T> SegmentedPicker(
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(PipTheme.ink.copy(alpha = 0.06f)).padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = value == current
            Text(
                label,
                style = pipDisplay(11, FontWeight.SemiBold),
                color = if (selected) PipTheme.accent else PipTheme.ink.copy(alpha = 0.5f),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
