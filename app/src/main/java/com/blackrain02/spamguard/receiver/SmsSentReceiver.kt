package com.blackrain02.spamguard.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsSentReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SMS_SENT = "com.blackrain02.spamguard.ACTION_SMS_SENT"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0) return

        val code = resultCode
        scope.launch {
            try {
                val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
                val values = ContentValues().apply {
                    if (code == Activity.RESULT_OK) {
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                    } else {
                        // خطا در ارسال به دکل مخابراتی / نبود اعتبار / حالت پرواز
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                    }
                }
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
