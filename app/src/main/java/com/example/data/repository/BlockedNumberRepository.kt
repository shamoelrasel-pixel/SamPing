package com.example.data.repository

import com.example.data.local.dao.BlockedNumberDao
import com.example.data.local.entity.BlockedNumberEntity
import com.example.domain.util.SenderIdentityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BlockedNumberRepository(
    private val blockedNumberDao: BlockedNumberDao
) {
    val blockedNumbersFlow: Flow<List<BlockedNumberEntity>> = blockedNumberDao.getAllBlockedFlow()

    suspend fun getBlockedNumbersSync(): List<BlockedNumberEntity> = withContext(Dispatchers.IO) {
        blockedNumberDao.getAllBlockedSync()
    }

    suspend fun isBlocked(phone: String): Boolean = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(phone)
        if (key == "UNKNOWN") return@withContext false
        blockedNumberDao.isKeyBlocked(key) > 0
    }

    suspend fun blockNumber(phone: String, contactName: String? = null) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(phone)
        if (key != "UNKNOWN") {
            blockedNumberDao.insertBlocked(
                BlockedNumberEntity(
                    phoneNumber = phone,
                    normalizedKey = key,
                    contactName = contactName,
                    blockedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun unblockNumber(phone: String) = withContext(Dispatchers.IO) {
        val key = SenderIdentityHelper.normalizeSenderKey(phone)
        if (key != "UNKNOWN") {
            blockedNumberDao.deleteBlockedByKey(key)
        }
    }

    suspend fun unblockById(id: Long) = withContext(Dispatchers.IO) {
        blockedNumberDao.deleteBlockedById(id)
    }
}
