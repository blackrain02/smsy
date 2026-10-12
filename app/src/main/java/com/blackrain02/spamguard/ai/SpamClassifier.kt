package com.blackrain02.spamguard.ai

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
    private val feedbackEngine = AdaptiveFeedbackEngine.getInstance(context)

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

        val isCommercial = isCommercialSender(sender)
        val hasOptOut = hasOptOutTrigger(body)
        val hasPromo = hasPromoTrigger(body)
        val hasLink = hasLinkTrigger(body)
        val feedbackBias = feedbackEngine.getFeedbackBias(body)

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

            // ترکیب هوشمندانه فاکتورهای پیامک‌های تبلیغاتی در ایران
            if (hasOptOut) {
                spamProb = maxOf(spamProb, 0.95f)
            } else if (isCommercial && (hasPromo || hasLink)) {
                spamProb = maxOf(spamProb, 0.88f)
            } else if (hasPromo && hasLink) {
                spamProb = maxOf(spamProb, 0.85f)
            } else if (isCommercial && spamProb >= 0.25f) {
                spamProb = maxOf(spamProb, 0.75f)
            }

            // اعمال ضریب یادگیری محلی فیدبک کاربر
            spamProb = (spamProb + feedbackBias).coerceIn(0.0f, 1.0f)

            val isSpam = if (feedbackBias <= -0.40f) {
                false // اعتماد قطعی محلی به دلیل سابقه خروج از قرنطینه توسط کاربر
            } else if (feedbackBias >= 0.40f) {
                true // اسپم قطعی محلی بر اساس تصحیح‌های دستی مکرر کاربر
            } else {
                spamProb >= 0.45f || hasOptOut || (isCommercial && (hasPromo || hasLink))
            }
            ClassificationResult(isSpam = isSpam, spamProbability = spamProb, isWhitelisted = false)
        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackSpam = hasOptOut || (isCommercial && (hasPromo || hasLink)) || (hasPromo && hasLink)
            ClassificationResult(
                isSpam = fallbackSpam,
                spamProbability = if (fallbackSpam) 0.90f else 0.0f,
                isWhitelisted = false
            )
        }
    }

    private fun isCommercialSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.replace("+98", "").replace("0098", "").trimStart('0').trim()
        
        // سرشماره‌های رسمی پیامک انبوه تبلیغاتی در ایران
        val isPrefixMatch = clean.startsWith("1000") || clean.startsWith("2000") || clean.startsWith("3000") ||
                clean.startsWith("5000") || clean.startsWith("9000") || clean.startsWith("021") ||
                clean.startsWith("21") || clean.startsWith("4040") || clean.startsWith("8080") ||
                clean.startsWith("9090")
        if (isPrefixMatch) return true

        // کدهای ارزش افزوده یا سرشماره‌های سازمانی کوتاه ۴ الی ۶ رقمی
        if (clean.length in 4..6 && clean.all { it.isDigit() }) return true

        // فرستنده‌های متنی تجاری/تبلیغاتی (نام‌های شرکتی به جز بانک‌ها که در وایت‌لیست هستند)
        if (sender.any { it.isLetter() }) return true

        return false
    }

    private fun hasOptOutTrigger(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        val optOutPatterns = listOf(
            "لغو 11", "لغو ۱۱", "لغو11", "لغو۱۱", "لغو: 11", "لغو: ۱۱", "لغو:11", "لغو:۱۱",
            "لغو با ۱۱", "لغو با 11", "لغو با ارسال", "ارسال 1 به", "ارسال ۱ به",
            "ارسال عدد", "لغو ۱", "لغو 1", "لغو اشتراک", "لغو پیامک", "لغو:", "لغو =",
            "لغو=", "لغو عدد", "عدد 1 را ارسال", "عدد ۱ را ارسال", "ارسال کد",
            "ارسال عدد 1", "ارسال عدد ۱", "لغو ارسال", "لغو تبلیغات"
        )
        return optOutPatterns.any { lower.contains(it) }
    }

    private fun hasPromoTrigger(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        val promoKeywords = listOf(
            "تخفیف", "off", "فروش ویژه", "جشنواره", "حراج", "تور ", "تورهای", "وام ", "اقساط", "قسطی",
            "کاشت مو", "کلینیک", "دندانپزشکی", "شارژ رایگان", "سیمکارت", "استخدام", "پیشنهاد ویژه",
            "شگفت انگیز", "شگفت‌انگیز", "خرید آنلاین", "خرید اینترنتی", "کد تخفیف", "مشاوره رایگان",
            "هدیه", "جایزه", "قرعه کشی", "ارسال رایگان", "فرصت محدود", "فقط امروز", "تا 50%", "تا ۵۰%",
            "درصد تخفیف", "پیک رایگان", "سفارش اول", "تخفیف ویژه", "ارزان", "بهترین قیمت", "افتتاحیه",
            "ثبت نام رایگان", "بیمه شخص ثالث", "تور لحظه آخری", "فروشگاه", "سوپرمارکت", "خرید با تخفیف"
        )
        return promoKeywords.any { lower.contains(it) }
    }

    private fun hasLinkTrigger(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        val linkIndicators = listOf(
            "http://", "https://", "b2n.ir", "yun.ir", "bit.ly", "rizy.ir",
            "zil.ink", "zarinp.al", "tapsi.link", "snapp.link", ".ir/", ".com/"
        )
        return linkIndicators.any { lower.contains(it) }
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
