package com.blackrain02.spamguard.ai

import android.content.Context
import com.blackrain02.spamguard.data.local.AppDatabase
import com.blackrain02.spamguard.data.local.SpamFeedbackDao
import com.blackrain02.spamguard.data.local.SpamFeedbackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * موتور یادگیری تطبیقی بر روی دستگاه (On-Device Feedback Loop)
 * با دریافت فیدبک کاربر (انتقال به قرنطینه یا خروج از آن)، وزن توکن‌ها را تنظیم کرده
 * و خطاهای تکراری مدل را بدون نیاز به سرور تصحیح می‌کند.
 */
class AdaptiveFeedbackEngine private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val feedbackDao: SpamFeedbackDao = db.spamFeedbackDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // حافظه کش فوق‌سریع در رم جهت دسترسی در کمتر از 0.1 میلی‌ثانیه حین ارزیابی پیام
    private val biasCache = ConcurrentHashMap<String, Float>()
    private val stopWords = setOf(
        "برای", "این", "است", "شد", "که", "به", "از", "با", "در", "را", "و", "یا",
        "تا", "یک", "رو", "هم", "نه", "اگر", "بر", "اما", "پس", "شود", "می", "دیگر",
        "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "for", "of", "or"
    )

    init {
        scope.launch {
            loadCache()
        }
    }

    private suspend fun loadCache() {
        try {
            val list = feedbackDao.getAll()
            for (entity in list) {
                biasCache[entity.token] = entity.biasScore
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * استخراج کلمات کلیدی موثر از متن
     */
    fun extractTokens(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val normalized = TextNormalizer.normalize(text)
        return normalized.split(Regex("[\\s\\p{Punct}]+"))
            .map { it.trim().lowercase() }
            .filter { token ->
                token.length in 3..25 &&
                        !token.all { it.isDigit() } &&
                        !stopWords.contains(token)
            }
            .distinct()
    }

    /**
     * ثبت فیدبک هرزنامه (کاربر پیام را دستی به قرنطینه فرستاد)
     */
    suspend fun recordSpamFeedback(body: String?) {
        val tokens = extractTokens(body)
        if (tokens.isEmpty()) return

        for (token in tokens) {
            val current = feedbackDao.getByToken(token) ?: SpamFeedbackEntity(token = token)
            val newSpamCount = current.spamCount + 1
            val newScore = calculateScore(newSpamCount, current.hamCount)
            val updated = current.copy(spamCount = newSpamCount, biasScore = newScore)
            feedbackDao.upsert(updated)
            biasCache[token] = newScore
        }
    }

    /**
     * ثبت فیدبک پیام سالم (کاربر پیام را از قرنطینه خارج کرد)
     */
    suspend fun recordHamFeedback(body: String?) {
        val tokens = extractTokens(body)
        if (tokens.isEmpty()) return

        for (token in tokens) {
            val current = feedbackDao.getByToken(token) ?: SpamFeedbackEntity(token = token)
            val newHamCount = current.hamCount + 1
            val newScore = calculateScore(current.spamCount, newHamCount)
            val updated = current.copy(hamCount = newHamCount, biasScore = newScore)
            feedbackDao.upsert(updated)
            biasCache[token] = newScore
        }
    }

    /**
     * محاسبه ضریب بایاس مجموع برای متن ورودی
     */
    fun getFeedbackBias(body: String?): Float {
        val tokens = extractTokens(body)
        if (tokens.isEmpty() || biasCache.isEmpty()) return 0.0f

        var total = 0.0f
        for (token in tokens) {
            val score = biasCache[token] ?: 0.0f
            total += score
        }
        return total.coerceIn(-0.85f, 0.85f)
    }

    /**
     * فرمول محاسبه امتیاز بایاس: امتیاز منفی = گرایش به سالم، مثبت = گرایش به اسپم
     */
    fun calculateScore(spamCount: Int, hamCount: Int): Float {
        val diff = (spamCount - hamCount).coerceIn(-10, 10)
        return (diff * 0.12f).coerceIn(-0.60f, 0.60f)
    }

    companion object {
        @Volatile
        private var instance: AdaptiveFeedbackEngine? = null

        fun getInstance(context: Context): AdaptiveFeedbackEngine {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveFeedbackEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
