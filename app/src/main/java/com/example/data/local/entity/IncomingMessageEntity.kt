package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "incoming_messages",
    indices = [
        Index(value = ["normalizedKey"]),
        Index(value = ["rawSender"]),
        Index(value = ["dateEpochMs"])
    ]
)
data class IncomingMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val telephonyId: Long? = null,
    val threadId: Long = -1L,
    val rawSender: String,
    val normalizedKey: String,
    val body: String,
    val dateEpochMs: Long,
    val isRead: Boolean = false,
    val simSubscriptionId: Int? = null,
    val receivedAtEpochMs: Long = System.currentTimeMillis()
)
