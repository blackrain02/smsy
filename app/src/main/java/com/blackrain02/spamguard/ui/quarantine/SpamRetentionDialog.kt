package com.blackrain02.spamguard.ui.quarantine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackrain02.spamguard.ui.util.LocalAppStrings

@Composable
fun SpamRetentionDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onSaveDays: (Int) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedDays by remember { mutableIntStateOf(currentDays) }
    var isCustomSelected by remember { mutableStateOf(currentDays !in listOf(0, 3, 7, 14, 30)) }
    var customDaysText by remember {
        mutableStateOf(if (currentDays !in listOf(0, 3, 7, 14, 30)) currentDays.toString() else "7")
    }

    val standardOptions = listOf(3, 7, 14, 30, 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = strings.spamRetentionDialogTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val effectiveDays = if (isCustomSelected) (customDaysText.toIntOrNull() ?: 7) else selectedDays
                Text(
                    text = strings.spamRetentionSubtitle(effectiveDays),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                standardOptions.forEach { days ->
                    val isChecked = !isCustomSelected && selectedDays == days
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isCustomSelected = false
                                selectedDays = days
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isChecked,
                            onClick = {
                                isCustomSelected = false
                                selectedDays = days
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.spamRetentionDays(days) + (if (days == 7) strings.defaultTag else ""),
                            fontSize = 13.5.sp
                        )
                    }
                }

                // گزینه روزهای دلخواه
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isCustomSelected = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.spamRetentionCustomDays,
                        fontSize = 13.5.sp
                    )
                }

                if (isCustomSelected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                customDaysText = input
                            }
                        },
                        label = { Text(strings.customDaysFieldLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalDays = if (isCustomSelected) {
                        (customDaysText.toIntOrNull() ?: 7).coerceAtLeast(1)
                    } else {
                        selectedDays
                    }
                    onSaveDays(finalDays)
                }
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
