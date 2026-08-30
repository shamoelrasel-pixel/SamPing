package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.IncomingMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomingMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: IncomingMessageEntity): Long

    @Query("SELECT * FROM incoming_messages ORDER BY dateEpochMs ASC")
    fun getAllIncomingFlow(): Flow<List<IncomingMessageEntity>>

    @Query("SELECT * FROM incoming_messages ORDER BY dateEpochMs ASC")
    suspend fun getAllIncomingSync(): List<IncomingMessageEntity>

    @Query("SELECT * FROM incoming_messages WHERE normalizedKey = :key ORDER BY dateEpochMs ASC")
    suspend fun getIncomingByKeySync(key: String): List<IncomingMessageEntity>

    @Query("UPDATE incoming_messages SET isRead = 1 WHERE normalizedKey = :key")
    suspend fun markAsReadByKey(key: String)

    @Query("DELETE FROM incoming_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM incoming_messages WHERE normalizedKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM incoming_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
