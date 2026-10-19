package com.blackrain02.spamguard.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.blackrain02.spamguard.data.repository.SmsRepository
import com.blackrain02.spamguard.notification.NotificationHelper
import com.blackrain02.spamguard.ui.theme.AppLanguage
import com.blackrain02.spamguard.ui.theme.ThemePreferences
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
                if (sender.isNotBlank()) {
                    NotificationHelper.cancelForSender(context, sender)
                } else if (notificationId != -1) {
                    notificationManager.cancel(notificationId)
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
                        NotificationHelper.cancelForSender(context, sender)
                    }
                }
            }

            NotificationHelper.ACTION_COPY_OTP -> {
                val otpCode = intent.getStringExtra(NotificationHelper.EXTRA_OTP_CODE) ?: ""
                if (otpCode.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", otpCode))
                    ThemePreferences.init(context)
                    val isFa = ThemePreferences.currentLanguage.value == AppLanguage.FA
                    val msg = if (isFa) "کد تایید $otpCode کپی شد" else "Verification code $otpCode copied"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (notificationId != -1) {
                        notificationManager.cancel(notificationId)
                    }
                }
            }
        }
    }
}
