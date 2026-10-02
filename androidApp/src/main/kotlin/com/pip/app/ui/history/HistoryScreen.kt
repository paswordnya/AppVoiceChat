package com.pip.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import com.pip.shared.domain.model.Message
import com.pip.shared.domain.model.MessageRole
import com.pip.shared.viewmodel.ChatSessionViewModel
import org.koin.mp.KoinPlatform

/** The redesign's History panel — search over the current session's real
 * message history ([ChatSessionViewModel.history], already loaded by
 * `ChatSessionScreen`'s own `LaunchedEffect`). Not the mockup's fabricated
 * multi-conversation, day-grouped list: the backend only has one active
 * session's scrollback today (`ChatRepository.getHistory()` returns a
 * single [com.pip.shared.domain.model.Conversation]), so this searches
 * that real thread instead of inventing conversations that don't exist. */
@Composable
fun HistoryScreen(onClose: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<ChatSessionViewModel>() }
    val conversation by viewModel.history.collectAsState()
    var query by remember { mutableStateOf("") }

    val messages = conversation?.messages.orEmpty()
    val filtered =
        if (query.isBlank()) {
            messages
        } else {
            messages.filter { it.content.contains(query, ignoreCase = true) }
        }

    Column(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "History",
                style = pipDisplay(20, FontWeight.SemiBold),
                color = PipTheme.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PipTheme.ink.copy(alpha = 0.06f))
                        .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = PipTheme.ink, modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("Search conversation", style = pipBody(14)) },
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                ),
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Belum ada percakapan." else "Tidak ada yang cocok.",
                    style = pipBody(13),
                    color = PipTheme.ink.copy(alpha = 0.4f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered) { message -> HistoryRow(message) }
            }
        }
    }
}

@Composable
private fun HistoryRow(message: Message) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            if (message.role == MessageRole.USER) "You" else "Pip",
            style = pipBody(12, FontWeight.SemiBold),
            color = PipTheme.accent,
        )
        Text(
            message.content,
            style = pipBody(14, FontWeight.Medium),
            color = PipTheme.ink,
            maxLines = 2,
        )
        Text(message.createdAt, style = pipBody(11), color = PipTheme.ink.copy(alpha = 0.4f))
    }
}
