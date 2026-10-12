package com.binacity.spamguard.data.repository

import android.content.Context
import android.net.Uri
import com.binacity.spamguard.data.local.BlocklistEntity
import com.binacity.spamguard.data.local.QuickTemplateEntity
import com.binacity.spamguard.ui.theme.BubblePalette
import com.binacity.spamguard.ui.theme.ThemeMode
import com.binacity.spamguard.ui.theme.ThemePreferences
import com.binacity.spamguard.ui.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupRepository(
    private val context: Context,
    private val blocklistRepo: BlocklistRepository,
    private val aliasRepo: SenderAliasRepository,
    private val templateRepo: TemplateRepository
) {

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

    suspend fun restoreBackupJson(jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("appName") || root.getString("appName") != "Smsy") {
                return@withContext Result.failure(Exception("Invalid backup file: Not a Smsy backup"))
            }

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

    suspend fun exportToFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = createBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun importFromFile(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
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
            restoreBackupJson(content.toString())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
