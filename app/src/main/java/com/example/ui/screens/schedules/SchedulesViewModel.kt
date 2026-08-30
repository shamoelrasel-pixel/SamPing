package com.example.ui.screens.schedules

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.engine.TemplateParser
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScheduleFilterTab(val displayName: String) {
    ALL("All"),
    UPCOMING("Upcoming"),
    RECURRING("Recurring"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

data class SchedulesUiState(
    val schedules: List<ScheduleEntity> = emptyList(),
    val selectedTab: ScheduleFilterTab = ScheduleFilterTab.UPCOMING,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class SchedulesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val scheduleRepo = app.scheduleRepository
    private val historyRepo = app.historyRepository
    private val alarmScheduler = app.alarmScheduler
    private val simManager = app.simManager
    private val smsSender = app.smsSender
    private val notificationHelper = app.notificationHelper

    private val _selectedTab = MutableStateFlow(ScheduleFilterTab.UPCOMING)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<SchedulesUiState> = combine(
        scheduleRepo.allSchedules,
        _selectedTab,
        _searchQuery
    ) { schedules, tab, query ->
        val filtered = schedules.filter { schedule ->
            val matchesTab = when (tab) {
                ScheduleFilterTab.ALL -> true
                ScheduleFilterTab.UPCOMING -> schedule.status == ScheduleStatus.SCHEDULED
                ScheduleFilterTab.RECURRING -> schedule.recurrenceType != RecurrenceType.ONCE
                ScheduleFilterTab.PAUSED -> schedule.status == ScheduleStatus.PAUSED
                ScheduleFilterTab.COMPLETED -> schedule.status == ScheduleStatus.COMPLETED
                ScheduleFilterTab.FAILED -> schedule.status == ScheduleStatus.FAILED || schedule.status == ScheduleStatus.MISSED
            }

            val matchesQuery = query.isBlank() ||
                    schedule.recipientName.contains(query, ignoreCase = true) ||
                    schedule.recipientPhone.contains(query, ignoreCase = true) ||
                    schedule.messageBody.contains(query, ignoreCase = true)

            matchesTab && matchesQuery
        }.sortedBy { it.nextExecutionEpochMs }

        SchedulesUiState(
            schedules = filtered,
            selectedTab = tab,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SchedulesUiState(isLoading = true)
    )

    fun selectTab(tab: ScheduleFilterTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePause(schedule: ScheduleEntity) {
        viewModelScope.launch {
            if (schedule.status == ScheduleStatus.SCHEDULED) {
                alarmScheduler.cancelSchedule(schedule.id)
                scheduleRepo.updateStatus(schedule.id, ScheduleStatus.PAUSED)
            } else if (schedule.status == ScheduleStatus.PAUSED) {
                scheduleRepo.updateStatus(schedule.id, ScheduleStatus.SCHEDULED)
                alarmScheduler.scheduleMessageExecution(
                    scheduleId = schedule.id,
                    triggerEpochMs = schedule.nextExecutionEpochMs,
                    preSendMinutes = schedule.preSendReminderMinutes
                )
            }
        }
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelSchedule(schedule.id)
            scheduleRepo.deleteSchedule(schedule)
        }
    }

    fun duplicateSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            val duplicated = schedule.copy(
                id = 0,
                nextExecutionEpochMs = System.currentTimeMillis() + 3600000L,
                executionCount = 0,
                retryAttempt = 0,
                status = ScheduleStatus.SCHEDULED,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            val newId = scheduleRepo.insertSchedule(duplicated)
            alarmScheduler.scheduleMessageExecution(
                scheduleId = newId,
                triggerEpochMs = duplicated.nextExecutionEpochMs,
                preSendMinutes = duplicated.preSendReminderMinutes
            )
        }
    }

    fun sendNow(schedule: ScheduleEntity) {
        viewModelScope.launch {
            val parsed = TemplateParser.parse(
                schedule.messageBody,
                schedule.recipientName,
                System.currentTimeMillis()
            )

            val hasSmsPermission = ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

            val simInfo = simManager.getSimInfoForSubscriptionId(schedule.simSubscriptionId)

            if (!hasSmsPermission) {
                val errorMsg = "SEND_SMS permission is not granted. Please grant permission in Settings."
                historyRepo.insertHistory(
                    HistoryEntity(
                        scheduleId = schedule.id,
                        recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                        recipientPhone = schedule.recipientPhone,
                        messageBody = parsed,
                        channel = MessageChannel.SMS,
                        simDisplayName = simInfo?.displayName ?: "SIM 1",
                        scheduledEpochMs = System.currentTimeMillis(),
                        executedEpochMs = System.currentTimeMillis(),
                        status = DeliveryStatus.FAILED,
                        errorReason = errorMsg,
                        retryAttempt = 0
                    )
                )
                notificationHelper.showPermissionRequiredNotification(
                    scheduleId = schedule.id,
                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone }
                )
                return@launch
            }

            val historyId = historyRepo.insertHistory(
                HistoryEntity(
                    scheduleId = schedule.id,
                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                    recipientPhone = schedule.recipientPhone,
                    messageBody = parsed,
                    channel = MessageChannel.SMS,
                    simDisplayName = simInfo?.displayName ?: "SIM 1",
                    scheduledEpochMs = System.currentTimeMillis(),
                    executedEpochMs = System.currentTimeMillis(),
                    status = DeliveryStatus.SENT,
                    errorReason = null,
                    retryAttempt = 0
                )
            )

            val result = smsSender.sendSms(
                scheduleId = schedule.id,
                historyId = historyId,
                recipientPhone = schedule.recipientPhone,
                messageText = parsed,
                subscriptionId = schedule.simSubscriptionId
            )

            if (result.isSuccess) {
                historyRepo.updateStatus(historyId, DeliveryStatus.SENT, null)
                notificationHelper.showSmsSentNotification(
                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                    messagePreview = parsed
                )
            } else {
                historyRepo.updateStatus(historyId, DeliveryStatus.FAILED, result.errorMessage)
            }
        }
    }
}
