package com.blackrain02.spamguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.blackrain02.spamguard.ai.SpamClassifier
import com.blackrain02.spamguard.data.repository.BlocklistRepository
import com.blackrain02.spamguard.data.repository.SmsRepository
import com.blackrain02.spamguard.data.repository.SpamRepository
import com.blackrain02.spamguard.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        val pendingResult = goAsync()

        scope.launch {
            try {
                val spamRepo = SpamRepository(context)
                val smsRepo = SmsRepository(context)
                val blocklistRepo = BlocklistRepository(context)

                // گام ۰: اگر شماره در فهرست سیاه قطعی (Blocklist) باشد، مستقیماً به قرنطینه می‌رود
                if (blocklistRepo.isBlocked(sender)) {
                    spamRepo.quarantine(sender, fullBody, 1.0f)
                    return@launch
                }

                // گام ۱: مخاطبین دفترچه تلفن و لیست سفید کاربر هرگز اسپم نیستند
                if (smsRepo.isContact(sender) || spamRepo.isUserWhitelisted(sender)) {
                    val uri = smsRepo.saveToInbox(sender, fullBody, timestamp)
                    val threadId = smsRepo.getOrCreateThreadId(sender)
                    val bankRepo = com.blackrain02.spamguard.data.repository.BankTransactionRepository(context)
                    bankRepo.recordIfBankMessage(sender, fullBody, timestamp)
                    NotificationHelper.showMessageNotification(context, sender, fullBody, threadId)
                    return@launch
                }

                // ارزیابی با هوش مصنوعی و لیست سفید
                val classifier = SpamClassifier.getInstance(context)
                val result = classifier.classify(sender, fullBody)

                if (result.isSpam) {
                    // پیامک تبلیغاتی: بدون ثبت در اینباکس سیستمی و بدون اعلان به قرنطینه می‌رود
                    spamRepo.quarantine(sender, fullBody, result.spamProbability)
                } else {
                    // پیامک عادی/بانکی: ذخیره در پیامک‌های رسمی گوشی و ارسال اعلان
                    val uri = smsRepo.saveToInbox(sender, fullBody, timestamp)
                    val threadId = smsRepo.getOrCreateThreadId(sender)
                    val bankRepo = com.blackrain02.spamguard.data.repository.BankTransactionRepository(context)
                    bankRepo.recordIfBankMessage(sender, fullBody, timestamp)
                    NotificationHelper.showMessageNotification(context, sender, fullBody, threadId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
