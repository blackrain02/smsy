package com.blackrain02.spamguard

import android.app.Application
import com.blackrain02.spamguard.ai.SpamClassifier
import com.blackrain02.spamguard.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpamGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        // آماده‌سازی اولیه نشست ONNX در ترد پس‌زمینه (Zero Cold Start)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SpamClassifier.getInstance(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
