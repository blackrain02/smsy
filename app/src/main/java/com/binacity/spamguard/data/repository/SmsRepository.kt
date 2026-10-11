package com.binacity.spamguard.data.repository

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.binacity.spamguard.ai.SpamClassifier
import com.binacity.spamguard.data.model.Conversation
import com.binacity.spamguard.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val quarantined: Int
)

class SmsRepository(private val context: Context) {

    /**
     * ذخیره پیامک ورودی در دیتابیس رسمی سیستم‌عامل
     */
    fun saveToInbox(sender: String, body: String, timestamp: Long): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * دریافت لیست گفتگوها به همراه تعداد پیام‌های خوانده‌نشده و تاریخ دقیق
     * کاملاً پایدار در برابر تفاوت‌های ساختاری رام‌های مختلف (مانند One UI سامسونگ)
     */
    suspend fun getConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        val list = mutableListOf<Conversation>()
        val unreadMap = HashMap<Long, Int>()

        // ۱. استخراج امن تعداد پیام‌های خوانده‌نشده
        try {
            val unreadCursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.READ} = 0",
                null,
                null
            )
            unreadCursor?.use {
                val tCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                if (tCol != -1) {
                    while (it.moveToNext()) {
                        val tid = it.getLong(tCol)
                        unreadMap[tid] = (unreadMap[tid] ?: 0) + 1
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // ۲. کوئری لیست گفتگوها از جدول رسمی مکالمات
        try {
            val uri = Telephony.Sms.Conversations.CONTENT_URI
            // بدون پروجکشن سخت‌گیرانه برای جلوگیری از SQLiteException در One UI
            val cursor = context.contentResolver.query(uri, null, null, null, "date DESC")
            cursor?.use {
                val idCol = it.getColumnIndex("_id").takeIf { c -> c != -1 }
                    ?: it.getColumnIndex("thread_id").takeIf { c -> c != -1 }
                    ?: it.getColumnIndex(Telephony.Sms.Conversations._ID)

                val snippetCol = it.getColumnIndex("snippet").takeIf { c -> c != -1 }
                    ?: it.getColumnIndex(Telephony.Sms.Conversations.SNIPPET)

                val dateCol = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val threadId = if (idCol != -1) it.getLong(idCol) else -1L
                    if (threadId == -1L) continue

                    val snippet = if (snippetCol != -1) it.getString(snippetCol) ?: "" else ""
                    val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                    val address = getAddressForThread(threadId)
                    val contactName = getContactName(address)
                    val unreadCount = unreadMap[threadId] ?: 0

                    list.add(
                        Conversation(
                            threadId = threadId,
                            address = address,
                            contactName = contactName,
                            snippet = snippet,
                            date = date,
                            unreadCount = unreadCount
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // ۳. راهکار جایگزین (Fallback): در صورتی که جدول conversations در رامی خالی یا غیرقابل دسترس بود
        if (list.isEmpty()) {
            try {
                val smsCursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms.THREAD_ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE
                    ),
                    null,
                    null,
                    "date DESC"
                )
                smsCursor?.use {
                    val threadIdCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                    val seenThreads = HashSet<Long>()

                    while (it.moveToNext()) {
                        val threadId = if (threadIdCol != -1) it.getLong(threadIdCol) else continue
                        if (seenThreads.contains(threadId)) continue
                        seenThreads.add(threadId)

                        val address = if (addressCol != -1) it.getString(addressCol) ?: "Unknown" else "Unknown"
                        val snippet = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                        val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                        val contactName = getContactName(address)
                        val unreadCount = unreadMap[threadId] ?: 0

                        list.add(
                            Conversation(
                                threadId = threadId,
                                address = address,
                                contactName = contactName,
                                snippet = snippet,
                                date = date,
                                unreadCount = unreadCount
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        list
    }

    /**
     * علامت‌گذاری پیام‌های یک گفتگو به عنوان خوانده‌شده
     */
    suspend fun markThreadAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * حذف یک گفتگوی کامل
     */
    suspend fun deleteConversation(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            count > 0
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * دریافت یا ایجاد شناسه گفتگو برای شماره جدید
     */
    fun getOrCreateThreadId(address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
    }

    /**
     * دریافت پیام‌های یک گفتگوی مشخص
     */
    suspend fun getMessages(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Message>()
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(threadId.toString())

            val cursor = context.contentResolver.query(uri, null, selection, selectionArgs, "date ASC")
            cursor?.use {
                val idCol = it.getColumnIndex(Telephony.Sms._ID)
                val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)
                val readCol = it.getColumnIndex(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val id = if (idCol != -1) it.getLong(idCol) else 0L
                    val address = if (addressCol != -1) it.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                    val type = if (typeCol != -1) it.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isRead = if (readCol != -1) it.getInt(readCol) == 1 else true

                    list.add(
                        Message(
                            id = id,
                            threadId = threadId,
                            address = address,
                            body = body,
                            date = date,
                            isSent = (type == Telephony.Sms.MESSAGE_TYPE_SENT),
                            isRead = isRead
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        list
    }

    /**
     * ارسال پیامک و ذخیره خودکار در Sent Messages
     */
    suspend fun sendMessage(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }

            // ذخیره در جدول پیام‌های ارسالی
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * اسکن و پالایش دسته‌ای تمام پیامک‌های قدیمی موجود در گوشی با موتور ONNX
     */
    suspend fun scanAndCleanHistoricalSms(
        spamRepository: SpamRepository,
        classifier: SpamClassifier,
        onProgress: (ScanProgress) -> Unit
    ): ScanProgress = withContext(Dispatchers.IO) {
        val messagesToProcess = mutableListOf<Triple<Long, String, String>>()
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY
            )

            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idCol = it.getColumnIndex(Telephony.Sms._ID)
                val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)

                while (it.moveToNext()) {
                    val id = if (idCol != -1) it.getLong(idCol) else continue
                    val address = if (addressCol != -1) it.getString(addressCol) ?: "Unknown" else "Unknown"
                    val body = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                    messagesToProcess.add(Triple(id, address, body))
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        val total = messagesToProcess.size
        var scanned = 0
        var quarantined = 0

        for (item in messagesToProcess) {
            val (id, address, body) = item
            try {
                if (spamRepository.isUserWhitelisted(address)) {
                    scanned++
                    onProgress(ScanProgress(scanned = scanned, total = total, quarantined = quarantined))
                    continue
                }
                val result = classifier.classify(address, body)
                if (result.isSpam) {
                    spamRepository.quarantine(address, body, result.spamProbability)
                    context.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms._ID} = ?",
                        arrayOf(id.toString())
                    )
                    quarantined++
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            scanned++
            onProgress(ScanProgress(scanned = scanned, total = total, quarantined = quarantined))
        }

        ScanProgress(scanned = scanned, total = total, quarantined = quarantined)
    }

    private fun getAddressForThread(threadId: Long): String {
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "date DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val col = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    if (col != -1) it.getString(col) ?: "Unknown" else "Unknown"
                } else "Unknown"
            } ?: "Unknown"
        } catch (e: Throwable) {
            "Unknown"
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank() || phoneNumber == "Unknown") return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val col = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (col != -1) it.getString(col) else null
                } else null
            }
        } catch (e: Throwable) {
            null
        }
    }
}
