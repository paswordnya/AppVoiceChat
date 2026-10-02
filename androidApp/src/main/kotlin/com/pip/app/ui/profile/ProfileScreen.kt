package com.pip.app.ui.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pip.shared.core.result.PipError
import com.pip.shared.core.result.PipResult
import com.pip.shared.network.dto.ProfileDto
import com.pip.shared.network.dto.ProfileHistoryEntryDto
import com.pip.shared.network.dto.ProfileUpdateDto
import com.pip.shared.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val styleOptions = listOf("formal", "casual", "direct")
private val toneOptions = listOf("professional", "friendly", "gentle")
private val lengthOptions = listOf("short", "medium", "long")
private val technicalOptions = listOf("beginner", "intermediate", "advanced")
private val frequencyOptions = listOf("jarang", "kadang", "sering")

private fun errorMessageFor(error: PipError): String =
    when (error) {
        is PipError.Unauthorized -> "Sesi login sudah tidak valid — coba login lagi."
        is PipError.NoConnectivity -> "Tidak bisa terhubung ke server. Coba lagi."
        is PipError.Server -> "Server error (${error.statusCode})."
        else -> "Terjadi kesalahan. Coba lagi."
    }

/** Adaptive Conversation Engine's Privacy screen (PRD_TDD_pip_Voice_AI.md
 * §23.4) — mirrors iOS's ProfileView.swift 1:1. */
@Composable
fun ProfileScreen(onClose: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<ProfileViewModel>() }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<ProfileDto?>(null) }
    var history by remember { mutableStateOf(listOf<ProfileHistoryEntryDto>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    suspend fun applyUpdate(update: ProfileUpdateDto) {
        isBusy = true
        when (val result = viewModel.update(update)) {
            is PipResult.Success -> {
                profile = result.value
                errorMessage = null
            }
            is PipResult.Failure -> errorMessage = errorMessageFor(result.error)
        }
        isBusy = false
    }

    LaunchedEffect(Unit) {
        when (val result = viewModel.load()) {
            is PipResult.Success -> {
                profile = result.value
                errorMessage = null
            }
            is PipResult.Failure -> errorMessage = errorMessageFor(result.error)
        }
        when (val historyResult = viewModel.loadHistory(50)) {
            is PipResult.Success -> history = historyResult.value
            is PipResult.Failure -> Unit
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(PipTheme.cream)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Personalisasi",
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
                errorMessage?.let {
                    Text(it, style = pipBody(13), color = MaterialTheme.colorScheme.error)
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    profile?.let { p ->
                        SummaryCard(p)
                        EditCard(
                            profile = p,
                            isBusy = isBusy,
                            onCommunicationStyle = { scope.launch { applyUpdate(ProfileUpdateDto(communicationStyle = it)) } },
                            onTone = { scope.launch { applyUpdate(ProfileUpdateDto(preferredTone = it)) } },
                            onLength = { scope.launch { applyUpdate(ProfileUpdateDto(preferredResponseLength = it)) } },
                            onTechnicalLevel = { scope.launch { applyUpdate(ProfileUpdateDto(technicalLevel = it)) } },
                            onHumor = { scope.launch { applyUpdate(ProfileUpdateDto(humorPreference = it)) } },
                            onEmoji = { scope.launch { applyUpdate(ProfileUpdateDto(emojiPreference = it)) } },
                        )
                        if (history.isNotEmpty()) {
                            HistoryCard(history)
                        }
                        DangerZone(
                            isBusy = isBusy,
                            onResetClick = { showResetConfirm = true },
                            onDeleteClick = { showDeleteConfirm = true },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset profil?") },
            text = { Text("Semua preferensi yang dipelajari akan kembali kosong. Riwayat percakapan tidak terhapus.") },
            confirmButton = {
                Button(onClick = {
                    showResetConfirm = false
                    scope.launch {
                        isBusy = true
                        when (val result = viewModel.reset()) {
                            is PipResult.Success -> {
                                profile = result.value
                                when (val historyResult = viewModel.loadHistory(50)) {
                                    is PipResult.Success -> history = historyResult.value
                                    is PipResult.Failure -> Unit
                                }
                                errorMessage = null
                            }
                            is PipResult.Failure -> errorMessage = errorMessageFor(result.error)
                        }
                        isBusy = false
                    }
                }) { Text("Reset") }
            },
            dismissButton = { OutlinedButton(onClick = { showResetConfirm = false }) { Text("Batal") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus semua data personalisasi?") },
            text = { Text("Profil dan seluruh riwayat perubahannya akan dihapus permanen. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            isBusy = true
                            when (viewModel.delete()) {
                                is PipResult.Success -> onClose()
                                is PipResult.Failure -> errorMessage = "Gagal menghapus data. Coba lagi."
                            }
                            isBusy = false
                        }
                    },
                ) { Text("Hapus") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun SummaryCard(profile: ProfileDto) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Yang Pip pelajari tentangmu", style = pipDisplay(16, FontWeight.SemiBold), color = PipTheme.ink)
        if (profile.confidenceScore < 0.1) {
            Text(
                "Belum cukup percakapan untuk mempelajari gaya kamu — makin sering ngobrol, makin Pip mengenalmu.",
                style = pipBody(13),
                color = PipTheme.ink.copy(alpha = 0.6f),
            )
        } else {
            SummaryRow("Gaya komunikasi", profile.communicationStyle)
            SummaryRow("Nada favorit", profile.preferredTone)
            SummaryRow("Panjang jawaban", profile.preferredResponseLength)
            SummaryRow("Level teknis", profile.technicalLevel)
            SummaryRow("Suka humor", profile.humorPreference)
            SummaryRow("Suka emoji", profile.emojiPreference)
            if (profile.favoriteTopics.isNotEmpty()) {
                SummaryRow("Topik favorit", profile.favoriteTopics.joinToString(", "))
            }
            Text(
                "Tingkat keyakinan: ${(profile.confidenceScore * 100).toInt()}%",
                style = pipBody(12),
                color = PipTheme.ink.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = pipBody(13), color = PipTheme.ink.copy(alpha = 0.6f))
        Text(value ?: "—", style = pipBody(13, FontWeight.Medium), color = PipTheme.ink)
    }
}

@Composable
private fun EditCard(
    profile: ProfileDto,
    isBusy: Boolean,
    onCommunicationStyle: (String) -> Unit,
    onTone: (String) -> Unit,
    onLength: (String) -> Unit,
    onTechnicalLevel: (String) -> Unit,
    onHumor: (String) -> Unit,
    onEmoji: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ubah manual", style = pipDisplay(15, FontWeight.SemiBold), color = PipTheme.ink)
        Text(
            "Preferensi yang kamu ubah di sini tidak akan ditimpa otomatis oleh Pip.",
            style = pipBody(12),
            color = PipTheme.ink.copy(alpha = 0.5f),
        )
        EditRow("Gaya komunikasi", styleOptions, profile.communicationStyle, isBusy, onCommunicationStyle)
        EditRow("Nada", toneOptions, profile.preferredTone, isBusy, onTone)
        EditRow("Panjang jawaban", lengthOptions, profile.preferredResponseLength, isBusy, onLength)
        EditRow("Level teknis", technicalOptions, profile.technicalLevel, isBusy, onTechnicalLevel)
        EditRow("Frekuensi humor", frequencyOptions, profile.humorPreference, isBusy, onHumor)
        EditRow("Frekuensi emoji", frequencyOptions, profile.emojiPreference, isBusy, onEmoji)
    }
}

@Composable
private fun EditRow(label: String, options: List<String>, current: String?, isBusy: Boolean, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = pipBody(13), color = PipTheme.ink, modifier = Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !isBusy) { expanded = true },
            ) {
                Text(current ?: "Pilih", style = pipBody(13, FontWeight.Medium), color = PipTheme.accent)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PipTheme.accent, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onPick(option) })
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<ProfileHistoryEntryDto>) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Riwayat perubahan", style = pipDisplay(15, FontWeight.SemiBold), color = PipTheme.ink)
        history.take(10).forEach { entry ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    listOfNotNull(entry.communicationStyle, entry.preferredTone, entry.technicalLevel).joinToString(" · "),
                    style = pipBody(12, FontWeight.Medium),
                    color = PipTheme.ink,
                )
                Text(entry.recordedAt, style = pipBody(11), color = PipTheme.ink.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun DangerZone(isBusy: Boolean, onResetClick: () -> Unit, onDeleteClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onResetClick, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text("Reset profil")
        }
        Button(
            onClick = onDeleteClick,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Hapus semua data personalisasi")
        }
    }
}
