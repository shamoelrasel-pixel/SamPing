package com.example.ui.screens.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.ScheduleEntity
import com.example.data.local.entity.TemplateEntity
import com.example.domain.engine.RecurrenceConfig
import com.example.domain.engine.RecurrenceEngine
import com.example.domain.engine.TemplateParser
import com.example.domain.model.EndConditionType
import com.example.domain.model.LeapYearHandling
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RetryPolicy
import com.example.domain.model.ScheduleStatus
import com.example.domain.model.ShortMonthHandling
import com.example.domain.model.SimInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class CreateScheduleUiState(
    val scheduleId: Long = 0L,
    val isEditMode: Boolean = false,
    val recipientName: String = "",
    val recipientPhone: String = "",
    val messageBody: String = "",
    val channel: MessageChannel = MessageChannel.SMS,
    val availableSims: List<SimInfo> = emptyList(),
    val selectedSimId: Int = -1,
    val scheduledDate: LocalDate = LocalDate.now(),
    val scheduledTime: LocalTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0),
    val recurrenceType: RecurrenceType = RecurrenceType.ONCE,
    val recurrenceInterval: Int = 1,
    val selectedDaysOfWeek: List<Int> = listOf(1, 3, 5), // Mon, Wed, Fri
    val dayOfMonth: Int = LocalDate.now().dayOfMonth,
    val weekOfMonthOrdinal: Int = 1, // 1..4 or -1 (last)
    val relativeDayOfWeek: Int = 1, // Monday
    val shortMonthHandling: ShortMonthHandling = ShortMonthHandling.LAST_VALID_DAY,
    val leapYearHandling: LeapYearHandling = LeapYearHandling.FEB_28,
    val endType: EndConditionType = EndConditionType.NEVER,
    val endDate: LocalDate = LocalDate.now().plusMonths(6),
    val maxOccurrences: Int = 5,
    val preSendReminderMinutes: Int = 0,
    val retryPolicy: RetryPolicy = RetryPolicy.NO_RETRY,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val showReviewDialog: Boolean = false
) {
    fun calculateTriggerEpochMs(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val ldt = LocalDateTime.of(scheduledDate, scheduledTime)
        return ldt.atZone(zoneId).toInstant().toEpochMilli()
    }

    fun toRecurrenceConfig(zoneId: ZoneId = ZoneId.systemDefault()): RecurrenceConfig {
        val endEpoch = if (endType == EndConditionType.UNTIL_DATE) {
            LocalDateTime.of(endDate, LocalTime.of(23, 59, 59)).atZone(zoneId).toInstant().toEpochMilli()
        } else null

        return RecurrenceConfig(
            type = recurrenceType,
            interval = recurrenceInterval,
            selectedDaysOfWeek = selectedDaysOfWeek,
            dayOfMonth = dayOfMonth,
            weekOfMonthOrdinal = weekOfMonthOrdinal,
            relativeDayOfWeek = relativeDayOfWeek,
            shortMonthHandling = shortMonthHandling,
            leapYearHandling = leapYearHandling,
            endType = endType,
            endEpochMs = endEpoch,
            maxOccurrences = if (endType == EndConditionType.AFTER_COUNT) maxOccurrences else null
        )
    }

    val parsedPreview: String
        get() = TemplateParser.parse(
            templateText = messageBody,
            recipientName = recipientName,
            scheduledEpochMs = calculateTriggerEpochMs()
        )
}

class CreateScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val scheduleRepo = app.scheduleRepository
    private val templateRepo = app.templateRepository
    private val preferencesRepo = app.userPreferencesRepository
    private val alarmScheduler = app.alarmScheduler
    private val simManager = app.simManager

    private val _uiState = MutableStateFlow(CreateScheduleUiState())
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    val templates: StateFlow<List<TemplateEntity>> = templateRepo.allTemplates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadDefaults()
    }

    fun initParams(phone: String?, name: String?) {
        if (!phone.isNullOrBlank() || !name.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    recipientPhone = phone ?: it.recipientPhone,
                    recipientName = name ?: it.recipientName
                )
            }
        }
    }

    private fun loadDefaults() {
        viewModelScope.launch {
            val userPrefs = preferencesRepo.userPreferencesFlow.first()
            val sims = simManager.getAvailableSims()
            _uiState.update {
                it.copy(
                    channel = userPrefs.defaultChannel,
                    availableSims = sims,
                    selectedSimId = if (userPrefs.defaultSimId != -1) userPrefs.defaultSimId else sims.firstOrNull()?.subscriptionId ?: -1,
                    preSendReminderMinutes = userPrefs.preSendReminderMinutes,
                    retryPolicy = userPrefs.defaultRetryPolicy
                )
            }
        }
    }

    fun loadScheduleForEdit(scheduleId: Long) {
        if (scheduleId <= 0) return
        viewModelScope.launch {
            val schedule = scheduleRepo.getScheduleById(scheduleId) ?: return@launch
            val dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(schedule.nextExecutionEpochMs),
                ZoneId.systemDefault()
            )

            val endLocalDate = schedule.endEpochMs?.let {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalDate()
            } ?: LocalDate.now().plusMonths(6)

            _uiState.update {
                it.copy(
                    scheduleId = schedule.id,
                    isEditMode = true,
                    recipientName = schedule.recipientName,
                    recipientPhone = schedule.recipientPhone,
                    messageBody = schedule.messageBody,
                    channel = schedule.channel,
                    selectedSimId = schedule.simSubscriptionId ?: -1,
                    scheduledDate = dt.toLocalDate(),
                    scheduledTime = dt.toLocalTime(),
                    recurrenceType = schedule.recurrenceType,
                    recurrenceInterval = schedule.recurrenceInterval,
                    selectedDaysOfWeek = schedule.selectedDaysOfWeek,
                    dayOfMonth = schedule.dayOfMonth,
                    weekOfMonthOrdinal = schedule.weekOfMonthOrdinal,
                    relativeDayOfWeek = schedule.relativeDayOfWeek,
                    shortMonthHandling = schedule.shortMonthHandling,
                    leapYearHandling = schedule.leapYearHandling,
                    endType = schedule.endType,
                    endDate = endLocalDate,
                    maxOccurrences = schedule.maxOccurrences ?: 5,
                    preSendReminderMinutes = schedule.preSendReminderMinutes,
                    retryPolicy = schedule.retryPolicy
                )
            }
        }
    }

    fun applyTemplate(template: TemplateEntity) {
        _uiState.update { it.copy(messageBody = template.content) }
    }

    fun updateRecipient(name: String, phone: String) {
        _uiState.update { it.copy(recipientName = name, recipientPhone = phone, errorMessage = null) }
    }

    fun updateMessageBody(body: String) {
        _uiState.update { it.copy(messageBody = body, errorMessage = null) }
    }

    fun insertVariable(variableKey: String) {
        _uiState.update {
            val current = it.messageBody
            val newBody = if (current.isEmpty()) variableKey else "$current $variableKey"
            it.copy(messageBody = newBody)
        }
    }

    fun updateChannel(channel: MessageChannel) {
        _uiState.update { it.copy(channel = channel) }
    }

    fun updateSelectedSim(simId: Int) {
        _uiState.update { it.copy(selectedSimId = simId) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(scheduledDate = date, dayOfMonth = date.dayOfMonth, errorMessage = null) }
    }

    fun updateTime(time: LocalTime) {
        _uiState.update { it.copy(scheduledTime = time, errorMessage = null) }
    }

    fun updateRecurrenceType(type: RecurrenceType) {
        _uiState.update { it.copy(recurrenceType = type) }
    }

    fun updateRecurrenceInterval(interval: Int) {
        _uiState.update { it.copy(recurrenceInterval = maxOf(1, interval)) }
    }

    fun toggleDayOfWeek(day: Int) {
        _uiState.update { state ->
            val list = state.selectedDaysOfWeek.toMutableList()
            if (list.contains(day)) {
                if (list.size > 1) list.remove(day)
            } else {
                list.add(day)
            }
            state.copy(selectedDaysOfWeek = list.sorted())
        }
    }

    fun updateDayOfMonth(day: Int) {
        _uiState.update { it.copy(dayOfMonth = day.coerceIn(1, 31)) }
    }

    fun updateRelativeDay(ordinal: Int, dayOfWeek: Int) {
        _uiState.update { it.copy(weekOfMonthOrdinal = ordinal, relativeDayOfWeek = dayOfWeek) }
    }

    fun updateShortMonthHandling(handling: ShortMonthHandling) {
        _uiState.update { it.copy(shortMonthHandling = handling) }
    }

    fun updateLeapYearHandling(handling: LeapYearHandling) {
        _uiState.update { it.copy(leapYearHandling = handling) }
    }

    fun updateEndCondition(endType: EndConditionType, endDate: LocalDate? = null, maxCount: Int? = null) {
        _uiState.update {
            it.copy(
                endType = endType,
                endDate = endDate ?: it.endDate,
                maxOccurrences = maxCount ?: it.maxOccurrences
            )
        }
    }

    fun updatePreSendReminderMinutes(minutes: Int) {
        _uiState.update { it.copy(preSendReminderMinutes = minutes) }
    }

    fun updateRetryPolicy(policy: RetryPolicy) {
        _uiState.update { it.copy(retryPolicy = policy) }
    }

    fun saveScheduleDirectly() {
        val state = _uiState.value
        if (state.recipientPhone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Recipient phone number is required") }
            return
        }
        if (state.messageBody.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Message body cannot be empty") }
            return
        }
        val triggerMs = state.calculateTriggerEpochMs()
        if (triggerMs <= System.currentTimeMillis() && !state.isEditMode) {
            _uiState.update { it.copy(errorMessage = "Scheduled time must be in the future") }
            return
        }

        _uiState.update { it.copy(errorMessage = null, showReviewDialog = false) }
        saveSchedule()
    }

    fun showReview() {
        val state = _uiState.value
        if (state.recipientPhone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a recipient phone number") }
            return
        }
        if (state.messageBody.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Message body cannot be empty") }
            return
        }
        val triggerMs = state.calculateTriggerEpochMs()
        if (triggerMs <= System.currentTimeMillis() && !state.isEditMode) {
            _uiState.update { it.copy(errorMessage = "Scheduled time must be in the future") }
            return
        }

        _uiState.update { it.copy(showReviewDialog = true, errorMessage = null) }
    }

    fun dismissReview() {
        _uiState.update { it.copy(showReviewDialog = false) }
    }

    fun saveSchedule() {
        val state = _uiState.value
        val triggerEpochMs = state.calculateTriggerEpochMs()
        val recurrenceConfig = state.toRecurrenceConfig()

        viewModelScope.launch {
            val entity = ScheduleEntity(
                id = state.scheduleId,
                recipientName = state.recipientName.trim(),
                recipientPhone = state.recipientPhone.trim(),
                messageBody = state.messageBody.trim(),
                channel = state.channel,
                simSubscriptionId = if (state.channel == MessageChannel.SMS && state.selectedSimId != -1) state.selectedSimId else null,
                recurrenceType = state.recurrenceType,
                recurrenceInterval = state.recurrenceInterval,
                selectedDaysOfWeek = state.selectedDaysOfWeek,
                dayOfMonth = state.dayOfMonth,
                weekOfMonthOrdinal = state.weekOfMonthOrdinal,
                relativeDayOfWeek = state.relativeDayOfWeek,
                shortMonthHandling = state.shortMonthHandling,
                leapYearHandling = state.leapYearHandling,
                startEpochMs = triggerEpochMs,
                nextExecutionEpochMs = triggerEpochMs,
                endType = state.endType,
                endEpochMs = recurrenceConfig.endEpochMs,
                maxOccurrences = recurrenceConfig.maxOccurrences,
                executionCount = 0,
                status = ScheduleStatus.SCHEDULED,
                preSendReminderMinutes = state.preSendReminderMinutes,
                retryPolicy = state.retryPolicy,
                retryAttempt = 0,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                lastErrorReason = null
            )

            val savedId = if (state.isEditMode) {
                scheduleRepo.updateSchedule(entity)
                state.scheduleId
            } else {
                scheduleRepo.insertSchedule(entity)
            }

            // Schedule alarm
            alarmScheduler.scheduleMessageExecution(
                scheduleId = savedId,
                triggerEpochMs = triggerEpochMs,
                preSendMinutes = state.preSendReminderMinutes
            )

            _uiState.update { it.copy(isSaved = true, showReviewDialog = false) }
        }
    }
}
