package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.SmsReceivePipeline

/**
 * BroadcastReceiver required for Android Default SMS App compliance (SMS_DELIVER_ACTION).
 * Handles incoming SMS messages and delegates to SmsReceivePipeline.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        SmsReceivePipeline.handleIncomingSms(context, intent) {
            try {
                pendingResult.finish()
            } catch (e: Exception) {
                // ignore if already finished
            }
        }
    }
}

