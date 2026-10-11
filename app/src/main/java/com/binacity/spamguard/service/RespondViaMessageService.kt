package com.binacity.spamguard.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            // کامپوننت اجباری سیستم‌عامل برای ارسال پیامک سریع هنگام رد تماس تلفنی
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
