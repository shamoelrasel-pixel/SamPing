package com.example.domain.engine

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TemplateParser {

    data class VariableInfo(
        val key: String,
        val description: String,
        val example: String
    )

    val AVAILABLE_VARIABLES = listOf(
        VariableInfo("{name}", "Recipient Full Name", "John Doe"),
        VariableInfo("{first_name}", "Recipient First Name", "John"),
        VariableInfo("{date}", "Scheduled Date", "Aug 22, 2026"),
        VariableInfo("{time}", "Scheduled Time", "09:00 AM"),
        VariableInfo("{day_of_week}", "Day of the Week", "Saturday"),
        VariableInfo("{month}", "Month Name", "August")
    )

    fun parse(
        templateText: String,
        recipientName: String,
        scheduledEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val zonedDateTime = Instant.ofEpochMilli(scheduledEpochMs).atZone(zoneId)
        val firstName = recipientName.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Friend"
        val cleanName = recipientName.ifBlank { "Friend" }

        val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())

        val dateStr = zonedDateTime.format(dateFormatter)
        val timeStr = zonedDateTime.format(timeFormatter)
        val dayOfWeekStr = zonedDateTime.format(dayOfWeekFormatter)
        val monthStr = zonedDateTime.format(monthFormatter)

        return templateText
            .replace("{name}", cleanName, ignoreCase = true)
            .replace("{recipient_name}", cleanName, ignoreCase = true)
            .replace("{first_name}", firstName, ignoreCase = true)
            .replace("{date}", dateStr, ignoreCase = true)
            .replace("{time}", timeStr, ignoreCase = true)
            .replace("{day_of_week}", dayOfWeekStr, ignoreCase = true)
            .replace("{day}", dayOfWeekStr, ignoreCase = true)
            .replace("{month}", monthStr, ignoreCase = true)
    }
}
