package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.engine.RecurrenceEngine
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.ScheduleStatus
import com.example.service.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val scheduleDao = database.scheduleDao()
                val historyDao = database.historyDao()
                val preferencesRepo = UserPreferencesRepository(context)
                val userPrefs = preferencesRepo.userPreferencesFlow.first()
                val alarmScheduler = AlarmScheduler(context)

                val activeSchedules = scheduleDao.getActiveScheduledItems()
                val now = System.currentTimeMillis()

                for (schedule in activeSchedules) {
                    if (schedule.nextExecutionEpochMs <= now) {
                        // The schedule was missed while device was powered down
                        if (userPrefs.missedPolicyCatchUp) {
                            // Immediate catch-up execution
                            val execIntent = Intent(context, AlarmReceiver::class.java).apply {
                                this.action = AlarmReceiver.ACTION_EXECUTE_SCHEDULE
                                putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, schedule.id)
                                putExtra(AlarmReceiver.EXTRA_IS_PRE_REMINDER, false)
                            }
                            context.sendBroadcast(execIntent)
                        } else {
                            // Record Missed Log in History
                            historyDao.insertHistory(
                                HistoryEntity(
                                    scheduleId = schedule.id,
                                    recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                                    recipientPhone = schedule.recipientPhone,
                                    messageBody = schedule.messageBody,
                                    channel = schedule.channel,
                                    simDisplayName = null,
                                    scheduledEpochMs = schedule.nextExecutionEpochMs,
                                    executedEpochMs = now,
                                    status = DeliveryStatus.MISSED,
                                    errorReason = "Device was powered off during scheduled delivery time",
                                    retryAttempt = 0
                                )
                            )

                            // Advance to next recurrence or mark missed
                            val nextCount = schedule.executionCount + 1
                            val nextExecutionMs = RecurrenceEngine.calculateNextExecution(
                                currentExecutionEpochMs = schedule.nextExecutionEpochMs,
                                config = schedule.toRecurrenceConfig(),
                                currentCount = nextCount
                            )

                            if (nextExecutionMs != null) {
                                val updated = schedule.copy(
                                    nextExecutionEpochMs = nextExecutionMs,
                                    executionCount = nextCount,
                                    status = ScheduleStatus.SCHEDULED,
                                    updatedAtEpochMs = now
                                )
                                scheduleDao.updateSchedule(updated)
                                alarmScheduler.scheduleMessageExecution(
                                    updated.id,
                                    nextExecutionMs,
                                    updated.preSendReminderMinutes
                                )
                            } else {
                                scheduleDao.updateSchedule(
                                    schedule.copy(
                                        status = ScheduleStatus.MISSED,
                                        lastErrorReason = "Missed due to device downtime",
                                        updatedAtEpochMs = now
                                    )
                                )
                            }
                        }
                    } else {
                        // Future schedule -> restore exact alarm
                        alarmScheduler.scheduleMessageExecution(
                            scheduleId = schedule.id,
                            triggerEpochMs = schedule.nextExecutionEpochMs,
                            preSendMinutes = schedule.preSendReminderMinutes
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
