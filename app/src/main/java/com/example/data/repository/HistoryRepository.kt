package com.example.data.repository

import com.example.data.local.dao.HistoryDao
import com.example.data.local.entity.HistoryEntity
import com.example.domain.model.DeliveryStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class HistoryRepository(private val historyDao: HistoryDao) {

    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()
    val failedCount: Flow<Int> = historyDao.getFailedCountFlow()

    fun getHistoryByStatus(status: DeliveryStatus): Flow<List<HistoryEntity>> {
        return historyDao.getHistoryByStatus(status)
    }

    fun getHistoryForSchedule(scheduleId: Long): Flow<List<HistoryEntity>> {
        return historyDao.getHistoryForSchedule(scheduleId)
    }

    fun getSentTodayCount(): Flow<Int> {
        val startOfDayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return historyDao.getSentTodayCountFlow(startOfDayMs)
    }

    suspend fun getHistoryById(id: Long): HistoryEntity? {
        return historyDao.getHistoryById(id)
    }

    suspend fun insertHistory(history: HistoryEntity): Long {
        return historyDao.insertHistory(history)
    }

    suspend fun updateHistory(history: HistoryEntity) {
        historyDao.updateHistory(history)
    }

    suspend fun updateStatus(id: Long, status: DeliveryStatus, errorReason: String? = null) {
        historyDao.updateStatus(id, status, errorReason)
    }

    suspend fun deleteHistory(history: HistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun deleteHistoryById(id: Long) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAllHistory()
    }
}
