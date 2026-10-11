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
            // بهینه‌سازی نخ‌های پردازش برای حفظ شارژ باتری موبایل
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
            
            // خروجی دوم مدل رگرسیون لجستیک: نگاشت احتمالات کلاس‌ها
            @Suppress("UNCHECKED_CAST")
            val probList = result[1].value as List<Map<Long, Float>>
            val spamProb = probList.firstOrNull()?.get(1L) ?: 0.0f
            
            tensor.close()
            result.close()

            // اگر احتمال اسپم بیش از ۷۵٪ باشد، پیامک هرزنامه تلقی می‌شود
            val isSpam = spamProb >= 0.75f
            ClassificationResult(isSpam = isSpam, spamProbability = spamProb, isWhitelisted = false)
        } catch (e: Exception) {
            e.printStackTrace()
            // اصل برائت: در صورت بروز خطای استنتاج، پیامک سالم فرض می‌شود تا چیزی حذف نشود
            ClassificationResult(isSpam = false, spamProbability = 0.0f, isWhitelisted = false)
        }
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
