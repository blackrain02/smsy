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
import com.binacity.spamguard.data.model.ContactItem
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

    private val pinnedPrefs = context.getSharedPreferences("smsy_pinned_threads", Context.MODE_PRIVATE)

    /**
     * کش حافظه‌ای سریع برای نگاشت شماره مخاطبین به نام‌ها جهت جلوگیری از افت سرعت UI
     */
    private var cachedContacts: Map<String, String>? = null
    private var cachedContactList: List<ContactItem>? = null
    private var lastContactCacheTime: Long = 0

    /**
     * ذخیره پیامک ورودی در دیتابیس رسمی سیستم‌عامل
     * با انتساب صریح Thread ID تا از چندگانگی گفتگو و باگ‌های خوانده‌نشده جلوگیری شود.
     */
    fun saveToInbox(sender: String, body: String, timestamp: Long, isRead: Boolean = false): Uri? {
        return try {
            val threadId = getOrCreateThreadId(sender)
            val values = ContentValues().apply {
                if (threadId > 0) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, if (isRead) 1 else 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * پیش‌بارگذاری تمام مخاطبین گوشی در حافظه رم با یک کوئری فوق‌سریع دسته‌ای (<30ms)
     */
    private fun getContactCache(): Map<String, String> {
        val now = System.currentTimeMillis()
        if (cachedContacts != null && (now - lastContactCacheTime) < 60_000) {
            return cachedContacts!!
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyMap()
        }

        val map = HashMap<String, String>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                if (numCol != -1 && nameCol != -1) {
                    while (it.moveToNext()) {
                        val num = it.getString(numCol) ?: continue
                        val name = it.getString(nameCol) ?: continue
                        val normalized = normalizePhoneNumber(num)
                        if (normalized.isNotBlank()) {
                            map[normalized] = name
                        }
                    }
                }
            }
            cachedContacts = map
            lastContactCacheTime = now
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return map
    }

    fun normalizePhoneNumber(phone: String): String {
        val digits = phone.replace(Regex("[^0-9+]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun getConversationKey(address: String): String {
        val norm = normalizePhoneNumber(address)
        return if (norm.length >= 7) norm else address.trim().lowercase()
    }

    /**
     * بررسی وضعیت سنجاق شدن یک گفتگو
     */
    fun isPinned(address: String): Boolean {
        val key = getConversationKey(address)
        val set = pinnedPrefs.getStringSet("pinned_keys", emptySet()) ?: emptySet()
        return set.contains(key)
    }

    /**
     * تغییر وضعیت سنجاق شدن (Pin/Unpin) یک گفتگو
     */
    fun togglePin(address: String): Boolean {
        val key = getConversationKey(address)
        val set = pinnedPrefs.getStringSet("pinned_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        val newState = if (set.contains(key)) {
            set.remove(key)
            false
        } else {
            set.add(key)
            true
        }
        pinnedPrefs.edit().putStringSet("pinned_keys", set).apply()
        return newState
    }

    private fun getPinnedKeys(): Set<String> {
        return pinnedPrefs.getStringSet("pinned_keys", emptySet()) ?: emptySet()
    }

    /**
     * دریافت لیست مخاطبین گوشی جهت انتخاب در ارسال پیام جدید
     */
    fun getContacts(query: String = ""): List<ContactItem> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val contacts: List<ContactItem> = if (cachedContactList != null && (now - lastContactCacheTime) < 60_000) {
            cachedContactList!!
        } else {
            val list = mutableListOf<ContactItem>()
            val seenNumbers = HashSet<String>()
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
                cursor?.use {
                    val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = if (nameCol != -1) it.getString(nameCol) ?: "" else ""
                        val num = if (numCol != -1) it.getString(numCol) ?: "" else ""
                        val cleanNum = num.replace(" ", "").replace("-", "")
                        if (cleanNum.isNotBlank() && !seenNumbers.contains(cleanNum)) {
                            seenNumbers.add(cleanNum)
                            list.add(ContactItem(name = name.ifBlank { num }, number = cleanNum))
                        }
                    }
                }
                cachedContactList = list
                lastContactCacheTime = now
                list
            } catch (e: Throwable) {
                e.printStackTrace()
                emptyList()
            }
        }

        if (query.isBlank()) {
            return contacts
        }

        val q = query.trim().lowercase()
        return contacts.filter {
            it.name.lowercase().contains(q) || it.number.contains(q)
        }
    }

    /**
     * دریافت لیست گفتگوها با معماری Single-Pass، کش مخاطبین و رفع باگ گفتگوهای تکراری
     */
    suspend fun getConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        val list = mutableListOf<Conversation>()
        val unreadMap = HashMap<String, Int>()
        val seenSenders = HashSet<String>()
        val contactsMap = getContactCache()
        val pinnedKeys = getPinnedKeys()

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            )

            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")
            cursor?.use {
                val threadIdCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                val readCol = it.getColumnIndex(Telephony.Sms.READ)

                if (threadIdCol == -1 || addressCol == -1) return@use

                while (it.moveToNext()) {
                    val address = it.getString(addressCol) ?: "Unknown"
                    if (address.isBlank()) continue

                    val convKey = getConversationKey(address)
                    val isRead = if (readCol != -1) it.getInt(readCol) == 1 else true

                    if (!isRead) {
                        unreadMap[convKey] = (unreadMap[convKey] ?: 0) + 1
                    }

                    // یکتا‌سازی بر اساس کلید شماره/فرستنده برای جلوگیری از تکرار گفتگوها در بازیابی یا تردهای موازی
                    if (!seenSenders.contains(convKey)) {
                        seenSenders.add(convKey)
                        val threadId = it.getLong(threadIdCol)
                        val snippet = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                        val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                        val contactName = contactsMap[normalizePhoneNumber(address)]
                        val isPinned = pinnedKeys.contains(convKey)

                        list.add(
                            Conversation(
                                threadId = threadId,
                                address = address,
                                contactName = contactName,
                                snippet = snippet,
                                date = date,
                                unreadCount = 0,
                                isPinned = isPinned
                            )
                        )
                    }
                }
            }

            // تخصیص مقادیر تجمیعی خوانده‌نشده
            for (i in list.indices) {
                val conv = list[i]
                val key = getConversationKey(conv.address)
                val count = unreadMap[key] ?: 0
                if (count > 0) {
                    list[i] = conv.copy(unreadCount = count)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // مرتب‌سازی: سنجاق‌شده‌ها در بالای لیست، سپس ترتیب نزولی تاریخ
        list.sortWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.date })
        list
    }

    /**
     * علامت‌گذاری پیام‌های یک گفتگو به عنوان خوانده‌شده
     */
    suspend fun markThreadAsRead(threadId: Long, address: String = "") = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            val norm = normalizePhoneNumber(address)
            if (address.isNotBlank() && norm.length >= 7) {
                context.contentResolver.update(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values,
                    "(${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} LIKE ?) AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString(), "%$norm")
                )
            } else {
                context.contentResolver.update(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * حذف یک گفتگوی کامل
     */
    suspend fun deleteConversation(threadId: Long, address: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val norm = normalizePhoneNumber(address)
            val count = if (address.isNotBlank() && norm.length >= 7) {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} LIKE ?",
                    arrayOf(threadId.toString(), "%$norm")
                )
            } else {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString())
                )
            }
            count > 0
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * حذف یک پیامک منفرد
     */
    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString())
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
     * دریافت پیام‌های یک گفتگوی مشخص با یکپارچه‌سازی تمام تردهای مرتبط با مخاطب
     */
    suspend fun getMessages(threadId: Long, address: String = ""): List<Message> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Message>()
        val seenMessageIds = HashSet<Long>()
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val norm = normalizePhoneNumber(address)
            val selection: String
            val selectionArgs: Array<String>

            if (address.isNotBlank() && norm.length >= 7) {
                selection = "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
                selectionArgs = arrayOf(threadId.toString(), "%$norm")
            } else if (address.isNotBlank()) {
                selection = "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?"
                selectionArgs = arrayOf(threadId.toString(), address)
            } else {
                selection = "${Telephony.Sms.THREAD_ID} = ?"
                selectionArgs = arrayOf(threadId.toString())
            }

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
                    if (id != 0L && seenMessageIds.contains(id)) continue
                    if (id != 0L) seenMessageIds.add(id)

                    val msgAddress = if (addressCol != -1) it.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                    val type = if (typeCol != -1) it.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isRead = if (readCol != -1) it.getInt(readCol) == 1 else true

                    list.add(
                        Message(
                            id = id,
                            threadId = threadId,
                            address = msgAddress,
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

            val threadId = getOrCreateThreadId(address)

            // ذخیره در جدول پیام‌های ارسالی با انتساب threadId
            val values = ContentValues().apply {
                if (threadId > 0) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
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
                    if (body.isNotBlank()) {
                        messagesToProcess.add(Triple(id, address, body))
                    }
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

    fun getContactName(phoneNumber: String): String? {
        val map = getContactCache()
        return map[normalizePhoneNumber(phoneNumber)]
    }
}
