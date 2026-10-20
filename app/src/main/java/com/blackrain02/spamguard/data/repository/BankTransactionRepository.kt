package com.blackrain02.spamguard.data.repository

import android.content.Context
import android.net.Uri
import com.blackrain02.spamguard.ai.BankSmsParser
import com.blackrain02.spamguard.data.local.AppDatabase
import com.blackrain02.spamguard.data.local.BankTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FinancialStats(
    val totalDeposits: Long = 0L,
    val totalWithdrawals: Long = 0L,
    val netBalance: Long = 0L,
    val transactionCount: Int = 0
)

class BankTransactionRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.bankTransactionDao()

    init {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            cleanupAndReparse()
        }
    }

    fun getAllTransactions(): Flow<List<BankTransactionEntity>> = dao.getAll()

    fun getTransactionsByType(type: String): Flow<List<BankTransactionEntity>> = dao.getByType(type)

    fun getFinancialStats(): Flow<FinancialStats> {
        return dao.getAll().map { list ->
            var deposits = 0L
            var withdrawals = 0L
            for (t in list) {
                if (t.type == "DEPOSIT") {
                    deposits += t.amount
                } else if (t.type == "WITHDRAWAL") {
                    withdrawals += t.amount
                }
            }
            FinancialStats(
                totalDeposits = deposits,
                totalWithdrawals = withdrawals,
                netBalance = deposits - withdrawals,
                transactionCount = list.size
            )
        }
    }

    suspend fun cleanupDuplicates(): Int = withContext(Dispatchers.IO) {
        dao.purgeDuplicates()
    }

    suspend fun cleanupAndReparse(): Int = withContext(Dispatchers.IO) {
        dao.purgeDuplicates()
        val all = dao.getAllList()
        val toUpdate = mutableListOf<BankTransactionEntity>()
        for (item in all) {
            val reparsed = BankSmsParser.parse(null, item.rawBody, item.timestamp)
            if (reparsed != null) {
                toUpdate.add(reparsed.copy(id = item.id))
            }
        }
        if (toUpdate.isNotEmpty()) {
            dao.updateAll(toUpdate)
        }
        toUpdate.size
    }

    suspend fun recordIfBankMessage(sender: String?, body: String?, timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        if (body.isNullOrBlank()) return@withContext false
        if (dao.exists(timestamp, body) > 0) return@withContext false
        val parsed = BankSmsParser.parse(sender, body, timestamp) ?: return@withContext false
        dao.insert(parsed)
        true
    }

    /**
     * اسکن یکباره صندوق دریافت برای استخراج و بایگانی تمام پیامک‌های بانکی گذشته بدون ثبت رکوردهای تکراری
     */
    suspend fun scanAndImportHistoricalBankSms(): Int = withContext(Dispatchers.IO) {
        dao.purgeDuplicates()

        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date"),
            null,
            null,
            "date DESC LIMIT 500"
        ) ?: return@withContext 0

        val existingSignatures = dao.getAllSignatures().toHashSet()
        val listToInsert = mutableListOf<BankTransactionEntity>()

        cursor.use { c ->
            val addressIdx = c.getColumnIndex("address")
            val bodyIdx = c.getColumnIndex("body")
            val dateIdx = c.getColumnIndex("date")

            while (c.moveToNext()) {
                val address = if (addressIdx >= 0) c.getString(addressIdx) else ""
                val body = if (bodyIdx >= 0) c.getString(bodyIdx) else ""
                val date = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()

                val sig = "${date}___${body}"
                if (sig in existingSignatures) {
                    continue
                }

                val parsed = BankSmsParser.parse(address, body, date)
                if (parsed != null) {
                    listToInsert.add(parsed)
                    existingSignatures.add(sig)
                }
            }
        }

        if (listToInsert.isNotEmpty()) {
            dao.insertAll(listToInsert)
        }
        listToInsert.size
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
