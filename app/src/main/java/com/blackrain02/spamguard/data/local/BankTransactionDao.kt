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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateAll(transactions: List<BankTransactionEntity>)

    @Query("SELECT COUNT(*) FROM bank_transactions WHERE timestamp = :timestamp AND rawBody = :rawBody")
    suspend fun exists(timestamp: Long, rawBody: String): Int

    @Query("SELECT timestamp || '___' || rawBody FROM bank_transactions")
    suspend fun getAllSignatures(): List<String>

    @Query("""
        DELETE FROM bank_transactions 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM bank_transactions 
            GROUP BY timestamp, rawBody
        )
    """)
    suspend fun purgeDuplicates(): Int

    @Query("DELETE FROM bank_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE bank_transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE bank_transactions SET type = :type WHERE id = :id")
    suspend fun updateType(id: Long, type: String)

    @Query("DELETE FROM bank_transactions")
    suspend fun deleteAll()
}
