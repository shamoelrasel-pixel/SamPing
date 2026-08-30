package com.example.ui.screens.blocked

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.BlockedNumberEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlockedNumbersViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val smsRepo = app.smsRepository

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val blockedNumbers: StateFlow<List<BlockedNumberEntity>> = smsRepo.getBlockedNumbersFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun blockNumber(phone: String, name: String? = null) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            smsRepo.blockSender(phone.trim(), name?.trim())
            _snackbarMessage.value = "Blocked $phone"
        }
    }

    fun unblockNumber(phone: String) {
        viewModelScope.launch {
            smsRepo.unblockSender(phone)
            _snackbarMessage.value = "Unblocked $phone"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
