package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * BroadcastReceiver that allows one-tap OTP / PIN code copying directly from the system notification shade.
 */
class OtpCopyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("OTP Code", code)
            clipboard?.setPrimaryClip(clip)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Code copied: $code", Toast.LENGTH_SHORT).show()
            }

            if (notifId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(notifId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_COPY_OTP = "com.example.ACTION_COPY_OTP"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notif_id"
    }
}
