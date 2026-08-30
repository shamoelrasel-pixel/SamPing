package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey
    val recipientPhoneKey: String, // Normalized sender identity key
    val rawRecipientPhone: String,
    val recipientName: String,
    val messageBody: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
