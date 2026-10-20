package com.blackrain02.spamguard.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدیریت سرشماره‌ها و نام‌های مجاز جهت استخراج تراکنش‌های مالی و بانکی
 * کاربر می‌تواند هر مخاطب یا سرشماره را به عنوان پیامک بانکی تعیین یا حذف کند.
 */
class BankSenderRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smsy_bank_senders", Context.MODE_PRIVATE)
    private val _bankSendersFlow = MutableStateFlow<Set<String>>(emptySet())
    val bankSendersFlow: StateFlow<Set<String>> = _bankSendersFlow.asStateFlow()

    companion object {
        private const val KEY_BANK_SENDERS = "designated_bank_senders"

        // سرشماره‌ها یا نام‌های متداول بانکی به عنوان مقدار اولیه پیشنهادی
        val DEFAULT_BANK_HINTS = setOf(
            "+989999987641", // بلوبانک
            "989999987641",
            "blubank",
            "بلوبانک",
            "bankmellat",
            "mellat",
            "بانک ملت",
            "bmi",
            "melli",
            "بانک ملی",
            "bsi",
            "saderat",
            "بانک صادرات",
            "tejarat",
            "بانک تجارت",
            "saman",
            "بانک سامان",
            "pasargad",
            "بانک پاسارگاد",
            "sepah",
            "بانک سپه",
            "keshavarzi",
            "بانک کشاورزی",
            "maskan",
            "بانک مسکن",
            "refah",
            "بانک رفاه",
            "resalat",
            "بانک رسالت",
            "qmb",
            "مهر ایران",
            "wepod",
            "ویپاد"
        )
    }

    init {
        loadSenders()
    }

    private fun loadSenders() {
        val saved = prefs.getStringSet(KEY_BANK_SENDERS, null)
        if (saved == null) {
            _bankSendersFlow.value = DEFAULT_BANK_HINTS
            prefs.edit().putStringSet(KEY_BANK_SENDERS, DEFAULT_BANK_HINTS).apply()
        } else {
            _bankSendersFlow.value = saved
        }
    }

    fun getBankSenders(): Set<String> {
        return _bankSendersFlow.value
    }

    fun isBankSender(address: String?, contactName: String? = null): Boolean {
        if (address.isNullOrBlank() && contactName.isNullOrBlank()) return false
        val senders = _bankSendersFlow.value
        val cleanAddr = address?.replace(" ", "")?.replace("-", "")?.lowercase() ?: ""
        val cleanName = contactName?.trim()?.lowercase() ?: ""

        return senders.any { s ->
            val cleanS = s.replace(" ", "").replace("-", "").lowercase()
            (cleanAddr.isNotEmpty() && (cleanAddr == cleanS || cleanAddr.endsWith(cleanS) || cleanS.endsWith(cleanAddr))) ||
            (cleanName.isNotEmpty() && (cleanName == cleanS || cleanName.contains(cleanS)))
        }
    }

    fun addBankSender(addressOrName: String) {
        val current = _bankSendersFlow.value.toMutableSet()
        current.add(addressOrName.trim())
        prefs.edit().putStringSet(KEY_BANK_SENDERS, current).apply()
        _bankSendersFlow.value = current
    }

    fun removeBankSender(addressOrName: String) {
        val current = _bankSendersFlow.value.toMutableSet()
        val trimmed = addressOrName.trim()
        val cleanTarget = trimmed.replace(" ", "").replace("-", "").lowercase()
        current.removeAll { s ->
            val cleanS = s.replace(" ", "").replace("-", "").lowercase()
            cleanS == cleanTarget || s.equals(trimmed, ignoreCase = true)
        }
        prefs.edit().putStringSet(KEY_BANK_SENDERS, current).apply()
        _bankSendersFlow.value = current
    }

    fun toggleBankSender(addressOrName: String): Boolean {
        val trimmed = addressOrName.trim()
        val exists = isBankSender(trimmed)
        return if (exists) {
            removeBankSender(trimmed)
            false
        } else {
            addBankSender(trimmed)
            true
        }
    }
}
