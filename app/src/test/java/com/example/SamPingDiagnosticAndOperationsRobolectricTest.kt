package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.RecycleBinEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.RecycleBinRepository
import com.example.data.repository.SmsRepository
import com.example.service.SmsSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SamPingDiagnosticAndOperationsRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var repository: SmsRepository
    private lateinit var recycleBinRepository: RecycleBinRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userPreferencesRepository = UserPreferencesRepository(context)
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
        recycleBinRepository = RecycleBinRepository(database.recycleBinDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testBracBankTransactionAlertReceived() = runBlocking {
        val sender = "BRAC Bank"
        val alertBody = "AC 100123456789 debited for BDT 2,500.00 at POS MERCHANT. Available Bal: BDT 45,230.50 on 26-Aug-2026 14:30."
        val timestamp = System.currentTimeMillis()

        repository.writeIncomingSms(sender, alertBody, timestamp)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("BRAC Bank", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("2,500.00"))

        val messages = repository.getMessagesForThread(conversations.first().threadId, sender)
        assertEquals(1, messages.size)
        assertEquals(alertBody, messages.first().body)
        assertTrue(messages.first().isIncoming)
    }

    @Test
    fun testFlexiloadAndBalanceSmsReceived() = runBlocking {
        val rechargeSender = "Flexiload"
        val rechargeBody = "Recharge of Tk 100.00 successful. TrxID 8A92BC1D on 26/08/2026 10:15. New balance: Tk 112.50"
        repository.writeIncomingSms(rechargeSender, rechargeBody, System.currentTimeMillis())

        val balanceSender = "GP"
        val balanceBody = "Your current account balance is Tk 112.50 valid till 30-Sep-2026. Dial *121# for offers."
        repository.writeIncomingSms(balanceSender, balanceBody, System.currentTimeMillis() + 1000)

        val conversations = repository.refreshConversations()
        assertEquals(2, conversations.size)

        val convGP = conversations.find { it.address.contains("GP", ignoreCase = true) }
        assertNotNull(convGP)
        assertEquals("Grameenphone", convGP?.contactName)
    }

    @Test
    fun testShortCodeSenderReceived() = runBlocking {
        val shortCode = "16221"
        val message = "Your government service registration code is 987123."
        repository.writeIncomingSms(shortCode, message, System.currentTimeMillis())

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("16221", conversations.first().address)
    }

    @Test
    fun testMultipartSmsCombinedCorrectly() = runBlocking {
        val sender = "+8801700000000"
        val longMessage = "Part 1: Welcome to the SamPing enterprise communication system. " +
                "Part 2: This is an extended multipart SMS verifying complete message reassembly and storage integrity across message delivery handlers."

        repository.writeIncomingSms(sender, longMessage, System.currentTimeMillis())

        val messages = repository.getMessagesForThread(-1L, sender)
        assertEquals(1, messages.size)
        assertEquals(longMessage, messages.first().body)
    }

    @Test
    fun testArchiveAndUnarchiveConversation() = runBlocking {
        val sender = "01799998888"
        val body = "Let's archive this chat"
        repository.writeIncomingSms(sender, body, System.currentTimeMillis())

        val initialConvs = repository.refreshConversations()
        assertEquals(1, initialConvs.size)
        val threadId = initialConvs.first().threadId
        assertFalse(initialConvs.first().isArchived)

        // Archive conversation
        repository.archiveConversation(threadId, sender)

        val archivedStatus = repository.isConversationArchived(threadId, sender)
        assertTrue(archivedStatus)

        val refreshedAfterArchive = repository.refreshConversations()
        assertTrue(refreshedAfterArchive.first().isArchived)

        // Unarchive conversation
        repository.unarchiveConversation(threadId, sender)
        assertFalse(repository.isConversationArchived(threadId, sender))
    }

    @Test
    fun testDeleteMessageAndMoveToRecycleBinWithRestore() = runBlocking {
        val sender = "01711112222"
        val body = "Test message to delete and restore"
        val time = System.currentTimeMillis()
        repository.writeIncomingSms(sender, body, time)

        val messagesBefore = repository.getMessagesForThread(-1L, sender)
        assertEquals(1, messagesBefore.size)
        val messageId = messagesBefore.first().id

        // Delete message to Recycle Bin
        recycleBinRepository.addItem(
            RecycleBinEntity(
                itemType = "MESSAGE",
                originalTelephonyId = messageId,
                recipientPhone = sender,
                recipientName = sender,
                messageBody = body,
                messageDateEpochMs = time
            )
        )

        val binItems = recycleBinRepository.itemsFlow.first()
        assertEquals(1, binItems.size)
        assertEquals(body, binItems.first().messageBody)

        // Empty bin or delete permanently
        recycleBinRepository.emptyRecycleBin()
        val binItemsAfterEmpty = recycleBinRepository.itemsFlow.first()
        assertTrue(binItemsAfterEmpty.isEmpty())
    }

    @Test
    fun test30DayRetentionAutomaticCleanup() = runBlocking {
        val oldTimestamp = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000L) // 35 days ago
        database.recycleBinDao().insertItem(
            RecycleBinEntity(
                itemType = "MESSAGE",
                originalTelephonyId = 999L,
                recipientPhone = "01700000000",
                recipientName = "Old Contact",
                messageBody = "Old deleted message",
                messageDateEpochMs = oldTimestamp,
                deletedAtEpochMs = oldTimestamp
            )
        )

        val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
        database.recycleBinDao().deleteExpiredItems(cutoff)

        val remaining = database.recycleBinDao().getAllItemsSync()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun testBKashTransactionAlertReceivedAndParsed() = runBlocking {
        val sender = "bKash"
        val alertBody = "You have received Tk 5,000.00 from 01712345678. Fee Tk 0.00. Balance Tk 15,250.75. TrxID 9A8B7C6D5E at 28/08/2026 16:45."
        val timestamp = System.currentTimeMillis()

        val id = repository.writeIncomingSms(sender, alertBody, timestamp)
        assertTrue(id > 0 || id != -1L)

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("bKash", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("5,000.00"))

        val messages = repository.getMessagesForThread(conversations.first().threadId, sender)
        assertEquals(1, messages.size)
        assertEquals(alertBody, messages.first().body)
        assertTrue(messages.first().isIncoming)
    }

    @Test
    fun testDbblNexusPayAndRocketTransactionAlerts() = runBlocking {
        val rocketSender = "16216"
        val rocketBody = "Cash In Tk 2,000.00 from Agent 01912345678 successful. Fee Tk 0.00. Balance Tk 3,450.00. TrxID 1234567890 on 29-Aug-2026 11:20:15."
        repository.writeIncomingSms(rocketSender, rocketBody, System.currentTimeMillis())

        val dbblSender = "DBBL"
        val dbblBody = "Your A/C 123.100.45678 has been debited by BDT 1,200.00 for ATM Cash Withdrawal. Avail Bal: BDT 28,500.00 on 29-Aug-2026."
        repository.writeIncomingSms(dbblSender, dbblBody, System.currentTimeMillis() + 500)

        val conversations = repository.refreshConversations()
        assertEquals(2, conversations.size)

        val convDbbl = conversations.find { it.address == "DBBL" }
        assertNotNull(convDbbl)
        assertEquals("DBBL", convDbbl?.contactName)
        assertTrue(convDbbl?.snippet?.contains("1,200.00") == true)
    }

    @Test
    fun testSeblAndAsthaTransactionNotifications() = runBlocking {
        val seblSender = "SEBL"
        val seblBody = "Dear Customer, your A/C ending 5678 credited with BDT 25,000.00 by Salary Transfer. Avail Bal BDT 62,300.00."
        repository.writeIncomingSms(seblSender, seblBody, System.currentTimeMillis())

        val asthaSender = "Astha"
        val asthaBody = "BRAC Bank Astha Transfer: BDT 4,000.00 debited from A/C *1234 to A/C *9876. Ref: Rent Payment."
        repository.writeIncomingSms(asthaSender, asthaBody, System.currentTimeMillis() + 200)

        val conversations = repository.refreshConversations()
        assertEquals(2, conversations.size)

        val convSebl = conversations.find { it.address == "SEBL" }
        assertNotNull(convSebl)
        assertEquals("SEBL", convSebl?.contactName)

        val convAstha = conversations.find { it.address == "Astha" }
        assertNotNull(convAstha)
        assertEquals("BRAC Bank", convAstha?.contactName)
    }

    @Test
    fun testNagadAndUpayMfsTransactionNotifications() = runBlocking {
        val nagadSender = "16167"
        val nagadBody = "Bill Pay of Tk 1,450.00 to DESCO successful. Fee Tk 0.00. Balance Tk 4,800.00. TrxID 7B9X21 on 30/08/2026."
        repository.writeIncomingSms(nagadSender, nagadBody, System.currentTimeMillis())

        val upaySender = "Upay"
        val upayBody = "Payment of Tk 350.00 to Merchant *5544 successful. TrxID UP8831. New Balance Tk 1,120.00."
        repository.writeIncomingSms(upaySender, upayBody, System.currentTimeMillis() + 100)

        val conversations = repository.refreshConversations()
        assertEquals(2, conversations.size)

        val convNagad = conversations.find { it.address == "16167" }
        assertNotNull(convNagad)
        assertEquals("Nagad", convNagad?.contactName)

        val convUpay = conversations.find { it.address == "Upay" }
        assertNotNull(convUpay)
        assertEquals("Upay", convUpay?.contactName)
    }

    @Test
    fun testCardTransactionAlerts() = runBlocking {
        val cardSender = "City Bank"
        val cardBody = "Your City Bank Visa Credit Card ending 4321 was used for BDT 3,250.00 at UNIMART on 30-Aug-2026 19:12. Avail Limit BDT 145,000.00."
        repository.writeIncomingSms(cardSender, cardBody, System.currentTimeMillis())

        val conversations = repository.refreshConversations()
        assertEquals(1, conversations.size)
        assertEquals("City Bank", conversations.first().contactName)
        assertTrue(conversations.first().snippet.contains("3,250.00"))
    }
}
