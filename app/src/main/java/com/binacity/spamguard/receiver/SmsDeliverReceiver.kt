package com.binacity.spamguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.binacity.spamguard.ai.SpamClassifier
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.data.repository.SpamRepository
import com.binacity.spamguard.notification.NotificationHelper
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

                // گام ۰: مخاطبین دفترچه تلفن و لیست سفید کاربر هرگز اسپم نیستند
                if (smsRepo.isContact(sender) || spamRepo.isUserWhitelisted(sender)) {
                    smsRepo.saveToInbox(sender, fullBody, timestamp)
                    NotificationHelper.showMessageNotification(context, sender, fullBody)
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
                    smsRepo.saveToInbox(sender, fullBody, timestamp)
                    NotificationHelper.showMessageNotification(context, sender, fullBody)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
