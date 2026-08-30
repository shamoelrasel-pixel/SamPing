package com.example.ui.screens.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoSendApplication
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.engine.RecurrenceEngine
import com.example.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    val schedulesForSelectedDate: List<ScheduleEntity> = emptyList(),
    val datesWithSchedulesInMonth: Set<LocalDate> = emptySet(),
    val allSchedules: List<ScheduleEntity> = emptyList()
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoSendApplication
    private val scheduleRepo = app.scheduleRepository

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _currentMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CalendarUiState> = combine(
        scheduleRepo.allSchedules,
        _selectedDate,
        _currentMonth
    ) { schedules, selectedDate, currentMonth ->
        val active = schedules.filter { it.status == ScheduleStatus.SCHEDULED }

        // Find which dates in the current month have scheduled items (including recurring occurrences)
        val datesWithSchedules = mutableSetOf<LocalDate>()
        val startOfMonth = currentMonth.atDay(1)
        val endOfMonth = currentMonth.atEndOfMonth()
        val zoneId = ZoneId.systemDefault()

        for (schedule in active) {
            val previewDates = RecurrenceEngine.previewUpcomingExecutions(
                startEpochMs = schedule.nextExecutionEpochMs,
                config = schedule.toRecurrenceConfig(),
                count = 30
            )

            for (epoch in previewDates) {
                val ld = java.time.Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDate()
                if (!ld.isBefore(startOfMonth) && !ld.isAfter(endOfMonth)) {
                    datesWithSchedules.add(ld)
                }
            }
        }

        // Find schedules matching the selected date
        val forSelectedDate = active.filter { schedule ->
            val previewDates = RecurrenceEngine.previewUpcomingExecutions(
                startEpochMs = schedule.nextExecutionEpochMs,
                config = schedule.toRecurrenceConfig(),
                count = 30
            )
            previewDates.any { epoch ->
                val ld = java.time.Instant.ofEpochMilli(epoch).atZone(zoneId).toLocalDate()
                ld == selectedDate
            }
        }.sortedBy { it.nextExecutionEpochMs }

        CalendarUiState(
            selectedDate = selectedDate,
            currentMonth = currentMonth,
            schedulesForSelectedDate = forSelectedDate,
            datesWithSchedulesInMonth = datesWithSchedules,
            allSchedules = schedules
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _currentMonth.value) {
            _currentMonth.value = YearMonth.from(date)
        }
    }

    fun previousMonth() {
        _currentMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        _currentMonth.update { it.plusMonths(1) }
    }
}
