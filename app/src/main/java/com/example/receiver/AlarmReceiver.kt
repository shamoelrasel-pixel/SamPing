package com.example.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.engine.RecurrenceEngine
import com.example.domain.engine.TemplateParser
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ScheduleStatus
import com.example.service.AlarmScheduler
import com.example.service.NotificationHelper
import com.example.service.SimManager
import com.example.service.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE_SCHEDULE = "com.example.autosend.ACTION_EXECUTE_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_IS_PRE_REMINDER = "extra_is_pre_reminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val isPreReminder = intent.getBooleanExtra(EXTRA_IS_PRE_REMINDER, false)

        if (scheduleId == -1L) return

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoSend:ExecutionLock"
        ).apply {
            acquire(15000L) // 15s safety timeout
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val scheduleDao = database.scheduleDao()
                val historyDao = database.historyDao()
                val notificationHelper = NotificationHelper(context)
                val alarmScheduler = AlarmScheduler(context)
                val simManager = SimManager(context)

                val schedule = scheduleDao.getScheduleById(scheduleId)
                if (schedule == null) return@launch

                // Handle pre-send reminder
                if (isPreReminder) {
                    if (schedule.status == ScheduleStatus.SCHEDULED) {
                        val parsedPreview = TemplateParser.parse(
                            schedule.messageBody,
                            schedule.recipientName,
                            schedule.nextExecutionEpochMs
                        )
                        notificationHelper.showPreSendReminderNotification(
                            scheduleId = schedule.id,
                            recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                            channelName = schedule.channel.displayName,
                            minutesRemaining = schedule.preSendReminderMinutes,
                            messagePreview = parsedPreview
                        )
                    }
                    return@launch
                }

                // If schedule is paused or cancelled, ignore
                if (schedule.status != ScheduleStatus.SCHEDULED) return@launch

                val parsedMessage = TemplateParser.parse(
                    schedule.messageBody,
                    schedule.recipientName,
                    schedule.nextExecutionEpochMs
                )

                val simInfo = simManager.getSimInfoForSubscriptionId(schedule.simSubscriptionId)
                val simDisplayName = simInfo?.displayName ?: "SIM 1"

                val hasSmsPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasSmsPermission) {
                    val errorMsg = "SEND_SMS permission is not granted. Cannot dispatch scheduled SMS."
                    val historyId = historyDao.insertHistory(
                        HistoryEntity(
                            scheduleId = schedule.id,
                            recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                            recipientPhone = schedule.recipientPhone,
                            messageBody = parsedMessage,
                            channel = MessageChannel.SMS,
                            simDisplayName = simDisplayName,
                            scheduledEpochMs = schedule.nextExecutionEpochMs,
                            executedEpochMs = System.currentTimeMillis(),
                            status = DeliveryStatus.FAILED,
                            errorReason = errorMsg,
                            retryAttempt = schedule.retryAttempt
                        )
                    )

                    scheduleDao.updateSchedule(
                        schedule.copy(
                            status = ScheduleStatus.FAILED,
                            lastErrorReason = errorMsg,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    )

                    notificationHelper.showPermissionRequiredNotification(
                        scheduleId = schedule.id,
                        recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone }
                    )
                    return@launch
                }

                // Record History Log with SENT status
                val historyId = historyDao.insertHistory(
                    HistoryEntity(
                        scheduleId = schedule.id,
                        recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                        recipientPhone = schedule.recipientPhone,
                        messageBody = parsedMessage,
                        channel = MessageChannel.SMS,
                        simDisplayName = simDisplayName,
                        scheduledEpochMs = schedule.nextExecutionEpochMs,
                        executedEpochMs = System.currentTimeMillis(),
                        status = DeliveryStatus.SENT,
                        errorReason = null,
                        retryAttempt = schedule.retryAttempt
                    )
                )

                val smsSender = SmsSender(context)
                val sendResult = smsSender.sendSms(
                    scheduleId = schedule.id,
                    historyId = historyId,
                    recipientPhone = schedule.recipientPhone,
                    messageText = parsedMessage,
                    subscriptionId = schedule.simSubscriptionId
                )

                if (sendResult.isSuccess) {
                    historyDao.updateStatus(historyId, DeliveryStatus.SENT, null)
                    
                    val app = context.applicationContext as? com.example.AutoSendApplication
                    val userPrefs = app?.userPreferencesRepository?.userPreferencesFlow?.firstOrNull()
                    val shouldNotify = userPrefs?.notifyOnSent ?: true
                    val sentTone = userPrefs?.scheduledSmsTone ?: "BELL"

                    if (shouldNotify) {
                        notificationHelper.showSmsSentNotification(
                            recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                            messagePreview = parsedMessage,
                            tone = sentTone
                        )
                    }
                    advanceScheduleRecurrence(schedule, scheduleDao, alarmScheduler)
                } else {
                    // Failure handling & Retry policy
                    val canRetry = schedule.retryAttempt < schedule.retryPolicy.maxAttempts
                    if (canRetry) {
                        val nextRetryAttempt = schedule.retryAttempt + 1
                        val retryTriggerMs = System.currentTimeMillis() + (schedule.retryPolicy.intervalMinutes * 60 * 1000L)

                        scheduleDao.updateSchedule(
                            schedule.copy(
                                retryAttempt = nextRetryAttempt,
                                nextExecutionEpochMs = retryTriggerMs,
                                lastErrorReason = sendResult.errorMessage,
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                        )

                        historyDao.updateStatus(
                            historyId,
                            DeliveryStatus.FAILED,
                            "Failed: ${sendResult.errorMessage}. Retrying (Attempt $nextRetryAttempt/${schedule.retryPolicy.maxAttempts})..."
                        )

                        alarmScheduler.scheduleMessageExecution(schedule.id, retryTriggerMs, 0)
                    } else {
                        scheduleDao.updateSchedule(
                            schedule.copy(
                                status = ScheduleStatus.FAILED,
                                lastErrorReason = sendResult.errorMessage,
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                        )

                        historyDao.updateStatus(
                            historyId,
                            DeliveryStatus.FAILED,
                            sendResult.errorMessage ?: "SMS delivery error"
                        )

                        notificationHelper.showSmsFailedNotification(
                            recipientName = schedule.recipientName.ifBlank { schedule.recipientPhone },
                            errorReason = sendResult.errorMessage ?: "Send failure",
                            scheduleId = schedule.id
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }

    private suspend fun advanceScheduleRecurrence(
        schedule: ScheduleEntity,
        scheduleDao: com.example.data.local.dao.ScheduleDao,
        alarmScheduler: AlarmScheduler
    ) {
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
                retryAttempt = 0,
                status = ScheduleStatus.SCHEDULED,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            scheduleDao.updateSchedule(updated)
            alarmScheduler.scheduleMessageExecution(
                scheduleId = updated.id,
                triggerEpochMs = nextExecutionMs,
                preSendMinutes = updated.preSendReminderMinutes
            )
        } else {
            val updated = schedule.copy(
                executionCount = nextCount,
                retryAttempt = 0,
                status = ScheduleStatus.COMPLETED,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            scheduleDao.updateSchedule(updated)
        }
    }
}
