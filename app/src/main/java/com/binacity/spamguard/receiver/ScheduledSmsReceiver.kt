package com.binacity.spamguard.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.binacity.spamguard.data.repository.ScheduledMessageRepository
import com.binacity.spamguard.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduledSmsReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_scheduled_message_id"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0) return

        val scheduledRepo = ScheduledMessageRepository(context)
        val smsRepo = SmsRepository(context)

        scope.launch {
            val scheduled = scheduledRepo.getById(messageId) ?: return@launch
            if (scheduled.status != "PENDING") return@launch

            val success = smsRepo.sendMessage(scheduled.address, scheduled.body, scheduled.subId)
            if (success) {
                scheduledRepo.markAsSent(messageId)
                showSentNotification(context, scheduled.address, scheduled.body)
            } else {
                scheduledRepo.markAsFailed(messageId)
            }
        }
    }

    private fun showSentNotification(context: Context, address: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, "channel_clean_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("پیام زمان‌بندی‌شده ارسال شد")
            .setContentText("به: $address\n$body")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 1000000).toInt(), notif)
    }
}
