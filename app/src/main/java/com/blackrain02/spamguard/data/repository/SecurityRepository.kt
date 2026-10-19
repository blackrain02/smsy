package com.blackrain02.spamguard.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.blackrain02.spamguard.data.model.Conversation
import java.security.MessageDigest
import java.util.UUID

data class PinVerificationResult(
    val isValid: Boolean,
    val isDecoy: Boolean
)

class SecurityRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smsy_security_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_DECOY_PIN_HASH = "decoy_pin_hash"
        private const val KEY_DECOY_PIN_SALT = "decoy_pin_salt"
        private const val KEY_LOCKED_KEYS = "locked_keys"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout_minutes"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        // وضعیت نشست فعال در حافظه رم پروسه
        private var sessionAuthTime: Long = 0L
        var isDecoySession: Boolean = false
            private set
    }

    val isDecoySession: Boolean get() = Companion.isDecoySession

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
     * اعتبارسنجی رمز عبور وارد شده (اصلی)
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        return hashPin(pin, storedSalt) == storedHash
    }

    /**
     * آیا پین اضطراری/فریب (Decoy PIN) تنظیم شده است؟
     */
    fun isDecoyPinSet(): Boolean {
        return !prefs.getString(KEY_DECOY_PIN_HASH, null).isNullOrBlank()
    }

    /**
     * تنظیم پین اضطراری/فریب جهت نمایش محتوای خالی تحت اجبار
     */
    fun setDecoyPin(pin: String) {
        val salt = UUID.randomUUID().toString().take(8)
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_DECOY_PIN_HASH, hash)
            .putString(KEY_DECOY_PIN_SALT, salt)
            .apply()
    }

    /**
     * حذف پین اضطراری
     */
    fun removeDecoyPin() {
        prefs.edit()
            .remove(KEY_DECOY_PIN_HASH)
            .remove(KEY_DECOY_PIN_SALT)
            .apply()
    }

    /**
     * اعتبارسنجی پین با پشتیبانی خودکار از پین واقعی و پین اضطراری
     */
    fun verifyPinWithDecoy(pin: String): PinVerificationResult? {
        if (verifyPin(pin)) {
            return PinVerificationResult(isValid = true, isDecoy = false)
        }
        if (isDecoyPinSet()) {
            val storedDecoyHash = prefs.getString(KEY_DECOY_PIN_HASH, null)
            val storedDecoySalt = prefs.getString(KEY_DECOY_PIN_SALT, "") ?: ""
            if (storedDecoyHash != null && hashPin(pin, storedDecoySalt) == storedDecoyHash) {
                return PinVerificationResult(isValid = true, isDecoy = true)
            }
        }
        return null
    }

    /**
     * ثبت احراز هویت نشست جاری
     */
    fun authenticateSession(isDecoy: Boolean = false) {
        sessionAuthTime = System.currentTimeMillis()
        Companion.isDecoySession = isDecoy
    }

    /**
     * تجدید زمان فعالیت نشست
     */
    fun touchAuth() {
        if (sessionAuthTime > 0L) {
            sessionAuthTime = System.currentTimeMillis()
        }
    }

    /**
     * خروج و ابطال نشست احراز هویت
     */
    fun invalidateSession() {
        sessionAuthTime = 0L
        Companion.isDecoySession = false
    }

    /**
     * بررسی معتبر بودن نشست احراز هویت با احتساب زمان انقضا
     */
    fun isSessionValid(): Boolean {
        if (sessionAuthTime <= 0L) return false
        val timeoutMin = getAutoLockTimeoutMinutes()
        if (timeoutMin <= 0) return false // حالت قفل آنی
        val elapsed = System.currentTimeMillis() - sessionAuthTime
        return elapsed < (timeoutMin * 60 * 1000L)
    }

    /**
     * زمان قفل خودکار نشست به دقیقه (۰ = خروج آنی، ۱ = یک دقیقه، ۵ = پنج دقیقه)
     */
    fun getAutoLockTimeoutMinutes(): Int {
        return prefs.getInt(KEY_AUTO_LOCK_TIMEOUT, 1)
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, minutes).apply()
    }

    /**
     * وضعیت محافظت از اسکرین‌شات و پیش‌نمایش در فهرست برنامه‌های اخیر (FLAG_SECURE)
     */
    fun isFlagSecureEnabled(): Boolean {
        return prefs.getBoolean(KEY_FLAG_SECURE, true)
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLAG_SECURE, enabled).apply()
    }

    /**
     * وضعیت فعال بودن احراز هویت اثر انگشت
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * حذف کامل رمز عبور و باز کردن تمام قفل‌ها
     */
    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_DECOY_PIN_HASH)
            .remove(KEY_DECOY_PIN_SALT)
            .remove(KEY_LOCKED_KEYS)
            .apply()
        invalidateSession()
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
