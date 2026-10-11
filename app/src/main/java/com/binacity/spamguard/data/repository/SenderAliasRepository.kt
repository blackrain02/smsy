package com.binacity.spamguard.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SenderGroupAlias(
    val id: String,
    val displayName: String,
    val numbers: Set<String>
)

class SenderAliasRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smsy_sender_aliases", Context.MODE_PRIVATE)

    /**
     * واکشی تمام گروه‌های ادغام‌شده / نام‌های مستعار
     */
    fun getAllAliases(): List<SenderGroupAlias> {
        val jsonStr = prefs.getString("aliases_json", null) ?: return emptyList()
        val list = mutableListOf<SenderGroupAlias>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val displayName = obj.getString("displayName")
                val numbersArray = obj.getJSONArray("numbers")
                val numbers = mutableSetOf<String>()
                for (j in 0 until numbersArray.length()) {
                    numbers.add(numbersArray.getString(j))
                }
                list.add(SenderGroupAlias(id = id, displayName = displayName, numbers = numbers))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * یافتن گروه ادغام بر اساس هر یک از شماره‌های عضو
     */
    fun getAliasForAddress(address: String): SenderGroupAlias? {
        val clean = cleanAddress(address)
        if (clean.isBlank()) return null

        val all = getAllAliases()
        for (alias in all) {
            for (num in alias.numbers) {
                if (isNumberMatch(clean, num)) {
                    return alias
                }
            }
        }
        return null
    }

    /**
     * ذخیره یا به‌روزرسانی نام مستعار و سرشماره‌های ادغام‌شده
     */
    fun saveAlias(displayName: String, numbers: Set<String>, existingId: String? = null): SenderGroupAlias {
        val list = getAllAliases().toMutableList()
        val id = existingId ?: java.util.UUID.randomUUID().toString()

        list.removeAll { it.id == id }
        val newAlias = SenderGroupAlias(
            id = id,
            displayName = displayName.trim(),
            numbers = numbers.map { cleanAddress(it) }.filter { it.isNotBlank() }.toSet()
        )
        list.add(newAlias)
        saveAll(list)
        return newAlias
    }

    /**
     * حذف یک گروه ادغام
     */
    fun deleteAlias(id: String) {
        val list = getAllAliases().toMutableList()
        list.removeAll { it.id == id }
        saveAll(list)
    }

    private fun saveAll(list: List<SenderGroupAlias>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("displayName", item.displayName)
                val numsArray = JSONArray()
                item.numbers.forEach { numsArray.put(it) }
                obj.put("numbers", numsArray)
                jsonArray.put(obj)
            }
            prefs.edit().putString("aliases_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanAddress(addr: String): String {
        return addr.trim().lowercase()
    }

    private fun isNumberMatch(a1: String, a2: String): Boolean {
        val c1 = cleanAddress(a1)
        val c2 = cleanAddress(a2)
        if (c1 == c2) return true

        val d1 = c1.replace(Regex("[^0-9]"), "")
        val d2 = c2.replace(Regex("[^0-9]"), "")
        if (d1.isNotEmpty() && d2.isNotEmpty()) {
            if (d1 == d2) return true
            if (d1.length >= 7 && d2.length >= 7 && (d1.endsWith(d2) || d2.endsWith(d1))) return true
        }
        return false
    }
}
