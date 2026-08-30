package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.SmsRepository
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.ScheduleStatus
import com.example.service.SmsSender
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class UnifiedConversationAndDraftRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var repository: SmsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val userPreferencesRepository = UserPreferencesRepository(context)
        repository = SmsRepository(
            context = context,
            scheduleDao = database.scheduleDao(),
            historyDao = database.historyDao(),
            draftDao = database.draftDao(),
            blockedNumberDao = database.blockedNumberDao(),
            recycleBinDao = database.recycleBinDao(),
            incomingMessageDao = database.incomingMessageDao(),
            userPreferencesRepository = userPreferencesRepository,
            smsSender = SmsSender(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testIncomingBankOtpMessageIsStoredAndRetrieved() = runBlocking {
        val sender = "bKash"
        val otpBody = "Your bKash verification code is 654321. Do not share this code."
        val timestamp = System.currentTimeMillis()

        // Test writing incoming SMS
        repository.writeIncomingSms(sender, otpBody, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("bKash", conversations.first().contactName)
        assertEquals(1, conversations.first().messageCount)
        assertTrue(conversations.first().snippet.contains("654321"))

        val messages = repository.getMessagesForThread(conversations.first().threadId, sender)
        assertEquals(1, messages.size)
        assertEquals(otpBody, messages.first().body)
        assertTrue(messages.first().isIncoming)
    }

    @Test
    fun testTwoOtpsFromSameBank_RemainInOneConversation() = runBlocking {
        val bankNumber = "+8801712345678"
        val otp1 = "Your BRAC Bank OTP is 112233"
        val otp2 = "Your BRAC Bank OTP is 445566"

        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "BRAC Bank",
                recipientPhone = bankNumber,
                messageBody = otp1,
                channel = MessageChannel.SMS,
                scheduledEpochMs = System.currentTimeMillis() - 60000,
                executedEpochMs = System.currentTimeMillis() - 60000,
                status = DeliveryStatus.SENT
            )
        )
        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "BRAC Bank",
                recipientPhone = bankNumber,
                messageBody = otp2,
                channel = MessageChannel.SMS,
                scheduledEpochMs = System.currentTimeMillis(),
                executedEpochMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENT
            )
        )

        val conversations = repository.refreshConversations()
        // Exactly 1 conversation created for the same normalized number
        assertEquals(1, conversations.size)
        assertEquals(2, conversations.first().messageCount)
        assertEquals("BRAC Bank", conversations.first().contactName)
    }

    @Test
    fun testOtpFollowedByBalanceSms_RemainInOneConversation() = runBlocking {
        val bankNumber = "01712345678"
        val otpMsg = "Your OTP is 789123"
        val balanceMsg = "Your account balance is 25,000 BDT"

        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "Bank",
                recipientPhone = bankNumber,
                messageBody = otpMsg,
                scheduledEpochMs = System.currentTimeMillis() - 10000,
                executedEpochMs = System.currentTimeMillis() - 10000,
                status = DeliveryStatus.SENT
            )
        )
        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "Bank",
                recipientPhone = bankNumber,
                messageBody = balanceMsg,
                scheduledEpochMs = System.currentTimeMillis(),
                executedEpochMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENT
            )
        )

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals(2, conversations.first().messageCount)
    }

    @Test
    fun testIncomingAndOutgoingSms_RemainInOneConversation() = runBlocking {
        val phone = "+8801811223344"
        val msg1 = "Hey are you available?"
        val msg2 = "Yes, calling you now."

        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "Rahim",
                recipientPhone = phone,
                messageBody = msg1,
                scheduledEpochMs = System.currentTimeMillis() - 20000,
                executedEpochMs = System.currentTimeMillis() - 20000,
                status = DeliveryStatus.DELIVERED
            )
        )
        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "Rahim",
                recipientPhone = phone,
                messageBody = msg2,
                scheduledEpochMs = System.currentTimeMillis() - 10000,
                executedEpochMs = System.currentTimeMillis() - 10000,
                status = DeliveryStatus.SENT
            )
        )

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals(2, conversations.first().messageCount)
    }

    @Test
    fun testDraftLifecycle_SaveRetrieveClear() = runBlocking {
        val phone = "01999887766"
        val name = "Karim"
        val draftText = "Draft message content for testing"

        // Save draft
        repository.saveDraft(phone, name, draftText)
        var retrievedDraft = repository.getDraft(phone)
        assertNotNull(retrievedDraft)
        assertEquals(draftText, retrievedDraft?.messageBody)

        // Verify conversation includes the draft in preview
        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertTrue(conversations.first().hasDraft)
        assertTrue(conversations.first().snippet.contains(draftText))

        // Delete / clear draft
        repository.deleteDraft(phone)
        retrievedDraft = repository.getDraft(phone)
        assertNull(retrievedDraft)
    }

    @Test
    fun testScheduledMessage_TransitionsThroughLifecycle() = runBlocking {
        val phone = "01555123456"
        val name = "Sultana"
        val scheduledBody = "Happy birthday in advance!"
        val scheduledTime = System.currentTimeMillis() + 3600000

        // Schedule message
        val scheduleId = database.scheduleDao().insertSchedule(
            ScheduleEntity(
                recipientName = name,
                recipientPhone = phone,
                messageBody = scheduledBody,
                startEpochMs = scheduledTime,
                nextExecutionEpochMs = scheduledTime,
                recurrenceType = RecurrenceType.ONCE,
                status = ScheduleStatus.SCHEDULED,
                channel = MessageChannel.SMS
            )
        )

        // Verify scheduled status in conversation
        var conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals(1, conversations.first().messageCount)

        // Simulate scheduler processing -> SENT / DELIVERED in history
        database.scheduleDao().updateStatus(scheduleId, ScheduleStatus.COMPLETED)
        val updatedSchedule = database.scheduleDao().getScheduleById(scheduleId)
        assertEquals(ScheduleStatus.COMPLETED, updatedSchedule?.status)

        database.historyDao().insertHistory(
            HistoryEntity(
                scheduleId = scheduleId,
                recipientName = name,
                recipientPhone = phone,
                messageBody = scheduledBody,
                scheduledEpochMs = scheduledTime,
                executedEpochMs = scheduledTime,
                status = DeliveryStatus.DELIVERED
            )
        )

        // Verify still in database and in conversation
        conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals(1, conversations.first().messageCount)
    }

    @Test
    fun testDuplicateConversationMerging() = runBlocking {
        // Same person represented with +880 and 017 format in raw database
        val raw1 = "+8801700112233"
        val raw2 = "01700112233"

        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "Contact A",
                recipientPhone = raw1,
                messageBody = "Message 1",
                scheduledEpochMs = System.currentTimeMillis() - 50000,
                executedEpochMs = System.currentTimeMillis() - 50000,
                status = DeliveryStatus.SENT
            )
        )
        database.historyDao().insertHistory(
            HistoryEntity(
                recipientName = "Contact A",
                recipientPhone = raw2,
                messageBody = "Message 2",
                scheduledEpochMs = System.currentTimeMillis() - 10000,
                executedEpochMs = System.currentTimeMillis() - 10000,
                status = DeliveryStatus.SENT
            )
        )

        // Both route to the same normalized thread
        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals(2, conversations.first().messageCount)
    }

    @Test
    fun testIncomingBankTransactionAlert() = runBlocking {
        val sender = "BRAC Bank"
        val alertBody = "Dear Customer, your A/C ...1234 has been debited with BDT 5,000.00 on 26-Aug-2026. Available Bal: BDT 45,230.00."
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(sender, alertBody, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("BRAC Bank", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("debited with BDT 5,000.00"))

        val messages = repository.getMessagesForThread(conversations.first().threadId, sender)
        assertEquals(1, messages.size)
        assertEquals(alertBody, messages.first().body)
        assertTrue(messages.first().isIncoming)
    }

    @Test
    fun testIncomingBalanceNotification() = runBlocking {
        val sender = "DBBL"
        val balanceBody = "Your Rocket A/C 017123456789 balance is Tk 12,450.75. Fee Tk 0.00."
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(sender, balanceBody, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("DBBL", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("12,450.75"))
    }

    @Test
    fun testIncomingFlexiloadConfirmation() = runBlocking {
        val sender = "Grameenphone"
        val flexiloadBody = "Flexiload Tk 200.00 successful for 01712345678. Transaction ID: FL987654321. New Balance: Tk 215.50."
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(sender, flexiloadBody, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("Grameenphone", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("Flexiload Tk 200.00"))
    }

    @Test
    fun testIncomingRechargeSms() = runBlocking {
        val sender = "Banglalink"
        val rechargeBody = "Recharge of Tk 50.00 successful. Valid till 25-Sep-2026. Main Bal: Tk 52.30."
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(sender, rechargeBody, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("Banglalink", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("Recharge of Tk 50.00"))
    }

    @Test
    fun testIncomingShortCodeSender() = runBlocking {
        val shortCode = "16247" // bKash short code
        val body = "You have received Tk 3,000.00 from 01819876543. Fee Tk 0.00. Balance Tk 5,420.00. TrxID 8N76ASDF98"
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(shortCode, body, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("bKash", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("received Tk 3,000.00"))
    }

    @Test
    fun testIncomingStandardSms() = runBlocking {
        val standardSender = "+8801711223344"
        val body = "Hello brother, are we meeting at 5 PM today?"
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(standardSender, body, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertTrue(conversations.first().snippet.contains("Hello brother"))
    }
}
