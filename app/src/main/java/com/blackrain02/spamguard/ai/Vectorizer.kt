package com.blackrain02.spamguard.ai

import android.content.Context
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.sqrt

class Vectorizer(context: Context) {
    private val vocabMap = HashMap<String, Int>()
    private val idfList = ArrayList<Float>()
    val numFeatures: Int

    init {
        val jsonString = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(jsonString)

        val vocabObj = obj.getJSONObject("vocabulary")
        val keys = vocabObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            vocabMap[key] = vocabObj.getInt(key)
        }

        val idfArray = obj.getJSONArray("idf")
        for (i in 0 until idfArray.length()) {
            idfList.add(idfArray.getDouble(i).toFloat())
        }

        numFeatures = idfList.size
    }

    /**
     * تبدیل متن نرمال‌شده به بردار TF-IDF سازگار با مدل scikit-learn
     */
    fun transform(text: String): FloatArray {
        val vector = FloatArray(numFeatures)
        if (text.isBlank() || numFeatures == 0) return vector

        val tokens = text.split(" ").filter { it.isNotBlank() }
        val termCounts = HashMap<String, Int>()

        // 1-grams
        for (token in tokens) {
            termCounts[token] = (termCounts[token] ?: 0) + 1
        }

        // 2-grams
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]} ${tokens[i + 1]}"
            termCounts[bigram] = (termCounts[bigram] ?: 0) + 1
        }

        // محاسبه TF-IDF با ویژگی Sublinear TF (1 + ln(tf))
        var sumSquares = 0.0f
        for ((term, count) in termCounts) {
            val idx = vocabMap[term]
            if (idx != null && idx < numFeatures) {
                val tf = 1.0f + ln(count.toDouble()).toFloat()
                val tfidf = tf * idfList[idx]
                vector[idx] = tfidf
                sumSquares += tfidf * tfidf
            }
        }

        // L2 Normalization (همانند scikit-learn)
        if (sumSquares > 0.0f) {
            val norm = sqrt(sumSquares.toDouble()).toFloat()
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }
}
