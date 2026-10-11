package com.binacity.spamguard.ui.chat

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binacity.spamguard.data.model.Message
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.ui.util.DateUtils
import kotlinx.coroutines.launch

data class SmsCounter(
    val charCount: Int,
    val remaining: Int,
    val parts: Int,
    val maxSingle: Int
)

fun calculateSmsCounter(text: String): SmsCounter {
    val isGsm7 = text.all { c -> c.code < 128 }
    val maxSingle = if (isGsm7) 160 else 70
    val maxMultipart = if (isGsm7) 153 else 67

    val len = text.length
    if (len == 0) {
        return SmsCounter(charCount = 0, remaining = maxSingle, parts = 1, maxSingle = maxSingle)
    }

    if (len <= maxSingle) {
        return SmsCounter(charCount = len, remaining = maxSingle - len, parts = 1, maxSingle = maxSingle)
    }

    val parts = ((len - 1) / maxMultipart) + 1
    val remaining = (parts * maxMultipart) - len
    return SmsCounter(charCount = len, remaining = remaining, parts = parts, maxSingle = maxSingle)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    threadId: Long,
    address: String,
    contactName: String?,
    smsRepository: SmsRepository,
    onBack: () -> Unit,
    mergedAddresses: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var messageForAction by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // بارگذاری اولیه پیام‌ها و علامت‌گذاری به عنوان خوانده‌شده
    LaunchedEffect(threadId, address, mergedAddresses) {
        messages = smsRepository.getMessages(threadId, address, mergedAddresses)
        smsRepository.markThreadAsRead(threadId, address, mergedAddresses)
    }

    // لیسنر بلادرنگ برای دریافت پیامک‌های جدید به محض ورود به سیستم‌عامل
    DisposableEffect(threadId, address, mergedAddresses) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch {
                    messages = smsRepository.getMessages(threadId, address, mergedAddresses)
                    smsRepository.markThreadAsRead(threadId, address, mergedAddresses)
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
            // تضمین علامت‌گذاری خوانده‌شده هنگام خروج از صفحه چت
            scope.launch {
                smsRepository.markThreadAsRead(threadId, address, mergedAddresses)
            }
        }
    }

    // اسکرول مستقیم به آخرین پیام در ورود اولیه بدون انیمیشن آزاردهنده
    var isInitialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (!isInitialScrollDone) {
                listState.scrollToItem(messages.size - 1)
                isInitialScrollDone = true
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = contactName ?: address,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (mergedAddresses.size > 1) {
                            Text(
                                text = "${mergedAddresses.size} merged senders",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (contactName != null) {
                            Text(
                                text = address,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // دکمه تماس مستقیم با فرستنده پیامک
                    if (address.any { it.isDigit() }) {
                        IconButton(
                            onClick = {
                                val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$address")
                                }
                                context.startActivity(callIntent)
                            }
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Text message", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            textStyle = LocalTextStyle.current.copy(
                                textDirection = TextDirection.Content,
                                fontSize = 14.sp
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // شمارنده پیامک پویا بر اساس طول کاراکتر و صفحات پیامک
                        val counter = remember(inputText) { calculateSmsCounter(inputText) }
                        if (inputText.isNotEmpty()) {
                            Text(
                                text = "${counter.remaining}/${counter.maxSingle}" + if (counter.parts > 1) " (${counter.parts})" else "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val textToSend = inputText
                                    inputText = ""
                                    scope.launch {
                                        val success = smsRepository.sendMessage(address, textToSend)
                                        if (success) {
                                            messages = smsRepository.getMessages(threadId, address, mergedAddresses)
                                        }
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier.background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(50)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val alignment = if (msg.isSent) Alignment.CenterEnd else Alignment.CenterStart
                val bg = if (msg.isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val textColor = if (msg.isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                val timeColor = if (msg.isSent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = alignment
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { messageForAction = msg }
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            // در صورتی که سرشماره‌ها ادغام شده باشند، سرشماره دقیق ارسال‌کننده نمایش داده می‌شود
                            if (mergedAddresses.size > 1 && !msg.isSent && msg.address.isNotBlank()) {
                                Text(
                                    text = msg.address,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            Text(
                                text = msg.body,
                                color = textColor,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = DateUtils.formatMessageTime(msg.date),
                                color = timeColor,
                                fontSize = 10.sp,
                                modifier = Modifier.align(if (msg.isSent) Alignment.End else Alignment.Start)
                            )
                        }
                    }
                }
            }
        }
    }

    // دیالوگ منوی پیام با نگه‌داشتن طولانی (Copy / Delete)
    if (messageForAction != null) {
        val target = messageForAction!!
        AlertDialog(
            onDismissRequest = { messageForAction = null },
            title = { Text("Message Options", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(target.body))
                            messageForAction = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Copy text", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                smsRepository.deleteMessage(target.id)
                                messages = smsRepository.getMessages(threadId, address, mergedAddresses)
                                messageForAction = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Delete message", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { messageForAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
