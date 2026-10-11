package com.binacity.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_whitelist")
data class UserWhitelistEntity(
    @PrimaryKey
    val sender: String,
    val addedAt: Long = System.currentTimeMillis()
)
