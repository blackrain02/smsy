package com.binacity.spamguard.data.repository

import android.content.Context
import com.binacity.spamguard.data.local.AppDatabase
import com.binacity.spamguard.data.local.SpamMessageEntity
import com.binacity.spamguard.data.local.UserWhitelistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SpamRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val spamDao = db.spamMessageDao()
    private val userWhitelistDao = db.userWhitelistDao()
    private val smsRepository = SmsRepository(context)

    fun getAllSpam(): Flow<List<SpamMessageEntity>> = spamDao.getAllSpam()

    fun getUnreadCount(): Flow<Int> = spamDao.getUnreadCount()

    suspend fun quarantine(sender: String, body: String, probability: Float): Long = withContext(Dispatchers.IO) {
        val entity = SpamMessageEntity(
            sender = sender,
            body = body,
            timestamp = System.currentTimeMillis(),
            spamProbability = probability
        )
        spamDao.insert(entity)
    }

    suspend fun deleteSpam(id: Long) = withContext(Dispatchers.IO) {
        spamDao.deleteById(id)
    }

    suspend fun deleteAllSpam() = withContext(Dispatchers.IO) {
        spamDao.deleteAll()
    }

    /**
     * بازیابی پیام هرزنامه و آموزش سیستم:
     * ۱. ذخیره مجدد در پیام‌های رسمی گوشی
     * ۲. حذف از قرنطینه
     * ۳. ثبت فرستنده در لیست سفید اختصاصی کاربر جهت یادگیری و عدم تکرار اشتباه
     */
    suspend fun restoreToInbox(spam: SpamMessageEntity) = withContext(Dispatchers.IO) {
        smsRepository.saveToInbox(spam.sender, spam.body, spam.timestamp)
        spamDao.deleteById(spam.id)
        
        // ثبت در حافظه یادگیری دائمی سیستم
        if (spam.sender.isNotBlank()) {
            userWhitelistDao.insert(UserWhitelistEntity(sender = spam.sender))
        }
    }

    suspend fun isUserWhitelisted(sender: String): Boolean = withContext(Dispatchers.IO) {
        userWhitelistDao.isWhitelisted(sender) > 0
    }
}
