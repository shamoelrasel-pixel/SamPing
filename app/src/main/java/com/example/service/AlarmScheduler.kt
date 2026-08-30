package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.MainActivity
import com.example.receiver.AlarmReceiver

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleMessageExecution(
        scheduleId: Long,
        triggerEpochMs: Long,
        preSendMinutes: Int = 0
    ) {
        val now = System.currentTimeMillis()
        if (triggerEpochMs <= now) return

        // 1. Schedule Primary Execution Alarm
        val executionIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EXECUTE_SCHEDULE
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_IS_PRE_REMINDER, false)
        }

        val executionPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            executionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            scheduleId.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use AlarmClockInfo for maximum reliability on all OEM power managers
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerEpochMs, showPendingIntent),
                    executionPendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMs,
                    executionPendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback for missing exact alarm permission
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMs,
                    executionPendingIntent
                )
            } catch (ignored: Exception) {
            }
        }

        // 2. Schedule Pre-Send Reminder Alarm (if enabled and time is in future)
        if (preSendMinutes > 0) {
            val preReminderEpochMs = triggerEpochMs - (preSendMinutes * 60 * 1000L)
            if (preReminderEpochMs > now) {
                val preIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_EXECUTE_SCHEDULE
                    putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(AlarmReceiver.EXTRA_IS_PRE_REMINDER, true)
                }
                val prePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (scheduleId + 100000).toInt(),
                    preIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            preReminderEpochMs,
                            prePendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            preReminderEpochMs,
                            prePendingIntent
                        )
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun cancelSchedule(scheduleId: Long) {
        val executionIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EXECUTE_SCHEDULE
        }
        val executionPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            executionIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (executionPendingIntent != null) {
            alarmManager.cancel(executionPendingIntent)
            executionPendingIntent.cancel()
        }

        val preIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EXECUTE_SCHEDULE
        }
        val prePendingIntent = PendingIntent.getBroadcast(
            context,
            (scheduleId + 100000).toInt(),
            preIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (prePendingIntent != null) {
            alarmManager.cancel(prePendingIntent)
            prePendingIntent.cancel()
        }
    }
}
