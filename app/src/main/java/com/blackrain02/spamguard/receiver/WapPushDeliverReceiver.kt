package com.blackrain02.spamguard.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import com.blackrain02.spamguard.ui.theme.AppLanguage
import com.blackrain02.spamguard.ui.theme.ThemePreferences

class WapPushDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            val mimeType = intent.type
            if (mimeType == "application/vnd.wap.mms-message") {
                val subId = intent.getIntExtra(
                    SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                )

                ThemePreferences.init(context)
                val isFa = ThemePreferences.currentLanguage.value == AppLanguage.FA
                val title = if (isFa) "پیام چندرسانه‌ای جدید (MMS)" else "New Multimedia Message (MMS)"
                val content = if (isFa) "یک پیام چندرسانه‌ای (MMS) دریافت شد." else "An incoming MMS message was received."

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notif = NotificationCompat.Builder(context, "channel_clean_messages")
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                manager.notify(777000 + (System.currentTimeMillis() % 10000).toInt(), notif)
            }
        }
    }
}
