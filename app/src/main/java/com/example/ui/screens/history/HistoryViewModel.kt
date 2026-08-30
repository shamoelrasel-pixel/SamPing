package com.example.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.HistoryEntity
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.service.SimManager
import com.example.service.SmsSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyItems: List<HistoryEntity> = emptyList(),
    val selectedStatus: DeliveryStatus? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val historyRepo = app.historyRepository
    private val smsSender = app.smsSender
    private val simManager = app.simManager

    private val _selectedStatus = MutableStateFlow<DeliveryStatus?>(null)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepo.allHistory,
        _selectedStatus,
        _searchQuery
    ) { items, status, query ->
        val filtered = items.filter { item ->
            val matchesStatus = status == null || item.status == status
            val matchesQuery = query.isBlank() ||
                    item.recipientName.contains(query, ignoreCase = true) ||
                    item.recipientPhone.contains(query, ignoreCase = true) ||
                    item.messageBody.contains(query, ignoreCase = true) ||
                    (item.errorReason?.contains(query, ignoreCase = true) == true)
            matchesStatus && matchesQuery
        }

        HistoryUiState(
            historyItems = filtered,
            selectedStatus = status,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun selectStatus(status: DeliveryStatus?) {
        _selectedStatus.value = status
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            historyRepo.clearAllHistory()
        }
    }

    fun editAndSaveLog(
        item: HistoryEntity,
        newRecipientName: String,
        newRecipientPhone: String,
        newMessageBody: String,
        newStatus: DeliveryStatus = item.status
    ) {
        viewModelScope.launch {
            val updated = item.copy(
                recipientName = newRecipientName,
                recipientPhone = newRecipientPhone,
                messageBody = newMessageBody,
                status = newStatus
            )
            historyRepo.updateHistory(updated)
        }
    }

    fun resendEditedMessage(
        item: HistoryEntity,
        recipientName: String,
        recipientPhone: String,
        messageBody: String
    ) {
        viewModelScope.launch {
            val updated = item.copy(
                recipientName = recipientName,
                recipientPhone = recipientPhone,
                messageBody = messageBody,
                executedEpochMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENT,
                errorReason = null
            )
            historyRepo.updateHistory(updated)

            val result = smsSender.sendSms(
                scheduleId = item.scheduleId ?: 0L,
                historyId = item.id,
                recipientPhone = recipientPhone,
                messageText = messageBody,
                subscriptionId = null
            )
            if (result.isSuccess) {
                historyRepo.updateStatus(item.id, DeliveryStatus.SENT, null)
            } else {
                historyRepo.updateStatus(item.id, DeliveryStatus.FAILED, result.errorMessage)
            }
        }
    }

    fun deleteLog(item: HistoryEntity) {
        viewModelScope.launch {
            historyRepo.deleteHistory(item)
        }
    }

    fun retryFailedItem(item: HistoryEntity) {
        viewModelScope.launch {
            historyRepo.updateStatus(item.id, DeliveryStatus.PROCESSING, "Retrying message...")
            val result = smsSender.sendSms(
                scheduleId = item.scheduleId ?: 0L,
                historyId = item.id,
                recipientPhone = item.recipientPhone,
                messageText = item.messageBody,
                subscriptionId = null
            )
            if (result.isSuccess) {
                historyRepo.updateStatus(item.id, DeliveryStatus.SENT, null)
            } else {
                historyRepo.updateStatus(item.id, DeliveryStatus.FAILED, result.errorMessage)
            }
        }
    }
}
