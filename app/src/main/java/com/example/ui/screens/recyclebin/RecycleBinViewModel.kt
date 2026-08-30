package com.example.ui.screens.recyclebin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.RecycleBinEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecycleBinUiState(
    val items: List<RecycleBinEntity> = emptyList(),
    val snackbarMessage: String? = null,
    val isLoading: Boolean = false
)

class RecycleBinViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val smsRepo = app.smsRepository

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val items: StateFlow<List<RecycleBinEntity>> = smsRepo.getRecycleBinItemsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        cleanExpiredItems()
    }

    fun cleanExpiredItems() {
        viewModelScope.launch {
            smsRepo.cleanExpiredRecycledItems()
        }
    }

    fun restoreItem(item: RecycleBinEntity) {
        viewModelScope.launch {
            smsRepo.restoreRecycledItem(item)
            _snackbarMessage.value = "Restored message to conversation"
        }
    }

    fun restoreConversation(threadId: Long, phone: String) {
        viewModelScope.launch {
            smsRepo.restoreRecycledConversation(threadId, phone)
            _snackbarMessage.value = "Restored entire conversation"
        }
    }

    fun deleteItemPermanently(item: RecycleBinEntity) {
        viewModelScope.launch {
            smsRepo.deleteRecycledItemById(item.id)
            _snackbarMessage.value = "Item permanently deleted"
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            smsRepo.emptyRecycleBin()
            _snackbarMessage.value = "Recycle bin emptied"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
