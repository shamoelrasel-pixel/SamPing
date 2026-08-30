package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAtEpochMs DESC")
    fun getAllBlockedFlow(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAtEpochMs DESC")
    suspend fun getAllBlockedSync(): List<BlockedNumberEntity>

    @Query("SELECT COUNT(*) FROM blocked_numbers WHERE normalizedKey = :normalizedKey")
    suspend fun isKeyBlocked(normalizedKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocked(entity: BlockedNumberEntity): Long

    @Query("DELETE FROM blocked_numbers WHERE normalizedKey = :normalizedKey")
    suspend fun deleteBlockedByKey(normalizedKey: String)

    @Query("DELETE FROM blocked_numbers WHERE id = :id")
    suspend fun deleteBlockedById(id: Long)
}
