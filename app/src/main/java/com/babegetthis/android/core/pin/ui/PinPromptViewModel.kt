package com.babegetthis.android.core.pin.ui

import androidx.lifecycle.ViewModel
import com.babegetthis.android.core.pin.data.PinRepository
import com.babegetthis.android.core.pin.data.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Thin bridge so PIN dialogs don't each need PinRepository threaded through their
// host screen's ViewModel. Hashing (PBKDF2, 120k iterations) runs off the main
// thread to avoid jank/ANR on the verify button.
@HiltViewModel
class PinPromptViewModel @Inject constructor(
    private val pinRepository: PinRepository,
) : ViewModel() {

    suspend fun verify(pin: String): PinResult =
        withContext(Dispatchers.Default) { pinRepository.verifyPin(pin) }

    suspend fun setupPin(pin: String): String =
        withContext(Dispatchers.Default) { pinRepository.setupPin(pin) }

    suspend fun changePin(current: String, newPin: String): PinResult =
        withContext(Dispatchers.Default) { pinRepository.changePin(current, newPin) }

    suspend fun removePin(current: String): PinResult =
        withContext(Dispatchers.Default) { pinRepository.removePin(current) }

    suspend fun verifyRecovery(code: String): PinResult =
        withContext(Dispatchers.Default) { pinRepository.verifyRecoveryCode(code) }

    suspend fun resetWithRecovery(code: String, newPin: String): Pair<PinResult, String?> =
        withContext(Dispatchers.Default) { pinRepository.resetPinWithRecoveryCode(code, newPin) }

    suspend fun regenerateRecoveryCode(): String =
        withContext(Dispatchers.Default) { pinRepository.regenerateRecoveryCode() }
}

// 4 numeric digits — enforced at entry so nothing weaker reaches storage.
fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }
