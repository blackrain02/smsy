package com.binacity.spamguard.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.binacity.spamguard.data.local.AppDatabase
import com.binacity.spamguard.data.local.ScheduledMessageEntity
import com.binacity.spamguard.receiver.ScheduledSmsReceiver
import kotlinx.coroutines.flow.Flow

class ScheduledMessageRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).scheduledMessageDao()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun getPendingMessages(): Flow<List<ScheduledMessageEntity>> = dao.getPendingMessages()

    fun getMessagesForAddress(address: String): Flow<List<ScheduledMessageEntity>> = dao.getMessagesForAddress(address)

    suspend fun scheduleMessage(address: String, body: String, scheduledTime: Long, subId: Int? = null): Long {
        val entity = ScheduledMessageEntity(
            address = address,
            body = body,
            scheduledTime = scheduledTime,
            subId = subId,
            status = "PENDING"
        )
        val id = dao.insert(entity)
        setAlarm(id, scheduledTime)
        return id
    }

    suspend fun cancelScheduledMessage(id: Long) {
        val item = dao.getById(id)
        if (item != null) {
            dao.update(item.copy(status = "CANCELLED"))
            cancelAlarm(id)
        }
    }

    suspend fun markAsSent(id: Long) {
        val item = dao.getById(id)
        if (item != null) {
            dao.update(item.copy(status = "SENT"))
        }
    }

    suspend fun markAsFailed(id: Long) {
        val item = dao.getById(id)
        if (item != null) {
            dao.update(item.copy(status = "FAILED"))
        }
    }

    suspend fun getById(id: Long): ScheduledMessageEntity? = dao.getById(id)

    private fun setAlarm(id: Long, scheduledTime: Long) {
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            putExtra(ScheduledSmsReceiver.EXTRA_MESSAGE_ID, id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun cancelAlarm(id: Long) {
        val intent = Intent(context, ScheduledSmsReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    suspend fun getAllList(): List<ScheduledMessageEntity> = dao.getAllList()
}
