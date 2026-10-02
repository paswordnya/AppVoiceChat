package com.pip.app.ui.account

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pip.app.ui.profile.ProfileScreen
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import com.pip.shared.core.result.PipResult
import com.pip.shared.network.dto.AuthUserDto
import com.pip.shared.viewmodel.AccountViewModel
import org.koin.mp.KoinPlatform

private data class SettingsLink(val label: String, val implemented: Boolean)

private val settingsLinks =
    listOf(
        SettingsLink("Personalization", implemented = true),
        SettingsLink("Account Settings", implemented = false),
        SettingsLink("Privacy Settings", implemented = false),
        SettingsLink("Notification Settings", implemented = false),
        SettingsLink("Security Settings", implemented = false),
        SettingsLink("Language Settings", implemented = false),
    )

/** The redesign's "Profile" screen — personal account info (email, year of
 * birth; the backend user model has nothing richer yet, so no name/DOB/
 * phone fields like the design mockup) plus a settings-links list. Not to
 * be confused with [com.pip.app.ui.profile.ProfileScreen] ("Personalisasi"
 * in this list), the Adaptive Conversation Engine's learned-preference
 * screen. */
@Composable
fun AccountScreen(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<AccountViewModel>() }
    var user by remember { mutableStateOf<AuthUserDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var openPersonalization by remember { mutableStateOf(false) }
    var comingSoonLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val result = viewModel.load()) {
            is PipResult.Success -> user = result.value
            is PipResult.Failure -> errorMessage = "Tidak bisa memuat akun. Coba lagi."
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PipTheme.ink.copy(alpha = 0.06f))
                            .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = PipTheme.ink, modifier = Modifier.size(16.dp))
                }
                Text(
                    "Profile",
                    style = pipDisplay(18, FontWeight.SemiBold),
                    color = PipTheme.ink,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PipTheme.ink.copy(alpha = 0.06f))
                            .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PipTheme.ink, modifier = Modifier.size(16.dp))
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
                errorMessage?.let { Text(it, style = pipBody(13), color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    user?.let { AvatarHeader(it) }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "PERSONAL INFORMATION",
                            style = pipBody(12, FontWeight.SemiBold),
                            color = PipTheme.ink.copy(alpha = 0.45f),
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White),
                        ) {
                            InfoRow("✉️", "EMAIL ADDRESS", user?.email ?: "—")
                            InfoRow("🎂", "YEAR OF BIRTH", user?.yearOfBirth?.toString() ?: "—", isLast = true)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SETTINGS", style = pipBody(12, FontWeight.SemiBold), color = PipTheme.ink.copy(alpha = 0.45f))
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White),
                        ) {
                            settingsLinks.forEachIndexed { index, link ->
                                LinkRow(
                                    label = link.label,
                                    isLast = index == settingsLinks.lastIndex,
                                    onClick = {
                                        if (link.label == "Personalization") {
                                            openPersonalization = true
                                        } else {
                                            comingSoonLabel = link.label
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        if (openPersonalization) {
            ProfileScreen(onClose = { openPersonalization = false })
        }
    }

    comingSoonLabel?.let { label ->
        AlertDialog(
            onDismissRequest = { comingSoonLabel = null },
            title = { Text(label) },
            text = { Text("Belum tersedia — menyusul di rilis berikutnya.") },
            confirmButton = { Button(onClick = { comingSoonLabel = null }) { Text("Oke") } },
        )
    }
}

@Composable
private fun AvatarHeader(user: AuthUserDto) {
    val initial = user.email.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PipTheme.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, style = pipDisplay(28, FontWeight.SemiBold), color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(user.email, style = pipDisplay(18, FontWeight.SemiBold), color = PipTheme.ink)
    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isLast) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(PipTheme.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, style = pipBody(16)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = pipBody(11, FontWeight.SemiBold), color = PipTheme.ink.copy(alpha = 0.45f))
            Text(value, style = pipBody(15, FontWeight.Medium), color = PipTheme.ink)
        }
    }
}

@Composable
private fun LinkRow(label: String, isLast: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = if (isLast) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = pipBody(15), color = PipTheme.ink, modifier = Modifier.weight(1f))
        Text("›", style = pipDisplay(18), color = PipTheme.ink.copy(alpha = 0.3f))
    }
}
