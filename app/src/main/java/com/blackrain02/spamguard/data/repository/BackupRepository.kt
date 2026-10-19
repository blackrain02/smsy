package com.blackrain02.spamguard.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.blackrain02.spamguard.data.local.BlocklistEntity
import com.blackrain02.spamguard.data.local.QuickTemplateEntity
import com.blackrain02.spamguard.ui.theme.BubblePalette
import com.blackrain02.spamguard.ui.theme.ThemeMode
import com.blackrain02.spamguard.ui.theme.ThemePreferences
import com.blackrain02.spamguard.ui.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupRepository(
    private val context: Context,
    private val blocklistRepo: BlocklistRepository,
    private val aliasRepo: SenderAliasRepository,
    private val templateRepo: TemplateRepository,
    private val securityRepo: SecurityRepository = SecurityRepository(context)
) {

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun encryptPayload(plainJson: String, pin: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject()
        envelope.put("appName", "Smsy")
        envelope.put("encrypted", true)
        envelope.put("version", 2)
        envelope.put("createdAt", System.currentTimeMillis())
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        envelope.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        return envelope.toString(2)
    }

    private fun decryptPayload(envelope: JSONObject, pin: String): String {
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Smsy")
        root.put("createdAt", System.currentTimeMillis())

        // 1. سرشماره‌های مسدود (Blocklist)
        val blockedList = blocklistRepo.getAllList()
        val blockedArray = JSONArray()
        for (b in blockedList) {
            val obj = JSONObject()
            obj.put("sender", b.sender)
            obj.put("reason", b.reason)
            obj.put("blockedAt", b.blockedAt)
            blockedArray.put(obj)
        }
        root.put("blocklist", blockedArray)

        // 2. گروه‌های ادغام و نام‌های سفارشی فرستنده‌ها (Aliases)
        val aliases = aliasRepo.getAllAliases()
        val aliasArray = JSONArray()
        for (a in aliases) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("displayName", a.displayName)
            val numArr = JSONArray()
            a.numbers.forEach { numArr.put(it) }
            obj.put("numbers", numArr)
            aliasArray.put(obj)
        }
        root.put("aliases", aliasArray)

        // 3. پاسخ‌های آماده (Templates)
        val templates = templateRepo.getAllList()
        val templateArray = JSONArray()
        for (t in templates) {
            val obj = JSONObject()
            obj.put("text", t.text)
            obj.put("isDefault", t.isDefault)
            obj.put("usageCount", t.usageCount)
            templateArray.put(obj)
        }
        root.put("templates", templateArray)

        // 4. تنظیمات برنامه (Preferences)
        val prefsObj = JSONObject()
        prefsObj.put("isSolarCalendar", DateUtils.isSolarCalendar(context))
        prefsObj.put("themeMode", ThemePreferences.getThemeMode(context).name)
        prefsObj.put("bubblePalette", ThemePreferences.getBubblePalette(context).name)
        prefsObj.put("fontScale", ThemePreferences.getFontScale(context).toDouble())
        root.put("preferences", prefsObj)

        root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String, pin: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputJson = JSONObject(jsonString)
            if (!inputJson.has("appName") || inputJson.getString("appName") != "Smsy") {
                return@withContext Result.failure(Exception("Invalid backup file: Not a Smsy backup"))
            }

            val finalJsonString = if (inputJson.optBoolean("encrypted", false)) {
                if (pin.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("ENCRYPTED_BACKUP_REQUIRES_PIN"))
                }
                try {
                    decryptPayload(inputJson, pin)
                } catch (e: Throwable) {
                    return@withContext Result.failure(Exception("Incorrect PIN or corrupted backup file"))
                }
            } else {
                jsonString
            }

            val root = JSONObject(finalJsonString)

            var restoredBlocked = 0
            var restoredAliases = 0
            var restoredTemplates = 0

            // بازیابی فهرست سیاه
            if (root.has("blocklist")) {
                val blockedArr = root.getJSONArray("blocklist")
                val list = mutableListOf<BlocklistEntity>()
                for (i in 0 until blockedArr.length()) {
                    val obj = blockedArr.getJSONObject(i)
                    list.add(
                        BlocklistEntity(
                            sender = obj.getString("sender"),
                            reason = obj.optString("reason", "Manual Block"),
                            blockedAt = obj.optLong("blockedAt", System.currentTimeMillis())
                        )
                    )
                }
                blocklistRepo.restoreList(list)
                restoredBlocked = list.size
            }

            // بازیابی گروه‌های ادغام
            if (root.has("aliases")) {
                val aliasArr = root.getJSONArray("aliases")
                for (i in 0 until aliasArr.length()) {
                    val obj = aliasArr.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("displayName")
                    val numbers = mutableSetOf<String>()
                    val numArr = obj.getJSONArray("numbers")
                    for (j in 0 until numArr.length()) {
                        numbers.add(numArr.getString(j))
                    }
                    aliasRepo.saveAlias(displayName = name, numbers = numbers, existingId = id)
                    restoredAliases++
                }
            }

            // بازیابی پاسخ‌های آماده
            if (root.has("templates")) {
                val templArr = root.getJSONArray("templates")
                val list = mutableListOf<QuickTemplateEntity>()
                for (i in 0 until templArr.length()) {
                    val obj = templArr.getJSONObject(i)
                    list.add(
                        QuickTemplateEntity(
                            text = obj.getString("text"),
                            isDefault = obj.optBoolean("isDefault", false),
                            usageCount = obj.optInt("usageCount", 0)
                        )
                    )
                }
                templateRepo.restoreList(list)
                restoredTemplates = list.size
            }

            // بازیابی تنظیمات ظاهری و تقویم
            if (root.has("preferences")) {
                val prefsObj = root.getJSONObject("preferences")
                if (prefsObj.has("isSolarCalendar")) {
                    DateUtils.setSolarCalendar(context, prefsObj.getBoolean("isSolarCalendar"))
                }
                if (prefsObj.has("themeMode")) {
                    try {
                        ThemePreferences.setThemeMode(context, ThemeMode.valueOf(prefsObj.getString("themeMode")))
                    } catch (ignored: Exception) {}
                }
                if (prefsObj.has("bubblePalette")) {
                    try {
                        ThemePreferences.setBubblePalette(context, BubblePalette.valueOf(prefsObj.getString("bubblePalette")))
                    } catch (ignored: Exception) {}
                }
                if (prefsObj.has("fontScale")) {
                    ThemePreferences.setFontScale(context, prefsObj.getDouble("fontScale").toFloat())
                }
            }

            val summary = "$restoredBlocked blocked senders, $restoredAliases merged groups, $restoredTemplates templates restored"
            Result.success(summary)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun exportToFile(uri: Uri, pin: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val plainJson = createBackupJson()
            val output = if (!pin.isNullOrBlank()) {
                encryptPayload(plainJson, pin)
            } else {
                plainJson
            }
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(output.toByteArray(Charsets.UTF_8))
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun importFromFile(uri: Uri, pin: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        content.append(line).append("\n")
                    }
                }
            }
            restoreBackupJson(content.toString(), pin)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
