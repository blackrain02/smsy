package com.binacity.spamguard.ui.compose

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binacity.spamguard.data.model.ContactItem
import com.binacity.spamguard.data.model.Message
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.ui.chat.calculateSmsCounter
import com.binacity.spamguard.ui.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    smsRepository: SmsRepository,
    onBack: () -> Unit,
    onNavigateToChat: (Long, String, String?) -> Unit
) {
    val context = LocalContext.current
    var recipients by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var recipientQuery by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // بارگذاری بلادرنگ مخاطبین بر اساس متن جستجو (حروف فارسی، انگلیسی و اعداد)
    val suggestedContacts by produceState(initialValue = emptyList<ContactItem>(), key1 = recipientQuery) {
        value = smsRepository.getContacts(recipientQuery)
    }

    fun addRecipient(item: ContactItem) {
        val cleanNumber = item.number.trim()
        if (cleanNumber.isNotBlank() && recipients.none { it.number == cleanNumber }) {
            recipients = recipients + item
        }
        recipientQuery = ""
    }

    fun addManualNumber(rawNumber: String) {
        val clean = rawNumber.trim()
        if (clean.length >= 3) {
            val contactName = smsRepository.getContactName(clean)
            addRecipient(ContactItem(name = contactName ?: clean, number = clean))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when {
                        recipients.size > 1 -> "Group message (${recipients.size})"
                        recipients.size == 1 -> recipients.first().name
                        else -> "New conversation"
                    }
                    Text(titleText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

                        val counter = remember(inputText) { calculateSmsCounter(inputText) }
                        if (inputText.isNotEmpty()) {
                            Text(
                                text = "${counter.remaining}/${counter.maxSingle}" + if (counter.parts > 1) " (${counter.parts})" else "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        val canSend = recipients.isNotEmpty() && inputText.isNotBlank() && !isSending

                        IconButton(
                            onClick = {
                                if (canSend) {
                                    isSending = true
                                    val textToSend = inputText
                                    inputText = ""
                                    val targets = recipients.toList()
                                    scope.launch {
                                        var allSuccess = true
                                        for (target in targets) {
                                            val sent = smsRepository.sendMessage(target.number, textToSend)
                                            if (!sent) allSuccess = false
                                        }
                                        isSending = false
                                        if (targets.size == 1) {
                                            val single = targets.first()
                                            val threadId = smsRepository.getOrCreateThreadId(single.number)
                                            onNavigateToChat(threadId, single.number, single.name)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Message sent to ${targets.size} recipients",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onBack()
                                        }
                                    }
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier.background(
                                if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(50)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // بخش چیپ‌های گیرندگان انتخاب شده (To)
            if (recipients.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "To:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (item in recipients) {
                                InputChip(
                                    selected = false,
                                    onClick = { },
                                    label = {
                                        Text(
                                            text = item.name,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    recipients = recipients.filter { it.number != item.number }
                                                }
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // فیلد اختصاصی و تمام‌عرض جستجوی مخاطب (با کیبورد متنی استاندارد فارسی و انگلیسی)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = recipientQuery,
                        onValueChange = { recipientQuery = it },
                        placeholder = {
                            Text(
                                "Search name or type phone number...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text, // کیبورد متنی کامل برای تایپ نام فارسی و انگلیسی
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (recipientQuery.isNotBlank()) {
                                    addManualNumber(recipientQuery)
                                }
                            }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    if (recipientQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { recipientQuery = "" },
                            modifier = Modifier.size(28.dp)
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // لیست انتخاب مخاطبین با پشتیبانی از شروع مستقیم چت و انتخاب گروهی
            val filteredContacts = remember(suggestedContacts, recipients) {
                val selectedNumbers = recipients.map { it.number }.toSet()
                suggestedContacts.filter { !selectedNumbers.contains(it.number) }
            }

            val trimmedQuery = recipientQuery.trim()
            val canAddDirectNumber = trimmedQuery.length >= 3 &&
                    trimmedQuery.any { it.isDigit() } &&
                    recipients.none { it.number == trimmedQuery }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // بنر راهنما در صورتی که مخاطب به عنوان گروه انتخاب شده باشد
                if (recipients.size == 1) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Selected: ${recipients.first().name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "Add more recipients below, or open 1-on-1 chat",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        val single = recipients.first()
                                        scope.launch {
                                            val clean = single.number.trim()
                                            val threadId = smsRepository.getOrCreateThreadId(clean)
                                            onNavigateToChat(threadId, clean, single.name)
                                        }
                                    }
                                ) {
                                    Text("Open Chat", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (recipients.size > 1) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Group SMS: message will send to ${recipients.size} recipients",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                if (canAddDirectNumber) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (recipients.isEmpty()) {
                                        scope.launch {
                                            val clean = trimmedQuery.trim()
                                            val threadId = smsRepository.getOrCreateThreadId(clean)
                                            val contactName = smsRepository.getContactName(clean)
                                            onNavigateToChat(threadId, clean, contactName)
                                        }
                                    } else {
                                        addManualNumber(trimmedQuery)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Send to $trimmedQuery",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        if (recipients.isEmpty()) "Tap to start conversation" else "Tap to add to group",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { addManualNumber(trimmedQuery) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add to group",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }
                }

                if (filteredContacts.isEmpty() && !canAddDirectNumber && recipientQuery.isNotBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No contacts matching '$recipientQuery'",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                items(filteredContacts, key = { it.number }) { contact ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (recipients.isEmpty()) {
                                    scope.launch {
                                        val clean = contact.number.trim()
                                        val threadId = smsRepository.getOrCreateThreadId(clean)
                                        onNavigateToChat(threadId, clean, contact.name)
                                    }
                                } else {
                                    addRecipient(contact)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    contact.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    contact.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    contact.number,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // دکمه اختصاصی افزودن به گروه برای پیامک چند نفره
                            IconButton(
                                onClick = { addRecipient(contact) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add to group",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
