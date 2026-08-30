package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Messages & Scheduler")
    object Schedules : Screen("schedules", "Schedules")
    object Calendar : Screen("calendar", "Calendar")
    object Templates : Screen("templates", "Templates")
    object History : Screen("history", "History & Logs")
    object Settings : Screen("settings", "Settings")
    object BirthdayReminder : Screen("birthday_reminder", "Birthday & Anniversary")
    object Onboarding : Screen("onboarding", "Welcome")
    object RecycleBin : Screen("recycle_bin", "Recycle Bin")
    object BlockedNumbers : Screen("blocked_numbers", "Blocked Numbers")
    
    object CreateSchedule : Screen("create_schedule?scheduleId={scheduleId}&templateId={templateId}&phone={phone}&name={name}", "New Schedule") {
        fun createRoute(scheduleId: Long? = null, templateId: Long? = null, phone: String? = null, name: String? = null): String {
            val sId = scheduleId ?: -1L
            val tId = templateId ?: -1L
            val p = phone?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
            val n = name?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
            return "create_schedule?scheduleId=$sId&templateId=$tId&phone=$p&name=$n"
        }
    }

    object ChatThread : Screen("chat_thread?threadId={threadId}&address={address}&name={name}", "Conversation") {
        fun createRoute(threadId: Long = -1L, address: String = "", name: String = ""): String {
            val encAddr = java.net.URLEncoder.encode(address, "UTF-8")
            val encName = java.net.URLEncoder.encode(name, "UTF-8")
            return "chat_thread?threadId=$threadId&address=$encAddr&name=$encName"
        }
    }

    object Compose : Screen("compose?address={address}&name={name}&body={body}", "New Message") {
        fun createRoute(address: String = "", name: String = "", body: String = ""): String {
            val encAddr = java.net.URLEncoder.encode(address, "UTF-8")
            val encName = java.net.URLEncoder.encode(name, "UTF-8")
            val encBody = java.net.URLEncoder.encode(body, "UTF-8")
            return "compose?address=$encAddr&name=$encName&body=$encBody"
        }
    }
}
