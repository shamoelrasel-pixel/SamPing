package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.HistoryEntity
import com.example.domain.model.DeliveryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_logs ORDER BY executedEpochMs DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_logs ORDER BY executedEpochMs DESC")
    suspend fun getAllHistorySync(): List<HistoryEntity>

    @Query("SELECT * FROM history_logs WHERE status = :status ORDER BY executedEpochMs DESC")
    fun getHistoryByStatus(status: DeliveryStatus): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_logs WHERE scheduleId = :scheduleId ORDER BY executedEpochMs DESC")
    fun getHistoryForSchedule(scheduleId: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_logs WHERE id = :id")
    suspend fun getHistoryById(id: Long): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity): Long

    @Update
    suspend fun updateHistory(history: HistoryEntity)

    @Query("UPDATE history_logs SET status = :status, errorReason = :errorReason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DeliveryStatus, errorReason: String? = null)

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM history_logs WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history_logs")
    suspend fun clearAllHistory()

    @Query("SELECT COUNT(*) FROM history_logs WHERE status IN ('SENT', 'DELIVERED') AND executedEpochMs >= :startOfDayMs")
    fun getSentTodayCountFlow(startOfDayMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM history_logs WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>
}
