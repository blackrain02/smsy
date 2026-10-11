package com.binacity.spamguard.ai

import android.content.Context
import org.json.JSONObject

class WhitelistFilter(context: Context) {
    private val safeSenders = mutableListOf<String>()
    private val safeKeywords = mutableListOf<String>()

    init {
        try {
            val jsonString = context.assets.open("whitelist_rules.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            
            val sendersArray = obj.getJSONArray("senders")
            for (i in 0 until sendersArray.length()) {
                safeSenders.add(sendersArray.getString(i).lowercase())
            }

            val keywordsArray = obj.getJSONArray("keywords")
            for (i in 0 until keywordsArray.length()) {
                safeKeywords.add(keywordsArray.getString(i).lowercase())
            }
        } catch (e: Exception) {
            // مقادیر پشتیبان در صورت بروز خطا در خواندن فایل
            safeKeywords.addAll(listOf("رمز پویا", "رمز یکبار مصرف", "کد تایید", "کد ورود", "واریز به حساب", "برداشت از حساب"))
        }
    }

    /**
     * آیا پیامک مربوط به تراکنش‌های بانکی، کدهای احراز هویت یا سرشماره‌های حیاتی است؟
     */
    fun isSafe(sender: String?, body: String?): Boolean {
        if (body.isNullOrBlank()) return true

        val lowerSender = sender?.lowercase() ?: ""
        val lowerBody = body.lowercase()

        // بررسی سرشماره یا نام فرستنده
        for (safeSender in safeSenders) {
            if (lowerSender.contains(safeSender)) return true
        }

        // بررسی کلیدواژه‌های حیاتی
        for (kw in safeKeywords) {
            if (lowerBody.contains(kw)) return true
        }

        return false
    }
}
