package com.pip.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pip.app.ui.components.BlobShape
import com.pip.app.ui.components.PipBlobFace
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import com.pip.shared.viewmodel.AuthUiState
import com.pip.shared.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/** Email/password signup, plus year of birth — the auth screen `OnboardingScreen` deferred. */
@Composable
fun SignupScreen(onSignedUp: () -> Unit, onGoToLogin: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<AuthViewModel>() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var yearOfBirth by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val canSubmit = email.isNotEmpty() && password.length >= 8 && !isSubmitting

    fun submit() {
        errorMessage = null
        isSubmitting = true
        val parsedYear = yearOfBirth.toIntOrNull()
        scope.launch {
            when (val result = viewModel.signup(email, password, parsedYear)) {
                is AuthUiState.Success -> onSignedUp()
                is AuthUiState.Error -> errorMessage = result.message
                else -> Unit
            }
            isSubmitting = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PipTheme.cream)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(110.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(BlobShape())
                        .background(Brush.linearGradient(colors = listOf(PipTheme.buddyFrom, PipTheme.buddyTo))),
            )
            PipBlobFace(modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(22.dp))

        Text("Buat akun", style = pipDisplay(26, FontWeight.SemiBold), color = PipTheme.ink)

        Spacer(Modifier.height(10.dp))

        Text(
            "Daftar dulu buat mulai ngobrol sama Pip.",
            style = pipBody(15),
            color = PipTheme.ink.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(22.dp))

        errorMessage?.let {
            Text(it, style = pipBody(13), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
        }

        val fieldColors =
            TextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
            )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Email", style = pipBody(14)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Password (min. 8 karakter)", style = pipBody(14)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = yearOfBirth,
            onValueChange = { yearOfBirth = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tahun lahir (opsional)", style = pipBody(14)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canSubmit) PipTheme.accent else PipTheme.accent.copy(alpha = 0.5f))
                    .clickable(enabled = canSubmit) { submit() }
                    .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Daftar", style = pipDisplay(16, FontWeight.SemiBold), color = Color.White)
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "Sudah punya akun? Masuk",
            style = pipBody(14, FontWeight.Medium),
            color = PipTheme.accent,
            modifier = Modifier.clickable { onGoToLogin() }.padding(8.dp),
        )
    }
}
