package com.blackrain02.spamguard.data.repository

import android.content.Context
import com.blackrain02.spamguard.data.local.AppDatabase
import com.blackrain02.spamguard.data.local.BlocklistEntity
import kotlinx.coroutines.flow.Flow

class BlocklistRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).blocklistDao()

    fun getAllBlocked(): Flow<List<BlocklistEntity>> = dao.getAllBlocked()

    suspend fun isBlocked(sender: String): Boolean = dao.isBlocked(sender)

    suspend fun blockSender(sender: String, reason: String = "Manual Block") {
        dao.blockSender(BlocklistEntity(sender = sender, reason = reason))
    }

    suspend fun unblockSender(sender: String) {
        dao.unblockSender(sender)
    }

    suspend fun getAllList(): List<BlocklistEntity> = dao.getAllBlockedList()

    suspend fun getBlockedSet(): Set<String> = getAllList().map { it.sender.trim().lowercase() }.toSet()

    suspend fun restoreList(list: List<BlocklistEntity>) {
        dao.insertAll(list)
    }
}
