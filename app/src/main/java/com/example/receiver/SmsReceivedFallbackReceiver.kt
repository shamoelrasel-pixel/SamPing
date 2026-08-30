package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.SmsReceivePipeline

/**
 * Fallback BroadcastReceiver for SMS_RECEIVED_ACTION to ensure high reliability across
 * varying Android versions, custom ROMs, and non-default SMS states.
 */
class SmsReceivedFallbackReceiver : BroadcastReceiver() {
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
