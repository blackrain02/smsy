package com.binacity.spamguard.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.binacity.spamguard.data.repository.SmsRepository

class SmsDeliveryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_DELIVERED = "com.binacity.spamguard.ACTION_SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (msgId <= 0) return

        val isSuccess = resultCode == Activity.RESULT_OK
        val smsRepo = SmsRepository(context)
        smsRepo.updateMessageDeliveryStatus(msgId, if (isSuccess) 2 else 3)
    }
}
