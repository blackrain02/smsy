package com.binacity.spamguard.ai

object TextNormalizer {
    private val DIGITS_REGEX = Regex("[0-9\\u06F0-\\u06F9\\u0660-\\u0669]+")
    private val URL_REGEX = Regex("https?://\\S+|www\\.\\S+")
    private val PUNCT_REGEX = Regex("[^\\p{L}\\p{N}\\s<>]")
    private val SPACES_REGEX = Regex("\\s+")

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""

        var cleaned = text
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("\u200c", " ") // نیم‌فاصله

        cleaned = DIGITS_REGEX.replace(cleaned, " <NUM> ")
        cleaned = URL_REGEX.replace(cleaned, " <URL> ")
        cleaned = PUNCT_REGEX.replace(cleaned, " ")
        cleaned = SPACES_REGEX.replace(cleaned, " ").trim()

        return cleaned.lowercase()
    }
}
