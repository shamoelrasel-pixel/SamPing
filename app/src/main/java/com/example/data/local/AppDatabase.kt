package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converters.Converters
import com.example.data.local.dao.BlockedNumberDao
import com.example.data.local.dao.DraftDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.IncomingMessageDao
import com.example.data.local.dao.RecycleBinDao
import com.example.data.local.dao.ScheduleDao
import com.example.data.local.dao.TemplateDao
import com.example.data.local.entity.BlockedNumberEntity
import com.example.data.local.entity.DraftEntity
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.IncomingMessageEntity
import com.example.data.local.entity.RecycleBinEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.data.local.entity.TemplateEntity
import com.example.domain.model.TemplateCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ScheduleEntity::class,
        TemplateEntity::class,
        HistoryEntity::class,
        DraftEntity::class,
        BlockedNumberEntity::class,
        RecycleBinEntity::class,
        IncomingMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao
    abstract fun templateDao(): TemplateDao
    abstract fun historyDao(): HistoryDao
    abstract fun draftDao(): DraftDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun incomingMessageDao(): IncomingMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autosend_database.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Prepopulate default templates on first database creation
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).templateDao().insertTemplates(defaultTemplates)
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val defaultTemplates = listOf(
            TemplateEntity(
                title = "Birthday Wish (Warm)",
                category = TemplateCategory.BIRTHDAY,
                content = "Happy Birthday, {first_name}! 🎂 Wishing you a wonderful year ahead filled with joy, success, and good health. Have an incredible day!"
            ),
            TemplateEntity(
                title = "Birthday Celebration",
                category = TemplateCategory.BIRTHDAY,
                content = "Wishing you the happiest of birthdays, {name}! 🎉 May all your dreams come true this year!"
            ),
            TemplateEntity(
                title = "Anniversary Greetings",
                category = TemplateCategory.ANNIVERSARY,
                content = "Happy Anniversary! 🥂 Wishing you both another year of wonderful memories, love, and happiness together."
            ),
            TemplateEntity(
                title = "Bill Payment Reminder",
                category = TemplateCategory.BILL_REMINDER,
                content = "Friendly reminder: The monthly bill is scheduled for payment today ({date}). Please check your account."
            ),
            TemplateEntity(
                title = "Rent Reminder",
                category = TemplateCategory.BILL_REMINDER,
                content = "Hi {first_name}, gentle reminder that rent payment for {month} is due. Thank you!"
            ),
            TemplateEntity(
                title = "Appointment Confirmation",
                category = TemplateCategory.APPOINTMENT,
                content = "Hello {name}, confirming our scheduled appointment on {date} at {time}. Please let me know if you need to reschedule."
            ),
            TemplateEntity(
                title = "Meeting Reminder",
                category = TemplateCategory.WORK,
                content = "Hi {first_name}, reminding you of our upcoming team sync today at {time}. See you there!"
            ),
            TemplateEntity(
                title = "Holiday Greetings",
                category = TemplateCategory.GREETINGS,
                content = "Wishing you and your family peace, joy, and prosperity this season! Warm regards, from our family to yours."
            ),
            TemplateEntity(
                title = "Weekly Check-in",
                category = TemplateCategory.WORK,
                content = "Hi {first_name}, hope you're having a productive week! Just checking in on our weekly project milestones."
            )
        )
    }
}
