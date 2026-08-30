package com.example.data.repository

import com.example.data.local.dao.ScheduleDao
import com.example.data.local.entity.ScheduleEntity
import com.example.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val scheduleDao: ScheduleDao) {

    val allSchedules: Flow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()
    val activeCount: Flow<Int> = scheduleDao.getActiveCountFlow()
    val totalCount: Flow<Int> = scheduleDao.getTotalCountFlow()

    fun getSchedulesByStatus(status: ScheduleStatus): Flow<List<ScheduleEntity>> {
        return scheduleDao.getSchedulesByStatus(status)
    }

    suspend fun getScheduleById(id: Long): ScheduleEntity? {
        return scheduleDao.getScheduleById(id)
    }

    fun getScheduleByIdFlow(id: Long): Flow<ScheduleEntity?> {
        return scheduleDao.getScheduleByIdFlow(id)
    }

    suspend fun getActiveScheduledItems(): List<ScheduleEntity> {
        return scheduleDao.getActiveScheduledItems()
    }

    suspend fun getDueSchedules(currentTime: Long): List<ScheduleEntity> {
        return scheduleDao.getDueSchedules(currentTime)
    }

    suspend fun insertSchedule(schedule: ScheduleEntity): Long {
        return scheduleDao.insertSchedule(schedule)
    }

    suspend fun updateSchedule(schedule: ScheduleEntity) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        scheduleDao.deleteSchedule(schedule)
    }

    suspend fun deleteScheduleById(id: Long) {
        scheduleDao.deleteScheduleById(id)
    }

    suspend fun updateStatus(id: Long, status: ScheduleStatus) {
        scheduleDao.updateStatus(id, status)
    }
}
