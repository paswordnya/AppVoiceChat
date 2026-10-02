package com.pip.app.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pip.app.ui.account.AccountScreen
import com.pip.app.ui.components.BlobShape
import com.pip.app.ui.components.PipBlobFace
import com.pip.app.ui.history.HistoryScreen
import com.pip.app.ui.settings.SettingsScreen
import com.pip.app.ui.theme.Buddies
import com.pip.app.ui.theme.BuddyStore
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import com.pip.shared.chat.CommandParser
import com.pip.shared.domain.model.MessageRole
import com.pip.shared.domain.usecase.ChatSendResult
import com.pip.shared.network.dto.ChatServerEvent
import com.pip.shared.viewmodel.ChatSessionViewModel
import com.pip.shared.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.math.roundToInt

private data class ReplyReference(val senderLabel: String, val snippet: String)

private data class ChatMessage(val isUser: Boolean, val text: String, val replyTo: ReplyReference? = null)

private data class Topic(val emoji: String, val label: String, val prompt: String)

private val topics =
    listOf(
        Topic("💬", "Chat Santai", "Just want to chat for a bit."),
        Topic("📚", "Belajar", "Help me study for my exam."),
        Topic("💼", "Kerja", "What meetings do I have today?"),
        Topic("💻", "Teknologi", "What's new in tech today?"),
        Topic("🛒", "Belanja", "Add milk and eggs to my list."),
        Topic("🎬", "Hiburan", "Recommend something to watch."),
        Topic("🍔", "Kuliner", "Find a good dinner spot nearby."),
        Topic("✈️", "Travel", "Plan a weekend trip."),
        Topic("💰", "Keuangan", "How much did I spend this week?"),
        Topic("❤️", "Kesehatan", "Remind me to drink water."),
        Topic("🛠️", "Bantuan & Tutorial", "Show me how this works."),
        Topic("🎨", "Kreativitas", "Give me an idea to sketch."),
    )

private val personalities = listOf("calm", "chatty", "witty", "coach")

/**
 * Full-screen chat surface, ported from iOS's `KeyboardSessionView.swift`:
 * streams replies over the shared `ChatSession` (`thinking` -> `typing`
 * token-by-token -> `done`), restores history on open, mic button jumps
 * into Voice, swipe-right-to-reply, buddy switcher, offline-mode/queued
 * tagging per the client AI Router (PRD §8/§9/§13a).
 */
@Composable
fun ChatSessionScreen(onRequestVoice: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<ChatSessionViewModel>() }
    val settingsViewModel = remember { KoinPlatform.getKoin().get<SettingsViewModel>() }
    val settings by settingsViewModel.settings.collectAsState()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var streamingText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var isStreaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showAccount by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showBuddyPopup by remember { mutableStateOf(false) }
    var showCategoryPopup by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val result = settingsViewModel.load()
        if (result is com.pip.shared.core.result.PipResult.Success) {
            Buddies.all.find { it.id == result.value.buddy }?.let(BuddyStore::select)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.connect()
        viewModel.loadHistory()
        val history = viewModel.history.value
        if (history != null && history.messages.isNotEmpty()) {
            messages = history.messages.map { m -> ChatMessage(isUser = m.role == MessageRole.USER, text = m.content) }
        } else {
            isThinking = true
            viewModel.send("/start")
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatServerEvent.ThinkingStarted -> isThinking = true
                is ChatServerEvent.Token -> {
                    isThinking = false
                    isStreaming = true
                    streamingText = viewModel.appendStreamedToken(event.text)
                }
                is ChatServerEvent.Done -> {
                    isStreaming = false
                    isThinking = false
                    if (event.text.isNotEmpty()) {
                        messages = messages + ChatMessage(isUser = false, text = event.text)
                    }
                    streamingText = ""
                    viewModel.resetStreaming()
                }
                is ChatServerEvent.Command -> {
                    isThinking = false
                    isStreaming = false
                    messages = messages + ChatMessage(isUser = false, text = event.text)
                }
                is ChatServerEvent.Error -> {
                    isThinking = false
                    isStreaming = false
                    streamingText = ""
                    messages = messages + ChatMessage(isUser = false, text = "⚠️ ${event.message}")
                }
                is ChatServerEvent.Notice -> {
                    messages = messages + ChatMessage(isUser = false, text = "ℹ️ ${event.text}")
                }
            }
        }
    }

    LaunchedEffect(messages.size, streamingText, isThinking) {
        val lastIndex = messages.size + if (isStreaming || isThinking) 1 else 0
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val quoted = replyingTo
        val replyRef =
            quoted?.let {
                ReplyReference(
                    senderLabel = if (it.isUser) "Kamu" else BuddyStore.selected.name,
                    snippet = it.text.take(80),
                )
            }
        messages = messages + ChatMessage(isUser = true, text = text, replyTo = replyRef)
        draft = ""
        replyingTo = null
        isThinking = true

        scope.launch {
            // Commands (matches iOS's `!text.hasPrefix("/")` check) skip
            // reply metadata — a reply banner on `/model gemini` etc. isn't
            // meaningful (PRD §8's CommandParser).
            val result =
                if (CommandParser.isCommand(text)) {
                    viewModel.send(text)
                } else {
                    viewModel.send(text, replyRef?.senderLabel, replyRef?.snippet)
                }
            when (result) {
                is ChatSendResult.SentToServer -> Unit // resolved via the events flow above
                is ChatSendResult.OfflineReply -> {
                    isThinking = false
                    messages = messages + ChatMessage(isUser = false, text = "${result.text}\n\n(mode offline)")
                }
                ChatSendResult.Queued -> {
                    isThinking = false
                    messages = messages +
                        ChatMessage(
                            isUser = false,
                            text = "ℹ️ Pesan disimpan — akan dikirim otomatis begitu online lagi.",
                        )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(onClick = { showHistory = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "History", tint = PipTheme.ink, modifier = Modifier.size(18.dp))
            }

            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { showBuddyPopup = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(28.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(BlobShape())
                                .background(
                                    Brush.linearGradient(colors = listOf(BuddyStore.selected.colorFrom, BuddyStore.selected.colorTo)),
                                ),
                    )
                    PipBlobFace(modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(BuddyStore.selected.name, style = pipDisplay(16, FontWeight.SemiBold), color = PipTheme.ink)
                    Text(
                        if (isThinking || isStreaming) {
                            "thinking…"
                        } else if (messages.isEmpty()) {
                            "ready when you are"
                        } else {
                            "here you go"
                        },
                        style = pipBody(12),
                        color = PipTheme.ink.copy(alpha = 0.45f),
                    )
                }
            }

            HeaderIconButton(onClick = { showCategoryPopup = true }) {
                Icon(Icons.Filled.Apps, contentDescription = "Topics", tint = PipTheme.ink, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(8.dp))

            HeaderIconButton(onClick = { showAccount = true }) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = PipTheme.ink, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { message -> MessageBubble(message, onReply = { replyingTo = it }) }
            if (isStreaming) {
                item { StreamingBubble(streamingText) }
            } else if (isThinking) {
                item { ThinkingDots() }
            }
        }

        Column {
            replyingTo?.let { quoted ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PipTheme.ink.copy(alpha = 0.05f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(3.dp).height(28.dp).background(PipTheme.accent))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Membalas ${if (quoted.isUser) "diri sendiri" else BuddyStore.selected.name}",
                            style = pipBody(11, FontWeight.SemiBold),
                            color = PipTheme.accent,
                        )
                        Text(quoted.text, style = pipBody(12), color = PipTheme.ink.copy(alpha = 0.6f), maxLines = 1)
                    }
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Batalkan balasan",
                        tint = PipTheme.ink.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp).clickable { replyingTo = null },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Pip…", style = pipBody(14)) },
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        TextFieldDefaults.colors(
                            unfocusedContainerColor = PipTheme.ink.copy(alpha = 0.06f),
                            focusedContainerColor = PipTheme.ink.copy(alpha = 0.06f),
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                        ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )

                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PipTheme.ink.copy(alpha = 0.06f))
                            .clickable(onClick = onRequestVoice),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice", tint = PipTheme.ink, modifier = Modifier.size(16.dp))
                }

                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PipTheme.accent)
                            .clickable(onClick = ::send),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

        if (showAccount) {
            AccountScreen(
                onClose = { showAccount = false },
                onOpenSettings = { showAccount = false; showSettings = true },
            )
        }

        if (showSettings) {
            SettingsScreen(
                onClose = { showSettings = false },
                onOpenAccount = { showSettings = false; showAccount = true },
            )
        }

        if (showHistory) {
            HistoryScreen(onClose = { showHistory = false })
        }

        if (showBuddyPopup) {
            BuddyPersonalityPopup(
                selectedBuddyId = BuddyStore.selected.id,
                selectedPersonality = settings?.personality,
                onSelectBuddy = { id ->
                    Buddies.all.find { it.id == id }?.let { BuddyStore.select(it) }
                    scope.launch { settingsViewModel.setBuddy(id) }
                },
                onSelectPersonality = { p -> scope.launch { settingsViewModel.setPersonality(p) } },
                onDismiss = { showBuddyPopup = false },
            )
        }

        if (showCategoryPopup) {
            CategoryPopup(
                onSelect = { topic ->
                    draft = topic.prompt
                    showCategoryPopup = false
                },
                onDismiss = { showCategoryPopup = false },
            )
        }
    }
}

@Composable
private fun HeaderIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PipTheme.ink.copy(alpha = 0.06f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun BuddyPersonalityPopup(
    selectedBuddyId: String,
    selectedPersonality: String?,
    onSelectBuddy: (String) -> Unit,
    onSelectPersonality: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(PipTheme.cream)
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(BlobShape())
                            .background(
                                Brush.linearGradient(colors = listOf(BuddyStore.selected.colorFrom, BuddyStore.selected.colorTo)),
                            ),
                )
                PipBlobFace(modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Text(BuddyStore.selected.name, style = pipDisplay(18, FontWeight.SemiBold), color = PipTheme.ink)
            Spacer(Modifier.height(20.dp))

            Text(
                "SWITCH BUDDY",
                style = pipBody(12, FontWeight.SemiBold),
                color = PipTheme.ink.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Buddies.all.forEach { buddy ->
                    Column(
                        modifier = Modifier.clickable { onSelectBuddy(buddy.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(BlobShape())
                                    .background(Brush.linearGradient(colors = listOf(buddy.colorFrom, buddy.colorTo))),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buddy.name,
                            style = pipBody(11, if (buddy.id == selectedBuddyId) FontWeight.SemiBold else FontWeight.Normal),
                            color = PipTheme.ink,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "PERSONALITY",
                style = pipBody(12, FontWeight.SemiBold),
                color = PipTheme.ink.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                personalities.forEach { p ->
                    val active = p == selectedPersonality
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) PipTheme.accent else PipTheme.ink.copy(alpha = 0.06f))
                                .clickable { onSelectPersonality(p) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            p.replaceFirstChar { it.uppercase() },
                            style = pipBody(12, FontWeight.Medium),
                            color = if (active) Color.White else PipTheme.ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPopup(onSelect: (Topic) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(PipTheme.cream)
                    .padding(20.dp),
        ) {
            Text("Choose a topic", style = pipDisplay(18, FontWeight.SemiBold), color = PipTheme.ink)
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(topics) { topic ->
                    Column(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(PipTheme.ink.copy(alpha = 0.04f))
                                .clickable { onSelect(topic) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(PipTheme.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(topic.emoji, style = pipBody(17)) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            topic.label,
                            style = pipBody(10, FontWeight.Medium),
                            color = PipTheme.ink.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/** Telegram-style swipe-right-to-reply, ported from iOS's `MessageBubble`'s `DragGesture`. */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onReply: (ChatMessage) -> Unit,
) {
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffsetPx, label = "reply-drag")
    val replyThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        message.replyTo?.let { replyTo ->
            Row(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.width(3.dp).height(26.dp).background(PipTheme.accent.copy(alpha = 0.6f)))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(replyTo.senderLabel, style = pipBody(11, FontWeight.SemiBold), color = PipTheme.accent)
                    Text(replyTo.snippet, style = pipBody(11), color = PipTheme.ink.copy(alpha = 0.5f), maxLines = 1)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                        .pointerInput(message) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetPx > replyThresholdPx) onReply(message)
                                    dragOffsetPx = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, replyThresholdPx * 1.3f)
                                },
                            )
                        }
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (message.isUser) PipTheme.accent else PipTheme.ink.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // Long-press select + copy, matching SwiftUI's default
                // selectable `Text` on iOS — plain Compose `Text` isn't
                // selectable without this wrapper.
                SelectionContainer {
                    MarkdownText(
                        message.text,
                        style = pipBody(14),
                        color = if (message.isUser) Color.White else PipTheme.ink,
                        linkColor = if (message.isUser) Color.White else PipTheme.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PipTheme.ink.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            MarkdownText(text, style = pipBody(14), color = PipTheme.ink, linkColor = PipTheme.accent)
        }
    }
}

@Composable
private fun ThinkingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 4.dp)) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(PipTheme.accent),
            )
        }
    }
}
