package com.example.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.domain.model.SimInfo
import com.example.domain.model.SmsChatMessage
import com.example.domain.util.SenderIdentityHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val threadId: Long = -1L,
    val address: String = "",
    val contactName: String = "",
    val messages: List<SmsChatMessage> = emptyList(),
    val messageInput: String = "",
    val isSending: Boolean = false,
    val isBlocked: Boolean = false,
    val isArchived: Boolean = false,
    val availableSims: List<SimInfo> = emptyList(),
    val selectedSimSubscriptionId: Int? = null,
    val isScheduleDialogVisible: Boolean = false,
    val scheduleTimeEpochMs: Long = System.currentTimeMillis() + 3600000L, // 1 hr later by default
    val errorMessage: String? = null,
    val successSnackbar: String? = null
)

class ChatThreadViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val smsRepository = app.smsRepository
    private val blockedNumberRepository = app.blockedNumberRepository
    private val simManager = app.simManager
    private val scheduleRepository = app.scheduleRepository
    private val alarmScheduler = app.alarmScheduler

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var incomingMessageJob: Job? = null
    private var draftSaveJob: Job? = null

    fun initConversation(threadId: Long, address: String, name: String) {
        val orgName = SenderIdentityHelper.resolveOrganizationName(address)
        val resolvedName = name.ifBlank {
            smsRepository.lookupContactName(address) ?: orgName ?: address
        }
        val sims = simManager.getAvailableSims()
        val defaultSim = sims.firstOrNull { it.isDefault }?.subscriptionId ?: sims.firstOrNull()?.subscriptionId

        _uiState.update {
            it.copy(
                threadId = threadId,
                address = address,
                contactName = resolvedName,
                availableSims = sims,
                selectedSimSubscriptionId = defaultSim
            )
        }

        checkBlockedStatus(address)
        checkArchivedStatus(threadId, address)
        smsRepository.setActiveConversation(address, threadId)
        observeIncomingMessages(address, threadId)
        loadMessages()
        loadDraft(address)
        markThreadRead()
    }

    private fun checkArchivedStatus(threadId: Long, address: String) {
        viewModelScope.launch {
            val archived = smsRepository.isConversationArchived(threadId, address)
            _uiState.update { it.copy(isArchived = archived) }
        }
    }

    fun toggleArchive() {
        val threadId = _uiState.value.threadId
        val address = _uiState.value.address
        val currentArchived = _uiState.value.isArchived
        viewModelScope.launch {
            if (currentArchived) {
                smsRepository.unarchiveConversation(threadId, address)
                _uiState.update { it.copy(isArchived = false, successSnackbar = "Conversation restored to Chats") }
            } else {
                smsRepository.archiveConversation(threadId, address)
                _uiState.update { it.copy(isArchived = true, successSnackbar = "Conversation archived") }
            }
        }
    }

    private fun checkBlockedStatus(address: String) {
        viewModelScope.launch {
            val blocked = blockedNumberRepository.isBlocked(address)
            _uiState.update { it.copy(isBlocked = blocked) }
        }
    }

    fun blockSender(onSuccess: (() -> Unit)? = null) {
        val address = _uiState.value.address
        val name = _uiState.value.contactName
        if (address.isBlank()) return

        viewModelScope.launch {
            blockedNumberRepository.blockNumber(address, name)
            _uiState.update { it.copy(isBlocked = true, successSnackbar = "$name blocked from SMS") }
            onSuccess?.invoke()
        }
    }

    fun unblockSender() {
        val address = _uiState.value.address
        if (address.isBlank()) return

        viewModelScope.launch {
            blockedNumberRepository.unblockNumber(address)
            _uiState.update { it.copy(isBlocked = false, successSnackbar = "Number unblocked") }
            loadMessages()
        }
    }

    fun deleteMessage(msg: SmsChatMessage) {
        viewModelScope.launch {
            val name = _uiState.value.contactName
            smsRepository.deleteMessage(msg, name)
            _uiState.update { it.copy(successSnackbar = "Message moved to Recycle Bin") }
            loadMessages()
        }
    }

    fun deleteEntireConversation(onSuccess: () -> Unit) {
        val threadId = _uiState.value.threadId
        val address = _uiState.value.address
        viewModelScope.launch {
            smsRepository.deleteThreads(listOf(threadId to address))
            onSuccess()
        }
    }

    private fun loadDraft(address: String) {
        viewModelScope.launch {
            val draft = smsRepository.getDraft(address)
            if (draft != null && draft.messageBody.isNotBlank()) {
                _uiState.update { current ->
                    if (current.messageInput.isBlank()) {
                        current.copy(messageInput = draft.messageBody)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages(address: String, threadId: Long) {
        incomingMessageJob?.cancel()
        incomingMessageJob = viewModelScope.launch {
            smsRepository.incomingMessages.collect { newMsg ->
                val isMatch = SenderIdentityHelper.isSameSender(address, newMsg.address) ||
                        (threadId > 0 && newMsg.threadId == threadId)

                if (isMatch) {
                    _uiState.update { current ->
                        val existingIndex = current.messages.indexOfFirst { it.id == newMsg.id }
                        val updated = if (existingIndex >= 0) {
                            current.messages.toMutableList().apply { set(existingIndex, newMsg) }
                        } else {
                            (current.messages + newMsg).sortedBy { it.dateEpochMs }
                        }
                        current.copy(messages = updated)
                    }
                    markThreadRead()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        smsRepository.clearActiveConversation()
    }

    fun loadMessages() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val msgs = smsRepository.getMessagesForThread(currentState.threadId, currentState.address)
            _uiState.update { it.copy(messages = msgs) }
        }
    }

    fun onMessageInputChange(text: String) {
        _uiState.update { it.copy(messageInput = text) }
        val address = _uiState.value.address.trim()
        val name = _uiState.value.contactName.trim()
        if (address.isNotBlank()) {
            draftSaveJob?.cancel()
            draftSaveJob = viewModelScope.launch {
                kotlinx.coroutines.delay(400)
                if (text.isNotBlank()) {
                    smsRepository.saveDraft(address, name, text)
                } else {
                    smsRepository.deleteDraft(address)
                }
            }
        }
    }

    fun saveDraftAndExit(onComplete: () -> Unit) {
        val text = _uiState.value.messageInput.trim()
        val address = _uiState.value.address.trim()
        val name = _uiState.value.contactName.trim()
        if (address.isNotBlank()) {
            viewModelScope.launch {
                draftSaveJob?.cancel()
                if (text.isNotBlank()) {
                    smsRepository.saveDraft(address, name, text)
                } else {
                    smsRepository.deleteDraft(address)
                }
                onComplete()
            }
        } else {
            onComplete()
        }
    }

    fun selectSim(subscriptionId: Int?) {
        _uiState.update { it.copy(selectedSimSubscriptionId = subscriptionId) }
    }

    fun saveCurrentAsDraft() {
        val text = _uiState.value.messageInput.trim()
        val address = _uiState.value.address.trim()
        val name = _uiState.value.contactName.trim()
        if (address.isBlank()) return

        viewModelScope.launch {
            draftSaveJob?.cancel()
            if (text.isNotBlank()) {
                smsRepository.saveDraft(address, name, text)
                _uiState.update { it.copy(successSnackbar = "Draft saved to conversation") }
            } else {
                smsRepository.deleteDraft(address)
                _uiState.update { it.copy(successSnackbar = "Draft cleared") }
            }
            loadMessages()
        }
    }

    fun sendInstantMessage() {
        val text = _uiState.value.messageInput.trim()
        val address = _uiState.value.address.trim()
        val name = _uiState.value.contactName.trim()
        val subId = _uiState.value.selectedSimSubscriptionId

        if (text.isBlank() || address.isBlank()) return

        viewModelScope.launch {
            draftSaveJob?.cancel()
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            val success = smsRepository.sendImmediateSms(
                recipientPhone = address,
                recipientName = name,
                messageText = text,
                subscriptionId = subId
            )

            if (success) {
                smsRepository.deleteDraft(address)
                _uiState.update {
                    it.copy(
                        messageInput = "",
                        isSending = false,
                        successSnackbar = "Message sent"
                    )
                }
                loadMessages()
            } else {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = "Failed to send SMS. Ensure SMS permission is granted."
                    )
                }
            }
        }
    }

    fun retryFailedMessage(message: SmsChatMessage) {
        val address = _uiState.value.address.trim()
        val name = _uiState.value.contactName.trim()
        val subId = _uiState.value.selectedSimSubscriptionId

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val success = smsRepository.sendImmediateSms(
                recipientPhone = address,
                recipientName = name,
                messageText = message.body,
                subscriptionId = subId
            )
            if (success) {
                _uiState.update {
                    it.copy(isSending = false, successSnackbar = "Message resent successfully")
                }
                loadMessages()
            } else {
                _uiState.update {
                    it.copy(isSending = false, errorMessage = "Retry failed. Check network/permissions.")
                }
            }
        }
    }

    fun openScheduleDialog() {
        _uiState.update {
            it.copy(
                isScheduleDialogVisible = true,
                scheduleTimeEpochMs = System.currentTimeMillis() + 3600000L
            )
        }
    }

    fun closeScheduleDialog() {
        _uiState.update { it.copy(isScheduleDialogVisible = false) }
    }

    fun updateScheduleTime(timeMs: Long) {
        _uiState.update { it.copy(scheduleTimeEpochMs = timeMs) }
    }

    fun confirmScheduleMessage() {
        val text = _uiState.value.messageInput.trim()
        val address = _uiState.value.address.trim()
        val name = _uiState.value.contactName.trim()
        val triggerMs = _uiState.value.scheduleTimeEpochMs
        val subId = _uiState.value.selectedSimSubscriptionId

        if (text.isBlank() || address.isBlank()) return

        viewModelScope.launch {
            val entity = com.example.data.local.entity.ScheduleEntity(
                recipientName = name,
                recipientPhone = address,
                messageBody = text,
                channel = com.example.domain.model.MessageChannel.SMS,
                simSubscriptionId = subId,
                startEpochMs = triggerMs,
                nextExecutionEpochMs = triggerMs,
                recurrenceType = com.example.domain.model.RecurrenceType.ONCE,
                status = com.example.domain.model.ScheduleStatus.SCHEDULED
            )

            val scheduleId = scheduleRepository.insertSchedule(entity)
            alarmScheduler.scheduleMessageExecution(scheduleId, triggerMs, 0)

            // Clear draft
            smsRepository.deleteDraft(address)

            _uiState.update {
                it.copy(
                    isScheduleDialogVisible = false,
                    messageInput = "",
                    successSnackbar = "Message scheduled for ${formatDateTime(triggerMs)}"
                )
            }
            loadMessages()
        }
    }

    fun deleteScheduledMessage(scheduleId: Long) {
        viewModelScope.launch {
            alarmScheduler.cancelSchedule(scheduleId)
            scheduleRepository.deleteScheduleById(scheduleId)
            loadMessages()
        }
    }

    private fun markThreadRead() {
        viewModelScope.launch {
            if (_uiState.value.threadId > 0 || _uiState.value.address.isNotBlank()) {
                smsRepository.markThreadAsRead(_uiState.value.threadId, _uiState.value.address)
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(successSnackbar = null, errorMessage = null) }
    }

    private fun formatDateTime(epochMs: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(epochMs))
    }
}
