package com.blackrain02.spamguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.rememberLazyListState
import com.blackrain02.spamguard.data.model.Conversation
import com.blackrain02.spamguard.data.repository.BankTransactionRepository
import com.blackrain02.spamguard.data.repository.SecurityRepository
import com.blackrain02.spamguard.data.repository.SmsRepository
import com.blackrain02.spamguard.data.repository.SpamRepository
import com.blackrain02.spamguard.notification.NotificationHelper
import com.blackrain02.spamguard.ui.chat.ChatScreen
import com.blackrain02.spamguard.ui.compose.NewMessageScreen
import com.blackrain02.spamguard.ui.conversations.ConversationListScreen
import com.blackrain02.spamguard.ui.finance.FinanceScreen
import com.blackrain02.spamguard.ui.quarantine.QuarantineScreen
import com.blackrain02.spamguard.ui.security.PinDialog
import com.blackrain02.spamguard.ui.security.PinDialogMode
import com.blackrain02.spamguard.ui.theme.AppLanguage
import com.blackrain02.spamguard.ui.theme.SmsyTheme
import com.blackrain02.spamguard.ui.theme.ThemePreferences
import com.blackrain02.spamguard.ui.util.AppStrings
import com.blackrain02.spamguard.ui.util.LocalAppStrings
import java.net.URLDecoder

sealed class Screen {
    object Conversations : Screen()
    object Finance : Screen()
    object Quarantine : Screen()
    data class Chat(
        val threadId: Long,
        val address: String,
        val contactName: String?,
        val mergedAddresses: Set<String> = emptySet(),
        val initialText: String = "",
        val highlightMessageId: Long? = null
    ) : Screen()
    object NewMessage : Screen()
}

class MainActivity : ComponentActivity() {
    private lateinit var smsRepository: SmsRepository
    private lateinit var spamRepository: SpamRepository
    private lateinit var bankRepo: BankTransactionRepository
    private lateinit var securityRepo: SecurityRepository

    private var pendingChatTarget by mutableStateOf<Screen.Chat?>(null)

    private val permissionTrigger = mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTrigger.intValue++
    }

    fun applyFlagSecure() {
        if (::securityRepo.isInitialized && securityRepo.isFlagSecureEnabled()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        applyFlagSecure()
        if (::securityRepo.isInitialized && securityRepo.isPinSet() && !securityRepo.isSessionValid()) {
            securityRepo.invalidateSession()
        }
        // پاک‌سازی خودکار تمام اعلان‌های برنامه به محض باز شدن
        NotificationHelper.cancelAll(this)
    }

    override fun onStop() {
        super.onStop()
        if (::securityRepo.isInitialized && securityRepo.getAutoLockTimeoutMinutes() == 0) {
            securityRepo.invalidateSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        smsRepository = SmsRepository(this)
        spamRepository = SpamRepository(this)
        bankRepo = BankTransactionRepository(this)
        securityRepo = SecurityRepository(this)
        applyFlagSecure()

        handleIncomingIntent(intent)
        checkAndRequestPermissions()

        setContent {
            ThemePreferences.init(applicationContext)
            val appLanguage = ThemePreferences.currentLanguage.value
            val layoutDirection = if (appLanguage == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
            val strings = remember(appLanguage) { AppStrings(appLanguage) }

            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalAppStrings provides strings
            ) {
                SmsyTheme {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Conversations) }
                    val unreadSpamCount by spamRepository.getUnreadCount().collectAsState(initial = 0)
                    val conversationListState = rememberLazyListState()
                    var conversationList by remember { mutableStateOf<List<Conversation>>(emptyList()) }

                    var showFinancePinDialog by remember { mutableStateOf<PinDialogMode?>(null) }
                    var pendingLockedChatTarget by remember { mutableStateOf<Screen.Chat?>(null) }

                    // باز کردن گفتگوی مورد نظر در صورت فراخوانی از لاگ تماس، مخاطبین یا اعلان‌ها
                    LaunchedEffect(pendingChatTarget) {
                        pendingChatTarget?.let { target ->
                            val alias = smsRepository.aliasRepository.getAliasForAddress(target.address)
                            val lockKey = securityRepo.getLockKey(target.address, alias?.id)
                            if (securityRepo.isConversationLocked(lockKey) && !securityRepo.isSessionValid()) {
                                pendingLockedChatTarget = target
                            } else {
                                securityRepo.touchAuth()
                                currentScreen = target
                            }
                            pendingChatTarget = null
                        }
                    }

                    // هدایت ایمن به تب امور مالی با اعتبارسنجی رمز عبور ۴ رقمی
                    val openFinanceTab = {
                        if (!securityRepo.isPinSet()) {
                            showFinancePinDialog = PinDialogMode.SET
                        } else if (!securityRepo.isSessionValid()) {
                            showFinancePinDialog = PinDialogMode.VERIFY
                        } else {
                            securityRepo.touchAuth()
                            currentScreen = Screen.Finance
                        }
                    }

                    // دیالوگ رمز برای ورود به تب مالی
                    if (showFinancePinDialog != null) {
                        PinDialog(
                            mode = showFinancePinDialog!!,
                            securityRepository = securityRepo,
                            subtitle = if (showFinancePinDialog == PinDialogMode.SET) strings.pinPromptSetFirst else strings.pinPromptFinance,
                            onSuccess = {
                                showFinancePinDialog = null
                                currentScreen = Screen.Finance
                            },
                            onDismiss = {
                                showFinancePinDialog = null
                            }
                        )
                    }

                    // دیالوگ رمز برای ورود به گفتگوی قفل شده فراخوانی شده از سیستم
                    if (pendingLockedChatTarget != null) {
                        PinDialog(
                            mode = PinDialogMode.VERIFY,
                            securityRepository = securityRepo,
                            subtitle = strings.pinPromptChat,
                            onSuccess = {
                                val target = pendingLockedChatTarget
                                pendingLockedChatTarget = null
                                if (target != null) {
                                    currentScreen = target
                                }
                            },
                            onDismiss = {
                                pendingLockedChatTarget = null
                            }
                        )
                    }

                    // مدیریت دکمه و ژست بازگشت سیستم‌عامل اندروید
                    BackHandler(enabled = currentScreen !is Screen.Conversations) {
                        currentScreen = Screen.Conversations
                    }

                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (currentScreen !is Screen.Chat && currentScreen !is Screen.NewMessage) {
                                Surface(
                                    tonalElevation = 3.dp,
                                    shadowElevation = 8.dp,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .height(54.dp)
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // ۱. تب صندوق پیام‌ها
                                        val isInbox = currentScreen is Screen.Conversations
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isInbox) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.clickable { currentScreen = Screen.Conversations }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Email,
                                                    contentDescription = "Inbox",
                                                    tint = if (isInbox) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                if (isInbox) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        strings.navMessages,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        // ۲. تب اختصاصی حسابداری مالی و تراکنش‌ها (محافظت شده با رمز عبور ۴ رقمی)
                                        val isFinance = currentScreen is Screen.Finance
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isFinance) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.clickable { openFinanceTab() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.AccountBalanceWallet,
                                                    contentDescription = "Finance",
                                                    tint = if (isFinance) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                if (isFinance) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        strings.navFinance,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        // ۳. تب قرنطینه هرزنامه‌ها
                                        val isQuarantine = currentScreen is Screen.Quarantine
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isQuarantine) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            modifier = Modifier.clickable { currentScreen = Screen.Quarantine }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                BadgedBox(badge = {
                                                    if (unreadSpamCount > 0) {
                                                        Badge { Text(unreadSpamCount.toString(), fontSize = 10.sp) }
                                                    }
                                                }) {
                                                    Icon(
                                                        Icons.Default.Shield,
                                                        contentDescription = "Quarantine",
                                                        tint = if (isQuarantine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                                if (isQuarantine) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        strings.navSpam,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding()),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    if (targetState is Screen.Chat || targetState is Screen.NewMessage) {
                                        (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(animationSpec = tween(280)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(280)) + fadeOut(animationSpec = tween(280)))
                                    } else if (initialState is Screen.Chat || initialState is Screen.NewMessage) {
                                        (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(280)) + fadeIn(animationSpec = tween(280)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) + fadeOut(animationSpec = tween(280)))
                                    } else {
                                        fadeIn(animationSpec = tween(200))
                                            .togetherWith(fadeOut(animationSpec = tween(200)))
                                    }
                                },
                                label = "ScreenTransition"
                            ) { screen ->
                                when (screen) {
                                    is Screen.Conversations -> {
                                        ConversationListScreen(
                                            smsRepository = smsRepository,
                                            spamRepository = spamRepository,
                                            permissionTrigger = permissionTrigger.intValue,
                                            onRequestPermissions = { checkAndRequestPermissions() },
                                            onNewMessageClick = { currentScreen = Screen.NewMessage },
                                            onOpenFinance = { openFinanceTab() },
                                            listState = conversationListState,
                                            cachedConversations = conversationList,
                                            onConversationsLoaded = { conversationList = it },
                                            onConversationClick = { conv ->
                                                smsRepository.markThreadAsReadAsync(conv.threadId, conv.address, conv.mergedAddresses, conv.aliasId)
                                                conversationList = conversationList.map {
                                                    if (it.address == conv.address || (conv.aliasId != null && it.aliasId == conv.aliasId)) {
                                                        it.copy(unreadCount = 0)
                                                    } else {
                                                        it
                                                    }
                                                }
                                                currentScreen = Screen.Chat(
                                                    threadId = conv.threadId,
                                                    address = conv.address,
                                                    contactName = conv.contactName,
                                                    mergedAddresses = conv.mergedAddresses
                                                )
                                            },
                                            onOpenSearchResultMessage = { threadId, address, contactName, messageId ->
                                                val alias = smsRepository.aliasRepository.getAliasForAddress(address)
                                                val merged = alias?.numbers ?: emptySet()
                                                smsRepository.markThreadAsReadAsync(threadId, address, merged, alias?.id)
                                                currentScreen = Screen.Chat(
                                                    threadId = threadId,
                                                    address = address,
                                                    contactName = contactName,
                                                    mergedAddresses = merged,
                                                    highlightMessageId = messageId
                                                )
                                            }
                                        )
                                    }
                                    is Screen.Finance -> {
                                        FinanceScreen(bankRepo = bankRepo)
                                    }
                                    is Screen.Quarantine -> {
                                        QuarantineScreen(
                                            spamRepository = spamRepository,
                                            smsRepository = smsRepository
                                        )
                                    }
                                    is Screen.Chat -> {
                                        ChatScreen(
                                            threadId = screen.threadId,
                                            address = screen.address,
                                            contactName = screen.contactName,
                                            smsRepository = smsRepository,
                                            onBack = { currentScreen = Screen.Conversations },
                                            mergedAddresses = screen.mergedAddresses,
                                            initialText = screen.initialText,
                                            highlightMessageId = screen.highlightMessageId
                                        )
                                    }
                                    is Screen.NewMessage -> {
                                        NewMessageScreen(
                                            smsRepository = smsRepository,
                                            onBack = { currentScreen = Screen.Conversations },
                                            onNavigateToChat = { threadId, address, contactName ->
                                                currentScreen = Screen.Chat(threadId, address, contactName)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }


    /**
     * استخراج شماره تلفن، مخاطب و متن از اینتنت‌های سیستمی (دکمه پیامک لاگ تماس، مخاطبین، به اشتراک‌گذاری)
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val (address, text) = parseSmsIntent(intent)
        if (!address.isNullOrBlank()) {
            val threadId = smsRepository.getOrCreateThreadId(address)
            val contactName = smsRepository.getContactName(address)
            val alias = smsRepository.aliasRepository.getAliasForAddress(address)
            val merged = alias?.numbers ?: emptySet()
            val finalName = alias?.displayName ?: contactName
            pendingChatTarget = Screen.Chat(
                threadId = threadId,
                address = address,
                contactName = finalName,
                mergedAddresses = merged,
                initialText = text ?: ""
            )
        }
    }

    private fun parseSmsIntent(intent: Intent?): Pair<String?, String?> {
        if (intent == null) return null to null
        var address: String? = null
        var text: String? = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra("sms_body")

        val data = intent.data
        if (data != null) {
            val scheme = data.scheme?.lowercase()
            if (scheme in listOf("sms", "smsto", "mms", "mmsto")) {
                val ssp = data.schemeSpecificPart ?: ""
                val cleanSsp = if (ssp.contains('?')) {
                    val parts = ssp.split('?', limit = 2)
                    if (text == null && parts.size > 1) {
                        val query = parts[1]
                        for (param in query.split('&')) {
                            val kv = param.split('=', limit = 2)
                            if (kv.isNotEmpty() && kv[0].equals("body", ignoreCase = true) && kv.size > 1) {
                                text = try {
                                    URLDecoder.decode(kv[1], "UTF-8")
                                } catch (_: Exception) {
                                    kv[1]
                                }
                            }
                        }
                    }
                    parts[0]
                } else {
                    ssp
                }
                if (cleanSsp.isNotBlank()) {
                    address = cleanSsp
                }
            }
        }

        if (address.isNullOrBlank()) {
            address = intent.getStringExtra("address")
                ?: intent.getStringExtra("sender")
                ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.firstOrNull()
        }

        return address?.trim()?.takeIf { it.isNotEmpty() } to text
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
