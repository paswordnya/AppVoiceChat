package com.pip.shared.data.repository

import com.pip.shared.core.result.PipResult
import com.pip.shared.core.storage.KeyValueStore
import com.pip.shared.network.dto.AuthResponseDto
import com.pip.shared.network.dto.AuthUserDto
import com.pip.shared.network.dto.LoginRequestDto
import com.pip.shared.network.dto.SignupRequestDto
import com.pip.shared.network.http.ApiEndpoint
import com.pip.shared.network.http.KtorHttpClient

/**
 * Owns the app's auth token — `KeyValueStore`-backed, same "one per install"
 * persistence pattern as [SessionRepository]'s session_id — and the
 * login/signup calls that produce it. `isLoggedIn`/`token` are local-only
 * reads (no network), used at app-startup by `SplashViewModel` to decide
 * whether to gate on Login or go straight to Main.
 */
class AuthRepository(
    private val httpClient: KtorHttpClient,
    private val keyValueStore: KeyValueStore,
) {
    val token: String?
        get() = keyValueStore.getString(KtorHttpClient.AUTH_TOKEN_KEY)

    fun isLoggedIn(): Boolean = token != null

    suspend fun signup(email: String, password: String, yearOfBirth: Int?): PipResult<AuthUserDto> =
        httpClient.requestWithBody<SignupRequestDto, AuthResponseDto>(
            ApiEndpoint.Signup,
            SignupRequestDto(email = email, password = password, yearOfBirth = yearOfBirth),
        ).onSuccess { keyValueStore.putString(KtorHttpClient.AUTH_TOKEN_KEY, it.token) }
            .map { it.user }

    suspend fun login(email: String, password: String): PipResult<AuthUserDto> =
        httpClient.requestWithBody<LoginRequestDto, AuthResponseDto>(
            ApiEndpoint.Login,
            LoginRequestDto(email = email, password = password),
        ).onSuccess { keyValueStore.putString(KtorHttpClient.AUTH_TOKEN_KEY, it.token) }
            .map { it.user }

    /** Backs the account/personal-info screen — re-fetches the logged-in
     * user's identity after relaunch, since [login]/[signup] only return it
     * inline on that one call and it isn't cached anywhere locally. */
    suspend fun getCurrentUser(): PipResult<AuthUserDto> = httpClient.request(ApiEndpoint.GetMe)
}
