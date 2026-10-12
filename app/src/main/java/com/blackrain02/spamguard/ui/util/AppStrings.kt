package com.blackrain02.spamguard.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.blackrain02.spamguard.ui.theme.AppLanguage

class AppStrings(val lang: AppLanguage) {
    val isFa: Boolean get() = lang == AppLanguage.FA

    val appName = "Smsy"
    val navMessages = if (isFa) "پیام‌ها" else "Messages"
    val navSpam = if (isFa) "قرنطینه هرزنامه" else "Spam & Quarantine"

    val searchMessages = if (isFa) "جستجو در پیام‌ها و مخاطبین..." else "Search messages and contacts..."
    val searchSpam = if (isFa) "جستجو در هرزنامه‌ها..." else "Search spam..."

    val newConversation = if (isFa) "پیام جدید" else "New Conversation"
    val settingsAndTools = if (isFa) "تنظیمات و ابزارها" else "Settings & Tools"
    val batchSelect = if (isFa) "انتخاب گروهی" else "Batch Select"
    val scanHistoricalSms = if (isFa) "اسکن و پاکسازی هرزنامه‌ها" else "Scan & Clean Spam"

    fun pinnedSection(count: Int) = if (isFa) "سنجاق‌شده‌ها ($count)" else "Pinned ($count)"
    fun allMessages(count: Int) = if (isFa) "تمام پیام‌ها ($count)" else "Messages ($count)"
    fun otherMessages(count: Int) = if (isFa) "سایر پیام‌ها ($count)" else "Other Messages ($count)"

    val setAsDefaultTitle = if (isFa) "تنظیم به عنوان برنامه پیامک پیش‌فرض" else "Set as default SMS app"
    val setAsDefaultSub = if (isFa) "برای محافظت هوش مصنوعی آفلاین از هرزنامه‌ها ضروری است" else "Required for offline AI spam protection"
    val setAsDefaultBtn = if (isFa) "تنظیم پیش‌فرض" else "Set as Default"

    val noMessagesFound = if (isFa) "پیامی یافت نشد" else "No messages found"
    fun noMessagesMatch(query: String) = if (isFa) "هیچ پیامی با '$query' مطابقت ندارد" else "No messages match '$query'"
    val noSpamFound = if (isFa) "هیچ هرزنامه‌ای در قرنطینه نیست" else "No spam in quarantine"
    val incomingSpamNotice = if (isFa) "پیامک‌های تبلیغاتی دریافتی در این بخش جدا می‌شوند" else "Incoming promotional SMS will be isolated here"

    // Context menu actions
    val pinToTop = if (isFa) "سنجاق کردن به بالا" else "Pin to top"
    val unpinFromTop = if (isFa) "برداشتن سنجاق از بالا" else "Unpin from top"
    val blockSender = if (isFa) "مسدودسازی قطعی (فهرست سیاه)" else "Block sender (Blacklist)"
    val unblockSender = if (isFa) "رفع مسدودسازی سرشماره" else "Unblock sender"
    val nameAndMerge = if (isFa) "تنظیم نام و ادغام سرشماره‌ها" else "Name & Merge Senders"
    val addToExistingGroup = if (isFa) "افزودن به گروه ادغام موجود" else "Add to Merged Group"
    val moveToSpam = if (isFa) "انتقال به پوشه هرزنامه‌ها" else "Move to Spam"
    val deleteConversation = if (isFa) "حذف گفتگو" else "Delete Conversation"

    // Settings
    val settingsTitle = if (isFa) "تنظیمات و ابزارها" else "Settings & Tools"
    val appearanceTheme = if (isFa) "حالت ظاهری (تم)" else "Theme Mode"
    val themeSystem = if (isFa) "پیش‌فرض سیستم" else "System"
    val themeLight = if (isFa) "روشن" else "Light"
    val themeDark = if (isFa) "تاریک" else "Dark"
    val bubblePalette = if (isFa) "پالت رنگ حباب‌های پیام" else "Chat Bubble Palette"
    val language = if (isFa) "زبان برنامه" else "Language"
    val langEnglish = "English"
    val langPersian = "فارسی"
    val calendarFormat = if (isFa) "نوع تقویم" else "Calendar Format"
    val calendarSolar = if (isFa) "خورشیدی (شمسی)" else "Solar (Shamsi)"
    val calendarGregorian = if (isFa) "میلادی" else "Gregorian"
    val manageTemplates = if (isFa) "مدیریت پاسخ‌های آماده" else "Quick Responses"
    val definitiveBlocklist = if (isFa) "فهرست سیاه قطعی" else "Definitive Blocklist"
    val exportBackup = if (isFa) "پشتیبان‌گیری (خروجی JSON)" else "Export Backup (JSON)"
    val restoreBackup = if (isFa) "بازیابی اطلاعات (ورود JSON)" else "Restore Backup (JSON)"

    // Chat
    val textMessage = if (isFa) "پیام متنی" else "Text message"
    val send = if (isFa) "ارسال" else "Send"
    val sendOptions = if (isFa) "گزینه‌های ارسال" else "Send Options"
    val sendViaSim = if (isFa) "ارسال از طریق سیم‌کارت" else "Send via SIM"
    val scheduleSms = if (isFa) "زمان‌بندی ارسال پیامک" else "Schedule SMS"
    val quickResponses = if (isFa) "پاسخ‌های آماده" else "Quick Responses"
    val copyOtp = if (isFa) "کپی رمز" else "Copy OTP"
    val deleteOtp = if (isFa) "حذف پیامک رمز" else "Delete OTP"
    val scheduleTitle = if (isFa) "زمان‌بندی ارسال پیامک" else "Schedule SMS Delivery"
    val scheduleConfirm = if (isFa) "ثبت زمان‌بندی" else "Schedule"
    val cancel = if (isFa) "انصراف" else "Cancel"
    val close = if (isFa) "بستن" else "Close"
    val delete = if (isFa) "حذف" else "Delete"
    val restore = if (isFa) "بازگردانی" else "Restore"
    val add = if (isFa) "افزودن" else "Add"
    val save = if (isFa) "ذخیره" else "Save"
    val edit = if (isFa) "ویرایش" else "Edit"
    val editTemplate = if (isFa) "ویرایش پاسخ آماده" else "Edit Quick Response"
    val batchSelectMode = if (isFa) "انتخاب چندتایی (عملیات گروهی)" else "Batch Select"
    val copyMessageText = if (isFa) "کپی متن پیام" else "Copy Message Text"
    val deleteThisMessage = if (isFa) "حذف این پیام" else "Delete This Message"
    val messageOptions = if (isFa) "گزینه‌های پیام" else "Message Options"

    // Quarantine
    val quarantineTitle = if (isFa) "قرنطینه هرزنامه‌ها" else "Spam Quarantine"
    val clearAll = if (isFa) "پاکسازی همه" else "Clear All"
    val restoreToInbox = if (isFa) "بازگردانی به صندوق دریافت" else "Restore to Inbox"
}

val LocalAppStrings = staticCompositionLocalOf { AppStrings(AppLanguage.EN) }
