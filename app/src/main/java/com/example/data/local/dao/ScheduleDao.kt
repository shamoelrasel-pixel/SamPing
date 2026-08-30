package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY nextExecutionEpochMs ASC")
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY nextExecutionEpochMs ASC")
    suspend fun getAllSchedulesSync(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE status = :status ORDER BY nextExecutionEpochMs ASC")
    fun getSchedulesByStatus(status: ScheduleStatus): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE id = :id")
    fun getScheduleByIdFlow(id: Long): Flow<ScheduleEntity?>

    @Query("SELECT * FROM schedules WHERE status = 'SCHEDULED' ORDER BY nextExecutionEpochMs ASC")
    suspend fun getActiveScheduledItems(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE status = 'SCHEDULED' AND nextExecutionEpochMs <= :currentTime ORDER BY nextExecutionEpochMs ASC")
    suspend fun getDueSchedules(currentTime: Long): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: Long)

    @Query("UPDATE schedules SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: ScheduleStatus)

    @Query("SELECT COUNT(*) FROM schedules WHERE status = 'SCHEDULED'")
    fun getActiveCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM schedules")
    fun getTotalCountFlow(): Flow<Int>
}
