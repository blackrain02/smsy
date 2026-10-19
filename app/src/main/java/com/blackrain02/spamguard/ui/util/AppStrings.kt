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

    // Smart Features (Finance, Events, Smart Reply)
    val financialDashboard = if (isFa) "مدیریت مالی و تراکنش‌های بانکی" else "Financial & Expense Dashboard"
    val totalDeposits = if (isFa) "مجموع واریزی‌ها" else "Total Deposits"
    val totalWithdrawals = if (isFa) "مجموع برداشت‌ها" else "Total Withdrawals"
    val netBalance = if (isFa) "تراز مالی" else "Net Balance"
    val allTransactions = if (isFa) "همه" else "All"
    val deposits = if (isFa) "واریز" else "Deposits"
    val withdrawals = if (isFa) "برداشت" else "Withdrawals"
    val noTransactions = if (isFa) "هیچ تراکنش بانکی ثبت نشده است." else "No bank transactions recorded."
    val financialScan = if (isFa) "اسکن پیامک‌های بانکی گذشته" else "Scan Historical Bank SMS"
    val transactionsCount = if (isFa) "تراکنش" else "Transactions"
    val eventDetected = if (isFa) "رویداد / سررسید" else "Event / Due Date"
    val addToCalendar = if (isFa) "افزودن به تقویم" else "Add to Calendar"
    val smartReplies = if (isFa) "پاسخ‌های پیشنهادی هوشمند" else "Smart Replies"

    // PIN & Security & Finance Tab
    val navFinance = if (isFa) "حسابداری مالی" else "Finance"
    val lockConversation = if (isFa) "قفل گفتگو با رمز" else "Lock conversation"
    val unlockConversation = if (isFa) "باز کردن قفل گفتگو" else "Unlock conversation"
    val lockedMessage = if (isFa) "گفتگوی قفل شده" else "Locked conversation"
    val lockedNotification = if (isFa) "پیام جدید دریافت شد (قفل شده)" else "New message received (Locked)"
    val enterPinTitle = if (isFa) "ورود رمز عبور ۴ رقمی" else "Enter 4-Digit PIN"
    val setPinTitle = if (isFa) "تعیین رمز عبور ۴ رقمی" else "Set 4-Digit PIN"
    val confirmPinTitle = if (isFa) "تکرار رمز عبور" else "Confirm PIN"
    val pinIncorrect = if (isFa) "رمز عبور نادرست است" else "Incorrect PIN"
    val pinMismatch = if (isFa) "رمزهای عبور یکسان نیستند" else "PINs do not match"
    val pinPromptFinance = if (isFa) "برای دسترسی به بخش مالی، رمز عبور ۴ رقمی را وارد کنید" else "Enter 4-digit PIN to access Financial Dashboard"
    val pinPromptChat = if (isFa) "برای مشاهده این گفتگو، رمز عبور را وارد کنید" else "Enter PIN to access this conversation"
    val pinPromptSetFirst = if (isFa) "لطفاً ابتدا یک رمز ۴ رقمی برای محافظت از داده‌ها تعیین کنید" else "Please set a 4-digit PIN first to protect your data"
    val securitySettings = if (isFa) "امنیت و رمز عبور (PIN)" else "Security & PIN Lock"
    val changePin = if (isFa) "تغییر رمز عبور" else "Change PIN"
    val removePin = if (isFa) "حذف رمز عبور" else "Remove PIN"
    val pinChangedSuccess = if (isFa) "رمز عبور با موفقیت به‌روزرسانی شد" else "PIN updated successfully"
    val pinRemovedSuccess = if (isFa) "رمز عبور و قفل‌ها حذف شدند" else "PIN and locks removed"
    val conversationLockedSuccess = if (isFa) "گفتگو با موفقیت قفل شد" else "Conversation locked successfully"
    val conversationUnlockedSuccess = if (isFa) "قفل گفتگو باز شد" else "Conversation unlocked"

    // Deep Search & Avatars
    val searchResultsMessages = if (isFa) "پیام‌های یافت‌شده در متن" else "Messages Found"
    val searchConversationsHeader = if (isFa) "گفتگوها" else "Conversations"
    val inChatSearch = if (isFa) "جستجو در این گفتگو..." else "Search in this chat..."

    // Security & Privacy Extensions
    val autoLockTimeout = if (isFa) "زمان قفل خودکار نشست" else "Auto-Lock Timeout"
    val autoLockImmediate = if (isFa) "بلافاصله هنگام خروج" else "Immediately on Exit"
    val autoLock1Min = if (isFa) "۱ دقیقه پس از عدم فعالیت" else "1 Minute Inactivity"
    val autoLock5Min = if (isFa) "۵ دقیقه پس از عدم فعالیت" else "5 Minutes Inactivity"
    val flagSecureTitle = if (isFa) "محافظت در برنامه‌های اخیر (ضد اسکرین‌شات)" else "App Switcher & Screenshot Shield"
    val flagSecureSubtitle = if (isFa) "جلوگیری از پیش‌نمایش در منوی برنامه‌ها و مسدودسازی اسکرین‌شات (FLAG_SECURE)" else "Prevents previews in recent apps tray & blocks screenshots"
    val biometricUnlock = if (isFa) "بازگشایی با اثر انگشت (بیومتریک)" else "Fingerprint Unlock (Biometric)"
    val biometricSubtitle = if (isFa) "احراز هویت سریع با سنسور اثر انگشت در کنار پین" else "Quick authentication with fingerprint sensor alongside PIN"
    val decoyPin = if (isFa) "رمز عبور اضطراری (پین فریب / Decoy)" else "Decoy / Duress PIN"
    val decoyPinSubtitle = if (isFa) "نمایش موجودی صفر و مخفی‌سازی داده‌ها در شرایط اجبار" else "Hides locked chats & shows zero balance under coercion"
    val setDecoyPin = if (isFa) "تعیین پین فریب اضطراری" else "Set Decoy PIN"
    val removeDecoyPin = if (isFa) "حذف پین فریب" else "Remove Decoy PIN"
    val decoyPinSetSuccess = if (isFa) "پین اضطراری با موفقیت ذخیره شد" else "Decoy PIN set successfully"
    val decoyPinRemoved = if (isFa) "پین اضطراری حذف شد" else "Decoy PIN removed"
    val biometricPromptTitle = if (isFa) "تأیید هویت Smsy" else "Smsy Authentication"
    val biometricPromptSubtitle = if (isFa) "لطفاً اثر انگشت خود را تأیید کنید" else "Please confirm your fingerprint"
    val usePinInstead = if (isFa) "ورود با رمز عبور" else "Use PIN"
    val encryptedBackupSuccess = if (isFa) "پشتیبان با رمزنگاری پیشرفته AES-GCM صادر شد" else "Backup exported with AES-GCM encryption"
    val enterPinToDecrypt = if (isFa) "فایل پشتیبان رمزنگاری شده است؛ ابتدا رمز را وارد کنید" else "Backup is encrypted; enter PIN to restore"
}

val LocalAppStrings = staticCompositionLocalOf { AppStrings(AppLanguage.EN) }
