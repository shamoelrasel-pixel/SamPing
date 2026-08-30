package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemType: String, // "CONVERSATION" or "MESSAGE"
    val originalTelephonyId: Long? = null,
    val threadId: Long = -1L,
    val recipientPhone: String,
    val recipientName: String,
    val messageBody: String,
    val messageDateEpochMs: Long,
    val isIncoming: Boolean = true,
    val deletedAtEpochMs: Long = System.currentTimeMillis()
)
