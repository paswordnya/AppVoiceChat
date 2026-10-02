package com.pip.shared.viewmodel

import com.pip.shared.core.result.PipResult
import com.pip.shared.data.repository.AuthRepository
import com.pip.shared.network.dto.AuthUserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the redesigned Profile screen — personal account info (email,
 * year of birth; the backend user model has nothing richer yet, so this
 * deliberately doesn't show the prototype's fabricated name/DOB/phone
 * fields). Named `Account`, not `Profile`, to avoid clashing with the
 * existing [ProfileViewModel] (the Adaptive Conversation Engine's learned-
 * preference screen, relabeled "Personalization" and linked from here).
 */
class AccountViewModel(private val repository: AuthRepository) {
    private val _user = MutableStateFlow<AuthUserDto?>(null)
    val user: StateFlow<AuthUserDto?> = _user.asStateFlow()

    suspend fun load(): PipResult<AuthUserDto> = repository.getCurrentUser().onSuccess { _user.value = it }
}
