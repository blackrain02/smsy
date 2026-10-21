package com.blackrain02.spamguard.ui.finance

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackrain02.spamguard.data.local.BankTransactionEntity
import com.blackrain02.spamguard.data.repository.BankSenderRepository
import com.blackrain02.spamguard.data.repository.BankTransactionRepository
import com.blackrain02.spamguard.ui.util.DateUtils
import com.blackrain02.spamguard.ui.util.LocalAppStrings
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinanceScreen(
    bankRepo: BankTransactionRepository
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val isSolarCalendar = remember { DateUtils.isSolarCalendar(context) }
    val securityRepo = remember { com.blackrain02.spamguard.data.repository.SecurityRepository(context) }
    val isDecoy = securityRepo.isDecoySession

    val allTransactions by bankRepo.getAllTransactions().collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("ALL") }
    var isScanning by remember { mutableStateOf(false) }

    val bankSenderRepo = remember { BankSenderRepository(context) }
    val bankSenders by bankSenderRepo.bankSendersFlow.collectAsState()
    var showBankSendersDialog by remember { mutableStateOf(false) }
    var showRescanConfirmDialog by remember { mutableStateOf(false) }
    var newSenderInput by remember { mutableStateOf("") }

    var selectedTransactionForEdit by remember { mutableStateOf<BankTransactionEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var editCategoryText by remember(selectedTransactionForEdit) {
        mutableStateOf(selectedTransactionForEdit?.category ?: "")
    }
    var editType by remember(selectedTransactionForEdit) {
        mutableStateOf(selectedTransactionForEdit?.type ?: "DEPOSIT")
    }

    LaunchedEffect(Unit) {
        bankRepo.cleanupAndReparse()
    }

    val activeTransactions = remember(allTransactions, isDecoy) {
        if (isDecoy) emptyList() else allTransactions
    }

    val filteredTransactions = remember(activeTransactions, selectedFilter) {
        when (selectedFilter) {
            "DEPOSIT" -> activeTransactions.filter { it.type == "DEPOSIT" }
            "WITHDRAWAL" -> activeTransactions.filter { it.type == "WITHDRAWAL" }
            else -> activeTransactions
        }
    }

    val totalDeposits = remember(activeTransactions) {
        activeTransactions.filter { it.type == "DEPOSIT" }.sumOf { it.amount }
    }
    val totalWithdrawals = remember(activeTransactions) {
        activeTransactions.filter { it.type == "WITHDRAWAL" }.sumOf { it.amount }
    }
    val netBalance = totalDeposits - totalWithdrawals

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.financialDashboard,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = {
                            if (!isScanning) {
                                isScanning = true
                                scope.launch {
                                    val count = bankRepo.scanAndImportHistoricalBankSms()
                                    isScanning = false
                                    val msg = if (strings.isFa) {
                                        if (count > 0) "$count تراکنش بانکی جدید با موفقیت شناسایی و ثبت شد" else "همه تراکنش‌ها همگام هستند (مورد جدیدی یافت نشد)"
                                    } else {
                                        if (count > 0) "$count new transactions imported" else "Everything is up to date"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = strings.financialScan,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { showBankSendersDialog = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = strings.designatedBankSenders,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showRescanConfirmDialog = true }) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = strings.clearAndRescan,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ۱. کارت‌های آمار و گزارش تجمیعی مالی
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // کارت واریزی‌ها
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                strings.totalDeposits,
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(Locale.ENGLISH, "%,d", totalDeposits) + " ت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF059669)
                        )
                    }
                }

                // کارت برداشت‌ها
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                strings.totalWithdrawals,
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(Locale.ENGLISH, "%,d", totalWithdrawals) + " ت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // کارت تراز کل
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            strings.netBalance,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val balancePrefix = if (netBalance > 0) "+" else ""
                    Text(
                        text = "$balancePrefix${String.format(Locale.ENGLISH, "%,d", netBalance)} ت",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (netBalance >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ۲. فیلترها (همه / واریز / برداشت)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text(strings.allTransactions + " (${allTransactions.size})", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = selectedFilter == "DEPOSIT",
                    onClick = { selectedFilter = "DEPOSIT" },
                    label = { Text(strings.deposits, fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedFilter == "WITHDRAWAL",
                    onClick = { selectedFilter = "WITHDRAWAL" },
                    label = { Text(strings.withdrawals, fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF4444),
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ۳. لیست تراکنش‌ها یا وضعیت خالی
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            strings.noTransactions,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.5.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { item ->
                        val isDeposit = item.type == "DEPOSIT"
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTransactionForEdit = item }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isDeposit) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isDeposit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = if (isDeposit) Color(0xFF059669) else Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = item.bankName + if (!item.cardNumber.isNullOrBlank()) " (${item.cardNumber})" else "",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.category,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• " + DateUtils.formatConversationDate(item.timestamp, isSolarCalendar),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val sign = if (isDeposit) "+" else "-"
                                    val amountFormatted = String.format(Locale.ENGLISH, "%,d", item.amount)
                                    Text(
                                        text = "$sign$amountFormatted ${item.amountUnit}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDeposit) Color(0xFF059669) else Color(0xFFDC2626)
                                    )
                                    if (item.balance != null) {
                                        Text(
                                            text = (if (strings.isFa) "مانده: " else "Bal: ") + String.format(Locale.ENGLISH, "%,d", item.balance) + " ت",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    if (showBankSendersDialog) {
        AlertDialog(
            onDismissRequest = { showBankSendersDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.designatedBankSenders, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.bankSenderDescription,
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
                            placeholder = { Text(strings.enterBankSenderHint, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (newSenderInput.isNotBlank()) {
                                    bankSenderRepo.addBankSender(newSenderInput.trim())
                                    newSenderInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = strings.add, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(bankSenders.toList()) { sender ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sender, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                IconButton(
                                    onClick = {
                                        bankSenderRepo.removeBankSender(sender)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBankSendersDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    if (showRescanConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRescanConfirmDialog = false },
            title = { Text(strings.clearAndRescan, fontWeight = FontWeight.Bold) },
            text = { Text(strings.clearAndRescanConfirm, fontSize = 13.5.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showRescanConfirmDialog = false
                        isScanning = true
                        scope.launch {
                            val count = bankRepo.clearAllAndRescan()
                            isScanning = false
                            val msg = if (strings.isFa) "$count تراکنش بانکی با موفقیت استخراج و بازنشانی شد" else "$count transactions refreshed"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(strings.clearAndRescan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescanConfirmDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    selectedTransactionForEdit?.let { tx ->
        ModalBottomSheet(
            onDismissRequest = { selectedTransactionForEdit = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            val isDeposit = editType == "DEPOSIT"
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
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.editTransaction,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { selectedTransactionForEdit = null }) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // کارت نمایش مبلغ، بانک و انتخاب نوع واریز/برداشت
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tx.bankName + if (!tx.cardNumber.isNullOrBlank()) " (${tx.cardNumber})" else "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sign = if (isDeposit) "+" else "-"
                        val amountFormatted = String.format(Locale.ENGLISH, "%,d", tx.amount)
                        Text(
                            text = "$sign$amountFormatted ${tx.amountUnit}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeposit) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = DateUtils.formatMessageDetailTime(tx.timestamp, isSolarCalendar),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // تغییر نوع تراکنش (واریز / برداشت)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilterChip(
                                selected = isDeposit,
                                onClick = { editType = "DEPOSIT" },
                                label = { Text(strings.depositType, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isDeposit) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF059669)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            FilterChip(
                                selected = !isDeposit,
                                onClick = { editType = "WITHDRAWAL" },
                                label = { Text(strings.withdrawalType, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (!isDeposit) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFFDC2626)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // برچسب و دسته‌بندی
                Text(
                    text = strings.transactionCategory,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                // برچسب‌های آماده
                val quickOptions = if (strings.isFa) {
                    listOf("بانکی", "حقوق و درآمد", "خرید و فروشگاه", "خوراک و رستوران", "قبوض و خدمات", "انتقال وجه", "قسط و وام", "پزشکی و سلامت", "خودرو", "سایر")
                } else {
                    listOf("Bank", "Salary", "Shopping", "Food & Dining", "Bills", "Transfer", "Loan", "Healthcare", "Auto", "Other")
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickOptions.forEach { opt ->
                        val isSelected = editCategoryText == opt
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { editCategoryText = opt }
                        ) {
                            Text(
                                text = opt,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // فیلد متن دلخواه برچسب
                OutlinedTextField(
                    value = editCategoryText,
                    onValueChange = { editCategoryText = it },
                    placeholder = { Text(strings.customLabelHint, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (tx.rawBody.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.originalSmsBody,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tx.rawBody,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // دکمه‌های عملیات: ذخیره تغییرات و حذف تراکنش
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                bankRepo.updateTransactionCategory(tx.id, editCategoryText)
                                bankRepo.updateTransactionType(tx.id, editType)
                                Toast.makeText(context, strings.transactionUpdated, Toast.LENGTH_SHORT).show()
                                selectedTransactionForEdit = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.saveChanges, fontSize = 13.5.sp)
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.delete, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.deleteTransaction, fontWeight = FontWeight.Bold) },
            text = { Text(strings.deleteTransactionConfirm, fontSize = 13.5.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        val txToDelete = selectedTransactionForEdit
                        showDeleteConfirmDialog = false
                        selectedTransactionForEdit = null
                        if (txToDelete != null) {
                            scope.launch {
                                bankRepo.deleteTransaction(txToDelete.id)
                                Toast.makeText(context, strings.transactionDeleted, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
