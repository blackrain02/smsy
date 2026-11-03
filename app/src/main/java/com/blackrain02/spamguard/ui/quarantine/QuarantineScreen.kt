package com.blackrain02.spamguard.ui.quarantine

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.blackrain02.spamguard.data.local.SpamMessageEntity
import com.blackrain02.spamguard.data.model.isRtlSnippet
import com.blackrain02.spamguard.data.repository.SmsRepository
import com.blackrain02.spamguard.data.repository.SpamRepository
import com.blackrain02.spamguard.ui.util.DateUtils
import com.blackrain02.spamguard.ui.util.LocalAppStrings
import kotlinx.coroutines.launch

data class SpamSenderGroup(
    val sender: String,
    val contactName: String?,
    val latestMessage: SpamMessageEntity,
    val messages: List<SpamMessageEntity>,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantineScreen(
    spamRepository: SpamRepository,
    smsRepository: SmsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val isSolarCalendar = remember { DateUtils.isSolarCalendar(context) }

    val spamList by spamRepository.getAllSpam().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var senderToDelete by remember { mutableStateOf<SpamSenderGroup?>(null) }
    var selectedGroupDetails by remember { mutableStateOf<SpamSenderGroup?>(null) }

    // تجمیع پیامک‌های هرزنامه به تفکیک سرشماره و جستجو
    val groupedSenders = remember(spamList, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            spamList
        } else {
            val q = searchQuery.trim().lowercase()
            spamList.filter {
                val name = smsRepository.getContactName(it.sender)?.lowercase() ?: ""
                it.sender.lowercase().contains(q) || it.body.lowercase().contains(q) || name.contains(q)
            }
        }

        filtered.groupBy { it.sender }
            .map { (sender, msgs) ->
                val sorted = msgs.sortedByDescending { it.timestamp }
                val contactName = smsRepository.getContactName(sender)
                SpamSenderGroup(
                    sender = sender,
                    contactName = contactName,
                    latestMessage = sorted.first(),
                    messages = sorted,
                    count = msgs.size
                )
            }
            .sortedByDescending { it.latestMessage.timestamp }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.quarantineTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (spamList.isNotEmpty()) {
                                    Text(
                                        text = strings.quarantinedSummary(groupedSenders.size, spamList.size),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (spamList.isNotEmpty()) {
                            IconButton(onClick = { showClearAllDialog = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = strings.clearAll,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // نوار جستجوی مینیمال
                if (spamList.isNotEmpty() || searchQuery.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = strings.searchSpamHint,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (spamList.isEmpty()) {
            // وضعیت خالی: محافظت فعال و بدون هرزنامه
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.quarantinedEmptyTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.quarantinedEmptyDesc,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else if (groupedSenders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.noMessagesMatch(searchQuery),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(groupedSenders, key = { it.sender }) { group ->
                    QuarantineSenderItem(
                        group = group,
                        isSolarCalendar = isSolarCalendar,
                        onClick = { selectedGroupDetails = group },
                        onRestore = {
                            scope.launch {
                                spamRepository.restoreSenderToInbox(group.sender)
                                Toast.makeText(context, strings.restoreSenderSuccess, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            senderToDelete = group
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                        thickness = 0.8.dp
                    )
                }
            }
        }
    }

    // شیت جزئیات و مرور پیام‌های یک فرستنده قرنطینه‌شده
    selectedGroupDetails?.let { group ->
        ModalBottomSheet(
            onDismissRequest = { selectedGroupDetails = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // هدر شیت
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = group.contactName ?: group.sender,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (group.contactName != null) {
                                Text(
                                    text = group.sender,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = { selectedGroupDetails = null }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // دکمه‌های عملیات کلی فرستنده
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                spamRepository.restoreSenderToInbox(group.sender)
                                Toast.makeText(context, strings.restoreSenderSuccess, Toast.LENGTH_SHORT).show()
                                selectedGroupDetails = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (group.count > 1) "${strings.restoreToInbox} (${group.count})" else strings.restoreToInbox,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            senderToDelete = group
                            selectedGroupDetails = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.delete, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.messagesCount(group.count),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // لیست پیامک‌های این فرستنده
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(group.messages, key = { it.id }) { msg ->
                        val cleanBody = msg.body.trim()
                        val isRtl = isRtlSnippet(cleanBody)
                        val layoutDir = LocalLayoutDirection.current
                        val textAlignment = if (isRtl) {
                            if (layoutDir == LayoutDirection.Rtl) TextAlign.Start else TextAlign.Right
                        } else {
                            TextAlign.Start
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val spamPercent = (msg.spamProbability * 100).toInt().coerceIn(70, 100)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "$spamPercent% ${strings.spamScore}",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Text(
                                        text = DateUtils.formatMessageDetailTime(msg.timestamp, isSolarCalendar),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = cleanBody,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = textAlignment,
                                    style = TextStyle(
                                        textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Content
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                spamRepository.restoreToInbox(msg)
                                                Toast.makeText(context, strings.restoreSenderSuccess, Toast.LENGTH_SHORT).show()
                                                if (group.messages.size <= 1) {
                                                    selectedGroupDetails = null
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.restoreToInbox, fontSize = 11.5.sp)
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                spamRepository.deleteSpam(msg.id)
                                                Toast.makeText(context, strings.deleteSenderSuccess, Toast.LENGTH_SHORT).show()
                                                if (group.messages.size <= 1) {
                                                    selectedGroupDetails = null
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.delete, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // دیالوگ پاکسازی همه پیام‌های قرنطینه
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.clearAll, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text(strings.clearAllSpamConfirm, fontSize = 13.5.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        scope.launch {
                            spamRepository.deleteAllSpam()
                            Toast.makeText(context, strings.deleteSenderSuccess, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.clearAll)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // دیالوگ تایید حذف پیام‌های یک فرستنده
    if (senderToDelete != null) {
        val target = senderToDelete!!
        AlertDialog(
            onDismissRequest = { senderToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.delete, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text(strings.deleteSenderSpamConfirm, fontSize = 13.5.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        val sender = target.sender
                        senderToDelete = null
                        scope.launch {
                            spamRepository.deleteBySender(sender)
                            Toast.makeText(context, strings.deleteSenderSuccess, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { senderToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

/**
 * ردیف مینیمال و استاندارد برای هر فرستنده هرزنامه در لیست قرنطینه
 */
@Composable
fun QuarantineSenderItem(
    group: SpamSenderGroup,
    isSolarCalendar: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanSnippet = group.latestMessage.body.replace(Regex("\\s+"), " ").trim()
    val isRtl = isRtlSnippet(cleanSnippet)
    val layoutDir = LocalLayoutDirection.current
    val textAlignment = if (isRtl) {
        if (layoutDir == LayoutDirection.Rtl) TextAlign.Start else TextAlign.Right
    } else {
        TextAlign.Start
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // آواتار هرزنامه با نشان تعداد
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (group.count > 1) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = group.count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ستون محتوا
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = group.contactName ?: group.sender,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                val pct = (group.latestMessage.spamProbability * 100).toInt().coerceIn(70, 100)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "$pct%",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = cleanSnippet,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlignment,
                style = TextStyle(
                    textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Content
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // تاریخ و دکمه‌های عملیات سریع
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = DateUtils.formatConversationDate(group.latestMessage.timestamp, isSolarCalendar),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
