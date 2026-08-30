package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.MessageChannel

@Entity(tableName = "history_logs")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long? = null,
    val recipientName: String,
    val recipientPhone: String,
    val messageBody: String,
    val channel: MessageChannel = MessageChannel.SMS,
    val simDisplayName: String? = null,
    val scheduledEpochMs: Long,
    val executedEpochMs: Long = System.currentTimeMillis(),
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val errorReason: String? = null,
    val retryAttempt: Int = 0
)
