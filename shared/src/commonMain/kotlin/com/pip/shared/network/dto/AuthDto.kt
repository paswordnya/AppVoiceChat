package com.pip.shared.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /auth/signup` request body (auth.py's `SignupRequest`). */
@Serializable
data class SignupRequestDto(
    val email: String,
    val password: String,
    @SerialName("year_of_birth") val yearOfBirth: Int? = null,
)

/** `POST /auth/login` request body (auth.py's `LoginRequest`). */
@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

/** Shared response shape for both `/auth/signup` and `/auth/login` (auth.py's `AuthResponse`). */
@Serializable
data class AuthResponseDto(
    val token: String,
    val user: AuthUserDto,
)

@Serializable
data class AuthUserDto(
    val id: Int,
    val email: String,
    @SerialName("year_of_birth") val yearOfBirth: Int? = null,
)
