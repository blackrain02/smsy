package com.blackrain02.spamguard.data.repository

import android.content.Context
import com.blackrain02.spamguard.ai.AdaptiveFeedbackEngine
import com.blackrain02.spamguard.data.local.AppDatabase
import com.blackrain02.spamguard.data.local.SpamMessageEntity
import com.blackrain02.spamguard.data.local.UserWhitelistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SpamRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val spamDao = db.spamMessageDao()
    private val userWhitelistDao = db.userWhitelistDao()
    private val smsRepository = SmsRepository(context)
    private val feedbackEngine = AdaptiveFeedbackEngine.getInstance(context)

    fun getAllSpam(): Flow<List<SpamMessageEntity>> = spamDao.getAllSpam()

    fun getUnreadCount(): Flow<Int> = spamDao.getUnreadCount()

    suspend fun quarantine(sender: String, body: String, probability: Float): Long = withContext(Dispatchers.IO) {
        val entity = SpamMessageEntity(
            sender = sender,
            body = body,
            timestamp = System.currentTimeMillis(),
            spamProbability = probability
        )
        feedbackEngine.recordSpamFeedback(body)
        spamDao.insert(entity)
    }

    suspend fun quarantineMessage(sender: String, body: String, timestamp: Long, probability: Float = 1.0f): Long = withContext(Dispatchers.IO) {
        val entity = SpamMessageEntity(
            sender = sender,
            body = body,
            timestamp = timestamp,
            spamProbability = probability
        )
        feedbackEngine.recordSpamFeedback(body)
        spamDao.insert(entity)
    }

    suspend fun deleteSpam(id: Long) = withContext(Dispatchers.IO) {
        spamDao.deleteById(id)
    }

    suspend fun deleteBySender(sender: String) = withContext(Dispatchers.IO) {
        spamDao.deleteBySender(sender)
    }

    suspend fun deleteAllSpam() = withContext(Dispatchers.IO) {
        spamDao.deleteAll()
    }

    /**
     * بازیابی تک‌پیام هرزنامه و آموزش سیستم
     */
    suspend fun restoreToInbox(spam: SpamMessageEntity) = withContext(Dispatchers.IO) {
        smsRepository.saveToInbox(spam.sender, spam.body, spam.timestamp, isRead = true)
        spamDao.deleteById(spam.id)
        
        // ثبت در حافظه یادگیری دائمی سیستم و یادگیری کلمات سالم
        if (spam.sender.isNotBlank()) {
            userWhitelistDao.insert(UserWhitelistEntity(sender = spam.sender))
        }
        feedbackEngine.recordHamFeedback(spam.body)
    }

    /**
     * بازیابی تمام پیام‌های یک سرشماره و آموزش سیستم
     */
    suspend fun restoreSenderToInbox(sender: String) = withContext(Dispatchers.IO) {
        val messages = spamDao.getMessagesBySender(sender)
        for (msg in messages) {
            smsRepository.saveToInbox(msg.sender, msg.body, msg.timestamp, isRead = true)
            feedbackEngine.recordHamFeedback(msg.body)
        }
        spamDao.deleteBySender(sender)

        if (sender.isNotBlank()) {
            userWhitelistDao.insert(UserWhitelistEntity(sender = sender))
        }
    }

    suspend fun isUserWhitelisted(sender: String): Boolean = withContext(Dispatchers.IO) {
        userWhitelistDao.isWhitelisted(sender) > 0
    }
}
