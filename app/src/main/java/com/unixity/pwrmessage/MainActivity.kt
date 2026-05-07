package com.unixity.pwrmessage

import android.content.Intent
import com.unixity.pwrmessage.service.SocketService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.unixity.pwrmessage.data.local.AppDatabase
import com.unixity.pwrmessage.data.local.ChatEntity
import com.unixity.pwrmessage.data.local.MessageEntity
import com.unixity.pwrmessage.data.prefs.UserPrefs
import com.unixity.pwrmessage.data.remote.SocketManager
import com.unixity.pwrmessage.ui.auth.AuthScreen
import com.unixity.pwrmessage.ui.chat.ChatListScreen
import com.unixity.pwrmessage.ui.chat.MessageScreen
import com.unixity.pwrmessage.ui.theme.PwRMessageTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Screen {
    object Auth : Screen()
    object ChatList : Screen()
    data class Messages(val username: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PwRMessageTheme {
                PwrMessageApp()
            }
        }
    }
}

@Composable
fun PwrMessageApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var screen by remember {
        mutableStateOf<Screen>(
            if (UserPrefs.isLoggedIn(context)) Screen.ChatList else Screen.Auth
        )
    }
    var onlineUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeChat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                (context as? ComponentActivity)?.requestPermissions(arrayOf(permission), 0)
            }
        }
        val serviceIntent = Intent(context, SocketService::class.java)
        context.startForegroundService(serviceIntent)
        val token = UserPrefs.getToken(context) ?: return@LaunchedEffect
        connectSocket(token, db, scope, activeChat, onlineUsersChanged = { onlineUsers = it }, context)
    }

    fun logout() {
        SocketManager.disconnect()
        UserPrefs.clear(context)
        activeChat = null
        onlineUsers = emptyList()
        screen = Screen.Auth
    }

    BackHandler(enabled = screen is Screen.Messages || screen is Screen.ChatList) {
        when (screen) {
            is Screen.Messages -> {
                activeChat = null
                screen = Screen.ChatList
            }
            is Screen.ChatList -> {
                // do nothing, don't exit
            }
            else -> {}
        }
    }

    when (val s = screen) {
        is Screen.Auth -> AuthScreen(
            onAuthSuccess = {
                val token = UserPrefs.getToken(context) ?: return@AuthScreen
                connectSocket(token, db, scope, activeChat, onlineUsersChanged = { onlineUsers = it }, context)
                screen = Screen.ChatList
            }
        )
        is Screen.ChatList -> ChatListScreen(
            onlineUsers = onlineUsers,
            onChatSelected = { username ->
                activeChat = username
                screen = Screen.Messages(username)
            },
            onLogout = { logout() }
        )
        is Screen.Messages -> MessageScreen(
            chatWith = s.username,
            onlineUsers = onlineUsers,
            onBack = {
                activeChat = null
                screen = Screen.ChatList
            }
        )
    }
}

private fun connectSocket(
    token: String,
    db: AppDatabase,
    scope: CoroutineScope,
    activeChat: String?,
    onlineUsersChanged: (List<String>) -> Unit,
    context: android.content.Context
) {
    SocketManager.onUserList = { users -> onlineUsersChanged(users) }

    SocketManager.onMessage = { msg ->
        scope.launch(Dispatchers.IO) {
            if (db.isBlocked(msg.from)) return@launch
            db.upsertChat(ChatEntity(msg.from, unread = activeChat != msg.from))
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                SocketManager.chatListUpdateListener?.invoke()
                SocketManager.messageUpdateListener?.invoke()
            }

            if (msg.type == "image") {
                val fileName = "img_${System.currentTimeMillis()}"
                val file = java.io.File(context.cacheDir, fileName)
                file.writeText(msg.text)
                db.insertMessage(
                    MessageEntity(
                        chatWith = msg.from,
                        text = file.absolutePath,
                        type = "image",
                        time = System.currentTimeMillis()
                    )
                )
            } else {
                db.insertMessage(
                    MessageEntity(
                        chatWith = msg.from,
                        text = msg.text,
                        type = "received",
                        time = System.currentTimeMillis()
                    )
                )
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                SocketManager.messageUpdateListener?.invoke()
            }
        }
    }

    SocketManager.onAuthError = {}
    SocketManager.connect(token)
}