package com.binacity.spamguard.ui.chat

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binacity.spamguard.data.model.Message
import com.binacity.spamguard.data.repository.ScheduledMessageRepository
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.data.repository.TemplateRepository
import com.binacity.spamguard.ui.util.DateUtils
import com.binacity.spamguard.ui.util.OtpHelper
import com.binacity.spamguard.ui.util.SimHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isSolarCalendar = remember { DateUtils.isSolarCalendar(context) }
    val clipboardManager = LocalClipboardManager.current
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var messageForAction by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val activeSims = remember { SimHelper.getActiveSims(context) }
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    val currentSim = activeSims.getOrNull(selectedSimIndex)

    val templateRepo = remember { TemplateRepository(context) }
    val templates by templateRepo.getAllTemplates().collectAsState(initial = emptyList())

    val scheduledRepo = remember { ScheduledMessageRepository(context) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    // بارگذاری اولیه پیام‌ها و علامت‌گذاری به عنوان خوانده‌شده
    LaunchedEffect(threadId, address, mergedAddresses) {
        messages = smsRepository.getMessages(threadId, address, mergedAddresses)
        smsRepository.markThreadAsRead(threadId, address, mergedAddresses)
    }

    // لیسنر بلادرنگ برای دریافت پیامک‌های جدید به محض ورود به سیستم‌عامل
    DisposableEffect(threadId, address, mergedAddresses) {
        var debounceJob: Job? = null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(500)
                    try {
                        messages = smsRepository.getMessages(threadId, address, mergedAddresses)
                        // فقط بروزرسانی کش درون حافظه جهت جلوگیری از لوپ اعلان بی‌پایان به دیتابیس
                        smsRepository.markKeyAsReadInMemory(address, mergedAddresses)
                    } catch (e: Throwable) {
                        e.printStackTrace()
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
            // تضمین علامت‌گذاری خوانده‌شده هنگام خروج از صفحه چت با CoroutineScope پایدار ریپازیتوری
            smsRepository.markThreadAsReadAsync(threadId, address, mergedAddresses)
        }
    }

    // اسکرول مستقیم به آخرین پیام در ورود اولیه بدون انیمیشن آزاردهنده
    var isInitialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            try {
                if (!isInitialScrollDone) {
                    listState.scrollToItem(messages.size - 1)
                    isInitialScrollDone = true
                } else {
                    listState.animateScrollToItem(messages.size - 1)
                }
            } catch (e: Throwable) {
                // نادیده گرفتن انصراف انیمیشن حین ترنزیشن صفحات
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
                    // دکمه افزودن/ویرایش مخاطب برای شماره‌های ذخیره‌نشده در دفترچه تلفن
                    if (contactName.isNullOrBlank() && address.any { it.isDigit() }) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallbackIntent = Intent(Intent.ACTION_INSERT).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                    }
                                    context.startActivity(fallbackIntent)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Add or update contact",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

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
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    // ردیف چیپ‌های پاسخ‌های آماده سریع
                    if (templates.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(templates.take(8), key = { it.id }) { tmpl ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable {
                                        inputText = if (inputText.isBlank()) tmpl.text else "$inputText ${tmpl.text}"
                                        scope.launch { templateRepo.useTemplate(tmpl.id) }
                                    }
                                ) {
                                    Text(
                                        text = tmpl.text,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 4,
                                    textStyle = LocalTextStyle.current.copy(
                                        textDirection = TextDirection.Content,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (inputText.isEmpty()) {
                                            Text(
                                                "Text message",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                val counter = remember(inputText) { calculateSmsCounter(inputText) }
                                if (inputText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${counter.remaining}/${counter.maxSingle}" + if (counter.parts > 1) " (${counter.parts})" else "",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // دکمه انتخاب سیم‌کارت در گوشی‌های ۲ سیم‌کارت
                        if (activeSims.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable {
                                    selectedSimIndex = (selectedSimIndex + 1) % activeSims.size
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        "SIM ${(currentSim?.slotIndex ?: 0) + 1}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // دکمه زمان‌بندی ارسال پیامک
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    showScheduleDialog = true
                                } else {
                                    Toast.makeText(context, "ابتدا متن پیام را وارد کنید", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Schedule SMS",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .clickable(enabled = inputText.isNotBlank()) {
                                    if (inputText.isNotBlank()) {
                                        val textToSend = inputText
                                        inputText = ""
                                        scope.launch {
                                            val success = smsRepository.sendMessage(address, textToSend, currentSim?.subscriptionId)
                                            if (success) {
                                                messages = smsRepository.getMessages(threadId, address, mergedAddresses)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val uniqueMessages = remember(messages) {
            val seen = HashSet<String>()
            messages.filter { msg ->
                val key = if (msg.id > 0) msg.id.toString() else "${msg.date}_${msg.body.hashCode()}"
                seen.add(key)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uniqueMessages, key = { if (it.id > 0) it.id else "${it.date}_${it.body.hashCode()}" }) { msg ->
                val alignment = if (msg.isSent) Alignment.CenterEnd else Alignment.CenterStart
                val bg = if (msg.isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val textColor = if (msg.isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                val timeColor = if (msg.isSent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                val otpCode = remember(msg.body) { OtpHelper.extractOtp(msg.body) }

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

                            // کارت هوشمند استخراج و کپی رمز یک‌بارمصرف (OTP) با قابلیت حذف تکی
                            if (otpCode != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (msg.isSent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "کد: $otpCode",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(otpCode))
                                                    Toast.makeText(context, "کد تایید کپی شد", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("کپی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        smsRepository.deleteMessage(msg.id)
                                                        messages = smsRepository.getMessages(threadId, address, mergedAddresses)
                                                        Toast.makeText(context, "پیامک حاوی کد تایید حذف شد", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("حذف پیام", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(if (msg.isSent) Alignment.End else Alignment.Start)
                            ) {
                                if (activeSims.size > 1 && msg.subId != null) {
                                    Text(
                                        text = "SIM ${if (msg.subId == activeSims.firstOrNull()?.subscriptionId) "1" else "2"}",
                                        fontSize = 9.sp,
                                        color = timeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = DateUtils.formatMessageDetailTime(msg.date, isSolarCalendar),
                                    color = timeColor,
                                    fontSize = 10.sp
                                )
                                if (msg.isSent) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    when (msg.deliveryStatus) {
                                        2 -> Text("✓✓", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                        3 -> Text("!", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        else -> Text("✓", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                                    }
                                }
                            }
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
            title = { Text("گزینه‌های پیام", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
                            Text("کپی متن پیام", color = MaterialTheme.colorScheme.onSurface)
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
                            Text("حذف این پیام", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { messageForAction = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // دیالوگ ارسال زمان‌بندی‌شده
    if (showScheduleDialog) {
        var selectedDelayMinutes by remember { mutableIntStateOf(15) }
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("ارسال زمان‌بندی‌شده", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column {
                    Text("زمان ارسال پیامک را انتخاب کنید:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        15 to "۱۵ دقیقه بعد",
                        60 to "۱ ساعت بعد",
                        180 to "۳ ساعت بعد",
                        720 to "۱۲ ساعت بعد",
                        1440 to "فردا همین ساعت"
                    ).forEach { (min, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDelayMinutes = min }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDelayMinutes == min,
                                onClick = { selectedDelayMinutes = min }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val scheduleTime = System.currentTimeMillis() + (selectedDelayMinutes * 60 * 1000L)
                        val textToSend = inputText
                        inputText = ""
                        showScheduleDialog = false
                        scope.launch {
                            scheduledRepo.scheduleMessage(address, textToSend, scheduleTime, currentSim?.subscriptionId)
                            Toast.makeText(context, "پیامک برای ارسال زمان‌بندی شد", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("زمان‌بندی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
