package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.RecycleBinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAtEpochMs DESC")
    fun getAllItemsFlow(): Flow<List<RecycleBinEntity>>

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAtEpochMs DESC")
    suspend fun getAllItemsSync(): List<RecycleBinEntity>

    @Query("SELECT COUNT(*) FROM recycle_bin")
    fun getItemCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: RecycleBinEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RecycleBinEntity>)

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM recycle_bin")
    suspend fun deleteAll()

    @Query("DELETE FROM recycle_bin WHERE deletedAtEpochMs < :cutoffEpochMs")
    suspend fun deleteExpiredItems(cutoffEpochMs: Long)
}
