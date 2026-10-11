package com.binacity.spamguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.binacity.spamguard.data.repository.SmsRepository
import com.binacity.spamguard.data.repository.SpamRepository
import com.binacity.spamguard.ui.chat.ChatScreen
import com.binacity.spamguard.ui.conversations.ConversationListScreen
import com.binacity.spamguard.ui.quarantine.QuarantineScreen
import com.binacity.spamguard.ui.theme.SmsyTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import com.binacity.spamguard.data.model.Conversation

import com.binacity.spamguard.ui.compose.NewMessageScreen

sealed class Screen {
    object Conversations : Screen()
    object Quarantine : Screen()
    data class Chat(
        val threadId: Long,
        val address: String,
        val contactName: String?,
        val mergedAddresses: Set<String> = emptySet()
    ) : Screen()
    object NewMessage : Screen()
}

class MainActivity : ComponentActivity() {
    private lateinit var smsRepository: SmsRepository
    private lateinit var spamRepository: SpamRepository

    private val permissionTrigger = mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTrigger.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        smsRepository = SmsRepository(this)
        spamRepository = SpamRepository(this)

        checkAndRequestPermissions()

        setContent {
            SmsyTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Conversations) }
                val unreadSpamCount by spamRepository.getUnreadCount().collectAsState(initial = 0)
                val conversationListState = rememberLazyListState()
                var conversationList by remember { mutableStateOf<List<Conversation>>(emptyList()) }

                // مدیریت دکمه و ژست بازگشت سیستم‌عامل اندروید جهت جلوگیری از بسته شدن برنامه
                BackHandler(enabled = currentScreen !is Screen.Conversations) {
                    currentScreen = Screen.Conversations
                }

                Scaffold(
                    bottomBar = {
                        if (currentScreen !is Screen.Chat && currentScreen !is Screen.NewMessage) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Conversations,
                                    onClick = { currentScreen = Screen.Conversations },
                                    icon = { Icon(Icons.Default.Email, contentDescription = "Inbox") },
                                    label = { Text("Inbox") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Quarantine,
                                    onClick = { currentScreen = Screen.Quarantine },
                                    icon = {
                                        BadgedBox(badge = {
                                            if (unreadSpamCount > 0) {
                                                Badge { Text(unreadSpamCount.toString()) }
                                            }
                                        }) {
                                            Icon(Icons.Default.Shield, contentDescription = "Quarantine")
                                        }
                                    },
                                    label = { Text("Quarantine") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
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
                                            currentScreen = Screen.Chat(conv.threadId, conv.address, conv.contactName, conv.mergedAddresses)
                                        }
                                    )
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
                                        mergedAddresses = screen.mergedAddresses
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
