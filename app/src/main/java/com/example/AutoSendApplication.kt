package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.BlockedNumberRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.RecycleBinRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SmsRepository
import com.example.data.repository.TemplateRepository
import com.example.service.AlarmScheduler
import com.example.service.NotificationHelper
import com.example.service.SimManager
import com.example.service.SmsSender

class AutoSendApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var scheduleRepository: ScheduleRepository
        private set

    lateinit var templateRepository: TemplateRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    lateinit var blockedNumberRepository: BlockedNumberRepository
        private set

    lateinit var recycleBinRepository: RecycleBinRepository
        private set

    lateinit var alarmScheduler: AlarmScheduler
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    lateinit var simManager: SimManager
        private set

    lateinit var smsSender: SmsSender
        private set

    lateinit var smsRepository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        scheduleRepository = ScheduleRepository(database.scheduleDao())
        templateRepository = TemplateRepository(database.templateDao())
        historyRepository = HistoryRepository(database.historyDao())
        userPreferencesRepository = UserPreferencesRepository(this)
        blockedNumberRepository = BlockedNumberRepository(database.blockedNumberDao())
        recycleBinRepository = RecycleBinRepository(database.recycleBinDao())

        alarmScheduler = AlarmScheduler(this)
        notificationHelper = NotificationHelper(this)
        simManager = SimManager(this)
        smsSender = SmsSender(this)
        smsRepository = SmsRepository(
            context = this,
            scheduleDao = database.scheduleDao(),
            historyDao = database.historyDao(),
            draftDao = database.draftDao(),
            blockedNumberDao = database.blockedNumberDao(),
            recycleBinDao = database.recycleBinDao(),
            incomingMessageDao = database.incomingMessageDao(),
            userPreferencesRepository = userPreferencesRepository,
            smsSender = smsSender
        )
        smsRepository.registerSmsObserver()
    }

    companion object {
        lateinit var instance: AutoSendApplication
            private set
    }
}
