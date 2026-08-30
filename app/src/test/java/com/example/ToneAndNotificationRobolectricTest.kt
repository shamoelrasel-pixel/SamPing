package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.NotificationHelper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToneAndNotificationRobolectricTest {

    private lateinit var context: Context
    private lateinit var notificationHelper: NotificationHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationHelper = NotificationHelper(context)
    }

    @Test
    fun testToneSoundUris() {
        val shamRingUri = notificationHelper.getSoundUri("SHAMRING")
        val chimeUri = notificationHelper.getSoundUri("CHIME")
        val bellUri = notificationHelper.getSoundUri("BELL")
        val shamPingUri = notificationHelper.getSoundUri("SHAMPING")
        val systemUri = notificationHelper.getSoundUri("SYSTEM")
        val silentUri = notificationHelper.getSoundUri("SILENT")

        assertNotNull(shamRingUri)
        assertNotNull(chimeUri)
        assertNotNull(bellUri)
        assertNotNull(shamPingUri)
        assertNotNull(systemUri)
        assertNull(silentUri)

        assertTrue(shamRingUri.toString().startsWith("android.resource://"))
        assertTrue(bellUri.toString().startsWith("android.resource://"))
        assertTrue(shamRingUri.toString().contains(R.raw.incoming_sms_tone.toString()))
        assertTrue(bellUri.toString().contains(R.raw.scheduled_sms_tone.toString()))
    }

    @Test
    fun testPlayToneExecutesWithoutException() {
        // Test all tone types to ensure no crashing or unhandled exceptions
        notificationHelper.playTone("SHAMRING")
        notificationHelper.playTone("CHIME")
        notificationHelper.playTone("BELL")
        notificationHelper.playTone("SHAMPING")
        notificationHelper.playTone("SYSTEM")
        notificationHelper.playTone("SILENT")
        notificationHelper.playTone("unknown_custom_tone")
    }

    @Test
    fun testIncomingAndSentNotificationsWithTone() {
        notificationHelper.showIncomingSmsNotification(
            senderPhone = "01712345678",
            senderName = "BRAC Bank",
            messageBody = "Your OTP is 998877",
            tone = "SHAMRING"
        )

        notificationHelper.showSmsSentNotification(
            recipientName = "Karim",
            messagePreview = "Hello world scheduled test",
            tone = "BELL"
        )
    }
}
