package com.blackrain02.spamguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bank_transactions")
data class BankTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val amount: Long,
    val amountUnit: String = "تومان",
    val type: String, // "DEPOSIT", "WITHDRAWAL", "OTHER"
    val balance: Long? = null,
    val cardNumber: String? = null,
    val category: String,
    val rawBody: String,
    val timestamp: Long
)
