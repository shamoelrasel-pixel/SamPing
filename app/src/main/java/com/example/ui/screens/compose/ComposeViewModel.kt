package com.example.ui.screens.compose

import android.app.Application
import android.database.Cursor
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.ScheduleEntity
import com.example.data.local.entity.TemplateEntity
import com.example.domain.model.ContactRecipient
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ScheduleStatus
import com.example.domain.model.SimInfo
import com.example.domain.util.SenderIdentityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposeUiState(
    val recipientPhone: String = "",
    val recipientName: String = "",
    val recipients: List<ContactRecipient> = emptyList(),
    val messageBody: String = "",
    val availableSims: List<SimInfo> = emptyList(),
    val selectedSimSubscriptionId: Int? = null,
    val deviceContacts: List<ContactRecipient> = emptyList(),
    val filteredContacts: List<ContactRecipient> = emptyList(),
    val availableTemplates: List<TemplateEntity> = emptyList(),
    val isContactDropdownVisible: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val draftSavedMessage: String? = null,
    val navigateToThread: Pair<Long, String>? = null
)

class ComposeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val smsRepository = app.smsRepository
    private val simManager = app.simManager
    private val templateRepository = app.templateRepository
    private val scheduleRepository = app.scheduleRepository
    private val alarmScheduler = app.alarmScheduler

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    private var draftSaveJob: Job? = null

    init {
        val sims = simManager.getAvailableSims()
        val defaultSim = sims.firstOrNull { it.isDefault }?.subscriptionId ?: sims.firstOrNull()?.subscriptionId
        _uiState.update {
            it.copy(
                availableSims = sims,
                selectedSimSubscriptionId = defaultSim
            )
        }
        loadContacts()
        loadTemplates()
    }

    fun initParams(phone: String, name: String, body: String) {
        if (phone.isBlank()) return
        val orgName = SenderIdentityHelper.resolveOrganizationName(phone)
        val resolvedName = name.ifBlank { smsRepository.lookupContactName(phone) ?: orgName ?: "" }

        _uiState.update {
            it.copy(
                recipientPhone = phone,
                recipientName = resolvedName,
                recipients = listOf(ContactRecipient(name = resolvedName, phoneNumber = phone)),
                messageBody = body
            )
        }

        if (phone.isNotBlank() && body.isBlank()) {
            checkAndLoadDraft(phone)
        }
    }

    fun addRecipient(phone: String, name: String = "") {
        val trimmedPhone = phone.trim()
        if (trimmedPhone.isBlank()) return
        val resolvedName = name.ifBlank { smsRepository.lookupContactName(trimmedPhone) ?: "" }
        val newRecipient = ContactRecipient(name = resolvedName, phoneNumber = trimmedPhone)
        
        _uiState.update { state ->
            val existing = state.recipients.toMutableList()
            if (existing.none { it.phoneNumber == trimmedPhone }) {
                existing.add(newRecipient)
            }
            state.copy(
                recipients = existing,
                recipientPhone = "",
                recipientName = "",
                isContactDropdownVisible = false
            )
        }
        autoSaveDraft()
    }

    fun removeRecipient(recipient: ContactRecipient) {
        _uiState.update { state ->
            val updated = state.recipients.filterNot { it.phoneNumber == recipient.phoneNumber }
            state.copy(recipients = updated)
        }
        autoSaveDraft()
    }

    fun toggleContactRecipient(contact: ContactRecipient) {
        _uiState.update { state ->
            val existing = state.recipients.toMutableList()
            val index = existing.indexOfFirst { it.phoneNumber == contact.phoneNumber }
            if (index >= 0) {
                existing.removeAt(index)
            } else {
                existing.add(contact)
            }
            state.copy(recipients = existing, isContactDropdownVisible = false)
        }
        autoSaveDraft()
    }

    fun checkAndLoadDraft(phone: String) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            val draft = smsRepository.getDraft(phone)
            if (draft != null && draft.messageBody.isNotBlank()) {
                _uiState.update { current ->
                    if (current.messageBody.isBlank()) {
                        current.copy(messageBody = draft.messageBody)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update { state ->
            val filtered = if (phone.length >= 2) {
                state.deviceContacts.filter {
                    it.name.contains(phone, ignoreCase = true) || it.phoneNumber.contains(phone)
                }.take(5)
            } else emptyList()

            state.copy(
                recipientPhone = phone,
                filteredContacts = filtered,
                isContactDropdownVisible = filtered.isNotEmpty()
            )
        }
        if (phone.isNotBlank() && _uiState.value.messageBody.isBlank()) {
            checkAndLoadDraft(phone)
        }
        autoSaveDraft()
    }

    fun selectContact(contact: ContactRecipient) {
        addRecipient(contact.phoneNumber, contact.name)
        checkAndLoadDraft(contact.phoneNumber)
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(recipientName = name) }
        autoSaveDraft()
    }

    fun onBodyChanged(body: String) {
        _uiState.update { it.copy(messageBody = body) }
        autoSaveDraft()
    }

    private fun autoSaveDraft() {
        val effectiveRecipients = getEffectiveRecipients()
        val body = _uiState.value.messageBody.trim()
        draftSaveJob?.cancel()
        if (effectiveRecipients.isNotEmpty()) {
            draftSaveJob = viewModelScope.launch {
                kotlinx.coroutines.delay(400)
                for (rec in effectiveRecipients) {
                    if (body.isNotBlank()) {
                        smsRepository.saveDraft(rec.phoneNumber, rec.name, body)
                    } else {
                        smsRepository.deleteDraft(rec.phoneNumber)
                    }
                }
            }
        }
    }

    private fun getEffectiveRecipients(): List<ContactRecipient> {
        val state = _uiState.value
        val list = state.recipients.toMutableList()
        val currentPhone = state.recipientPhone.trim()
        if (currentPhone.isNotBlank() && list.none { it.phoneNumber == currentPhone }) {
            list.add(ContactRecipient(name = state.recipientName.trim(), phoneNumber = currentPhone))
        }
        return list
    }

    fun saveDraftAndExit(onComplete: () -> Unit) {
        val effectiveRecipients = getEffectiveRecipients()
        val body = _uiState.value.messageBody.trim()
        if (effectiveRecipients.isNotEmpty()) {
            viewModelScope.launch {
                draftSaveJob?.cancel()
                for (rec in effectiveRecipients) {
                    if (body.isNotBlank()) {
                        smsRepository.saveDraft(rec.phoneNumber, rec.name, body)
                    } else {
                        smsRepository.deleteDraft(rec.phoneNumber)
                    }
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

    fun applyTemplate(template: TemplateEntity) {
        _uiState.update { it.copy(messageBody = template.content) }
        autoSaveDraft()
    }

    fun saveDraft(onSuccess: (() -> Unit)? = null) {
        val effectiveRecipients = getEffectiveRecipients()
        val body = _uiState.value.messageBody.trim()

        if (effectiveRecipients.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a phone number to save draft") }
            return
        }
        if (body.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Message text is empty") }
            return
        }

        viewModelScope.launch {
            draftSaveJob?.cancel()
            for (rec in effectiveRecipients) {
                smsRepository.saveDraft(rec.phoneNumber, rec.name, body)
            }
            _uiState.update { it.copy(draftSavedMessage = "Saved to Drafts") }
            onSuccess?.invoke()
        }
    }

    fun sendInstantSms(onSuccess: (String, String) -> Unit) {
        val effectiveRecipients = getEffectiveRecipients()
        val body = _uiState.value.messageBody.trim()
        val subId = _uiState.value.selectedSimSubscriptionId

        if (effectiveRecipients.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter at least one recipient") }
            return
        }
        if (body.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a message") }
            return
        }

        viewModelScope.launch {
            draftSaveJob?.cancel()
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            var successCount = 0

            // Send individually to each recipient, recorded in existing conversation
            for (recipient in effectiveRecipients) {
                val success = smsRepository.sendImmediateSms(
                    recipientPhone = recipient.phoneNumber,
                    recipientName = recipient.name,
                    messageText = body,
                    subscriptionId = subId
                )
                if (success) {
                    successCount++
                    smsRepository.deleteDraft(recipient.phoneNumber)
                }
            }

            _uiState.update { it.copy(isSending = false) }

            if (successCount > 0) {
                val first = effectiveRecipients.first()
                onSuccess(first.phoneNumber, first.name)
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to send SMS. Ensure SMS permission is granted.") }
            }
        }
    }

    fun scheduleSms(
        triggerEpochMs: Long,
        onScheduled: () -> Unit
    ) {
        val effectiveRecipients = getEffectiveRecipients()
        val body = _uiState.value.messageBody.trim()
        val subId = _uiState.value.selectedSimSubscriptionId

        if (effectiveRecipients.isEmpty() || body.isBlank()) return

        viewModelScope.launch {
            draftSaveJob?.cancel()

            // Schedule individual SMS for each recipient
            for (recipient in effectiveRecipients) {
                val entity = ScheduleEntity(
                    recipientName = recipient.name.ifBlank { recipient.phoneNumber },
                    recipientPhone = recipient.phoneNumber,
                    messageBody = body,
                    channel = MessageChannel.SMS,
                    simSubscriptionId = subId,
                    startEpochMs = triggerEpochMs,
                    nextExecutionEpochMs = triggerEpochMs,
                    recurrenceType = RecurrenceType.ONCE,
                    status = ScheduleStatus.SCHEDULED
                )

                val scheduleId = scheduleRepository.insertSchedule(entity)
                alarmScheduler.scheduleMessageExecution(scheduleId, triggerEpochMs, 0)

                // Clear draft if any
                smsRepository.deleteDraft(recipient.phoneNumber)
            }

            onScheduled()
        }
    }

    fun setRecipient(phone: String, name: String) {
        addRecipient(phone, name)
        checkAndLoadDraft(phone)
    }

    fun refreshContacts() {
        loadContacts()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, draftSavedMessage = null) }
    }

    private fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<ContactRecipient>()
            try {
                val cursor: Cursor? = app.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
                cursor?.use {
                    val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = if (nameCol >= 0) it.getString(nameCol) ?: "" else ""
                        val num = if (numCol >= 0) it.getString(numCol) ?: "" else ""
                        if (num.isNotBlank()) {
                            list.add(ContactRecipient(name = name, phoneNumber = num))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore if permission not granted
            }
            _uiState.update { it.copy(deviceContacts = list) }
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            templateRepository.allTemplates.collect { list ->
                _uiState.update { it.copy(availableTemplates = list) }
            }
        }
    }
}
