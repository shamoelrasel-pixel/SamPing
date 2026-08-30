package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts ORDER BY updatedAtEpochMs DESC")
    fun getAllDrafts(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllDraftsSync(): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE recipientPhoneKey = :key LIMIT 1")
    suspend fun getDraftByKey(key: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE recipientPhoneKey = :key")
    suspend fun deleteDraftByKey(key: String)

    @Delete
    suspend fun deleteDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts")
    suspend fun deleteAllDrafts()
}
