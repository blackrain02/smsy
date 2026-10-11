package com.binacity.spamguard.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

data class ClassificationResult(
    val isSpam: Boolean,
    val spamProbability: Float,
    val isWhitelisted: Boolean
)

class SpamClassifier private constructor(context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val vectorizer = Vectorizer(context)
    private val whitelistFilter = WhitelistFilter(context)

    init {
        val modelBytes = context.assets.open("spam_classifier.onnx").readBytes()
        val opts = OrtSession.SessionOptions().apply {
            setInterOpNumThreads(1)
            setIntraOpNumThreads(1)
        }
        session = env.createSession(modelBytes, opts)
    }

    @Synchronized
    fun classify(sender: String?, body: String?): ClassificationResult {
        // ۱. گارد امنیتی: اگر فرستنده یا متن مربوط به بانک/OTP باشد، مستقیماً تایید می‌شود
        if (whitelistFilter.isSafe(sender, body)) {
            return ClassificationResult(isSpam = false, spamProbability = 0.0f, isWhitelisted = true)
        }

        // ۲. نرمال‌سازی و تبدیل متن به بردار ویژگی‌های TF-IDF
        val normalized = TextNormalizer.normalize(body)
        val vector = vectorizer.transform(normalized)

        // ۳. استنتاج سریع در نشست ONNX (<2ms)
        return try {
            val buffer = FloatBuffer.wrap(vector)
            val tensor = OnnxTensor.createTensor(env, buffer, longArrayOf(1, vector.size.toLong()))
            
            val result = session.run(mapOf("float_input" to tensor))
            
            @Suppress("UNCHECKED_CAST")
            val probList = result[1].value as List<Map<Long, Float>>
            var spamProb = probList.firstOrNull()?.get(1L) ?: 0.0f
            
            tensor.close()
            result.close()

            val isCommercial = isCommercialSender(sender)
            val hasExplicitAd = hasExplicitAdTriggers(body)

            // تقویت وزن هوش مصنوعی با نشانگرهای رسمی مخابراتی پیامک‌های تبلیغاتی در ایران
            if (hasExplicitAd) {
                spamProb = maxOf(spamProb, 0.88f)
            } else if (isCommercial && spamProb >= 0.40f) {
                spamProb = maxOf(spamProb, 0.75f)
            }

            val isSpam = spamProb >= 0.55f || hasExplicitAd
            ClassificationResult(isSpam = isSpam, spamProbability = spamProb, isWhitelisted = false)
        } catch (e: Exception) {
            e.printStackTrace()
            val hasExplicitAd = hasExplicitAdTriggers(body)
            ClassificationResult(isSpam = hasExplicitAd, spamProbability = if (hasExplicitAd) 0.9f else 0.0f, isWhitelisted = false)
        }
    }

    private fun isCommercialSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.replace("+98", "").replace("0098", "").trimStart('0')
        return clean.startsWith("1000") || clean.startsWith("2000") || clean.startsWith("3000") ||
               clean.startsWith("5000") || clean.startsWith("9000") || clean.startsWith("021") ||
               (clean.length in 4..6 && clean.all { it.isDigit() })
    }

    private fun hasExplicitAdTriggers(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        val optOutPatterns = listOf("لغو 11", "لغو ۱۱", "لغو با ۱۱", "لغو با 11", "لغو: 11", "لغو: ۱۱", "لغو با ارسال", "ارسال 1 به", "ارسال ۱ به", "ارسال عدد")
        if (optOutPatterns.any { lower.contains(it) }) return true

        val promoKeywords = listOf("تخفیف", "فروش ویژه", "جشنواره", "حراج", "تور ", "وام ", "اقساط", "کاشت مو", "کلینیک", "دندانپزشکی", "شارژ رایگان", "سیمکارت", "استخدام")
        var matches = 0
        for (kw in promoKeywords) {
            if (lower.contains(kw)) matches++
        }
        return matches >= 2
    }

    companion object {
        @Volatile
        private var instance: SpamClassifier? = null

        fun getInstance(context: Context): SpamClassifier {
            return instance ?: synchronized(this) {
                instance ?: SpamClassifier(context.applicationContext).also { instance = it }
            }
        }
    }
}
