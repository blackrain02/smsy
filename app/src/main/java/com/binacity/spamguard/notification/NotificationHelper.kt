package com.binacity.spamguard.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.binacity.spamguard.ui.MainActivity

object NotificationHelper {
    private const val CHANNEL_MESSAGES_ID = "channel_clean_messages"
    private const val CHANNEL_MESSAGES_NAME = "پیام‌های ورودی"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان پیامک‌های سالم و عادی"
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showMessageNotification(context: Context, sender: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("sender", sender)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val groupKey = "sms_group_" + sender.trim()
        val summaryId = sender.hashCode()
        val messageId = (System.currentTimeMillis() % 1000000000).toInt()

        // اعلان پیام مجزای این سرشماره داخل گروه
        val messageNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setGroup(groupKey)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // اعلان تجمیعی سرشماره جهت گروه‌بندی خودکار در پنل اعلان‌ها
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(sender))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(messageId, messageNotification)
        manager.notify(summaryId, summaryNotification)
    }
}
