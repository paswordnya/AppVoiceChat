package com.pip.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pip.app.ui.components.BlobShape
import com.pip.app.ui.components.PipBlobFace
import com.pip.app.ui.theme.Buddies
import com.pip.app.ui.theme.Buddy
import com.pip.app.ui.theme.BuddyStore
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import com.pip.shared.network.dto.UserSettingsDto
import com.pip.shared.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val personalities = listOf("calm", "chatty", "witty", "coach")

private val brandColorOptions =
    listOf(
        "#3B82F6" to "Blue",
        "#8B5CF6" to "Purple",
        "#22C55E" to "Green",
        "#F97316" to "Orange",
        "#EF4444" to "Red",
        "#EC4899" to "Pink",
        "#14B8A6" to "Teal",
    )

/** The redesign's Settings screen — buddy/personality, voice & reaction
 * toggles, speaking speed, brand color, and privacy. Distinct from
 * [com.pip.app.ui.profile.ProfileScreen] ("Personalisasi"), which is linked
 * from [com.pip.app.ui.account.AccountScreen] instead of from here. */
@Composable
fun SettingsScreen(onClose: () -> Unit, onOpenAccount: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<SettingsViewModel>() }
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.load()
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Your sidekick",
                    style = pipDisplay(18, FontWeight.SemiBold),
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

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isLoading || settings == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val s = settings!!
                    HeroCard(onOpenAccount)
                    BuddySwitcher(s.buddy) { id ->
                        Buddies.all.find { it.id == id }?.let(BuddyStore::select)
                        scope.launch { viewModel.setBuddy(id) }
                    }
                    PersonalityChips(s.personality) { scope.launch { viewModel.setPersonality(it) } }
                    VoiceAndReactionsCard(s, onToggleVoiceReplies = { scope.launch { viewModel.toggleVoiceReplies() } }, onToggleAnimatedReactions = { scope.launch { viewModel.toggleAnimatedReactions() } }, onToggleHaptics = { scope.launch { viewModel.toggleHaptics() } }, onSpeedChange = { scope.launch { viewModel.setSpeakingSpeed(it) } })
                    AppearanceCard(s, onBrandColor = { scope.launch { viewModel.setBrandColor(it) } })
                    PrivacyCard(s, onToggleStoreConversations = { scope.launch { viewModel.toggleStoreConversations() } })
                    OutlinedButton(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Clear all history") }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeroCard(onOpenAccount: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(64.dp)) {
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
        Spacer(Modifier.height(6.dp))
        Text(BuddyStore.selected.name, style = pipDisplay(18, FontWeight.SemiBold), color = PipTheme.ink)
        Spacer(Modifier.height(2.dp))
        Text(
            "your calm little sidekick",
            style = pipBody(13),
            color = PipTheme.ink.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(onClick = onOpenAccount)
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your account", style = pipBody(14, FontWeight.Medium), color = PipTheme.ink, modifier = Modifier.weight(1f))
            Text("›", style = pipDisplay(18), color = PipTheme.ink.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = pipBody(12, FontWeight.SemiBold),
        color = PipTheme.ink.copy(alpha = 0.45f),
    )
}

@Composable
private fun BuddySwitcher(selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Switch buddy")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Buddies.all.forEach { buddy ->
                BuddyChip(buddy, selected = buddy.id == selectedId, onClick = { onSelect(buddy.id) })
            }
        }
    }
}

@Composable
private fun BuddyChip(buddy: Buddy, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(BlobShape())
                    .background(Brush.linearGradient(colors = listOf(buddy.colorFrom, buddy.colorTo))),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buddy.name,
            style = pipBody(11, if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) PipTheme.ink else PipTheme.ink.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PersonalityChips(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Personality")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            personalities.forEach { p ->
                val active = p == selected
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) PipTheme.accent else PipTheme.ink.copy(alpha = 0.06f))
                            .clickable { onSelect(p) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        p.replaceFirstChar { it.uppercase() },
                        style = pipBody(13, FontWeight.Medium),
                        color = if (active) Color.White else PipTheme.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceAndReactionsCard(
    settings: UserSettingsDto,
    onToggleVoiceReplies: () -> Unit,
    onToggleAnimatedReactions: () -> Unit,
    onToggleHaptics: () -> Unit,
    onSpeedChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Voice & reactions")
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ToggleRow("Voice replies", settings.voiceReplies, onToggleVoiceReplies)
            ToggleRow("Animated reactions", settings.animatedReactions, onToggleAnimatedReactions)
            ToggleRow("Haptics", settings.haptics, onToggleHaptics, isLast = true)
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        ) {
            Text("Speaking speed", style = pipBody(14), color = PipTheme.ink)
            Spacer(Modifier.height(6.dp))
            var localValue by remember(settings.speakingSpeed) { mutableFloatStateOf(settings.speakingSpeed.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("slow", style = pipBody(12), color = PipTheme.ink.copy(alpha = 0.45f))
                Slider(
                    value = localValue,
                    onValueChange = { localValue = it },
                    onValueChangeFinished = { onSpeedChange(localValue.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Text("fast", style = pipBody(12), color = PipTheme.ink.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun AppearanceCard(settings: UserSettingsDto, onBrandColor: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Appearance")
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Theme", style = pipBody(15), color = PipTheme.ink)
                Text("System Default", style = pipBody(14), color = PipTheme.ink.copy(alpha = 0.5f))
            }
            Column {
                Text("Brand color", style = pipBody(15), color = PipTheme.ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    brandColorOptions.forEach { (hex, label) ->
                        val selected = hex.equals(settings.brandColor, ignoreCase = true)
                        Column(
                            modifier = Modifier.clickable { onBrandColor(hex) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex))),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Text("✓", style = pipBody(13, FontWeight.Bold), color = Color.White)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = pipBody(10), color = PipTheme.ink.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyCard(settings: UserSettingsDto, onToggleStoreConversations: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Privacy")
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp),
        ) {
            ToggleRow("Store conversations", settings.storeConversations, onToggleStoreConversations, isLast = true)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit, isLast: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (isLast) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = pipBody(15), color = PipTheme.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
