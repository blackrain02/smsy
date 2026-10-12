package com.binacity.spamguard.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sender = intent.getStringExtra(NotificationHelper.EXTRA_SENDER) ?: ""
        val threadId = intent.getLongExtra(NotificationHelper.EXTRA_THREAD_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            NotificationHelper.ACTION_MARK_READ -> {
                if (notificationId != -1) {
                    notificationManager.cancel(notificationId)
                }
                if (sender.isNotBlank()) {
                    notificationManager.cancel(sender.hashCode())
                }
                val smsRepo = SmsRepository(context)
                smsRepo.markThreadAsReadAsync(threadId, sender)
            }

            NotificationHelper.ACTION_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank() && sender.isNotBlank()) {
                    val smsRepo = SmsRepository(context)
                    scope.launch {
                        val success = smsRepo.sendMessage(sender, replyText)
                        smsRepo.markThreadAsReadAsync(threadId, sender)
                        if (notificationId != -1) {
                            notificationManager.cancel(notificationId)
                        }
                        notificationManager.cancel(sender.hashCode())
                    }
                }
            }

            NotificationHelper.ACTION_COPY_OTP -> {
                val otpCode = intent.getStringExtra(NotificationHelper.EXTRA_OTP_CODE) ?: ""
                if (otpCode.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", otpCode))
                    Toast.makeText(context, "کد تایید $otpCode کپی شد", Toast.LENGTH_SHORT).show()
                    if (notificationId != -1) {
                        notificationManager.cancel(notificationId)
                    }
                }
            }
        }
    }
}
