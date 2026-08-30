package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.AutoSendApplication
import com.example.domain.util.SenderIdentityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust SMS processing pipeline handling incoming OTPs, PINs, banking & MFS transaction alerts,
 * and alphanumeric/shortcode messages with 8-stage diagnostic logging and de-duplication.
 */
object SmsReceivePipeline {

    private const val TAG = "SamPingSms"

    // Sliding window deduplication cache: signature -> timestamp
    private val recentProcessedSignatures = ConcurrentHashMap<String, Long>()
    private const val DEDUP_EXPIRY_MS = 3_000L // 3 seconds window strictly to deduplicate dual SMS_DELIVER/SMS_RECEIVED broadcasts

    fun handleIncomingSms(context: Context, intent: Intent, onComplete: (() -> Unit)? = null) {
        val action = intent.action ?: ""
        val extrasSummary = intent.extras?.keySet()?.joinToString() ?: "none"
        
        // 1. RECEIVED
        Log.d(TAG, "[RECEIVED] Broadcast Action: '$action', Extras: [$extrasSummary]")

        val isSmsAction = action == Telephony.Sms.Intents.SMS_DELIVER_ACTION ||
                action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
                action == Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION ||
                action == "android.intent.action.DATA_SMS_RECEIVED" ||
                intent.hasExtra("pdus")

        if (!isSmsAction) {
            Log.w(TAG, "[RECEIVED] Ignored non-SMS action: $action")
            onComplete?.invoke()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clean up expired deduplication cache entries
                val now = System.currentTimeMillis()
                val iterator = recentProcessedSignatures.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > DEDUP_EXPIRY_MS) {
                        iterator.remove()
                    }
                }

                // 2. PDU_DECODED
                var messages = try {
                    Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList()
                } catch (e: Exception) {
                    Log.e(TAG, "[PDU_DECODED] Failed getting messages via getMessagesFromIntent", e)
                    null
                }

                val format = intent.getStringExtra("format")

                if (messages.isNullOrEmpty()) {
                    // Fallback universal PDU extraction (handles Array, ArrayList, Collection)
                    val pdusObj = intent.extras?.get("pdus")
                    val rawPdus: List<ByteArray> = when (pdusObj) {
                        is Array<*> -> pdusObj.filterIsInstance<ByteArray>()
                        is Iterable<*> -> pdusObj.filterIsInstance<ByteArray>()
                        is ByteArray -> listOf(pdusObj)
                        else -> emptyList()
                    }

                    if (rawPdus.isNotEmpty()) {
                        messages = rawPdus.mapNotNull { pdu ->
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    if (!format.isNullOrBlank()) {
                                        SmsMessage.createFromPdu(pdu, format)
                                    } else {
                                        try {
                                            SmsMessage.createFromPdu(pdu, "3gpp")
                                        } catch (e1: Throwable) {
                                            try {
                                                SmsMessage.createFromPdu(pdu, "3gpp2")
                                            } catch (e2: Throwable) {
                                                @Suppress("DEPRECATION")
                                                SmsMessage.createFromPdu(pdu)
                                            }
                                        }
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    SmsMessage.createFromPdu(pdu)
                                }
                            } catch (ex: Exception) {
                                Log.e(TAG, "[PDU_DECODED] PDU parse error", ex)
                                try {
                                    @Suppress("DEPRECATION")
                                    SmsMessage.createFromPdu(pdu)
                                } catch (e: Throwable) {
                                    null
                                }
                            }
                        }
                    }
                }

                if (messages.isNullOrEmpty()) {
                    Log.w(TAG, "[PDU_DECODED] No SMS PDUs successfully decoded from broadcast intent.")
                    return@launch
                }

                Log.d(TAG, "[PDU_DECODED] Decoded ${messages.size} message part(s), Format: '${format ?: "auto"}'")

                val app = context.applicationContext as? AutoSendApplication
                val notificationHelper = app?.notificationHelper ?: NotificationHelper(context)
                val smsRepo = app?.smsRepository
                val blockedRepo = app?.blockedNumberRepository

                // Extract SIM subscription ID (dual SIM support)
                val subId = if (intent.hasExtra("subscription")) {
                    intent.getIntExtra("subscription", -1)
                } else if (intent.hasExtra("android.telephony.extra.SUBSCRIPTION_INDEX")) {
                    intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
                } else {
                    null
                }

                // Group by normalized sender key so all parts belonging to the same sender are merged cleanly
                val messagesBySenderKey = messages.groupBy { sms ->
                    val disp = sms.displayOriginatingAddress?.trim()
                    val orig = sms.originatingAddress?.trim()
                    val candidate = when {
                        !disp.isNullOrBlank() -> disp
                        !orig.isNullOrBlank() -> orig
                        else -> "Unknown"
                    }
                    SenderIdentityHelper.normalizeSenderKey(candidate)
                }

                for ((senderKey, senderMessages) in messagesBySenderKey) {
                    // Extract best displayable raw sender string from the parts
                    val rawSender = senderMessages.mapNotNull { sms ->
                        val disp = sms.displayOriginatingAddress?.trim()
                        val orig = sms.originatingAddress?.trim()
                        when {
                            !disp.isNullOrBlank() -> disp
                            !orig.isNullOrBlank() -> orig
                            else -> null
                        }
                    }.firstOrNull() ?: "Unknown"

                    // 3. MULTIPART_MERGED
                    val combinedBody = senderMessages.joinToString(separator = "") {
                        it.displayMessageBody ?: it.messageBody ?: ""
                    }
                    val timestamp = senderMessages.map { it.timestampMillis }
                        .filter { it > 0 }
                        .minOrNull() ?: System.currentTimeMillis()

                    // Sanitize log snippet to avoid exposing sensitive OTP/PIN in production logs
                    val isSecurityMsg = SenderIdentityHelper.isOtpOrSecurityMessage(combinedBody)
                    val logSnippet = if (isSecurityMsg) {
                        "[Security/OTP Message - ${combinedBody.length} chars]"
                    } else if (combinedBody.length > 35) {
                        combinedBody.take(30) + "..."
                    } else {
                        combinedBody
                    }

                    Log.d(TAG, "[MULTIPART_MERGED] Sender: '$rawSender', Parts: ${senderMessages.size}, TotalLength: ${combinedBody.length}, Preview='$logSnippet'")

                    // 4. SENDER_PARSED
                    val normalizedKey = if (senderKey != "UNKNOWN") senderKey else SenderIdentityHelper.normalizeSenderKey(rawSender)
                    val orgName = SenderIdentityHelper.resolveOrganizationName(rawSender, combinedBody)
                    val contactName = smsRepo?.lookupContactName(rawSender) ?: orgName ?: rawSender
                    Log.d(TAG, "[SENDER_PARSED] Raw: '$rawSender', NormalizedKey: '$normalizedKey', OrgName: '$orgName', ContactName: '$contactName'")

                    val isBlocked = blockedRepo?.isBlocked(rawSender) == true ||
                            (normalizedKey != "UNKNOWN" && blockedRepo?.isBlocked(normalizedKey) == true)

                    if (isBlocked) {
                        Log.d(TAG, "[BLOCKED] SMS from blocked identity '$rawSender' ($normalizedKey) suppressed.")
                        continue
                    }

                    // De-duplication check (only deduplicates exact identical broadcasts within 3s)
                    val msgSignature = "${rawSender}_${combinedBody.hashCode()}_${timestamp / 1000}"
                    if (recentProcessedSignatures.containsKey(msgSignature)) {
                        Log.d(TAG, "[DEDUPLICATED] Duplicate broadcast signature detected for '$rawSender', skipping redundant processing.")
                        continue
                    }
                    recentProcessedSignatures[msgSignature] = System.currentTimeMillis()

                    // 5. DATABASE_INSERTED
                    var insertedId: Long = -1L
                    try {
                        insertedId = smsRepo?.writeIncomingSms(rawSender, combinedBody, timestamp, subId) ?: -1L
                        Log.d(TAG, "[DATABASE_INSERTED] LocalId: $insertedId, Sender: '$rawSender', Success=${insertedId > 0 || insertedId != -1L}")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DATABASE_INSERTED] Database insertion failed for '$rawSender'", e)
                    }

                    // 6. THREAD_UPDATED
                    val isCurrentlyOpen = smsRepo?.isConversationActive(rawSender) == true
                    val activeThreadId = smsRepo?.getActiveThreadId() ?: -1L
                    Log.d(TAG, "[THREAD_UPDATED] Key: '$normalizedKey', ActiveConversation: $isCurrentlyOpen, ActiveThreadId: $activeThreadId")

                    // 7. UI_DISPLAYED
                    val userPrefs = app?.userPreferencesRepository?.userPreferencesFlow?.firstOrNull()
                    val tone = userPrefs?.incomingSmsTone ?: "CHIME"

                    if (isCurrentlyOpen) {
                        if (tone.trim().uppercase() != "SILENT") {
                            notificationHelper.playTone(tone)
                        }
                        Log.d(TAG, "[UI_DISPLAYED] Live UI emitted to active chat thread (in-app chime played)")
                    } else {
                        Log.d(TAG, "[UI_DISPLAYED] Inbox conversations state refreshed for '$contactName'")
                    }

                    // 8. NOTIFICATION_CREATED
                    if (!isCurrentlyOpen) {
                        notificationHelper.showIncomingSmsNotification(
                            senderPhone = rawSender,
                            senderName = contactName,
                            messageBody = combinedBody,
                            tone = tone
                        )
                        Log.d(TAG, "[NOTIFICATION_CREATED] Dispatched notification for '$contactName' (Tone: $tone)")
                    }

                    // Refresh conversation list state
                    smsRepo?.refreshConversations()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Error] Exception in incoming SMS pipeline", e)
            } finally {
                onComplete?.invoke()
            }
        }
    }
}
