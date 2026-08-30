package com.example

import com.example.domain.engine.RecurrenceConfig
import com.example.domain.engine.RecurrenceEngine
import com.example.domain.engine.TemplateParser
import com.example.domain.model.EndConditionType
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ShortMonthHandling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceAndTemplateUnitTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun testOnce_hasNoNextTrigger() {
        val config = RecurrenceConfig(type = RecurrenceType.ONCE)
        val currentEpoch = ZonedDateTime.of(2026, 5, 10, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val next = RecurrenceEngine.calculateNextExecution(currentEpoch, config, currentCount = 1, zoneId = zoneId)
        assertNull(next)
    }

    @Test
    fun testDailyRecurrence() {
        val config = RecurrenceConfig(type = RecurrenceType.DAILY)
        val currentZdt = ZonedDateTime.of(2026, 5, 10, 9, 30, 0, 0, zoneId)
        val currentEpoch = currentZdt.toInstant().toEpochMilli()

        val nextEpoch = RecurrenceEngine.calculateNextExecution(currentEpoch, config, currentCount = 1, zoneId = zoneId)
        assertNotNull(nextEpoch)

        val nextZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextEpoch!!), zoneId)
        assertEquals(2026, nextZdt.year)
        assertEquals(5, nextZdt.monthValue)
        assertEquals(11, nextZdt.dayOfMonth)
        assertEquals(9, nextZdt.hour)
        assertEquals(30, nextZdt.minute)
    }

    @Test
    fun testTemplateParser() {
        val template = "Hi {first_name}, your meeting is scheduled on {date}."
        val epochMs = ZonedDateTime.of(2026, 8, 22, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val parsed = TemplateParser.parse(template, "Alice Smith", epochMs, zoneId)
        assertTrue(parsed.contains("Hi Alice"))
        assertTrue(parsed.contains("Aug 22, 2026"))
    }
}
