package com.pip.shared.viewmodel

import com.pip.shared.core.result.PipError
import com.pip.shared.core.result.PipResult
import com.pip.shared.domain.usecase.Login
import com.pip.shared.domain.usecase.Signup
import com.pip.shared.network.dto.AuthUserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthUiState {
    data object Idle : AuthUiState

    data object Loading : AuthUiState

    data class Success(val user: AuthUserDto) : AuthUiState

    data class Error(val message: String) : AuthUiState
}

/**
 * Backs the Login/Signup screens on both platforms — the "auth screen"
 * `OnboardingView`/`OnboardingScreen` deferred. Suspend functions here are
 * called from the platform's own coroutine scope (SwiftUI `Task`/Compose
 * `LaunchedEffect`), matching `ChatSessionViewModel`'s convention. [login]/
 * [signup] return the terminal [AuthUiState] directly (Success/Error) so
 * callers don't need Flow-collection interop just to get a one-shot result;
 * [state] is also updated (including the intermediate `Loading`) for any UI
 * that wants to observe it reactively instead.
 */
class AuthViewModel(
    private val loginUseCase: Login,
    private val signupUseCase: Signup,
) {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    suspend fun login(email: String, password: String): AuthUiState {
        _state.value = AuthUiState.Loading
        val result =
            when (val r = loginUseCase(email, password)) {
                is PipResult.Success -> AuthUiState.Success(r.value)
                is PipResult.Failure -> AuthUiState.Error(loginErrorMessage(r.error))
            }
        _state.value = result
        return result
    }

    suspend fun signup(email: String, password: String, yearOfBirth: Int?): AuthUiState {
        _state.value = AuthUiState.Loading
        val result =
            when (val r = signupUseCase(email, password, yearOfBirth)) {
                is PipResult.Success -> AuthUiState.Success(r.value)
                is PipResult.Failure -> AuthUiState.Error(signupErrorMessage(r.error))
            }
        _state.value = result
        return result
    }

    private fun loginErrorMessage(error: PipError): String =
        when (error) {
            is PipError.Unauthorized -> "Email atau password salah."
            is PipError.NoConnectivity -> "Tidak bisa terhubung ke server. Coba lagi."
            is PipError.Server -> if (error.statusCode == 422) "Data tidak valid." else "Server error (${error.statusCode})."
            else -> "Gagal login. Coba lagi."
        }

    private fun signupErrorMessage(error: PipError): String =
        when (error) {
            is PipError.Server ->
                when (error.statusCode) {
                    409 -> "Email sudah terdaftar."
                    422 -> "Data tidak valid — cek email/password (min. 8 karakter)."
                    else -> "Server error (${error.statusCode})."
                }
            is PipError.NoConnectivity -> "Tidak bisa terhubung ke server. Coba lagi."
            else -> "Gagal daftar. Coba lagi."
        }
}
