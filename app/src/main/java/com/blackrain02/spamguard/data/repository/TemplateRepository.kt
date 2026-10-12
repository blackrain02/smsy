package com.blackrain02.spamguard.data.repository

import android.content.Context
import android.provider.Telephony
import com.blackrain02.spamguard.data.local.AppDatabase
import com.blackrain02.spamguard.data.local.QuickTemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TemplateRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).quickTemplateDao()

    val defaultTemplates = listOf(
        "درود، در جلسه هستم. در اولین فرصت تماس می‌گیرم.",
        "سلام، پیام شما دریافت شد. بررسی و اطلاع می‌دهم.",
        "سلام، لطفاً تا ساعتی دیگر با من تماس بگیرید.",
        "ممنون، هماهنگ شد.",
        "لطفاً جزییات را پیامک کنید."
    )

    suspend fun ensureDefaultTemplates() {
        if (dao.count() == 0) {
            val list = defaultTemplates.map {
                QuickTemplateEntity(text = it, isDefault = true)
            }
            dao.insertAll(list)
        }
    }

    fun getAllTemplates(): Flow<List<QuickTemplateEntity>> = dao.getAllTemplates()

    suspend fun getAllList(): List<QuickTemplateEntity> = dao.getAllTemplatesList()

    suspend fun addTemplate(text: String): Long {
        return dao.insert(QuickTemplateEntity(text = text.trim()))
    }

    suspend fun useTemplate(id: Long) {
        dao.incrementUsage(id)
    }

    suspend fun deleteTemplate(id: Long) {
        dao.delete(id)
    }

    suspend fun restoreList(list: List<QuickTemplateEntity>) {
        dao.insertAll(list)
    }

    /**
     * تحلیل هوشمند پیام‌های ارسالی کاربر در تاریخچه گوشی جهت استخراج الگوهای متنی پرتکرار
     */
    suspend fun generateSmartTemplatesFromSentHistory(): List<String> = withContext(Dispatchers.IO) {
        val candidates = mutableMapOf<String, Int>()
        try {
            val uri = Telephony.Sms.Sent.CONTENT_URI
            val projection = arrayOf(Telephony.Sms.BODY)
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )
            cursor?.use {
                val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                var count = 0
                while (it.moveToNext() && count < 500) {
                    count++
                    val body = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                    val clean = body.replace(Regex("\\s+"), " ").trim()
                    // فیلتر: طول منطقی بین ۸ تا ۸۰ کاراکتر، بدون لینک و بدون رمز یک‌بارمصرف
                    if (clean.length in 8..80 &&
                        !clean.contains("http://") &&
                        !clean.contains("https://") &&
                        !clean.all { ch -> ch.isDigit() }
                    ) {
                        candidates[clean] = (candidates[clean] ?: 0) + 1
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // استخراج پیام‌هایی که حداقل ۲ بار تکرار شده‌اند یا غنی‌ترین پیام‌ها
        val sorted = candidates.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { text -> defaultTemplates.none { it.equals(text, ignoreCase = true) } }
            .take(5)

        sorted
    }

    suspend fun autoImportSmartTemplates(): Int = withContext(Dispatchers.IO) {
        val newTemplates = generateSmartTemplatesFromSentHistory()
        val current = getAllList().map { it.text.lowercase().trim() }.toSet()
        var added = 0
        for (item in newTemplates) {
            if (!current.contains(item.lowercase().trim())) {
                addTemplate(item)
                added++
            }
        }
        added
    }
}
