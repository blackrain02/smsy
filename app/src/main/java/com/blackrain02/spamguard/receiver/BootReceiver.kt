package com.blackrain02.spamguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blackrain02.spamguard.data.repository.ScheduledMessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val repo = ScheduledMessageRepository(context)
                    repo.rescheduleAllPending()
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
