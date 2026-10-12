package com.binacity.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocklist")
data class BlocklistEntity(
    @PrimaryKey
    val sender: String,
    val reason: String = "Manual Block",
    val blockedAt: Long = System.currentTimeMillis()
)
