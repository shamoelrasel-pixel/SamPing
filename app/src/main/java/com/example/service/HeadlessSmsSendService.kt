package com.example.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service required for Android Default SMS App compliance.
 * Handles headless "respond via message" intents when AutoSend is set as default SMS app.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
