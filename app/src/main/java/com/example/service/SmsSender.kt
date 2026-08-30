package com.example.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.receiver.SmsStatusReceiver

class SmsSender(private val context: Context) {

    data class SendResult(
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(subscriptionId: Int?): SmsManager {
        return try {
            if (subscriptionId != null && subscriptionId != -1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val defaultManager = context.getSystemService(SmsManager::class.java)
                    defaultManager?.createForSubscriptionId(subscriptionId)
                        ?: SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                } else {
                    SmsManager.getDefault()
                }
            }
        } catch (e: Exception) {
            SmsManager.getDefault()
        }
    }

    fun sendSms(
        scheduleId: Long,
        historyId: Long,
        recipientPhone: String,
        messageText: String,
        subscriptionId: Int?
    ): SendResult {
        if (!hasSmsPermission()) {
            return SendResult(
                isSuccess = false,
                errorMessage = "SEND_SMS permission not granted. Please allow SMS permission in settings."
            )
        }

        val cleanedPhone = recipientPhone.replace(Regex("[^0-9+]"), "").trim()
        if (cleanedPhone.isBlank()) {
            return SendResult(
                isSuccess = false,
                errorMessage = "Recipient phone number is invalid or empty"
            )
        }

        if (messageText.isBlank()) {
            return SendResult(
                isSuccess = false,
                errorMessage = "Message body is empty"
            )
        }

        return try {
            val smsManager = getSmsManager(subscriptionId)
            val parts = smsManager.divideMessage(messageText)

            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            for (i in parts.indices) {
                val sentIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    putExtra(SmsStatusReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(SmsStatusReceiver.EXTRA_HISTORY_ID, historyId)
                    putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                    putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
                }
                val sentPi = PendingIntent.getBroadcast(
                    context,
                    ((historyId * 100) + i).toInt(),
                    sentIntent,
                    pendingIntentFlags
                )
                sentIntents.add(sentPi)

                val deliveryIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                    putExtra(SmsStatusReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(SmsStatusReceiver.EXTRA_HISTORY_ID, historyId)
                    putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                    putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
                }
                val delPi = PendingIntent.getBroadcast(
                    context,
                    ((historyId * 100) + i + 500000).toInt(),
                    deliveryIntent,
                    pendingIntentFlags
                )
                deliveryIntents.add(delPi)
            }

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    cleanedPhone,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    cleanedPhone,
                    null,
                    messageText,
                    sentIntents.firstOrNull(),
                    deliveryIntents.firstOrNull()
                )
            }

            SendResult(isSuccess = true)
        } catch (e: Exception) {
            e.printStackTrace()
            SendResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "SMS dispatch failed"
            )
        }
    }
}
