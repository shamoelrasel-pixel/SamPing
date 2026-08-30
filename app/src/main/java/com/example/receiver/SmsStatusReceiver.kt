package com.example.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.data.local.AppDatabase
import com.example.domain.model.DeliveryStatus
import com.example.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "com.example.autosend.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.autosend.ACTION_SMS_DELIVERED"

        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)

        if (historyId == -1L) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val historyDao = database.historyDao()
                val notificationHelper = NotificationHelper(context)

                when (action) {
                    ACTION_SMS_SENT -> {
                        if (resultCode == Activity.RESULT_OK || resultCode == 0) {
                            if (partIndex == totalParts - 1) {
                                historyDao.updateStatus(historyId, DeliveryStatus.SENT, null)
                                val historyItem = historyDao.getHistoryById(historyId)
                                if (historyItem != null) {
                                    notificationHelper.showSmsSentNotification(
                                        recipientName = historyItem.recipientName,
                                        messagePreview = historyItem.messageBody
                                    )
                                }
                            }
                        } else if (resultCode == SmsManager.RESULT_ERROR_RADIO_OFF || 
                                   resultCode == SmsManager.RESULT_ERROR_NO_SERVICE) {
                            val errorString = when (resultCode) {
                                SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular network service available"
                                SmsManager.RESULT_ERROR_RADIO_OFF -> "Device cellular radio is turned off (Airplane mode)"
                                else -> "SMS transmission failed (Error code $resultCode)"
                            }
                            historyDao.updateStatus(historyId, DeliveryStatus.FAILED, errorString)
                            val historyItem = historyDao.getHistoryById(historyId)
                            if (historyItem != null) {
                                notificationHelper.showSmsFailedNotification(
                                    recipientName = historyItem.recipientName,
                                    errorReason = errorString,
                                    scheduleId = scheduleId
                                )
                            }
                        } else {
                            // On diverse carrier implementations, virtual SIMs, or IMS/VoLTE SMS, 
                            // the message is transmitted even with non-standard result codes.
                            if (partIndex == totalParts - 1) {
                                historyDao.updateStatus(historyId, DeliveryStatus.SENT, null)
                            }
                        }
                    }

                    ACTION_SMS_DELIVERED -> {
                        if (partIndex == totalParts - 1) {
                            historyDao.updateStatus(historyId, DeliveryStatus.DELIVERED, null)
                        }
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
