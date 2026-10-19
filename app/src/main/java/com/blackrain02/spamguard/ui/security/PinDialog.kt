package com.blackrain02.spamguard.ui.security

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackrain02.spamguard.data.repository.SecurityRepository
import com.blackrain02.spamguard.ui.util.LocalAppStrings
import kotlinx.coroutines.delay

enum class PinDialogMode {
    VERIFY,
    SET,
    CHANGE,
    SET_DECOY
}

@Composable
fun PinDialog(
    mode: PinDialogMode,
    securityRepository: SecurityRepository,
    subtitle: String? = null,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val focusRequester = remember { FocusRequester() }

    val hasBiometric = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(BiometricManager::class.java)
            bm?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } else false
    }

    var cancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }

    fun triggerBiometric() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasBiometric && securityRepository.isBiometricEnabled() && mode == PinDialogMode.VERIFY) {
            try {
                cancellationSignal?.cancel()
                val signal = CancellationSignal()
                cancellationSignal = signal

                val prompt = BiometricPrompt.Builder(context)
                    .setTitle(strings.biometricPromptTitle)
                    .setSubtitle(strings.biometricPromptSubtitle)
                    .setNegativeButton(strings.usePinInstead, context.mainExecutor) { _, _ -> }
                    .build()

                prompt.authenticate(
                    signal,
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            securityRepository.authenticateSession(isDecoy = false)
                            onSuccess()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            // User selected PIN or dismissed
                        }
                        override fun onAuthenticationFailed() {
                            // Fingerprint not recognized
                        }
                    }
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (mode == PinDialogMode.VERIFY && hasBiometric && securityRepository.isBiometricEnabled()) {
            delay(200)
            triggerBiometric()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancellationSignal?.cancel()
        }
    }

    // وضعیت‌های داخلی برای مدهای چندمرحله‌ای
    var currentStep by remember(mode) {
        mutableStateOf(
            when (mode) {
                PinDialogMode.VERIFY -> "VERIFY"
                PinDialogMode.SET, PinDialogMode.SET_DECOY -> "SET_NEW"
                PinDialogMode.CHANGE -> "VERIFY_OLD"
            }
        )
    }

    var tempNewPin by remember { mutableStateOf("") }
    var inputPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentStep) {
        inputPin = ""
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = {
            cancellationSignal?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                mode == PinDialogMode.SET_DECOY -> Icons.Default.Shield
                                mode == PinDialogMode.VERIFY -> Icons.Default.Lock
                                else -> Icons.Default.Password
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val dialogTitle = when (currentStep) {
                    "VERIFY", "VERIFY_OLD" -> strings.enterPinTitle
                    "SET_NEW" -> if (mode == PinDialogMode.SET_DECOY) strings.setDecoyPin else strings.setPinTitle
                    "CONFIRM_NEW" -> strings.confirmPinTitle
                    else -> strings.enterPinTitle
                }

                Text(
                    text = dialogTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!subtitle.isNullOrBlank() && (currentStep == "VERIFY" || currentStep == "SET_NEW")) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ۴ دایره / باکس نشان‌دهنده ارقام وارد شده
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        focusRequester.requestFocus()
                    }
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < inputPin.length
                        val isError = errorMessage != null
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error
                                        isFilled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error
                                        isFilled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // ورودی نامرئی با فوکوس خودکار و کیبورد عددی
                BasicTextField(
                    value = inputPin,
                    onValueChange = { newVal ->
                        if (newVal.length <= 4 && newVal.all { it.isDigit() }) {
                            inputPin = newVal
                            errorMessage = null
                            if (newVal.length == 4) {
                                when (currentStep) {
                                    "VERIFY" -> {
                                        val result = securityRepository.verifyPinWithDecoy(newVal)
                                        if (result != null) {
                                            securityRepository.authenticateSession(isDecoy = result.isDecoy)
                                            onSuccess()
                                        } else {
                                            errorMessage = strings.pinIncorrect
                                            inputPin = ""
                                        }
                                    }
                                    "VERIFY_OLD" -> {
                                        if (securityRepository.verifyPin(newVal)) {
                                            currentStep = "SET_NEW"
                                        } else {
                                            errorMessage = strings.pinIncorrect
                                            inputPin = ""
                                        }
                                    }
                                    "SET_NEW" -> {
                                        tempNewPin = newVal
                                        currentStep = "CONFIRM_NEW"
                                    }
                                    "CONFIRM_NEW" -> {
                                        if (newVal == tempNewPin) {
                                            if (mode == PinDialogMode.SET_DECOY) {
                                                securityRepository.setDecoyPin(newVal)
                                            } else {
                                                securityRepository.setPin(newVal)
                                            }
                                            securityRepository.authenticateSession(isDecoy = false)
                                            onSuccess()
                                        } else {
                                            errorMessage = strings.pinMismatch
                                            currentStep = "SET_NEW"
                                            tempNewPin = ""
                                            inputPin = ""
                                        }
                                    }
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (inputPin.length < 4) {
                                errorMessage = strings.enterPinTitle
                            }
                        }
                    ),
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0.01f)
                        .focusRequester(focusRequester)
                )

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // دکمه فراخوانی مجدد سنسور اثر انگشت در صورت فعال بودن
                    if (mode == PinDialogMode.VERIFY && hasBiometric && securityRepository.isBiometricEnabled()) {
                        IconButton(onClick = { triggerBiometric() }) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(onClick = {
                        cancellationSignal?.cancel()
                        onDismiss()
                    }) {
                        Text(strings.cancel)
                    }
                }
            }
        }
    }
}
