package com.example.data.repository

import com.example.data.local.dao.RecycleBinDao
import com.example.data.local.entity.RecycleBinEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecycleBinRepository(
    private val recycleBinDao: RecycleBinDao
) {
    val itemsFlow: Flow<List<RecycleBinEntity>> = recycleBinDao.getAllItemsFlow()
    val itemCountFlow: Flow<Int> = recycleBinDao.getItemCountFlow()

    suspend fun getItemsSync(): List<RecycleBinEntity> = withContext(Dispatchers.IO) {
        cleanupExpired()
        recycleBinDao.getAllItemsSync()
    }

    suspend fun addItem(item: RecycleBinEntity): Long = withContext(Dispatchers.IO) {
        recycleBinDao.insertItem(item)
    }

    suspend fun addItems(items: List<RecycleBinEntity>) = withContext(Dispatchers.IO) {
        recycleBinDao.insertItems(items)
    }

    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        recycleBinDao.deleteItemById(id)
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        recycleBinDao.deleteAll()
    }

    suspend fun cleanupExpired(retentionDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000L)
        recycleBinDao.deleteExpiredItems(cutoff)
    }
}
