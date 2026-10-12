package com.blackrain02.spamguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BankTransactionDao {
    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BankTransactionEntity>>

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC")
    suspend fun getAllList(): List<BankTransactionEntity>

    @Query("SELECT * FROM bank_transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getByType(type: String): Flow<List<BankTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: BankTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<BankTransactionEntity>)

    @Query("DELETE FROM bank_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bank_transactions")
    suspend fun deleteAll()
}
