package com.binacity.spamguard.ui.conversations

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.binacity.spamguard.ai.SpamClassifier
import com.binacity.spamguard.data.model.Conversation
import com.binacity.spamguard.data.repository.ScanProgress
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.data.repository.SpamRepository
import com.binacity.spamguard.ui.util.DateUtils
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
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    cachedConversations: List<Conversation> = emptyList(),
    onConversationsLoaded: (List<Conversation>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var conversations by remember { mutableStateOf(cachedConversations) }

    LaunchedEffect(cachedConversations) {
        if (cachedConversations.isNotEmpty() && conversations.isEmpty()) {
            conversations = cachedConversations
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var isDefaultApp by remember { mutableStateOf(checkIsDefaultSmsApp(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<ScanProgress?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var finalResult by remember { mutableStateOf<ScanProgress?>(null) }
    var showRestrictedHelpDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    var selectedConversationForAction by remember { mutableStateOf<Conversation?>(null) }
    var conversationToMerge by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()

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
                val fresh = smsRepository.getConversations()
                conversations = fresh
                onConversationsLoaded(fresh)
            } catch (e: Throwable) {
                e.printStackTrace()
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

    // لیسنر بلادرنگ تغییرات پیامک‌ها در سیستم‌عامل برای همگام‌سازی فوری تعداد خوانده‌نشده
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (hasSmsPermission) {
                    refreshList()
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
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
            TopAppBar(
                title = { Text("Smsy", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
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
                            contentDescription = "Scan & Clean Spam",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMessageClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Conversation")
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
                                "Set as default SMS app",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Required for offline AI spam protection",
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
                            Text("Set as Default", fontSize = 12.sp)
                        }
                    }
                }
            }

            // فیلد جستجو در گفتگوها
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search messages and contacts...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
            } else if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No messages match '$searchQuery'" else "No messages found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredConversations, key = { "${it.threadId}_${it.address}_${it.aliasId ?: ""}" }) { item ->
                        ConversationItem(
                            conversation = item,
                            onClick = { onConversationClick(item) },
                            onLongClick = { selectedConversationForAction = item }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }

    // منوی عملیات گفتگو (سنجاق کردن / ادغام سرشماره‌ها / حذف)
    if (selectedConversationForAction != null) {
        val target = selectedConversationForAction!!
        AlertDialog(
            onDismissRequest = { selectedConversationForAction = null },
            title = {
                Text(
                    text = target.contactName ?: target.address,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            smsRepository.togglePin(target.address)
                            selectedConversationForAction = null
                            refreshList()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                if (target.isPinned) "Unpin from top" else "Pin to top",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // گزینه تنظیم نام و ادغام سرشماره‌ها
                    TextButton(
                        onClick = {
                            conversationToMerge = target
                            selectedConversationForAction = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CallMerge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                if (target.aliasId != null) "Edit Name & Merged Senders" else "Set Name & Merge Senders",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    TextButton(
                        onClick = {
                            conversationToDelete = target
                            selectedConversationForAction = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                "Delete conversation",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedConversationForAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // دیالوگ نام‌گذاری و ادغام سرشماره‌ها
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

        AlertDialog(
            onDismissRequest = { conversationToMerge = null },
            title = { Text("Name & Merge Senders", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Set a custom name (e.g. Bank Mellat) and merge multiple numbers into one unified thread.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = aliasName,
                        onValueChange = { aliasName = it },
                        label = { Text("Custom Name (e.g. Bank Mellat)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Merged Numbers (${currentNumbers.size}):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            placeholder = { Text("Add number...", fontSize = 12.sp) },
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
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            },
            confirmButton = {
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
                    enabled = aliasName.isNotBlank() && currentNumbers.isNotEmpty()
                ) {
                    Text("Save & Merge")
                }
            },
            dismissButton = {
                Row {
                    if (target.aliasId != null) {
                        TextButton(
                            onClick = {
                                smsRepository.aliasRepository.deleteAlias(target.aliasId)
                                conversationToMerge = null
                                refreshList()
                            }
                        ) {
                            Text("Unmerge", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { conversationToMerge = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isUnread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initials = (conversation.contactName ?: conversation.address).take(1).uppercase()
        val avatarBg = if (isUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        val avatarTextColor = if (isUnread) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier = Modifier
                .size(46.dp)
                .background(avatarBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = avatarTextColor
            )
        }

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

                if (conversation.mergedAddresses.size > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${conversation.mergedAddresses.size} senders",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.snippet,
                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
            )
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
                Text(
                    text = DateUtils.formatConversationDate(conversation.date),
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
