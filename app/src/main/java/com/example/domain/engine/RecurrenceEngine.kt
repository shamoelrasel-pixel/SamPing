package com.example.domain.engine

import com.example.domain.model.EndConditionType
import com.example.domain.model.LeapYearHandling
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ShortMonthHandling
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class RecurrenceConfig(
    val type: RecurrenceType = RecurrenceType.ONCE,
    val interval: Int = 1, // e.g. every X weeks/months/years
    val selectedDaysOfWeek: List<Int> = emptyList(), // 1 = Monday, 7 = Sunday (ISO standard)
    val dayOfMonth: Int = 1, // 1..31
    val weekOfMonthOrdinal: Int = 1, // 1..4, or -1 for LAST
    val relativeDayOfWeek: Int = 1, // 1..7
    val shortMonthHandling: ShortMonthHandling = ShortMonthHandling.LAST_VALID_DAY,
    val leapYearHandling: LeapYearHandling = LeapYearHandling.FEB_28,
    val endType: EndConditionType = EndConditionType.NEVER,
    val endEpochMs: Long? = null,
    val maxOccurrences: Int? = null
)

object RecurrenceEngine {

    /**
     * Calculates the next execution timestamp (epoch ms) after [currentExecutionEpochMs].
     * Returns null if schedule has reached its end condition or is a one-time message.
     */
    fun calculateNextExecution(
        currentExecutionEpochMs: Long,
        config: RecurrenceConfig,
        currentCount: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (config.type == RecurrenceType.ONCE) {
            return null
        }

        // Check occurrence count limit
        if (config.endType == EndConditionType.AFTER_COUNT && config.maxOccurrences != null) {
            if (currentCount >= config.maxOccurrences) {
                return null
            }
        }

        val currentDateTime = Instant.ofEpochMilli(currentExecutionEpochMs)
            .atZone(zoneId)

        val nextDateTime = computeNextDateTime(currentDateTime, config) ?: return null
        val nextEpochMs = nextDateTime.toInstant().toEpochMilli()

        // Check end date limit
        if (config.endType == EndConditionType.UNTIL_DATE && config.endEpochMs != null) {
            if (nextEpochMs > config.endEpochMs) {
                return null
            }
        }

        return nextEpochMs
    }

    /**
     * Generates a preview list of up to [count] upcoming execution timestamps.
     */
    fun previewUpcomingExecutions(
        startEpochMs: Long,
        config: RecurrenceConfig,
        count: Int = 5,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Long> {
        val list = mutableListOf<Long>()
        if (config.type == RecurrenceType.ONCE) {
            list.add(startEpochMs)
            return list
        }

        var currentEpoch = startEpochMs
        var currentCount = 0

        // Check if start time meets end condition
        if (config.endType == EndConditionType.UNTIL_DATE && config.endEpochMs != null && currentEpoch > config.endEpochMs) {
            return list
        }

        list.add(currentEpoch)
        currentCount++

        while (list.size < count) {
            val nextEpoch = calculateNextExecution(currentEpoch, config, currentCount, zoneId)
                ?: break
            list.add(nextEpoch)
            currentEpoch = nextEpoch
            currentCount++
        }

        return list
    }

    private fun computeNextDateTime(
        from: ZonedDateTime,
        config: RecurrenceConfig
    ): ZonedDateTime? {
        val time = from.toLocalTime()
        val interval = maxOf(1, config.interval)

        return when (config.type) {
            RecurrenceType.ONCE -> null

            RecurrenceType.DAILY -> {
                from.plusDays(interval.toLong())
            }

            RecurrenceType.WEEKDAYS -> {
                val validDays = if (config.selectedDaysOfWeek.isNotEmpty()) {
                    config.selectedDaysOfWeek.map { DayOfWeek.of(it) }.sorted()
                } else {
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                }

                var checkDate = from.toLocalDate().plusDays(1)
                // Search up to 14 days ahead for next matching weekday
                for (i in 0 until 14) {
                    if (validDays.contains(checkDate.dayOfWeek)) {
                        return checkDate.atTime(time).atZone(from.zone)
                    }
                    checkDate = checkDate.plusDays(1)
                }
                from.plusDays(1)
            }

            RecurrenceType.WEEKLY, RecurrenceType.EVERY_X_WEEKS -> {
                from.plusWeeks(interval.toLong())
            }

            RecurrenceType.MONTHLY_DATE, RecurrenceType.EVERY_X_MONTHS -> {
                var targetMonth = from.toLocalDate().plusMonths(interval.toLong())
                val desiredDay = config.dayOfMonth.coerceIn(1, 31)

                if (config.shortMonthHandling == ShortMonthHandling.SKIP_MONTH) {
                    // Advance months until we find one with enough days
                    var loopSafety = 0
                    while (YearMonth.from(targetMonth).lengthOfMonth() < desiredDay && loopSafety < 12) {
                        targetMonth = targetMonth.plusMonths(1)
                        loopSafety++
                    }
                    val validDay = minOf(desiredDay, YearMonth.from(targetMonth).lengthOfMonth())
                    targetMonth.withDayOfMonth(validDay).atTime(time).atZone(from.zone)
                } else {
                    // Clamp to last day of target month if month is shorter
                    val maxDays = YearMonth.from(targetMonth).lengthOfMonth()
                    val actualDay = minOf(desiredDay, maxDays)
                    targetMonth.withDayOfMonth(actualDay).atTime(time).atZone(from.zone)
                }
            }

            RecurrenceType.MONTHLY_RELATIVE -> {
                val nextMonthDate = from.toLocalDate().plusMonths(interval.toLong()).withDayOfMonth(1)
                val targetDayOfWeek = DayOfWeek.of(config.relativeDayOfWeek.coerceIn(1, 7))
                val adjustedDate = if (config.weekOfMonthOrdinal == -1) {
                    // Last matching day of month
                    nextMonthDate.with(TemporalAdjusters.lastInMonth(targetDayOfWeek))
                } else {
                    val ordinal = config.weekOfMonthOrdinal.coerceIn(1, 4)
                    nextMonthDate.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, targetDayOfWeek))
                }
                adjustedDate.atTime(time).atZone(from.zone)
            }

            RecurrenceType.YEARLY, RecurrenceType.EVERY_X_YEARS -> {
                val fromDate = from.toLocalDate()
                val targetYear = fromDate.year + interval
                val targetMonth = fromDate.month

                val targetDate = if (fromDate.monthValue == 2 && fromDate.dayOfMonth == 29) {
                    val isLeap = java.time.Year.of(targetYear).isLeap
                    if (isLeap) {
                        LocalDate.of(targetYear, 2, 29)
                    } else {
                        if (config.leapYearHandling == LeapYearHandling.MAR_1) {
                            LocalDate.of(targetYear, 3, 1)
                        } else {
                            LocalDate.of(targetYear, 2, 28)
                        }
                    }
                } else {
                    LocalDate.of(targetYear, targetMonth, fromDate.dayOfMonth)
                }
                targetDate.atTime(time).atZone(from.zone)
            }

            RecurrenceType.CUSTOM_INTERVAL -> {
                from.plusDays(interval.toLong())
            }
        }
    }

    /**
     * Previews the next [count] executions starting after [startEpochMs].
     */
    fun previewNextExecutions(
        startEpochMs: Long,
        config: RecurrenceConfig,
        count: Int = 5,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Long> {
        val result = mutableListOf<Long>()
        var current = startEpochMs
        var occurrence = 1
        while (result.size < count) {
            val next = calculateNextExecution(current, config, occurrence, zoneId) ?: break
            result.add(next)
            current = next
            occurrence++
        }
        return result
    }
}
