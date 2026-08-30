package com.example.domain.model

data class SmsConversation(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val dateEpochMs: Long,
    val messageCount: Int,
    val isRead: Boolean,
    val unreadCount: Int = 0,
    val photoUri: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val hasScheduledMessages: Boolean = false,
    val scheduledCount: Int = 0,
    val hasDraft: Boolean = false,
    val draftSnippet: String? = null
) {
    val recipientPhone: String get() = address
    val recipientName: String get() = contactName ?: ""
}

data class SmsChatMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateEpochMs: Long,
    val isIncoming: Boolean,
    val isRead: Boolean,
    val status: Int = -1,
    val subscriptionId: Int? = null,
    val isScheduled: Boolean = false,
    val scheduledTriggerEpochMs: Long? = null,
    val scheduleId: Long? = null,
    val isDraft: Boolean = false,
    val isDelivered: Boolean = false,
    val isFailed: Boolean = false,
    val errorReason: String? = null,
    val deliveryStatus: DeliveryStatus? = null
)
