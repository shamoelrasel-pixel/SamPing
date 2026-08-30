package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * BroadcastReceiver required for Android Default SMS App compliance.
 * Handles incoming WAP PUSH MMS messages when AutoSend is set as the default SMS application.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            // Handled for WAP Push MMS
        }
    }
}
