package com.blackrain02.spamguard.service

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import com.blackrain02.spamguard.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RespondViaMessageService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val uri: Uri? = intent.data
            val recipient = uri?.schemeSpecificPart?.let {
                val qIndex = it.indexOf('?')
                if (qIndex != -1) it.substring(0, qIndex) else it
            } ?: ""

            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra("android.intent.extra.TEXT")
                ?: uri?.getQueryParameter("body")
                ?: ""

            if (recipient.isNotBlank() && text.isNotBlank()) {
                val smsRepo = SmsRepository(applicationContext)
                scope.launch {
                    try {
                        smsRepo.sendMessage(recipient, text)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
