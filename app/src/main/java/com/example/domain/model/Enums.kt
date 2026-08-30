package com.example.domain.model

enum class MessageChannel(val displayName: String) {
    SMS("SMS (Direct SIM)")
}

enum class ScheduleStatus(val displayName: String) {
    SCHEDULED("Scheduled"),
    PROCESSING("Processing"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed"),
    MISSED("Missed"),
    CONFIRMATION_REQUIRED("Action Required")
}

enum class RecurrenceType(val displayName: String) {
    ONCE("Once (No Repeat)"),
    DAILY("Daily"),
    WEEKDAYS("Selected Weekdays"),
    WEEKLY("Weekly"),
    EVERY_X_WEEKS("Every X Weeks"),
    MONTHLY_DATE("Monthly (Same Date)"),
    MONTHLY_RELATIVE("Monthly (Relative Day)"),
    EVERY_X_MONTHS("Every X Months"),
    YEARLY("Yearly"),
    EVERY_X_YEARS("Every X Years"),
    CUSTOM_INTERVAL("Custom Interval")
}

enum class EndConditionType(val displayName: String) {
    NEVER("Never (Indefinite)"),
    UNTIL_DATE("Until Specific Date"),
    AFTER_COUNT("After Number of Times")
}

enum class ShortMonthHandling(val displayName: String) {
    LAST_VALID_DAY("Send on Last Day of Month (e.g. Feb 28/29)"),
    SKIP_MONTH("Skip Months with Fewer Days")
}

enum class LeapYearHandling(val displayName: String) {
    FEB_28("Send on Feb 28 on Non-Leap Years"),
    MAR_1("Send on Mar 1 on Non-Leap Years")
}

enum class RetryPolicy(val displayName: String, val maxAttempts: Int, val intervalMinutes: Long) {
    NO_RETRY("Do Not Retry", 0, 0),
    RETRY_ONCE_5MIN("Retry Once (After 5 mins)", 1, 5),
    RETRY_UP_TO_3_TIMES("Retry 3 Times (Every 5 mins)", 3, 5)
}

enum class DeliveryStatus(val displayName: String) {
    SCHEDULED("Scheduled"),
    PROCESSING("Processing"),
    SENT("Sent / Handed to System"),
    DELIVERED("Delivered (Verified)"),
    FAILED("Failed"),
    MISSED("Missed (Device Offline)"),
    CANCELLED("Cancelled"),
    CONFIRMATION_REQUIRED("Pending User Confirmation")
}

enum class TemplateCategory(val displayName: String) {
    BIRTHDAY("Birthdays"),
    ANNIVERSARY("Anniversaries"),
    GREETINGS("Greetings & Wishes"),
    BILL_REMINDER("Bills & Finance"),
    APPOINTMENT("Appointments & Events"),
    WORK("Work & Business"),
    CUSTOM("Custom")
}

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val iccId: String = "",
    val isDefault: Boolean = false
)

data class ContactRecipient(
    val name: String,
    val phoneNumber: String
)
