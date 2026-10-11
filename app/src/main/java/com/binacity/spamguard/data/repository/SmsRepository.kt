package com.binacity.spamguard.data.repository

import android.Manifest
import android.content.ContentUris
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val quarantined: Int
)

class SmsRepository(private val context: Context) {

    private val pinnedPrefs = context.getSharedPreferences("smsy_pinned_threads", Context.MODE_PRIVATE)
    private val readThreadsPrefs = context.getSharedPreferences("smsy_read_threads", Context.MODE_PRIVATE)
    private val aliasRepo = SenderAliasRepository(context)
    private val recentlyReadKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val aliasRepository: SenderAliasRepository get() = aliasRepo

    fun markKeyAsReadInMemory(address: String, mergedAddresses: Set<String> = emptySet(), aliasId: String? = null) {
        val keys = mutableSetOf<String>()
        if (address.isNotBlank()) keys.add(getConversationKey(address))
        for (m in mergedAddresses) {
            if (m.isNotBlank()) keys.add(getConversationKey(m))
        }
        if (aliasId != null) {
            keys.add("alias_$aliasId")
        }
        recentlyReadKeys.addAll(keys)

        val now = System.currentTimeMillis()
        val editor = readThreadsPrefs.edit()
        for (k in keys) {
            editor.putLong(k, now)
        }
        editor.apply()
    }

    fun markThreadAsReadAsync(threadId: Long, address: String = "", mergedAddresses: Set<String> = emptySet(), aliasId: String? = null) {
        markKeyAsReadInMemory(address, mergedAddresses, aliasId)
        repoScope.launch {
            markThreadAsRead(threadId, address, mergedAddresses)
        }
    }

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
                put(Telephony.Sms.SEEN, if (isRead) 1 else 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            val result = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            if (!isRead) {
                val key = getConversationKey(sender)
                recentlyReadKeys.remove(key)
                readThreadsPrefs.edit().remove(key).apply()
            }
            result
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
     * آیا این شماره در دفترچه تلفن گوشی ثبت است؟ (جهت جلوگیری قطعی از اسپم شدن)
     */
    fun isContact(phoneNumber: String): Boolean {
        val map = getContactCache()
        val norm = normalizePhoneNumber(phoneNumber)
        return !map[norm].isNullOrBlank()
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
     * دریافت لیست مخاطبین گوشی جهت انتخاب و جستجو در ارسال پیام جدید
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
     * دریافت لیست گفتگوها با پشتیبانی از ادغام سرشماره‌ها، نام‌گذاری اختصاصی و سنجاق
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
        val allAliases = aliasRepo.getAllAliases()

        // ایندکس‌گذاری سریع نام‌های مستعار و گروه‌ها در O(1) به جای جستجوی خطی تکراری
        val exactAliasMap = HashMap<String, SenderGroupAlias>()
        val normalizedAliasMap = HashMap<String, SenderGroupAlias>()
        for (alias in allAliases) {
            for (num in alias.numbers) {
                exactAliasMap[num.trim().lowercase()] = alias
                val norm = normalizePhoneNumber(num)
                if (norm.length >= 7) {
                    normalizedAliasMap[norm] = alias
                }
            }
        }

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

                    val addressLower = address.trim().lowercase()
                    val addressNorm = normalizePhoneNumber(address)
                    val matchedAlias = exactAliasMap[addressLower] ?: if (addressNorm.length >= 7) normalizedAliasMap[addressNorm] else null

                    val convKey = if (matchedAlias != null) "alias_${matchedAlias.id}" else getConversationKey(address)
                    val isRead = if (readCol != -1) it.getInt(readCol) == 1 else true

                    if (!isRead) {
                        val addrKey = getConversationKey(address)
                        val lastReadTime = maxOf(
                            readThreadsPrefs.getLong(convKey, 0L),
                            readThreadsPrefs.getLong(addrKey, 0L)
                        )
                        val msgDate = if (dateCol != -1) it.getLong(dateCol) else 0L
                        if (lastReadTime == 0L || msgDate > lastReadTime) {
                            unreadMap[convKey] = (unreadMap[convKey] ?: 0) + 1
                        }
                    }

                    // یکتا‌سازی بر اساس کلید گفتگو (یا گروه ادغام)
                    if (!seenSenders.contains(convKey)) {
                        seenSenders.add(convKey)
                        val threadId = it.getLong(threadIdCol)
                        val snippet = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                        val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                        val contactName = matchedAlias?.displayName ?: contactsMap[addressNorm]
                        val isPinned = pinnedKeys.contains(convKey) || pinnedKeys.contains(getConversationKey(address))

                        list.add(
                            Conversation(
                                threadId = threadId,
                                address = address,
                                contactName = contactName,
                                snippet = snippet,
                                date = date,
                                unreadCount = 0,
                                isPinned = isPinned,
                                mergedAddresses = matchedAlias?.numbers ?: emptySet(),
                                aliasId = matchedAlias?.id
                            )
                        )
                    }
                }
            }

            // تخصیص مقادیر تجمیعی خوانده‌نشده با اولویت پاک‌سازی فوری و پایدار
            for (i in list.indices) {
                val conv = list[i]
                val key = if (conv.aliasId != null) "alias_${conv.aliasId}" else getConversationKey(conv.address)
                val addrKey = getConversationKey(conv.address)
                val lastReadTime = maxOf(
                    readThreadsPrefs.getLong(key, 0L),
                    readThreadsPrefs.getLong(addrKey, 0L),
                    conv.mergedAddresses.maxOfOrNull { readThreadsPrefs.getLong(getConversationKey(it), 0L) } ?: 0L
                )

                val isRecentlyRead = recentlyReadKeys.contains(key) ||
                        recentlyReadKeys.contains(addrKey) ||
                        conv.mergedAddresses.any { recentlyReadKeys.contains(getConversationKey(it)) } ||
                        (lastReadTime > 0 && lastReadTime >= conv.date)

                if (isRecentlyRead) {
                    list[i] = conv.copy(unreadCount = 0)
                } else {
                    val count = unreadMap[key] ?: 0
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
     * علامت‌گذاری پیام‌های یک گفتگو به عنوان خوانده‌شده به همراه بروزرسانی SEEN و پشتیبانی از ادغام
     */
    suspend fun markThreadAsRead(threadId: Long, address: String = "", mergedAddresses: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        try {
            markKeyAsReadInMemory(address, mergedAddresses)

            val targets = if (mergedAddresses.isNotEmpty()) mergedAddresses else if (address.isNotBlank()) setOf(address) else emptySet()

            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }

            // 1. علامت‌گذاری صریح از طریق سیستم Conversation Provider اندروید
            if (threadId > 0) {
                try {
                    context.contentResolver.update(
                        android.net.Uri.parse("content://sms/conversations/$threadId"),
                        values,
                        null,
                        null
                    )
                } catch (ignored: Throwable) {}
                try {
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.THREAD_ID} = ?",
                        arrayOf(threadId.toString())
                    )
                } catch (ignored: Throwable) {}
            }

            // 2. استخراج Primary Key تمام پیامک‌های خوانده‌نشده مرتبط جهت بروزرسانی قطعی با ContentUris
            val unreadIds = mutableListOf<Long>()
            try {
                val idCursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.THREAD_ID),
                    "${Telephony.Sms.READ} = 0 OR ${Telephony.Sms.READ} IS NULL",
                    null,
                    null
                )
                idCursor?.use { c ->
                    val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                    val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val thIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                    while (c.moveToNext()) {
                        val msgId = if (idIdx != -1) c.getLong(idIdx) else -1L
                        val msgAddr = if (addrIdx != -1) c.getString(addrIdx) ?: "" else ""
                        val msgTh = if (thIdx != -1) c.getLong(thIdx) else -1L

                        val matchesThread = threadId > 0 && msgTh == threadId
                        val matchesAddr = targets.any { t ->
                            t.equals(msgAddr, ignoreCase = true) ||
                            (t.isNotBlank() && getConversationKey(t) == getConversationKey(msgAddr))
                        }

                        if (matchesThread || matchesAddr) {
                            if (msgId > 0) unreadIds.add(msgId)
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            // بروزرسانی مستقیم تک‌تک آیدی‌ها با URI اختصاصی (بدون هیچ خطای SQLite OEM)
            for (msgId in unreadIds) {
                try {
                    val itemUri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, msgId)
                    context.contentResolver.update(itemUri, values, null, null)
                } catch (ignored: Throwable) {}
                try {
                    val inboxItemUri = ContentUris.withAppendedId(Telephony.Sms.Inbox.CONTENT_URI, msgId)
                    context.contentResolver.update(inboxItemUri, values, null, null)
                } catch (ignored: Throwable) {}
            }

            // 3. بروزرسانی تکمیلی بر اساس تطبیق شماره و سرشماره‌های کوتاه (Shortcodes)
            for (target in targets) {
                try {
                    context.contentResolver.update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        "${Telephony.Sms.ADDRESS} = ?",
                        arrayOf(target)
                    )
                } catch (ignored: Throwable) {}

                val norm = normalizePhoneNumber(target)
                if (norm.length >= 7) {
                    try {
                        context.contentResolver.update(
                            Telephony.Sms.CONTENT_URI,
                            values,
                            "${Telephony.Sms.ADDRESS} LIKE ?",
                            arrayOf("%$norm")
                        )
                    } catch (ignored: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * انتقال یک گفتگوی کامل به همراه تمام پیام‌های آن به قرنطینه اسپم و حذف از صندوق ورودی
     */
    suspend fun quarantineConversation(
        threadId: Long,
        address: String = "",
        mergedAddresses: Set<String> = emptySet(),
        spamRepository: SpamRepository
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val messages = getMessages(threadId, address, mergedAddresses)
            for (msg in messages) {
                val s = if (msg.address.isNotBlank()) msg.address else address
                spamRepository.quarantineMessage(
                    sender = s,
                    body = msg.body,
                    timestamp = msg.date,
                    probability = 1.0f
                )
            }
            deleteConversation(threadId, address, mergedAddresses)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * حذف یک گفتگوی کامل یا تمام سرشماره‌های ادغام‌شده آن
     */
    suspend fun deleteConversation(threadId: Long, address: String = "", mergedAddresses: Set<String> = emptySet()): Boolean = withContext(Dispatchers.IO) {
        try {
            val targets = if (mergedAddresses.isNotEmpty()) mergedAddresses else if (address.isNotBlank()) setOf(address) else emptySet()
            val whereParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (threadId > 0) {
                whereParts.add("${Telephony.Sms.THREAD_ID} = ?")
                args.add(threadId.toString())
            }

            for (target in targets) {
                whereParts.add("${Telephony.Sms.ADDRESS} = ?")
                args.add(target)
                val norm = normalizePhoneNumber(target)
                if (norm.length >= 7) {
                    whereParts.add("${Telephony.Sms.ADDRESS} LIKE ?")
                    args.add("%$norm")
                }
            }

            val selection = if (whereParts.isNotEmpty()) whereParts.joinToString(" OR ") else "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = if (whereParts.isNotEmpty()) args.toTypedArray() else arrayOf(threadId.toString())

            val count = context.contentResolver.delete(Telephony.Sms.CONTENT_URI, selection, selectionArgs)
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
     * دریافت پیام‌های یک گفتگوی مشخص با پشتیبانی کامل از ادغام سرشماره‌های هم‌نام
     */
    suspend fun getMessages(threadId: Long, address: String = "", mergedAddresses: Set<String> = emptySet()): List<Message> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Message>()
        val seenMessageIds = HashSet<Long>()
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val targets = if (mergedAddresses.isNotEmpty()) mergedAddresses else if (address.isNotBlank()) setOf(address) else emptySet()

            val whereParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (threadId > 0) {
                whereParts.add("${Telephony.Sms.THREAD_ID} = ?")
                args.add(threadId.toString())
            }

            for (target in targets) {
                whereParts.add("${Telephony.Sms.ADDRESS} = ?")
                args.add(target)
                val norm = normalizePhoneNumber(target)
                if (norm.length >= 7) {
                    whereParts.add("${Telephony.Sms.ADDRESS} LIKE ?")
                    args.add("%$norm")
                }
            }

            val selection = if (whereParts.isNotEmpty()) whereParts.joinToString(" OR ") else "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = if (whereParts.isNotEmpty()) args.toTypedArray() else arrayOf(threadId.toString())

            val cursor = context.contentResolver.query(uri, null, selection, selectionArgs, "date ASC")
            cursor?.use {
                val idCol = it.getColumnIndex(Telephony.Sms._ID)
                val threadCol = it.getColumnIndex(Telephony.Sms.THREAD_ID)
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
                    val msgThreadId = if (threadCol != -1) it.getLong(threadCol) else threadId
                    val body = if (bodyCol != -1) it.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                    val type = if (typeCol != -1) it.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isRead = if (readCol != -1) it.getInt(readCol) == 1 else true

                    list.add(
                        Message(
                            id = id,
                            threadId = msgThreadId,
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
                put(Telephony.Sms.SEEN, 1)
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
     * اسکن و پالایش دسته‌ای تمام پیامک‌های قدیمی با مصونیت ۱۰۰٪ مخاطبین دفترچه تلفن
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
                // مصونیت قطعی: شماره‌های موجود در مخاطبین یا لیست سفید هرگز اسپم نیستند
                if (isContact(address) || spamRepository.isUserWhitelisted(address)) {
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
        val alias = aliasRepo.getAliasForAddress(phoneNumber)
        if (alias != null) return alias.displayName

        val map = getContactCache()
        return map[normalizePhoneNumber(phoneNumber)]
    }
}
