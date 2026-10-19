package com.blackrain02.spamguard.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.blackrain02.spamguard.data.model.Conversation
import java.security.MessageDigest
import java.util.UUID

class SecurityRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smsy_security_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_LOCKED_KEYS = "locked_keys"
    }

    /**
     * آیا کاربر رمز عبور ۴ رقمی تعیین کرده است؟
     */
    fun isPinSet(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    /**
     * تنظیم و ذخیره رمز عبور جدید به صورت هش شده (Salted SHA-256)
     */
    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString().take(8)
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .apply()
    }

    /**
     * اعتبارسنجی رمز عبور وارد شده
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        return hashPin(pin, storedSalt) == storedHash
    }

    /**
     * حذف کامل رمز عبور و باز کردن تمام قفل‌ها
     */
    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_LOCKED_KEYS)
            .apply()
    }

    /**
     * بررسی وضعیت قفل بودن یک گفتگو بر اساس کلید یکتا (پشتیبانی کامل از گروه‌های ادغام شده)
     */
    fun isConversationLocked(lockKey: String): Boolean {
        if (!isPinSet()) return false
        val set = prefs.getStringSet(KEY_LOCKED_KEYS, emptySet()) ?: emptySet()
        return set.contains(lockKey)
    }

    /**
     * قفل کردن یا باز کردن قفل یک گفتگو
     */
    fun setConversationLocked(lockKey: String, locked: Boolean) {
        val currentSet = (prefs.getStringSet(KEY_LOCKED_KEYS, emptySet()) ?: emptySet()).toMutableSet()
        if (locked) {
            currentSet.add(lockKey)
        } else {
            currentSet.remove(lockKey)
        }
        prefs.edit().putStringSet(KEY_LOCKED_KEYS, currentSet).apply()
    }

    /**
     * دریافت کلید قفل برای یک گفتگو (اگر ادغام شده باشد شناسه گروه وگرنه شماره نرمال شده)
     */
    fun getLockKey(conv: Conversation): String {
        return getLockKey(conv.address, conv.aliasId)
    }

    /**
     * ایجاد کلید قفل با تضمین یکپارچگی سرشماره‌های مرج‌شده
     */
    fun getLockKey(address: String, aliasId: String? = null): String {
        return if (!aliasId.isNullOrBlank()) {
            "alias:$aliasId"
        } else {
            "addr:${normalizeAddress(address)}"
        }
    }

    private fun normalizeAddress(address: String): String {
        val digits = address.replace(Regex("[^0-9+]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else address.trim().lowercase()
    }

    private fun hashPin(pin: String, salt: String): String {
        val bytes = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
