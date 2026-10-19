package com.blackrain02.spamguard.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.blackrain02.spamguard.data.repository.SecurityRepository
import com.blackrain02.spamguard.data.repository.SenderAliasRepository
import com.blackrain02.spamguard.receiver.NotificationActionReceiver
import com.blackrain02.spamguard.ui.MainActivity
import com.blackrain02.spamguard.ui.theme.AppLanguage
import com.blackrain02.spamguard.ui.theme.ThemePreferences
import com.blackrain02.spamguard.ui.util.OtpHelper
import java.util.concurrent.ConcurrentHashMap

object NotificationHelper {
    const val CHANNEL_MESSAGES_ID = "channel_clean_messages"
    const val CHANNEL_MESSAGES_NAME = "پیام‌های ورودی"

    const val APP_NOTIFICATION_GROUP = "com.blackrain02.spamguard.SMS_GROUP"
    const val GROUP_SUMMARY_ID = 99999

    const val ACTION_MARK_READ = "com.blackrain02.spamguard.ACTION_MARK_READ"
    const val ACTION_REPLY = "com.blackrain02.spamguard.ACTION_REPLY"
    const val ACTION_COPY_OTP = "com.blackrain02.spamguard.ACTION_COPY_OTP"
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_SENDER = "extra_sender"
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_OTP_CODE = "extra_otp_code"

    // تاریخچه پیام‌های خوانده‌نشده هر سرشماره جهت استک شدن زیر هم با NotificationCompat.MessagingStyle
    private val recentMessagesPerSender = ConcurrentHashMap<String, MutableList<Pair<String, Long>>>()

    fun getNotificationId(sender: String): Int {
        return (Math.abs(sender.trim().hashCode()) % 80000) + 1000
    }

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

    /**
     * بستن و حذف تمام اعلان‌های برنامه با باز شدن برنامه یا خوانده‌شدن کامل
     */
    fun cancelAll(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
            recentMessagesPerSender.clear()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * بستن اعلان‌های یک سرشماره مشخص هنگام باز کردن چت آن
     */
    fun cancelForSender(context: Context, sender: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(getNotificationId(sender))
            recentMessagesPerSender.remove(sender)
            if (recentMessagesPerSender.isEmpty()) {
                manager.cancel(GROUP_SUMMARY_ID)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun showMessageNotification(context: Context, sender: String, body: String, threadId: Long = -1L) {
        ThemePreferences.init(context)
        val isFa = ThemePreferences.currentLanguage.value == AppLanguage.FA

        // ۱. بررسی حریم خصوصی و قفل بودن گفتگو
        val securityRepo = SecurityRepository(context)
        val aliasRepo = SenderAliasRepository(context)
        val alias = aliasRepo.getAliasForAddress(sender)
        val lockKey = securityRepo.getLockKey(sender, alias?.id)
        val isLocked = securityRepo.isConversationLocked(lockKey)

        val displaySender = alias?.displayName ?: sender
        val displayBody = if (isLocked) {
            if (isFa) "پیام جدید دریافت شد (قفل شده)" else "New message received (Locked)"
        } else {
            body
        }

        // ۲. تجمیع پیامک‌های این سرشماره جهت استک شدن در MessagingStyle
        val senderList = recentMessagesPerSender.getOrPut(sender) { mutableListOf() }
        synchronized(senderList) {
            senderList.add(displayBody to System.currentTimeMillis())
            if (senderList.size > 20) {
                senderList.removeAt(0)
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("sender", sender)
            putExtra("address", sender)
            if (threadId != -1L) {
                putExtra("thread_id", threadId)
            }
        }
        val notificationId = getNotificationId(sender)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val userPerson = Person.Builder().setName("Me").build()
        val senderPerson = Person.Builder().setName(displaySender).build()
        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(displaySender)
            .setGroupConversation(false)

        synchronized(senderList) {
            for (item in senderList) {
                messagingStyle.addMessage(item.first, item.second, senderPerson)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(displaySender)
            .setContentText(displayBody)
            .setStyle(messagingStyle)
            .setGroup(APP_NOTIFICATION_GROUP)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // فقط در صورتی که قفل نباشد اکشن‌های کپی رمز و پاسخ سریع اضافه شود
        if (!isLocked) {
            val otpCode = OtpHelper.extractOtp(body)
            if (otpCode != null) {
                val copyOtpIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_COPY_OTP
                    putExtra(EXTRA_OTP_CODE, otpCode)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
                val copyOtpPending = PendingIntent.getBroadcast(
                    context,
                    notificationId + 1,
                    copyOtpIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_menu_save,
                    if (isFa) "کپی کد $otpCode" else "Copy Code $otpCode",
                    copyOtpPending
                )
            }

            // Inline Reply
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(if (isFa) "پاسخ..." else "Reply...")
                .build()
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_REPLY
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                if (isFa) "پاسخ" else "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()
            builder.addAction(replyAction)
        }

        // Action Mark Read
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.checkbox_on_background,
            if (isFa) "خوانده شد" else "Mark as read",
            markReadPendingIntent
        )

        // اعلان خلاصه گروه سراسری برنامه (تضمین تجمیع تمام نوتیفیکیشن‌ها زیر یک عنوان و دسته در سیستم‌عامل)
        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Smsy")
            .setContentText(if (isFa) "پیام‌های ورودی جدید" else "New incoming messages")
            .setGroup(APP_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(notificationId, builder.build())
            manager.notify(GROUP_SUMMARY_ID, summaryNotification)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
