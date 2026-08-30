package com.example.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.dao.BlockedNumberDao
import com.example.data.local.dao.DraftDao
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.IncomingMessageDao
import com.example.data.local.dao.RecycleBinDao
import com.example.data.local.dao.ScheduleDao
import com.example.data.local.entity.BlockedNumberEntity
import com.example.data.local.entity.DraftEntity
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.IncomingMessageEntity
import com.example.data.local.entity.RecycleBinEntity
import com.example.data.local.entity.ScheduleEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.ScheduleStatus
import com.example.domain.model.SmsChatMessage
import com.example.domain.model.SmsConversation
import com.example.domain.util.SenderIdentityHelper
import com.example.service.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PinResult {
    data class Success(val isPinned: Boolean, val message: String) : PinResult()
    data class LimitReached(val message: String = "Maximum of 5 chats can be pinned") : PinResult()
}

class SmsRepository(
    private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val historyDao: HistoryDao,
    private val draftDao: DraftDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val recycleBinDao: RecycleBinDao,
    private val incomingMessageDao: IncomingMessageDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val smsSender: SmsSender
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_repo_prefs", Context.MODE_PRIVATE)
    private val _conversationsFlow = MutableStateFlow<List<SmsConversation>>(emptyList())
    val conversationsFlow: Flow<List<SmsConversation>> = _conversationsFlow.asStateFlow()
    val conversations: kotlinx.coroutines.flow.StateFlow<List<SmsConversation>> = _conversationsFlow.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SmsChatMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var smsObserver: ContentObserver? = null

    @Volatile
    private var activeOpenKey: String? = null
    @Volatile
    private var activeOpenThreadId: Long = -1L

    private val contactNameCache = mutableMapOf<String, String>()
    private val unreadOverrides = mutableMapOf<String, Boolean>() // senderKey -> isRead override

    companion object {
        private const val TAG = "SamPingSms"
        const val MAX_PINNED_CHATS = 5
        private const val KEY_PINNED_CHATS = "pinned_chats_set"
    }

    /**
     * Registers a ContentObserver on Telephony.Sms.CONTENT_URI to automatically detect
     * external incoming SMS, transactional alerts, Flexiload, operator updates, and system provider inserts.
     */
    fun registerSmsObserver() {
        if (smsObserver != null) return
        try {
            smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.d(TAG, "[SMS Provider insert detected] URI: $uri -> triggering provider synchronization")
                    repositoryScope.launch {
                        try {
                            syncFromTelephonyProvider(uri)
                            refreshConversations()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling SMS provider change", e)
                        }
                    }
                }
            }
            context.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                smsObserver!!
            )
            Log.d(TAG, "[SMS Provider insert detected] Observer registered successfully on Telephony.Sms.CONTENT_URI")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver for Telephony.Sms.CONTENT_URI", e)
        }
    }

    /**
     * Synchronizes recent incoming SMS messages from the Android Telephony Provider into
     * local durable Room storage, ensuring all transactional and bank alerts are captured.
     */
    suspend fun syncFromTelephonyProvider(changedUri: Uri? = null) = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext
        try {
            val cursor = try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.THREAD_ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.READ
                    ),
                    "${Telephony.Sms.TYPE} = ?",
                    arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                    "${Telephony.Sms.DATE} DESC LIMIT 50"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Query error in syncFromTelephonyProvider", e)
                null
            }

            cursor?.use {
                val idCol = it.getColumnIndex(Telephony.Sms._ID)
                val tIdCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addrCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                val readCol = it.getColumnIndex(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val telephonyId = if (idCol >= 0) it.getLong(idCol) else continue
                    val threadId = if (tIdCol >= 0) it.getLong(tIdCol) else -1L
                    val address = if (addrCol >= 0) it.getString(addrCol) ?: "Unknown" else "Unknown"
                    val body = if (bodyCol >= 0) it.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol >= 0) it.getLong(dateCol) else System.currentTimeMillis()
                    val isRead = if (readCol >= 0) it.getInt(readCol) == 1 else false

                    val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
                    val orgName = SenderIdentityHelper.resolveOrganizationName(address, body)
                    val contactName = lookupContactName(address) ?: orgName ?: address

                    Log.d(TAG, "[Sender identity resolved] Provider SMS from '$address' -> NormalizedKey: '$senderKey', OrgName: '$orgName', ContactName: '$contactName'")
                    Log.d(TAG, "[Conversation matched] Provider SMS matched to ThreadId: $threadId, SenderKey: '$senderKey', RawAddress: '$address'")

                    // Check if already in incomingMessageDao
                    val existingList = incomingMessageDao.getIncomingByKeySync(senderKey)
                    val alreadyExists = existingList.any { inc ->
                        inc.telephonyId == telephonyId ||
                        (inc.body == body && Math.abs(inc.dateEpochMs - date) < 3000)
                    }

                    if (!alreadyExists) {
                        val localId = incomingMessageDao.insertMessage(
                            IncomingMessageEntity(
                                telephonyId = telephonyId,
                                threadId = threadId,
                                rawSender = address,
                                normalizedKey = senderKey,
                                body = body,
                                dateEpochMs = date,
                                isRead = isRead
                            )
                        )
                        Log.d(TAG, "[Database insert success/failure] Synced provider SMS into local database: LocalID $localId, TelephonyId $telephonyId (Success)")

                        // Emit to live conversation if open
                        if (isConversationActive(address, threadId)) {
                            emitIncomingMessage(
                                SmsChatMessage(
                                    id = telephonyId,
                                    threadId = threadId,
                                    address = address,
                                    body = body,
                                    dateEpochMs = date,
                                    isIncoming = true,
                                    isRead = isRead
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncFromTelephonyProvider", e)
        }
    }

    fun getActiveThreadId(): Long = activeOpenThreadId

    fun setActiveConversation(address: String, threadId: Long) {
        activeOpenKey = SenderIdentityHelper.normalizeSenderKey(address)
        activeOpenThreadId = threadId
    }

    fun clearActiveConversation() {
        activeOpenKey = null
        activeOpenThreadId = -1L
    }

    fun isConversationActive(address: String, threadId: Long = -1L): Boolean {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        if (key != "UNKNOWN" && activeOpenKey == key) return true
        if (threadId > 0 && activeOpenThreadId == threadId) return true
        return false
    }

    fun emitIncomingMessage(msg: SmsChatMessage) {
        _incomingMessages.tryEmit(msg)
    }

    private fun getPinnedChatKeys(): MutableSet<String> {
        return prefs.getStringSet(KEY_PINNED_CHATS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun savePinnedChatKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_PINNED_CHATS, keys).apply()
    }

    fun isPinned(threadId: Long, address: String): Boolean {
        val keys = getPinnedChatKeys()
        val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
        return keys.contains("thread_$threadId") || (senderKey != "UNKNOWN" && keys.contains("key_$senderKey"))
    }

    fun togglePin(threadId: Long, address: String): PinResult {
        val keys = getPinnedChatKeys()
        val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
        val key = if (senderKey != "UNKNOWN") "key_$senderKey" else "thread_$threadId"

        if (keys.contains(key) || (senderKey != "UNKNOWN" && keys.contains("key_$senderKey"))) {
            keys.remove(key)
            if (senderKey != "UNKNOWN") keys.remove("key_$senderKey")
            savePinnedChatKeys(keys)
            return PinResult.Success(false, "Unpinned chat")
        } else {
            if (keys.size >= MAX_PINNED_CHATS) {
                return PinResult.LimitReached("Maximum of $MAX_PINNED_CHATS chats can be pinned")
            }
            keys.add(key)
            savePinnedChatKeys(keys)
            return PinResult.Success(true, "Pinned chat")
        }
    }

    fun pinConversations(items: List<Pair<Long, String>>): PinResult {
        val keys = getPinnedChatKeys()
        var addedCount = 0
        for ((threadId, address) in items) {
            val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
            val key = if (senderKey != "UNKNOWN") "key_$senderKey" else "thread_$threadId"
            if (!keys.contains(key) && !(senderKey != "UNKNOWN" && keys.contains("key_$senderKey"))) {
                if (keys.size >= MAX_PINNED_CHATS) {
                    savePinnedChatKeys(keys)
                    return PinResult.LimitReached("Pinned $addedCount chats. Maximum of $MAX_PINNED_CHATS chats can be pinned.")
                }
                keys.add(key)
                addedCount++
            }
        }
        savePinnedChatKeys(keys)
        return PinResult.Success(true, "Pinned $addedCount chat${if (addedCount > 1) "s" else ""}")
    }

    fun unpinConversations(items: List<Pair<Long, String>>) {
        val keys = getPinnedChatKeys()
        for ((threadId, address) in items) {
            val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
            val key = if (senderKey != "UNKNOWN") "key_$senderKey" else "thread_$threadId"
            keys.remove(key)
            if (senderKey != "UNKNOWN") keys.remove("key_$senderKey")
        }
        savePinnedChatKeys(keys)
    }

    fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultSmsApp(): Boolean {
        return com.example.service.DefaultSmsHelper.isDefaultSmsApp(context)
    }

    // --- Draft Operations ---
    suspend fun saveDraft(address: String, name: String, body: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        if (key == "UNKNOWN" || body.isBlank()) {
            if (key != "UNKNOWN") {
                draftDao.deleteDraftByKey(key)
            }
            refreshConversations()
            return@withContext
        }
        draftDao.saveDraft(
            DraftEntity(
                recipientPhoneKey = key,
                rawRecipientPhone = address,
                recipientName = name,
                messageBody = body,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        refreshConversations()
    }

    suspend fun getDraft(address: String): DraftEntity? = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        draftDao.getDraftByKey(key)
    }

    suspend fun deleteDraft(address: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        draftDao.deleteDraftByKey(key)
        refreshConversations()
    }

    /**
     * Refreshes all conversations and consolidates every message type
     * (Received, Sent, Scheduled, Drafts, History) into exactly ONE conversation per normalized identity.
     */
    suspend fun refreshConversations(): List<SmsConversation> = withContext(Dispatchers.IO) {
        // Internal accumulator grouped by canonical sender key
        data class MergedConversationData(
            var threadId: Long = -1L,
            var rawAddress: String = "",
            var contactName: String? = null,
            var latestSnippet: String = "",
            var latestDateEpochMs: Long = 0L,
            var messageCount: Int = 0,
            var unreadCount: Int = 0,
            var scheduledCount: Int = 0,
            var hasDraft: Boolean = false,
            var draftSnippet: String? = null
        )

        val mergedMap = mutableMapOf<String, MergedConversationData>()

        // 1. Fetch Local Drafts
        val allDrafts = try {
            draftDao.getAllDraftsSync()
        } catch (e: Exception) {
            emptyList()
        }
        for (draft in allDrafts) {
            val key = draft.recipientPhoneKey
            val entry = mergedMap.getOrPut(key) {
                MergedConversationData(
                    rawAddress = draft.rawRecipientPhone,
                    contactName = draft.recipientName.ifBlank { null }
                )
            }
            entry.hasDraft = true
            entry.draftSnippet = draft.messageBody
            if (draft.updatedAtEpochMs > entry.latestDateEpochMs) {
                entry.latestDateEpochMs = draft.updatedAtEpochMs
                entry.latestSnippet = "[Draft] ${draft.messageBody}"
            }
        }

        // 2. Fetch Active Schedules
        val activeSchedules = try {
            scheduleDao.getAllSchedulesSync().filter { it.status == ScheduleStatus.SCHEDULED }
        } catch (e: Exception) {
            emptyList()
        }
        for (schedule in activeSchedules) {
            val key = SenderIdentityHelper.normalizeSenderKey(schedule.recipientPhone)
            val entry = mergedMap.getOrPut(key) {
                MergedConversationData(
                    rawAddress = schedule.recipientPhone,
                    contactName = schedule.recipientName.ifBlank { null }
                )
            }
            entry.scheduledCount++
            entry.messageCount++
            if (entry.latestSnippet.isBlank() && !entry.hasDraft) {
                entry.latestSnippet = "Scheduled: ${schedule.messageBody}"
                if (schedule.nextExecutionEpochMs > entry.latestDateEpochMs) {
                    entry.latestDateEpochMs = schedule.nextExecutionEpochMs
                }
            }
        }

        // 3. Fetch Local History Logs (Sent, Delivered, Failed)
        val historyLogs = try {
            historyDao.getAllHistorySync()
        } catch (e: Exception) {
            emptyList()
        }
        for (log in historyLogs) {
            val key = SenderIdentityHelper.normalizeSenderKey(log.recipientPhone)
            val entry = mergedMap.getOrPut(key) {
                MergedConversationData(
                    rawAddress = log.recipientPhone,
                    contactName = log.recipientName.ifBlank { null }
                )
            }
            entry.messageCount++
            if (log.executedEpochMs >= entry.latestDateEpochMs && !entry.hasDraft) {
                entry.latestDateEpochMs = log.executedEpochMs
                entry.latestSnippet = log.messageBody
            }
        }

        // 4. Query Telephony SMS (both Inbox and Sent)
        if (hasReadSmsPermission()) {
            try {
                val cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.THREAD_ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.READ,
                        Telephony.Sms.TYPE
                    ),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(Telephony.Sms._ID)
                    val tIdCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addrCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                    val readCol = it.getColumnIndex(Telephony.Sms.READ)

                    while (it.moveToNext()) {
                        val threadId = if (tIdCol >= 0) it.getLong(tIdCol) else -1L
                        val address = if (addrCol >= 0) it.getString(addrCol) ?: "Unknown" else "Unknown"
                        val body = if (bodyCol >= 0) it.getString(bodyCol) ?: "" else ""
                        val date = if (dateCol >= 0) it.getLong(dateCol) else System.currentTimeMillis()
                        val isRead = if (readCol >= 0) it.getInt(readCol) == 1 else true

                        val key = SenderIdentityHelper.normalizeSenderKey(address)
                        val entry = mergedMap.getOrPut(key) {
                            MergedConversationData(
                                threadId = threadId,
                                rawAddress = address
                            )
                        }

                        if (entry.threadId <= 0 && threadId > 0) {
                            entry.threadId = threadId
                        }
                        if (entry.rawAddress.isBlank() || entry.rawAddress == "Unknown") {
                            entry.rawAddress = address
                        }

                        entry.messageCount++
                        if (!isRead) {
                            entry.unreadCount++
                        }

                        if (date >= entry.latestDateEpochMs && !entry.hasDraft) {
                            entry.latestDateEpochMs = date
                            entry.latestSnippet = body
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. Query Local Incoming Messages (guarantees incoming OTP/PIN/SMS always appear even if system provider lagged)
        val localIncoming = try {
            incomingMessageDao.getAllIncomingSync()
        } catch (e: Exception) {
            emptyList()
        }
        for (inc in localIncoming) {
            val key = inc.normalizedKey.ifBlank { SenderIdentityHelper.normalizeSenderKey(inc.rawSender) }
            val entry = mergedMap.getOrPut(key) {
                MergedConversationData(
                    threadId = inc.threadId,
                    rawAddress = inc.rawSender
                )
            }
            if (entry.rawAddress.isBlank() || entry.rawAddress == "Unknown") {
                entry.rawAddress = inc.rawSender
            }
            // Check if this incoming message was already counted from Telephony
            val alreadyCounted = inc.telephonyId != null && inc.telephonyId > 0 && entry.messageCount > 0
            if (!alreadyCounted) {
                if (inc.dateEpochMs >= entry.latestDateEpochMs && !entry.hasDraft) {
                    entry.latestDateEpochMs = inc.dateEpochMs
                    entry.latestSnippet = inc.body
                }
                if (!inc.isRead) {
                    entry.unreadCount++
                }
                entry.messageCount++
            } else {
                if (inc.dateEpochMs >= entry.latestDateEpochMs && !entry.hasDraft) {
                    entry.latestDateEpochMs = inc.dateEpochMs
                    entry.latestSnippet = inc.body
                }
            }
        }

        // Clean up expired items in recycle bin (30 day retention)
        try {
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
            recycleBinDao.deleteExpiredItems(cutoff)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val blockedList = try { blockedNumberDao.getAllBlockedSync() } catch (e: Exception) { emptyList() }
        val blockedKeys = blockedList.map { it.normalizedKey }.toSet()
        val userPrefs = try { userPreferencesRepository.userPreferencesFlow.firstOrNull() } catch (e: Exception) { null }
        val archivedKeys = userPrefs?.archivedConversationKeys ?: emptySet()

        val pinnedKeys = getPinnedChatKeys()
        val resultList = mutableListOf<SmsConversation>()
        var fallbackThreadIdCounter = 10000L

        for ((key, data) in mergedMap) {
            if (data.rawAddress.isBlank() && key == "UNKNOWN") continue
            // Filter out blocked senders
            if (blockedKeys.contains(key)) continue

            // Resolve contact name: Contacts -> Known Org -> Raw
            val contactName = lookupContactName(data.rawAddress)
                ?: data.contactName
                ?: SenderIdentityHelper.resolveOrganizationName(data.rawAddress, data.latestSnippet)

            val isChatPinned = (data.threadId > 0 && pinnedKeys.contains("thread_${data.threadId}")) ||
                    pinnedKeys.contains("key_$key")

            val isArchived = (data.threadId > 0 && archivedKeys.contains("thread_${data.threadId}")) ||
                    archivedKeys.contains("key_$key") || archivedKeys.contains(key)

            // Check manual unread overrides
            var isRead = data.unreadCount == 0
            val overrideKey = if (data.threadId > 0) "thread_${data.threadId}" else "key_$key"
            if (unreadOverrides.containsKey(overrideKey)) {
                isRead = unreadOverrides[overrideKey] == true
            }

            val threadId = if (data.threadId > 0) data.threadId else fallbackThreadIdCounter++

            resultList.add(
                SmsConversation(
                    threadId = threadId,
                    address = data.rawAddress,
                    contactName = contactName,
                    snippet = if (data.hasDraft && data.draftSnippet != null) "[Draft] ${data.draftSnippet}" else data.latestSnippet.ifBlank { "No messages" },
                    dateEpochMs = if (data.latestDateEpochMs > 0) data.latestDateEpochMs else System.currentTimeMillis(),
                    messageCount = data.messageCount,
                    isRead = isRead,
                    unreadCount = if (!isRead) maxOf(1, data.unreadCount) else 0,
                    isPinned = isChatPinned,
                    isArchived = isArchived,
                    hasScheduledMessages = data.scheduledCount > 0,
                    scheduledCount = data.scheduledCount,
                    hasDraft = data.hasDraft,
                    draftSnippet = data.draftSnippet
                )
            )
        }

        // Sort: Pinned first (date desc), then normal (date desc)
        val sortedList = resultList.sortedWith(
            compareByDescending<SmsConversation> { it.isPinned }
                .thenByDescending { it.dateEpochMs }
        )

        _conversationsFlow.value = sortedList
        sortedList
    }

    /**
     * Retrieves all messages (Received, Sent, Scheduled, Draft, Delivery logs) for a given thread / address,
     * unified into a single chronological timeline.
     */
    suspend fun getMessagesForThread(threadId: Long, phoneAddress: String): List<SmsChatMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsChatMessage>()
        val senderKey = SenderIdentityHelper.normalizeSenderKey(phoneAddress)

        // Check if sender is blocked
        if (senderKey != "UNKNOWN" && blockedNumberDao.isKeyBlocked(senderKey) > 0) {
            return@withContext emptyList()
        }

        // 1. Query Telephony SMS (Inbox & Sent)
        if (hasReadSmsPermission()) {
            try {
                val cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.THREAD_ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.READ,
                        Telephony.Sms.STATUS
                    ),
                    null,
                    null,
                    "${Telephony.Sms.DATE} ASC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(Telephony.Sms._ID)
                    val tIdCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addrCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)
                    val readCol = it.getColumnIndex(Telephony.Sms.READ)
                    val statusCol = it.getColumnIndex(Telephony.Sms.STATUS)

                    while (it.moveToNext()) {
                        val rowThreadId = if (tIdCol >= 0) it.getLong(tIdCol) else -1L
                        val address = if (addrCol >= 0) it.getString(addrCol) ?: phoneAddress else phoneAddress
                        val rowSenderKey = SenderIdentityHelper.normalizeSenderKey(address)

                        // Match either thread ID or normalized sender key
                        val isMatch = (threadId > 0 && rowThreadId == threadId) ||
                                (senderKey != "UNKNOWN" && rowSenderKey == senderKey) ||
                                SenderIdentityHelper.isSameSender(address, phoneAddress)

                        if (isMatch) {
                            val id = if (idCol >= 0) it.getLong(idCol) else 0L
                            val body = if (bodyCol >= 0) it.getString(bodyCol) ?: "" else ""
                            val date = if (dateCol >= 0) it.getLong(dateCol) else System.currentTimeMillis()
                            val type = if (typeCol >= 0) it.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX
                            val isRead = if (readCol >= 0) it.getInt(readCol) == 1 else true
                            val status = if (statusCol >= 0) it.getInt(statusCol) else -1

                            messages.add(
                                SmsChatMessage(
                                    id = id,
                                    threadId = if (rowThreadId > 0) rowThreadId else threadId,
                                    address = address,
                                    body = body,
                                    dateEpochMs = date,
                                    isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                                    isRead = isRead,
                                    status = status,
                                    isDelivered = status == Telephony.Sms.STATUS_COMPLETE
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 1.5 Query Local Incoming Messages for this sender (guarantees all banking, OTP, and transaction messages appear)
        try {
            val allIncoming = incomingMessageDao.getAllIncomingSync()
            val localIncomingList = allIncoming.filter { inc ->
                inc.normalizedKey == senderKey ||
                        inc.rawSender.equals(phoneAddress, ignoreCase = true) ||
                        SenderIdentityHelper.isSameSender(inc.rawSender, phoneAddress) ||
                        (threadId > 0 && inc.threadId == threadId)
            }
            for (inc in localIncomingList) {
                val alreadyExists = messages.any {
                    it.isIncoming && (
                        (inc.telephonyId != null && inc.telephonyId > 0 && it.id == inc.telephonyId) ||
                        (it.body == inc.body && Math.abs(it.dateEpochMs - inc.dateEpochMs) < 3000)
                    )
                }
                if (!alreadyExists) {
                    messages.add(
                        SmsChatMessage(
                            id = inc.telephonyId ?: inc.id,
                            threadId = if (inc.threadId > 0) inc.threadId else threadId,
                            address = inc.rawSender,
                            body = inc.body,
                            dateEpochMs = inc.dateEpochMs,
                            isIncoming = true,
                            isRead = inc.isRead
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Query Local History Logs for this recipient (SENT, DELIVERED, FAILED)
        try {
            val historyList = historyDao.getAllHistorySync().filter {
                SenderIdentityHelper.normalizeSenderKey(it.recipientPhone) == senderKey ||
                        SenderIdentityHelper.isSameSender(it.recipientPhone, phoneAddress)
            }
            for (log in historyList) {
                // Avoid duplicate if already found from telephony sent messages
                val alreadyExists = messages.any {
                    !it.isIncoming && it.body == log.messageBody &&
                            Math.abs(it.dateEpochMs - log.executedEpochMs) < 2000
                }
                if (!alreadyExists) {
                    messages.add(
                        SmsChatMessage(
                            id = 2000000L + log.id,
                            threadId = threadId,
                            address = log.recipientPhone,
                            body = log.messageBody,
                            dateEpochMs = log.executedEpochMs,
                            isIncoming = false,
                            isRead = true,
                            status = when (log.status) {
                                DeliveryStatus.DELIVERED -> 1
                                DeliveryStatus.SENT -> 0
                                DeliveryStatus.FAILED -> -2
                                else -> 0
                            },
                            isDelivered = log.status == DeliveryStatus.DELIVERED,
                            isFailed = log.status == DeliveryStatus.FAILED,
                            errorReason = log.errorReason,
                            deliveryStatus = log.status
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Attach upcoming SCHEDULED messages
        try {
            val scheduledList = scheduleDao.getAllSchedulesSync().filter {
                it.status == ScheduleStatus.SCHEDULED &&
                        (SenderIdentityHelper.normalizeSenderKey(it.recipientPhone) == senderKey ||
                                SenderIdentityHelper.isSameSender(it.recipientPhone, phoneAddress))
            }
            for (sch in scheduledList) {
                messages.add(
                    SmsChatMessage(
                        id = -sch.id,
                        threadId = threadId,
                        address = sch.recipientPhone,
                        body = sch.messageBody,
                        dateEpochMs = sch.nextExecutionEpochMs,
                        isIncoming = false,
                        isRead = true,
                        isScheduled = true,
                        scheduledTriggerEpochMs = sch.nextExecutionEpochMs,
                        scheduleId = sch.id,
                        deliveryStatus = DeliveryStatus.SCHEDULED
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Attach DRAFT if any
        try {
            val draft = draftDao.getDraftByKey(senderKey)
            if (draft != null && draft.messageBody.isNotBlank()) {
                messages.add(
                    SmsChatMessage(
                        id = -999999L,
                        threadId = threadId,
                        address = draft.rawRecipientPhone,
                        body = draft.messageBody,
                        dateEpochMs = draft.updatedAtEpochMs,
                        isIncoming = false,
                        isRead = true,
                        isDraft = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        messages.sortedBy { it.dateEpochMs }
    }

    suspend fun sendImmediateSms(
        recipientPhone: String,
        recipientName: String,
        messageText: String,
        subscriptionId: Int?
    ): Boolean = withContext(Dispatchers.IO) {
        // Record in History
        val historyId = historyDao.insertHistory(
            HistoryEntity(
                scheduleId = null,
                recipientName = recipientName.ifBlank { recipientPhone },
                recipientPhone = recipientPhone,
                messageBody = messageText,
                channel = MessageChannel.SMS,
                simDisplayName = if (subscriptionId != null) "SIM $subscriptionId" else null,
                scheduledEpochMs = System.currentTimeMillis(),
                executedEpochMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENT,
                errorReason = null,
                retryAttempt = 0
            )
        )

        // Clear any active draft for this recipient
        val senderKey = SenderIdentityHelper.normalizeSenderKey(recipientPhone)
        draftDao.deleteDraftByKey(senderKey)

        // Send via SmsSender
        val result = smsSender.sendSms(
            scheduleId = -1L,
            historyId = historyId,
            recipientPhone = recipientPhone,
            messageText = messageText,
            subscriptionId = subscriptionId
        )

        if (result.isSuccess) {
            writeSentSmsToProvider(recipientPhone, messageText)
            refreshConversations()
            true
        } else {
            historyDao.updateStatus(historyId, DeliveryStatus.FAILED, result.errorMessage)
            refreshConversations()
            false
        }
    }

    fun writeSentSmsToProvider(address: String, body: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun writeIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        simSubscriptionId: Int? = null
    ): Long = withContext(Dispatchers.IO) {
        val isActive = isConversationActive(address)
        val senderKey = SenderIdentityHelper.normalizeSenderKey(address)

        // 1. Insert into Telephony content provider if available (Default SMS app capability)
        var telephonyId: Long? = null
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, if (isActive) 1 else 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (simSubscriptionId != null && simSubscriptionId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, simSubscriptionId)
                }
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            telephonyId = uri?.lastPathSegment?.toLongOrNull()
            if (telephonyId != null) {
                Log.d(TAG, "[SMS Provider insert detected] Written to Telephony Inbox URI: $uri (ID: $telephonyId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed inserting to Telephony.Sms.Inbox.CONTENT_URI", e)
        }

        // 2. Durable Room Database persistence (guarantees offline/app restart durability)
        val localId = try {
            incomingMessageDao.insertMessage(
                IncomingMessageEntity(
                    telephonyId = telephonyId,
                    threadId = if (activeOpenKey == senderKey) activeOpenThreadId else -1L,
                    rawSender = address,
                    normalizedKey = senderKey,
                    body = body,
                    dateEpochMs = timestamp,
                    isRead = isActive,
                    simSubscriptionId = simSubscriptionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Database insert success/failure] Failed inserting to Room incoming_messages", e)
            -1L
        }

        val effectiveId = telephonyId ?: (if (localId > 0) localId else timestamp)
        Log.d(TAG, "[Database insert success/failure] Incoming SMS stored (LocalID: $localId, TelephonyID: $telephonyId, EffectiveID: $effectiveId, Sender: '$address')")

        // 3. Emit to live UI stream
        val chatMsg = SmsChatMessage(
            id = effectiveId,
            threadId = if (activeOpenKey == senderKey) activeOpenThreadId else -1L,
            address = address,
            body = body,
            dateEpochMs = timestamp,
            isIncoming = true,
            isRead = isActive
        )
        emitIncomingMessage(chatMsg)
        effectiveId
    }

    suspend fun writeIncomingSmsToProvider(address: String, body: String, timestamp: Long): Long {
        return writeIncomingSms(address, body, timestamp, null)
    }

    suspend fun markThreadAsRead(threadId: Long, address: String = "") = withContext(Dispatchers.IO) {
        markThreadsAsRead(listOf(threadId to address))
    }

    suspend fun markThreadsAsRead(items: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            for ((threadId, address) in items) {
                val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
                val overrideKey = if (senderKey != "UNKNOWN") "key_$senderKey" else "thread_$threadId"
                unreadOverrides[overrideKey] = true

                if (senderKey != "UNKNOWN") {
                    incomingMessageDao.markAsReadByKey(senderKey)
                }

                if (threadId > 0) {
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                        arrayOf(threadId.toString())
                    )
                }
                if (address.isNotBlank()) {
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                        arrayOf(address)
                    )
                }
            }
            refreshConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markThreadsAsUnread(items: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 0)
            }
            for ((threadId, address) in items) {
                val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
                val overrideKey = if (senderKey != "UNKNOWN") "key_$senderKey" else "thread_$threadId"
                unreadOverrides[overrideKey] = false

                if (threadId > 0) {
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.THREAD_ID} = ?",
                        arrayOf(threadId.toString())
                    )
                }
                if (address.isNotBlank()) {
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.ADDRESS} = ?",
                        arrayOf(address)
                    )
                }
            }
            refreshConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        deleteThreads(listOf(threadId to ""))
    }

    suspend fun deleteThreads(items: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        try {
            unpinConversations(items)
            for ((threadId, address) in items) {
                val senderKey = SenderIdentityHelper.normalizeSenderKey(address)
                val contactName = lookupContactName(address) ?: address

                // Backup thread messages to recycle bin before deleting
                try {
                    val messages = getMessagesForThread(threadId, address)
                    val recycleList = messages.map { msg ->
                        RecycleBinEntity(
                            itemType = "CONVERSATION",
                            originalTelephonyId = if (msg.id < 2000000L && msg.id > 0) msg.id else null,
                            threadId = threadId,
                            recipientPhone = address.ifBlank { msg.address },
                            recipientName = contactName,
                            messageBody = msg.body,
                            messageDateEpochMs = msg.dateEpochMs,
                            isIncoming = msg.isIncoming,
                            deletedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                    if (recycleList.isNotEmpty()) {
                        recycleBinDao.insertItems(recycleList)
                    } else if (address.isNotBlank()) {
                        // At least backup a conversation marker
                        recycleBinDao.insertItem(
                            RecycleBinEntity(
                                itemType = "CONVERSATION",
                                threadId = threadId,
                                recipientPhone = address,
                                recipientName = contactName,
                                messageBody = "Conversation with $contactName",
                                messageDateEpochMs = System.currentTimeMillis(),
                                isIncoming = true,
                                deletedAtEpochMs = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (senderKey != "UNKNOWN") {
                    incomingMessageDao.deleteByKey(senderKey)
                    draftDao.deleteDraftByKey(senderKey)
                }

                if (threadId > 0) {
                    val uri = ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId)
                    context.contentResolver.delete(uri, null, null)
                }
                if (address.isNotBlank()) {
                    context.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms.ADDRESS} = ?",
                        arrayOf(address)
                    )
                    // Delete local history records for this recipient
                    val logs = historyDao.getAllHistorySync().filter {
                        SenderIdentityHelper.normalizeSenderKey(it.recipientPhone) == senderKey ||
                                SenderIdentityHelper.isSameSender(it.recipientPhone, address)
                    }
                    logs.forEach { historyDao.deleteHistory(it) }
                }
            }
            refreshConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteMessage(msg: SmsChatMessage, contactName: String = "") = withContext(Dispatchers.IO) {
        try {
            // 1. Save to Recycle Bin
            recycleBinDao.insertItem(
                RecycleBinEntity(
                    itemType = "MESSAGE",
                    originalTelephonyId = if (msg.id < 2000000L && msg.id > 0) msg.id else null,
                    threadId = msg.threadId,
                    recipientPhone = msg.address,
                    recipientName = contactName.ifBlank { lookupContactName(msg.address) ?: msg.address },
                    messageBody = msg.body,
                    messageDateEpochMs = msg.dateEpochMs,
                    isIncoming = msg.isIncoming,
                    deletedAtEpochMs = System.currentTimeMillis()
                )
            )

            // 2. Remove from actual store
            if (msg.isScheduled && msg.scheduleId != null) {
                scheduleDao.deleteScheduleById(msg.scheduleId)
            } else if (msg.id >= 2000000L) {
                val histId = msg.id - 2000000L
                historyDao.deleteHistoryById(histId)
            } else if (msg.id > 0) {
                val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, msg.id)
                context.contentResolver.delete(uri, null, null)
                incomingMessageDao.deleteById(msg.id)
            }
            refreshConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restoreRecycledItem(item: RecycleBinEntity) = withContext(Dispatchers.IO) {
        try {
            if (item.isIncoming) {
                writeIncomingSms(item.recipientPhone, item.messageBody, item.messageDateEpochMs, null)
            } else {
                // Restore outgoing message to telephony or local history
                if (hasReadSmsPermission()) {
                    try {
                        val values = ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, item.recipientPhone)
                            put(Telephony.Sms.BODY, item.messageBody)
                            put(Telephony.Sms.DATE, item.messageDateEpochMs)
                            put(Telephony.Sms.READ, 1)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        }
                        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                historyDao.insertHistory(
                    HistoryEntity(
                        recipientPhone = item.recipientPhone,
                        recipientName = item.recipientName,
                        messageBody = item.messageBody,
                        scheduledEpochMs = item.messageDateEpochMs,
                        executedEpochMs = item.messageDateEpochMs,
                        channel = MessageChannel.SMS,
                        simDisplayName = "SIM 1",
                        status = DeliveryStatus.SENT
                    )
                )
            }
            recycleBinDao.deleteItemById(item.id)
            refreshConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecycleBinItemsFlow(): Flow<List<RecycleBinEntity>> = recycleBinDao.getAllItemsFlow()

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        recycleBinDao.deleteAll()
    }

    suspend fun deleteRecycledItemById(id: Long) = withContext(Dispatchers.IO) {
        recycleBinDao.deleteItemById(id)
    }

    suspend fun cleanExpiredRecycledItems() = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
        recycleBinDao.deleteExpiredItems(thirtyDaysAgo)
    }

    suspend fun restoreRecycledConversation(threadId: Long, recipientPhone: String) = withContext(Dispatchers.IO) {
        val senderKey = SenderIdentityHelper.normalizeSenderKey(recipientPhone)
        val allRecycled = recycleBinDao.getAllItemsSync()
        val matching = allRecycled.filter {
            (threadId > 0 && it.threadId == threadId) ||
            SenderIdentityHelper.normalizeSenderKey(it.recipientPhone) == senderKey ||
            SenderIdentityHelper.isSameSender(it.recipientPhone, recipientPhone)
        }
        for (item in matching) {
            restoreRecycledItem(item)
        }
        refreshConversations()
    }

    fun getBlockedNumbersFlow(): Flow<List<BlockedNumberEntity>> = blockedNumberDao.getAllBlockedFlow()

    suspend fun getBlockedNumbersSync(): List<BlockedNumberEntity> = withContext(Dispatchers.IO) {
        blockedNumberDao.getAllBlockedSync()
    }

    suspend fun toggleArchive(threadId: Long, address: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        val targetKey = if (key != "UNKNOWN") "key_$key" else "thread_$threadId"
        val userPrefs = userPreferencesRepository.userPreferencesFlow.firstOrNull()
        val currentArchived = userPrefs?.archivedConversationKeys ?: emptySet()
        val isNowArchived = !currentArchived.contains(targetKey)
        userPreferencesRepository.setConversationArchived(targetKey, isNowArchived)
        if (threadId > 0) {
            userPreferencesRepository.setConversationArchived("thread_$threadId", isNowArchived)
        }
        if (key != "UNKNOWN") {
            userPreferencesRepository.setConversationArchived("key_$key", isNowArchived)
            userPreferencesRepository.setConversationArchived(key, isNowArchived)
        }
        refreshConversations()
    }

    suspend fun isConversationArchived(threadId: Long, address: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        val targetKey = if (key != "UNKNOWN") "key_$key" else "thread_$threadId"
        prefs.archivedConversationKeys.contains(targetKey) ||
                (threadId > 0 && prefs.archivedConversationKeys.contains("thread_$threadId")) ||
                (key != "UNKNOWN" && prefs.archivedConversationKeys.contains("key_$key")) ||
                (key != "UNKNOWN" && prefs.archivedConversationKeys.contains(key))
    }

    suspend fun archiveConversation(threadId: Long, address: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        val targetKey = if (key != "UNKNOWN") "key_$key" else "thread_$threadId"
        userPreferencesRepository.setConversationArchived(targetKey, true)
        if (threadId > 0) userPreferencesRepository.setConversationArchived("thread_$threadId", true)
        if (key != "UNKNOWN") {
            userPreferencesRepository.setConversationArchived("key_$key", true)
            userPreferencesRepository.setConversationArchived(key, true)
        }
        refreshConversations()
    }

    suspend fun unarchiveConversation(threadId: Long, address: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(address)
        val targetKey = if (key != "UNKNOWN") "key_$key" else "thread_$threadId"
        userPreferencesRepository.setConversationArchived(targetKey, false)
        if (threadId > 0) userPreferencesRepository.setConversationArchived("thread_$threadId", false)
        if (key != "UNKNOWN") {
            userPreferencesRepository.setConversationArchived("key_$key", false)
            userPreferencesRepository.setConversationArchived(key, false)
        }
        refreshConversations()
    }

    suspend fun archiveConversations(items: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        for ((threadId, address) in items) {
            val key = SenderIdentityHelper.normalizeSenderKey(address)
            val targetKey = if (key != "UNKNOWN") "key_$key" else "thread_$threadId"
            userPreferencesRepository.setConversationArchived(targetKey, true)
            if (threadId > 0) userPreferencesRepository.setConversationArchived("thread_$threadId", true)
            if (key != "UNKNOWN") {
                userPreferencesRepository.setConversationArchived("key_$key", true)
                userPreferencesRepository.setConversationArchived(key, true)
            }
        }
        refreshConversations()
    }

    suspend fun unarchiveConversations(items: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        for ((threadId, address) in items) {
            val key = SenderIdentityHelper.normalizeSenderKey(address)
            val targetKey = if (key != "UNKNOWN") "key_$key" else "thread_$threadId"
            userPreferencesRepository.setConversationArchived(targetKey, false)
            if (threadId > 0) userPreferencesRepository.setConversationArchived("thread_$threadId", false)
            if (key != "UNKNOWN") {
                userPreferencesRepository.setConversationArchived("key_$key", false)
                userPreferencesRepository.setConversationArchived(key, false)
            }
        }
        refreshConversations()
    }

    suspend fun blockSender(phone: String, contactName: String? = null) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(phone)
        if (key != "UNKNOWN") {
            blockedNumberDao.insertBlocked(
                BlockedNumberEntity(
                    phoneNumber = phone,
                    normalizedKey = key,
                    contactName = contactName ?: lookupContactName(phone),
                    blockedAtEpochMs = System.currentTimeMillis()
                )
            )
            refreshConversations()
        }
    }

    suspend fun unblockSender(phone: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(phone)
        if (key != "UNKNOWN") {
            blockedNumberDao.deleteBlockedByKey(key)
            refreshConversations()
        }
    }

    suspend fun isSenderBlocked(phone: String): Boolean = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(phone)
        if (key == "UNKNOWN") return@withContext false
        blockedNumberDao.isKeyBlocked(key) > 0
    }

    fun lookupContactName(phone: String): String? {
        val senderKey = SenderIdentityHelper.normalizeSenderKey(phone)
        if (contactNameCache.containsKey(senderKey)) {
            return contactNameCache[senderKey]
        }

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    if (!name.isNullOrBlank()) {
                        contactNameCache[senderKey] = name
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if contacts permission not granted
        }
        return null
    }
}
