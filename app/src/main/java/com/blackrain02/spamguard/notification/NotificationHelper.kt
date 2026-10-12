package com.blackrain02.spamguard.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.blackrain02.spamguard.receiver.NotificationActionReceiver
import com.blackrain02.spamguard.ui.MainActivity
import com.blackrain02.spamguard.ui.util.OtpHelper

object NotificationHelper {
    const val CHANNEL_MESSAGES_ID = "channel_clean_messages"
    const val CHANNEL_MESSAGES_NAME = "پیام‌های ورودی"

    const val ACTION_MARK_READ = "com.blackrain02.spamguard.ACTION_MARK_READ"
    const val ACTION_REPLY = "com.blackrain02.spamguard.ACTION_REPLY"
    const val ACTION_COPY_OTP = "com.blackrain02.spamguard.ACTION_COPY_OTP"
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_SENDER = "extra_sender"
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_OTP_CODE = "extra_otp_code"

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

    fun showMessageNotification(context: Context, sender: String, body: String, threadId: Long = -1L) {
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

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setGroup(groupKey)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // ۱. اکشن هوشمند کپی رمز یک‌بارمصرف (در صورت تشخیص OTP)
        val otpCode = OtpHelper.extractOtp(body)
        if (otpCode != null) {
            val copyOtpIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COPY_OTP
                putExtra(EXTRA_OTP_CODE, otpCode)
                putExtra(EXTRA_NOTIFICATION_ID, messageId)
            }
            val copyOtpPending = PendingIntent.getBroadcast(
                context,
                messageId + 1,
                copyOtpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_save,
                "کپی کد $otpCode",
                copyOtpPending
            )
        }

        // ۲. اکشن پاسخ مستقیم (Inline Reply)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("پاسخ...")
            .build()
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_ID, messageId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            messageId + 2,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "پاسخ",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()
        builder.addAction(replyAction)

        // ۳. اکشن علامت‌گذاری به عنوان خوانده‌شده (Mark as Read)
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_ID, messageId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            messageId + 3,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.checkbox_on_background,
            "خوانده شد",
            markReadPendingIntent
        )

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

        try {
            manager.notify(messageId, builder.build())
            manager.notify(summaryId, summaryNotification)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
