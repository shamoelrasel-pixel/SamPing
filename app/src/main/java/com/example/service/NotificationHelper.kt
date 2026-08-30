package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.util.SenderIdentityHelper
import com.example.receiver.OtpCopyReceiver

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_INCOMING_SHAMRING = "channel_incoming_shamring_v7"
        const val CHANNEL_INCOMING_CHIME = "channel_incoming_chime_v7"
        const val CHANNEL_INCOMING_BELL = "channel_incoming_bell_v7"
        const val CHANNEL_INCOMING_SYSTEM = "channel_incoming_system_v7"
        const val CHANNEL_INCOMING_SILENT = "channel_incoming_silent_v7"

        const val CHANNEL_DELIVERY_BELL = "channel_delivery_bell_v7"
        const val CHANNEL_DELIVERY_SHAMRING = "channel_delivery_shamring_v7"
        const val CHANNEL_DELIVERY_CHIME = "channel_delivery_chime_v7"
        const val CHANNEL_DELIVERY_SYSTEM = "channel_delivery_system_v7"
        const val CHANNEL_DELIVERY_SILENT = "channel_delivery_silent_v7"

        const val CHANNEL_REMINDERS = "channel_reminders_v7"
        const val CHANNEL_ALERTS = "channel_alerts_v7"

        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private val defaultNotificationSound: Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    init {
        createNotificationChannels()
    }

    fun getSoundUri(tone: String): Uri? {
        val normalizedTone = tone.trim().uppercase()
        return when (normalizedTone) {
            "SHAMRING", "CHIME" -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.incoming_sms_tone}")
            "BELL", "SHAMPING" -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.scheduled_sms_tone}")
            "SYSTEM" -> defaultNotificationSound
            "SILENT" -> null
            else -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.incoming_sms_tone}")
        }
    }

    /**
     * Plays the custom tone reliably using MediaPlayer with proper AudioAttributes,
     * with multi-stage fallback to ensure tone preview and notification alerts always work.
     */
    fun playTone(tone: String) {
        val normalizedTone = tone.trim().uppercase()
        if (normalizedTone == "SILENT") return

        try {
            if (normalizedTone == "SYSTEM") {
                val ringtone = RingtoneManager.getRingtone(context, defaultNotificationSound)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .build()
                    }
                    ringtone.play()
                    return
                }
            }

            val resId = when (normalizedTone) {
                "BELL", "SHAMPING" -> R.raw.scheduled_sms_tone
                else -> R.raw.incoming_sms_tone
            }

            // Primary: MediaPlayer.create manages lifecycle, AudioAttributes and preparation reliably
            val mediaPlayer = MediaPlayer.create(context, resId)
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener { mp ->
                    try {
                        mp.reset()
                        mp.release()
                    } catch (e: Exception) {
                        // ignore cleanup exceptions
                    }
                }
                mediaPlayer.start()
                return
            }

            // Secondary fallback: manual AssetFileDescriptor without premature closing
            val afd = context.resources.openRawResourceFd(resId)
            if (afd != null) {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                )
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                player.prepare()
                afd.close()
                player.setOnCompletionListener { mp ->
                    try {
                        mp.reset()
                        mp.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                player.start()
                return
            }

            // Tertiary fallback: System ringtone
            val defaultRingtone = RingtoneManager.getRingtone(context, defaultNotificationSound)
            defaultRingtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
            // Final safety fallback
            try {
                val fallbackRingtone = RingtoneManager.getRingtone(context, defaultNotificationSound)
                fallbackRingtone?.play()
            } catch (fallbackError: Exception) {
                fallbackError.printStackTrace()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            val shamRingUri = getSoundUri("SHAMRING")
            val bellUri = getSoundUri("BELL")

            val incomingShamRing = NotificationChannel(
                CHANNEL_INCOMING_SHAMRING,
                "Incoming SMS (ShamRing Alert)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS alerts with ShamRing tone"
                setSound(shamRingUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val incomingChime = NotificationChannel(
                CHANNEL_INCOMING_CHIME,
                "Incoming SMS (ShamPing Chime)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS alerts with ShamPing Chime melody"
                setSound(shamRingUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val incomingBell = NotificationChannel(
                CHANNEL_INCOMING_BELL,
                "Incoming SMS (ShamPing Bell)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS alerts with ShamPing Bell ping"
                setSound(bellUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val incomingSystem = NotificationChannel(
                CHANNEL_INCOMING_SYSTEM,
                "Incoming SMS (System Sound)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS alerts with system notification sound"
                setSound(defaultNotificationSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val incomingSilent = NotificationChannel(
                CHANNEL_INCOMING_SILENT,
                "Incoming SMS (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS visual and vibration alerts only"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val deliveryBell = NotificationChannel(
                CHANNEL_DELIVERY_BELL,
                "Scheduled SMS Sent (ShamPing Bell)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sent confirmations with ShamPing Bell ping"
                setSound(bellUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val deliveryShamRing = NotificationChannel(
                CHANNEL_DELIVERY_SHAMRING,
                "Scheduled SMS Sent (ShamRing Alert)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sent confirmations with ShamRing Alert"
                setSound(shamRingUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val deliveryChime = NotificationChannel(
                CHANNEL_DELIVERY_CHIME,
                "Scheduled SMS Sent (ShamPing Chime)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sent confirmations with ShamPing Chime melody"
                setSound(shamRingUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val deliverySystem = NotificationChannel(
                CHANNEL_DELIVERY_SYSTEM,
                "Scheduled SMS Sent (System Sound)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sent confirmations with system notification sound"
                setSound(defaultNotificationSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val deliverySilent = NotificationChannel(
                CHANNEL_DELIVERY_SILENT,
                "Scheduled SMS Sent (Silent)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sent confirmations silent without sound"
                setSound(null, null)
                setShowBadge(true)
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Message Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts before scheduled messages are executed"
                setSound(shamRingUri ?: defaultNotificationSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150, 200)
                enableLights(true)
                setShowBadge(true)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Failures & Urgent Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for message delivery failures or missed schedules"
                setSound(defaultNotificationSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
                setShowBadge(true)
            }

            val legacyChannels = listOf(
                "channel_incoming_shamring_v6", "channel_incoming_chime_v6", "channel_incoming_bell_v6", "channel_incoming_system_v6", "channel_incoming_silent_v6",
                "channel_delivery_bell_v6", "channel_delivery_shamring_v6", "channel_delivery_chime_v6", "channel_delivery_system_v6", "channel_delivery_silent_v6",
                "channel_reminders_v6", "channel_alerts_v6",
                "channel_incoming_shamring_v5", "channel_incoming_chime_v5", "channel_incoming_bell_v5", "channel_incoming_system_v5", "channel_incoming_silent_v5",
                "channel_delivery_bell_v5", "channel_delivery_shamring_v5", "channel_delivery_chime_v5", "channel_delivery_system_v5", "channel_delivery_silent_v5",
                "channel_reminders_v5", "channel_alerts_v5",
                "channel_incoming_shamring_v4", "channel_incoming_chime_v4", "channel_incoming_bell_v4", "channel_incoming_system_v4", "channel_incoming_silent_v4",
                "channel_delivery_bell_v4", "channel_delivery_shamring_v4", "channel_delivery_chime_v4", "channel_delivery_system_v4", "channel_delivery_silent_v4",
                "channel_incoming_sms", "channel_scheduled_sms", "channel_sms_delivery"
            )
            for (legacyId in legacyChannels) {
                try {
                    notificationManager.deleteNotificationChannel(legacyId)
                } catch (e: Exception) {
                    // ignore
                }
            }

            notificationManager.createNotificationChannels(
                listOf(
                    incomingShamRing, incomingChime, incomingBell, incomingSystem, incomingSilent,
                    deliveryBell, deliveryShamRing, deliveryChime, deliverySystem, deliverySilent,
                    reminderChannel, alertChannel
                )
            )
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getIncomingChannelId(tone: String): String {
        val normalizedTone = tone.trim().uppercase()
        return when (normalizedTone) {
            "SHAMRING" -> CHANNEL_INCOMING_SHAMRING
            "CHIME" -> CHANNEL_INCOMING_CHIME
            "BELL", "SHAMPING" -> CHANNEL_INCOMING_BELL
            "SYSTEM" -> CHANNEL_INCOMING_SYSTEM
            "SILENT" -> CHANNEL_INCOMING_SILENT
            else -> CHANNEL_INCOMING_SHAMRING
        }
    }

    private fun getDeliveryChannelId(tone: String): String {
        val normalizedTone = tone.trim().uppercase()
        return when (normalizedTone) {
            "BELL", "SHAMPING" -> CHANNEL_DELIVERY_BELL
            "SHAMRING" -> CHANNEL_DELIVERY_SHAMRING
            "CHIME" -> CHANNEL_DELIVERY_CHIME
            "SYSTEM" -> CHANNEL_DELIVERY_SYSTEM
            "SILENT" -> CHANNEL_DELIVERY_SILENT
            else -> CHANNEL_DELIVERY_BELL
        }
    }

    /**
     * Displays an incoming SMS notification with strict OTP/PIN privacy.
     * When sensitive codes (OTP, PIN, verification codes) are detected:
     * - The notification content displays generic text "New message received".
     * - No sensitive codes are shown in previews or lock screens.
     * - The full code is visible only upon opening the conversation inside the app.
     */
    fun showIncomingSmsNotification(
        senderPhone: String,
        senderName: String,
        messageBody: String,
        tone: String = "SHAMRING"
    ) {
        if (tone.trim().uppercase() != "SILENT") {
            playTone(tone)
        }

        if (!hasNotificationPermission()) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sender_phone", senderPhone)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 100000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getIncomingChannelId(tone)
        val soundUri = getSoundUri(tone)
        val displayName = senderName.ifBlank {
            SenderIdentityHelper.resolveOrganizationName(senderPhone, messageBody) ?: senderPhone
        }

        val notifId = senderPhone.hashCode()
        val otpCode = SenderIdentityHelper.extractOtpCode(messageBody)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(displayName)
            .setContentText(messageBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(messageBody)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 250, 200, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!otpCode.isNullOrBlank()) {
            val copyIntent = Intent(context, OtpCopyReceiver::class.java).apply {
                action = OtpCopyReceiver.ACTION_COPY_OTP
                putExtra(OtpCopyReceiver.EXTRA_OTP_CODE, otpCode)
                putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                (notifId + 777),
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_save,
                "Copy $otpCode",
                copyPendingIntent
            )
        }

        if (soundUri != null) {
            builder.setSound(soundUri)
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showPreSendReminderNotification(
        scheduleId: Long,
        recipientName: String,
        channelName: String,
        minutesRemaining: Int,
        messagePreview: String
    ) {
        if (!hasNotificationPermission()) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            context,
            (scheduleId + 30000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Upcoming Message in $minutesRemaining min")
            .setContentText("Scheduled for $recipientName via $channelName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Upcoming scheduled $channelName message to $recipientName in $minutesRemaining minutes:\n\"$messagePreview\"")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(getSoundUri("SHAMRING"))
            .setVibrate(longArrayOf(0, 200, 150, 200))
            .setAutoCancel(true)
            .setContentIntent(pendingAppIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((scheduleId + 50000).toInt(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showSmsSentNotification(
        recipientName: String,
        messagePreview: String,
        tone: String = "BELL"
    ) {
        if (tone.trim().uppercase() != "SILENT") {
            playTone(tone)
        }

        if (!hasNotificationPermission()) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 100000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getDeliveryChannelId(tone)
        val soundUri = getSoundUri(tone)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Scheduled SMS Sent Successfully")
            .setContentText("Dispatched to $recipientName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Sent to $recipientName:\n\"$messagePreview\""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 150, 100, 150))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundUri != null) {
            builder.setSound(soundUri)
        }

        try {
            val notifId = (System.currentTimeMillis() % 100000).toInt()
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showSmsFailedNotification(recipientName: String, errorReason: String, scheduleId: Long) {
        if (!hasNotificationPermission()) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (scheduleId + 40000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Message Failed to Send")
            .setContentText("Failed sending to $recipientName: $errorReason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Could not send scheduled message to $recipientName.\nReason: $errorReason\nTap to open the app and review or retry.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultNotificationSound)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((scheduleId + 60000).toInt(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showPermissionRequiredNotification(scheduleId: Long, recipientName: String) {
        if (!hasNotificationPermission()) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (scheduleId + 70000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SMS Permission Required")
            .setContentText("Cannot send scheduled SMS to $recipientName without SMS permission.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("AutoSend was scheduled to send an SMS to $recipientName, but SEND_SMS permission is not granted. Tap here to grant permission and retry.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultNotificationSound)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Open App & Grant Permission",
                pendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify((scheduleId + 70000).toInt(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
