package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.engine.RecurrenceConfig
import com.example.domain.model.EndConditionType
import com.example.domain.model.LeapYearHandling
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RetryPolicy
import com.example.domain.model.ScheduleStatus
import com.example.domain.model.ShortMonthHandling

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipientName: String,
    val recipientPhone: String,
    val messageBody: String,
    val channel: MessageChannel = MessageChannel.SMS,
    val simSubscriptionId: Int? = null,
    val recurrenceType: RecurrenceType = RecurrenceType.ONCE,
    val recurrenceInterval: Int = 1,
    val selectedDaysOfWeek: List<Int> = emptyList(),
    val dayOfMonth: Int = 1,
    val weekOfMonthOrdinal: Int = 1,
    val relativeDayOfWeek: Int = 1,
    val shortMonthHandling: ShortMonthHandling = ShortMonthHandling.LAST_VALID_DAY,
    val leapYearHandling: LeapYearHandling = LeapYearHandling.FEB_28,
    val startEpochMs: Long,
    val nextExecutionEpochMs: Long,
    val endType: EndConditionType = EndConditionType.NEVER,
    val endEpochMs: Long? = null,
    val maxOccurrences: Int? = null,
    val executionCount: Int = 0,
    val status: ScheduleStatus = ScheduleStatus.SCHEDULED,
    val preSendReminderMinutes: Int = 0, // 0 = disabled
    val retryPolicy: RetryPolicy = RetryPolicy.NO_RETRY,
    val retryAttempt: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val lastErrorReason: String? = null
) {
    fun toRecurrenceConfig(): RecurrenceConfig {
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
            endEpochMs = endEpochMs,
            maxOccurrences = maxOccurrences
        )
    }
}
