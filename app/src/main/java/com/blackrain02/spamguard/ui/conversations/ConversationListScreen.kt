package com.blackrain02.spamguard.ui.conversations

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackrain02.spamguard.ai.SpamClassifier
import com.blackrain02.spamguard.data.local.BlocklistEntity
import com.blackrain02.spamguard.data.local.QuickTemplateEntity
import com.blackrain02.spamguard.data.model.Conversation
import com.blackrain02.spamguard.data.repository.BackupRepository
import com.blackrain02.spamguard.data.repository.BankTransactionRepository
import com.blackrain02.spamguard.data.repository.FinancialStats
import com.blackrain02.spamguard.data.repository.ScanProgress
import com.blackrain02.spamguard.data.repository.SmsRepository
import com.blackrain02.spamguard.data.repository.SpamRepository
import com.blackrain02.spamguard.data.repository.TemplateRepository
import com.blackrain02.spamguard.ui.security.PinDialog
import com.blackrain02.spamguard.ui.security.PinDialogMode
import com.blackrain02.spamguard.ui.theme.BubblePalette
import com.blackrain02.spamguard.ui.theme.ThemeMode
import com.blackrain02.spamguard.ui.theme.AppLanguage
import com.blackrain02.spamguard.ui.theme.ThemePreferences
import com.blackrain02.spamguard.ui.util.LocalAppStrings
import com.blackrain02.spamguard.ui.util.DateUtils
import android.widget.Toast
import com.blackrain02.spamguard.ui.MainActivity
import com.blackrain02.spamguard.ui.util.ContactAvatar
import com.blackrain02.spamguard.ui.util.ConversationSkeletonList
import com.blackrain02.spamguard.data.repository.MessageSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationListScreen(
    smsRepository: SmsRepository,
    spamRepository: SpamRepository,
    onConversationClick: (Conversation) -> Unit,
    permissionTrigger: Int = 0,
    onRequestPermissions: () -> Unit = {},
    onNewMessageClick: () -> Unit = {},
    onOpenFinance: () -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    cachedConversations: List<Conversation> = emptyList(),
    onConversationsLoaded: (List<Conversation>) -> Unit = {},
    onOpenSearchResultMessage: (Long, String, String?, Long) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var conversations by remember { mutableStateOf(cachedConversations) }
    var isLoading by remember { mutableStateOf(cachedConversations.isEmpty()) }

    LaunchedEffect(cachedConversations) {
        if (cachedConversations.isNotEmpty() && conversations.isEmpty()) {
            conversations = cachedConversations
            isLoading = false
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var fullTextResults by remember { mutableStateOf<List<MessageSearchResult>>(emptyList()) }
    var isSearchingMessages by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            delay(300)
            isSearchingMessages = true
            withContext(Dispatchers.IO) {
                try {
                    val res = smsRepository.searchMessagesFullText(query)
                    withContext(Dispatchers.Main) {
                        fullTextResults = res
                        isSearchingMessages = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        fullTextResults = emptyList()
                        isSearchingMessages = false
                    }
                }
            }
        } else {
            fullTextResults = emptyList()
            isSearchingMessages = false
        }
    }
    var isSolarCalendar by remember { mutableStateOf(DateUtils.isSolarCalendar(context)) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var isDefaultApp by remember { mutableStateOf(checkIsDefaultSmsApp(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<ScanProgress?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var finalResult by remember { mutableStateOf<ScanProgress?>(null) }
    var showRestrictedHelpDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    var conversationToSpam by remember { mutableStateOf<Conversation?>(null) }
    var conversationToAddToGroup by remember { mutableStateOf<Conversation?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedConversations by remember { mutableStateOf<Set<Conversation>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchSpamDialog by remember { mutableStateOf(false) }
    var selectedConversationForAction by remember { mutableStateOf<Conversation?>(null) }
    var conversationToMerge by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()

    val templateRepo = remember { TemplateRepository(context) }
    val backupRepo = remember {
        BackupRepository(
            context = context,
            blocklistRepo = smsRepository.blocklistRepository,
            aliasRepo = smsRepository.aliasRepository,
            templateRepo = templateRepo
        )
    }

    LaunchedEffect(Unit) {
        templateRepo.ensureDefaultTemplates()
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBlocklistDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    val securityRepo = remember { smsRepository.securityRepository }
    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var pendingUnlockedConversation by remember { mutableStateOf<Conversation?>(null) }
    var conversationToLockUnlock by remember { mutableStateOf<Conversation?>(null) }
    var pinActionSubtitle by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = backupRepo.exportToFile(uri)
                if (res.isSuccess) {
                    Toast.makeText(context, if (strings.isFa) "فایل پشتیبان با موفقیت صادر شد" else "Backup exported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (strings.isFa) "خطا در صادرات فایل پشتیبان" else "Error exporting backup", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = backupRepo.importFromFile(uri)
                if (res.isSuccess) {
                    Toast.makeText(context, (if (strings.isFa) "پشتیبان با موفقیت بازگردانی شد: " else "Backup restored successfully: ") + (res.getOrNull() ?: ""), Toast.LENGTH_LONG).show()
                    isSolarCalendar = DateUtils.isSolarCalendar(context)
                } else {
                    Toast.makeText(context, (if (strings.isFa) "خطا در خواندن فایل پشتیبان: " else "Error reading backup: ") + (res.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedConversations = emptySet()
    }

    val hasSmsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    val defaultRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val nowDefault = checkIsDefaultSmsApp(context)
        isDefaultApp = nowDefault
        if (!nowDefault) {
            showRestrictedHelpDialog = true
        }
    }

    fun refreshList() {
        scope.launch {
            try {
                if (conversations.isEmpty()) {
                    isLoading = true
                }
                val fresh = smsRepository.getConversations()
                conversations = fresh
                onConversationsLoaded(fresh)
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(permissionTrigger, hasSmsPermission) {
        if (hasSmsPermission) {
            refreshList()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultApp = checkIsDefaultSmsApp(context)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    refreshList()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // لیسنر بلادرنگ تغییرات پیامک‌ها در سیستم‌عامل با دی‌بانس ۳۵۰ میلی‌ثانیه جهت حذف لگ اسکرول
    DisposableEffect(Unit) {
        var debounceJob: Job? = null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (hasSmsPermission) {
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(350)
                        refreshList()
                    }
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            debounceJob?.cancel()
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else {
            val q = searchQuery.trim().lowercase()
            conversations.filter {
                (it.contactName?.lowercase()?.contains(q) == true) ||
                it.address.lowercase().contains(q) ||
                it.snippet.lowercase().contains(q) ||
                it.mergedAddresses.any { num -> num.lowercase().contains(q) }
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -it / 2 }))
                        .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it / 2 }))
                },
                label = "TopBarTransition"
            ) { inSelection ->
                if (inSelection) {
                    TopAppBar(
                        title = {
                            Text(
                                "${selectedConversations.size} selected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedConversations = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                selectedConversations = if (selectedConversations.size == filteredConversations.size) {
                                    emptySet()
                                } else {
                                    filteredConversations.toSet()
                                }
                            }) {
                                Icon(
                                    if (selectedConversations.size == filteredConversations.size && filteredConversations.isNotEmpty())
                                        Icons.Default.ChecklistRtl
                                    else
                                        Icons.Default.SelectAll,
                                    contentDescription = "Select All"
                                )
                            }
                            IconButton(
                                onClick = { showBatchSpamDialog = true },
                                enabled = selectedConversations.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = "Batch Move to Spam",
                                    tint = if (selectedConversations.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = { showBatchDeleteDialog = true },
                                enabled = selectedConversations.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Batch Delete",
                                    tint = if (selectedConversations.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text(strings.appName, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            IconButton(
                                onClick = onNewMessageClick
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = strings.newConversation,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showSettingsSheet = true }
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = strings.settingsAndTools,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { isSelectionMode = true }
                            ) {
                                Icon(
                                    Icons.Default.Checklist,
                                    contentDescription = strings.batchSelect,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (!isScanning) {
                                        isScanning = true
                                        scope.launch {
                                            val classifier = SpamClassifier.getInstance(context)
                                            val result = smsRepository.scanAndCleanHistoricalSms(
                                                spamRepository = spamRepository,
                                                classifier = classifier,
                                                onProgress = { progress ->
                                                    scanProgress = progress
                                                }
                                            )
                                            isScanning = false
                                            finalResult = result
                                            showResultDialog = true
                                            refreshList()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = strings.scanHistoricalSms,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // بنر تنظیم به عنوان برنامه پیش‌فرض
            if (!isDefaultApp) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                strings.setAsDefaultTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                strings.setAsDefaultSub,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                requestDefaultSmsRole(context) { intent ->
                                    defaultRoleLauncher.launch(intent)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.setAsDefaultBtn, fontSize = 12.sp)
                        }
                    }
                }
            }

            // فیلد جستجو در گفتگوها (طراحی فشرده و مدرن)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    strings.searchMessages,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val handleConversationClick = { item: Conversation ->
                if (item.isLocked) {
                    pendingUnlockedConversation = item
                    pinActionSubtitle = strings.pinPromptChat
                    pinDialogMode = PinDialogMode.VERIFY
                } else {
                    onConversationClick(item)
                }
            }

            // لیست پیامک‌ها
            if (!hasSmsPermission) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "SMS Permission Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Smsy runs 100% locally with zero internet access to guard your privacy.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = onRequestPermissions) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else if (isLoading && conversations.isEmpty()) {
                ConversationSkeletonList(count = 9)
            } else if (searchQuery.isNotBlank()) {
                if (filteredConversations.isEmpty() && fullTextResults.isEmpty() && !isSearchingMessages) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            strings.noMessagesMatch(searchQuery),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ۱. گفتگوهای منطبق
                        if (filteredConversations.isNotEmpty()) {
                            item(key = "header_search_conversations") {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Forum,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            strings.searchConversationsHeader + " (${filteredConversations.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            items(
                                items = filteredConversations,
                                key = { "search_c_" + (it.aliasId ?: "${it.threadId}_${it.address}") },
                                contentType = { "conversation" }
                            ) { item ->
                                ConversationItem(
                                    conversation = item,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    isSolarCalendar = isSolarCalendar,
                                    onClick = { handleConversationClick(item) },
                                    onLongClick = { selectedConversationForAction = item }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // ۲. پیام‌های یافت‌شده در متن (Deep Search)
                        if (fullTextResults.isNotEmpty()) {
                            item(key = "header_search_messages") {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            strings.searchResultsMessages + " (${fullTextResults.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            items(
                                items = fullTextResults,
                                key = { "search_m_${it.messageId}" },
                                contentType = { "search_message" }
                            ) { result ->
                                MessageSearchResultItem(
                                    result = result,
                                    isSolarCalendar = isSolarCalendar,
                                    onClick = {
                                        onOpenSearchResultMessage(result.threadId, result.address, result.contactName, result.messageId)
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            } else if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.noMessagesFound,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                val uniqueConversations = remember(filteredConversations) {
                    val seen = HashSet<String>()
                    filteredConversations.filter { conv ->
                        val key = conv.aliasId ?: "${conv.threadId}_${conv.address}"
                        seen.add(key)
                    }
                }

                val (pinnedList, normalList) = remember(uniqueConversations) {
                    uniqueConversations.partition { it.isPinned }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // بخش سرشماره‌های سنجاق‌شده
                    if (pinnedList.isNotEmpty()) {
                        item(key = "header_pinned_section") {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        strings.pinnedSection(pinnedList.size),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        items(
                            items = pinnedList,
                            key = { "pinned_" + (it.aliasId ?: "${it.threadId}_${it.address}") },
                            contentType = { "conversation" }
                        ) { item ->
                            val isSelected = selectedConversations.contains(item)
                            ConversationItem(
                                conversation = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                isSolarCalendar = isSolarCalendar,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedConversations = if (isSelected) {
                                            selectedConversations - item
                                        } else {
                                            selectedConversations + item
                                        }
                                    } else {
                                        handleConversationClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (isSelectionMode) {
                                        selectedConversations = if (isSelected) {
                                            selectedConversations - item
                                        } else {
                                            selectedConversations + item
                                        }
                                    } else {
                                        selectedConversationForAction = item
                                    }
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }

                        if (normalList.isNotEmpty()) {
                            item(key = "header_normal_section") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Forum,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            strings.otherMessages(normalList.size),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // بخش پیام‌های عادی
                    items(
                        items = normalList,
                        key = { it.aliasId ?: "${it.threadId}_${it.address}" },
                        contentType = { "conversation" }
                    ) { item ->
                        val isSelected = selectedConversations.contains(item)
                        ConversationItem(
                            conversation = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            isSolarCalendar = isSolarCalendar,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedConversations = if (isSelected) {
                                        selectedConversations - item
                                    } else {
                                        selectedConversations + item
                                    }
                                } else {
                                    handleConversationClick(item)
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    selectedConversations = if (isSelected) {
                                        selectedConversations - item
                                    } else {
                                        selectedConversations + item
                                    }
                                } else {
                                    selectedConversationForAction = item
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }

    // دیالوگ انتخاب نوع تقویم (شمسی / میلادی با ارقام انگلیسی)
    if (showCalendarDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarDialog = false },
            title = { Text(strings.calendarFormat, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                DateUtils.setSolarCalendar(context, true)
                                isSolarCalendar = true
                                showCalendarDialog = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSolarCalendar,
                            onClick = {
                                DateUtils.setSolarCalendar(context, true)
                                isSolarCalendar = true
                                showCalendarDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(strings.calendarSolar, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("e.g. 1403/07/20", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                DateUtils.setSolarCalendar(context, false)
                                isSolarCalendar = false
                                showCalendarDialog = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isSolarCalendar,
                            onClick = {
                                DateUtils.setSolarCalendar(context, false)
                                isSolarCalendar = false
                                showCalendarDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(strings.calendarGregorian, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("e.g. 2026/10/12", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    // منوی عملیات مدرن گفتگو با نگه‌داشتن روی سرشماره (ModalBottomSheet)
    if (selectedConversationForAction != null) {
        val target = selectedConversationForAction!!
        ModalBottomSheet(
            onDismissRequest = { selectedConversationForAction = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (target.contactName ?: target.address).take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.contactName ?: target.address,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (target.contactName != null && target.address.isNotBlank()) {
                            Text(
                                text = target.address,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                // ۱. سنجاق کردن / برداشتن سنجاق
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            smsRepository.togglePin(target.address)
                            selectedConversationForAction = null
                            refreshList()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if (target.isPinned) strings.unpinFromTop else strings.pinToTop,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ۲. قفل / باز کردن قفل با رمز عبور ۴ رقمی
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val targetConv = target
                            selectedConversationForAction = null
                            if (!securityRepo.isPinSet()) {
                                conversationToLockUnlock = targetConv
                                pinActionSubtitle = strings.pinPromptSetFirst
                                pinDialogMode = PinDialogMode.SET
                            } else {
                                if (targetConv.isLocked) {
                                    conversationToLockUnlock = targetConv
                                    pinActionSubtitle = strings.unlockConversation
                                    pinDialogMode = PinDialogMode.VERIFY
                                } else {
                                    val lockKey = securityRepo.getLockKey(targetConv)
                                    securityRepo.setConversationLocked(lockKey, true)
                                    Toast.makeText(context, strings.conversationLockedSuccess, Toast.LENGTH_SHORT).show()
                                    refreshList()
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (target.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if (target.isLocked) strings.unlockConversation else strings.lockConversation,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ۲. مسدودسازی قطعی / رفع مسدودی در فهرست سیاه
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                if (target.isBlocked) {
                                    smsRepository.blocklistRepository.unblockSender(target.address)
                                    for (num in target.mergedAddresses) {
                                        smsRepository.blocklistRepository.unblockSender(num)
                                    }
                                    Toast.makeText(context, if (strings.isFa) "سرشماره از فهرست سیاه خارج شد" else "Sender removed from blocklist", Toast.LENGTH_SHORT).show()
                                } else {
                                    val reason = if (strings.isFa) "مسدودسازی دستی" else "Manual block"
                                    smsRepository.blocklistRepository.blockSender(target.address, reason)
                                    for (num in target.mergedAddresses) {
                                        smsRepository.blocklistRepository.blockSender(num, reason)
                                    }
                                    Toast.makeText(context, if (strings.isFa) "سرشماره به فهرست سیاه اضافه شد" else "Sender added to blocklist", Toast.LENGTH_SHORT).show()
                                }
                                selectedConversationForAction = null
                                refreshList()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (target.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        tint = if (target.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if (target.isBlocked) strings.unblockSender else strings.blockSender,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (target.isBlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }

                // ۳. تنظیم نام و ادغام سرشماره‌ها
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            conversationToMerge = target
                            selectedConversationForAction = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CallMerge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        strings.nameAndMerge,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ۴. افزودن به گروه ادغام موجود
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            conversationToAddToGroup = target
                            selectedConversationForAction = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        strings.addToExistingGroup,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ۵. انتقال به اسپم (قرنطینه)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            conversationToSpam = target
                            selectedConversationForAction = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        strings.moveToSpam,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ۶. انتخاب دسته‌ای (بچ)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isSelectionMode = true
                            selectedConversations = setOf(target)
                            selectedConversationForAction = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        strings.batchSelectMode,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ۷. حذف گفتگو
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            conversationToDelete = target
                            selectedConversationForAction = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        strings.deleteConversation,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // شیت جامع تنظیمات، شخصی‌سازی، پشتیبان‌گیری و ابزارها
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.settingsTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showSettingsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                // ۱. انتخاب زبان برنامه
                Text(strings.language, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                val activeLanguage = ThemePreferences.currentLanguage.value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.values().forEach { lang ->
                        val isSelected = activeLanguage == lang
                        val label = if (lang == AppLanguage.EN) strings.langEnglish else strings.langPersian
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                ThemePreferences.setLanguage(context, lang)
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ۲. شخصی‌سازی ظاهری (تم)
                Text(strings.appearanceTheme, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                val activeThemeMode = ThemePreferences.currentThemeMode.value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = activeThemeMode == mode
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> strings.themeSystem
                            ThemeMode.LIGHT -> strings.themeLight
                            ThemeMode.DARK -> strings.themeDark
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                ThemePreferences.setThemeMode(context, mode)
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ۳. انتخاب پالت رنگی
                val activePalette = ThemePreferences.currentBubblePalette.value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.bubblePalette + ":", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BubblePalette.values().forEach { pal ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(pal.primaryColor, CircleShape)
                                .clickable {
                                    ThemePreferences.setBubblePalette(context, pal)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (activePalette == pal) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(14.dp))

                // ۴. فرمت تقویم
                Text(strings.calendarFormat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isSolarCalendar,
                        onClick = {
                            DateUtils.setSolarCalendar(context, true)
                            isSolarCalendar = true
                        },
                        label = { Text(strings.calendarSolar, fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = !isSolarCalendar,
                        onClick = {
                            DateUtils.setSolarCalendar(context, false)
                            isSolarCalendar = false
                        },
                        label = { Text(strings.calendarGregorian, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(14.dp))

                // ۵. مدیریت فهرست سیاه قطعی
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBlocklistDialog = true; showSettingsSheet = false }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(strings.definitiveBlocklist, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                }

                // ۶. پاسخ‌های آماده و هوشمند
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTemplatesDialog = true; showSettingsSheet = false }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Quickreply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(strings.manageTemplates, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                }

                // ۷. مدیریت مالی و حسابداری هوشمند تراکنش‌های بانکی
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSettingsSheet = false
                            onOpenFinance()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(strings.financialDashboard, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                }

                // ۸. امنیت، حریم خصوصی و رمز عبور (PIN)
                Text(strings.securitySettings, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))

                // تعیین / تغییر پین ۴ رقمی
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSettingsSheet = false
                            if (securityRepo.isPinSet()) {
                                pinActionSubtitle = strings.changePin
                                pinDialogMode = PinDialogMode.CHANGE
                            } else {
                                pinActionSubtitle = strings.pinPromptSetFirst
                                pinDialogMode = PinDialogMode.SET
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (securityRepo.isPinSet()) strings.changePin else strings.setPinTitle,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (securityRepo.isPinSet()) {
                    // پین فریب اضطراری (Decoy PIN)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (securityRepo.isDecoyPinSet()) {
                                    securityRepo.removeDecoyPin()
                                    Toast.makeText(context, strings.decoyPinRemoved, Toast.LENGTH_SHORT).show()
                                } else {
                                    showSettingsSheet = false
                                    pinActionSubtitle = strings.decoyPinSubtitle
                                    pinDialogMode = PinDialogMode.SET_DECOY
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (securityRepo.isDecoyPinSet()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (securityRepo.isDecoyPinSet()) strings.removeDecoyPin else strings.setDecoyPin,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                strings.decoyPinSubtitle,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // حذف پین
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSettingsSheet = false
                                securityRepo.removePin()
                                Toast.makeText(context, strings.pinRemovedSuccess, Toast.LENGTH_SHORT).show()
                                refreshList()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.removePin, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    }
                }

                // بازگشایی با اثر انگشت (بیومتریک)
                var biometricEnabled by remember { mutableStateOf(securityRepo.isBiometricEnabled()) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newVal = !biometricEnabled
                            securityRepo.setBiometricEnabled(newVal)
                            biometricEnabled = newVal
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.biometricUnlock, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(strings.biometricSubtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = {
                            securityRepo.setBiometricEnabled(it)
                            biometricEnabled = it
                        }
                    )
                }

                // محافظت در منوی برنامه‌های اخیر (FLAG_SECURE)
                var flagSecureEnabled by remember { mutableStateOf(securityRepo.isFlagSecureEnabled()) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newVal = !flagSecureEnabled
                            securityRepo.setFlagSecureEnabled(newVal)
                            flagSecureEnabled = newVal
                            (context as? MainActivity)?.applyFlagSecure()
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.flagSecureTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(strings.flagSecureSubtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = flagSecureEnabled,
                        onCheckedChange = {
                            securityRepo.setFlagSecureEnabled(it)
                            flagSecureEnabled = it
                            (context as? MainActivity)?.applyFlagSecure()
                        }
                    )
                }

                // زمان قفل خودکار نشست (Auto-Lock Timeout)
                var autoLockMinutes by remember { mutableStateOf(securityRepo.getAutoLockTimeoutMinutes()) }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(strings.autoLockTimeout, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to strings.autoLockImmediate,
                            1 to strings.autoLock1Min,
                            5 to strings.autoLock5Min
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = autoLockMinutes == mins,
                                onClick = {
                                    securityRepo.setAutoLockTimeoutMinutes(mins)
                                    autoLockMinutes = mins
                                },
                                label = { Text(label, fontSize = 11.5.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(14.dp))

                // ۷. پشتیبان‌گیری و بازگردانی آفلاین
                Text(strings.exportBackup + " / " + strings.restoreBackup, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            createBackupLauncher.launch("smsy_backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportBackup, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            openBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.restoreBackup, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // شیت مدیریت فهرست سیاه قطعی
    if (showBlocklistDialog) {
        val blockedList by smsRepository.blocklistRepository.getAllBlocked().collectAsState(initial = emptyList())
        var newSenderInput by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showBlocklistDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.definitiveBlocklist, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showBlocklistDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (strings.isFa) "پیامک‌های دریافتی از این سرشماره‌ها بدون ارزیابی مستقیم قرنطینه می‌شوند."
                    else "Incoming messages from these senders are isolated immediately without evaluation.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSenderInput,
                        onValueChange = { newSenderInput = it },
                        placeholder = { Text(if (strings.isFa) "شماره یا سرشماره..." else "Number or sender...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newSenderInput.isNotBlank()) {
                                scope.launch {
                                    smsRepository.blocklistRepository.blockSender(newSenderInput.trim())
                                    newSenderInput = ""
                                    refreshList()
                                }
                            }
                        }
                    ) {
                        Text(strings.add)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (blockedList.isEmpty()) {
                    Text(
                        if (strings.isFa) "هیچ سرشماره‌ای در فهرست سیاه نیست." else "No senders in blocklist.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(blockedList, key = { it.sender }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.sender, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(item.reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            smsRepository.blocklistRepository.unblockSender(item.sender)
                                            refreshList()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }

    // شیت مدیریت پاسخ‌های آماده و استخراج هوشمند
    if (showTemplatesDialog) {
        val templatesList by templateRepo.getAllTemplates().collectAsState(initial = emptyList())
        var newTemplateInput by remember { mutableStateOf("") }
        var editingTemplateId by remember { mutableStateOf<Long?>(null) }
        var isExtracting by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = {
                showTemplatesDialog = false
                editingTemplateId = null
                newTemplateInput = ""
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.manageTemplates, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = {
                        showTemplatesDialog = false
                        editingTemplateId = null
                        newTemplateInput = ""
                    }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!isExtracting) {
                            isExtracting = true
                            scope.launch {
                                val added = templateRepo.autoImportSmartTemplates()
                                isExtracting = false
                                val msg = if (strings.isFa) "$added الگوی پرتکرار از تاریخچه ارسالی شما استخراج شد"
                                else "$added frequent response templates extracted"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isExtracting) {
                            if (strings.isFa) "در حال تحلیل پیام‌ها..." else "Analyzing sent messages..."
                        } else {
                            if (strings.isFa) "استخراج خودکار پاسخ‌ها با هوش مصنوعی محلی" else "Auto-extract templates with offline AI"
                        },
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTemplateInput,
                        onValueChange = { newTemplateInput = it },
                        placeholder = {
                            Text(
                                if (editingTemplateId != null) strings.editTemplate
                                else (if (strings.isFa) "متن پاسخ آماده جدید..." else "New template text..."),
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val clean = newTemplateInput.trim()
                            if (clean.isNotBlank()) {
                                scope.launch {
                                    val editId = editingTemplateId
                                    if (editId != null) {
                                        templateRepo.updateTemplate(editId, clean)
                                        editingTemplateId = null
                                    } else {
                                        templateRepo.addTemplate(clean)
                                    }
                                    newTemplateInput = ""
                                }
                            }
                        }
                    ) {
                        Text(if (editingTemplateId != null) strings.save else strings.add)
                    }
                    if (editingTemplateId != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            editingTemplateId = null
                            newTemplateInput = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancel, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (templatesList.isEmpty()) {
                    Text(
                        if (strings.isFa) "هیچ پاسخ آماده‌ای ثبت نشده است." else "No quick templates saved.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(templatesList, key = { it.id }) { item ->
                            val isEditingThis = editingTemplateId == item.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isEditingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (item.usageCount > 0) {
                                            Text(
                                                if (strings.isFa) "${item.usageCount} بار استفاده شده" else "Used ${item.usageCount} times",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                editingTemplateId = item.id
                                                newTemplateInput = item.text
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = strings.edit,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    if (editingTemplateId == item.id) {
                                                        editingTemplateId = null
                                                        newTemplateInput = ""
                                                    }
                                                    templateRepo.deleteTemplate(item.id)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }

    // دیالوگ اعتبارسنجی / تنظیم رمز عبور ۴ رقمی (PIN)
    if (pinDialogMode != null) {
        PinDialog(
            mode = pinDialogMode!!,
            securityRepository = securityRepo,
            subtitle = pinActionSubtitle,
            onSuccess = {
                val pendingConv = pendingUnlockedConversation
                val lockTarget = conversationToLockUnlock
                pinDialogMode = null
                pendingUnlockedConversation = null
                conversationToLockUnlock = null

                if (pendingConv != null) {
                    onConversationClick(pendingConv)
                } else if (lockTarget != null) {
                    val lockKey = securityRepo.getLockKey(lockTarget)
                    if (lockTarget.isLocked) {
                        securityRepo.setConversationLocked(lockKey, false)
                        Toast.makeText(context, strings.conversationUnlockedSuccess, Toast.LENGTH_SHORT).show()
                    } else {
                        securityRepo.setConversationLocked(lockKey, true)
                        Toast.makeText(context, strings.conversationLockedSuccess, Toast.LENGTH_SHORT).show()
                    }
                    refreshList()
                } else {
                    Toast.makeText(context, strings.pinChangedSuccess, Toast.LENGTH_SHORT).show()
                    refreshList()
                }
            },
            onDismiss = {
                pinDialogMode = null
                pendingUnlockedConversation = null
                conversationToLockUnlock = null
            }
        )
    }

    // شیت نام‌گذاری و ادغام سرشماره‌ها
    if (conversationToMerge != null) {
        val target = conversationToMerge!!
        var aliasName by remember(target) {
            mutableStateOf(target.contactName ?: "")
        }
        var currentNumbers by remember(target) {
            mutableStateOf(
                if (target.mergedAddresses.isNotEmpty()) target.mergedAddresses
                else setOf(target.address)
            )
        }
        var newNumberInput by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { conversationToMerge = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.nameAndMerge, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { conversationToMerge = null }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (strings.isFa) "یک نام دلخواه انتخاب کنید و چندین سرشماره مختلف را در یک گفتگوی یکپارچه ادغام نمایید."
                    else "Set a custom name and merge multiple senders into one unified thread.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = aliasName,
                    onValueChange = { aliasName = it },
                    label = { Text(if (strings.isFa) "نام سرشماره یا مجموعه" else "Custom Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    (if (strings.isFa) "سرشماره‌های ادغام‌شده" else "Merged Senders") + " (${currentNumbers.size}):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (num in currentNumbers) {
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(num, fontSize = 12.sp) },
                            trailingIcon = {
                                if (currentNumbers.size > 1) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                currentNumbers = currentNumbers - num
                                            }
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newNumberInput,
                        onValueChange = { newNumberInput = it },
                        placeholder = { Text(if (strings.isFa) "افزودن سرشماره دیگر..." else "Add number...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val clean = newNumberInput.trim()
                            if (clean.isNotBlank()) {
                                currentNumbers = currentNumbers + clean
                                newNumberInput = ""
                            }
                        },
                        enabled = newNumberInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.add)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (target.aliasId != null) {
                        OutlinedButton(
                            onClick = {
                                smsRepository.aliasRepository.deleteAlias(target.aliasId)
                                conversationToMerge = null
                                refreshList()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (strings.isFa) "تفکیک (حذف ادغام)" else "Unmerge")
                        }
                    }
                    Button(
                        onClick = {
                            if (aliasName.isNotBlank() && currentNumbers.isNotEmpty()) {
                                smsRepository.aliasRepository.saveAlias(
                                    displayName = aliasName,
                                    numbers = currentNumbers,
                                    existingId = target.aliasId
                                )
                                conversationToMerge = null
                                refreshList()
                            }
                        },
                        enabled = aliasName.isNotBlank() && currentNumbers.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.save)
                    }
                }
            }
        }
    }

    // دیالوگ تایید حذف گفتگو
    if (conversationToDelete != null) {
        val target = conversationToDelete!!
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete entire thread with ${target.contactName ?: target.address}? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            smsRepository.deleteConversation(target.threadId, target.address, target.mergedAddresses)
                            conversationToDelete = null
                            refreshList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // دیالوگ تایید انتقال به اسپم (قرنطینه)
    if (conversationToSpam != null) {
        val target = conversationToSpam!!
        AlertDialog(
            onDismissRequest = { conversationToSpam = null },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Move to Spam?", fontWeight = FontWeight.Bold) },
            text = {
                Text("All messages from ${target.contactName ?: target.address} will be moved to quarantine and deleted from inbox.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toSpam = target
                        conversationToSpam = null
                        scope.launch {
                            smsRepository.quarantineConversation(
                                threadId = toSpam.threadId,
                                address = toSpam.address,
                                mergedAddresses = toSpam.mergedAddresses,
                                spamRepository = spamRepository
                            )
                            refreshList()
                        }
                    }
                ) {
                    Text("Move to Spam")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToSpam = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // شیت افزودن سرشماره به گروه ادغام موجود
    if (conversationToAddToGroup != null) {
        val target = conversationToAddToGroup!!
        val allAliases = remember(target) { smsRepository.aliasRepository.getAllAliases() }
        ModalBottomSheet(
            onDismissRequest = { conversationToAddToGroup = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.addToExistingGroup, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { conversationToAddToGroup = null }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (allAliases.isEmpty()) {
                    Text(
                        if (strings.isFa) "هنوز هیچ گروه ادغامی وجود ندارد. ابتدا از گزینه ادغام سرشماره‌ها یک گروه بسازید."
                        else "No merged groups exist yet. Create a group first with 'Name & Merge Senders'.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (strings.isFa) "گروه مورد نظر برای افزودن ${target.contactName ?: target.address} را انتخاب کنید:"
                        else "Select an existing group to add ${target.contactName ?: target.address} into:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allAliases, key = { it.id }) { alias ->
                            Surface(
                                onClick = {
                                    val targetNumbers = if (target.mergedAddresses.isNotEmpty()) target.mergedAddresses else setOf(target.address)
                                    smsRepository.aliasRepository.addNumbersToAlias(alias.id, targetNumbers)
                                    conversationToAddToGroup = null
                                    refreshList()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alias.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${alias.numbers.size} numbers (${alias.numbers.take(3).joinToString(", ")})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

    // دیالوگ تایید انتقال گروهی به اسپم (قرنطینه)
    if (showBatchSpamDialog && selectedConversations.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchSpamDialog = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Quarantine ${selectedConversations.size} conversations?", fontWeight = FontWeight.Bold) },
            text = {
                Text("All messages from the ${selectedConversations.size} selected senders will be moved to spam quarantine and removed from your inbox.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toSpam = selectedConversations.toList()
                        showBatchSpamDialog = false
                        isSelectionMode = false
                        selectedConversations = emptySet()
                        scope.launch {
                            smsRepository.quarantineConversations(toSpam, spamRepository)
                            refreshList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Spam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchSpamDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // دیالوگ تایید حذف گروهی گفتگوها
    if (showBatchDeleteDialog && selectedConversations.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedConversations.size} conversations?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete all messages for the ${selectedConversations.size} selected conversations? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = selectedConversations.toList()
                        showBatchDeleteDialog = false
                        isSelectionMode = false
                        selectedConversations = emptySet()
                        scope.launch {
                            smsRepository.deleteConversations(toDelete)
                            refreshList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // راهنمای تصویری باز کردن Restricted Settings در اندروید ۱۳ و ۱۴
    if (showRestrictedHelpDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedHelpDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Samsung Security Notice", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Because Smsy was installed via APK (sideloaded), Android has temporarily restricted changing default apps.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Quick 5-second fix:\n1. Tap 'Open Settings' below.\n2. Tap the 3 dots (⋮) in the top-right corner.\n3. Select 'Allow restricted settings'.\n4. Return here and tap 'Set as Default'.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRestrictedHelpDialog = false
                    openAppSettings(context)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedHelpDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // دیالوگ وضعیت اسکن زنده
    if (isScanning && scanProgress != null) {
        val p = scanProgress!!
        val fraction = if (p.total > 0) p.scanned.toFloat() / p.total else 0f
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Cleaning Historical SMS...", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Analyzing with on-device ONNX AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Scanned ${p.scanned} of ${p.total} messages...",
                        fontSize = 13.sp
                    )
                    if (p.quarantined > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${p.quarantined} spam messages quarantined.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // دیالوگ گزارش پایانی
    if (showResultDialog && finalResult != null) {
        val res = finalResult!!
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("Scan Complete", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Total messages scanned: ${res.scanned}", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Spam messages quarantined: ${res.quarantined}",
                        color = if (res.quarantined > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Quarantined messages were moved to the 'Quarantine' tab and can be reviewed anytime.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showResultDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isSolarCalendar: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isUnread = conversation.unreadCount > 0
    val strings = LocalAppStrings.current
    val itemBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)) + shrinkHorizontally(animationSpec = tween(180))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
        }

        ContactAvatar(
            photoUri = conversation.photoUri,
            nameOrAddress = conversation.contactName ?: conversation.address,
            isUnread = isUnread,
            size = 46.dp,
            isMerged = conversation.mergedAddresses.size > 1
        )

        Spacer(modifier = Modifier.width(12.dp))

        // ستون میانی: نام مخاطب و نشان ادغام در بالا، متن خلاصه پیام در پایین
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = conversation.contactName ?: conversation.address,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (conversation.isLocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                if (conversation.mergedAddresses.size > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CallMerge,
                                contentDescription = "Merged Senders",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${conversation.mergedAddresses.size}",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (conversation.isLocked) {
                Text(
                    text = strings.lockedMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            } else {
                val cleanSnippet = remember(conversation.snippet) {
                    conversation.snippet.replace(Regex("\\s+"), " ").trim()
                }
                val isRtl = remember(cleanSnippet) {
                    cleanSnippet.any { it in '\u0600'..'\u06FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }
                }
                val layoutDir = LocalLayoutDirection.current
                val textAlignment = if (isRtl) {
                    if (layoutDir == LayoutDirection.Rtl) TextAlign.Start else TextAlign.Right
                } else {
                    TextAlign.Start
                }

                Text(
                    text = cleanSnippet,
                    color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = textAlignment,
                    style = TextStyle(
                        textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Content
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // ستون انتهایی یکپارچه: تاریخ و سنجاق در بالا، بج پیام خوانده‌نشده در پایین
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                val formattedDate = remember(conversation.date, isSolarCalendar) {
                    DateUtils.formatConversationDate(conversation.date, isSolarCalendar)
                }
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                )
            }

            if (isUnread) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun MessageSearchResultItem(
    result: MessageSearchResult,
    isSolarCalendar: Boolean = true,
    onClick: () -> Unit
) {
    val dateText = remember(result.date, isSolarCalendar) {
        DateUtils.formatConversationDate(result.date, isSolarCalendar)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            photoUri = result.photoUri,
            nameOrAddress = result.contactName ?: result.address,
            size = 44.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.contactName ?: result.address,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateText,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = (if (result.isSent) "✓ " else "") + result.body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun checkIsDefaultSmsApp(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_SMS) ?: false
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

fun requestDefaultSmsRole(context: Context, launcher: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        if (intent != null) {
            launcher(intent)
        } else {
            openDefaultAppsSettings(context)
        }
    } else {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
        launcher(intent)
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

fun openDefaultAppsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppSettings(context)
    }
}
