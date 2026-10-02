package com.pip.shared.domain.usecase

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.AuthRepository
import com.pip.shared.network.dto.AuthUserDto

class Login(private val authRepository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): PipResult<AuthUserDto> =
        authRepository.login(email, password)
}
