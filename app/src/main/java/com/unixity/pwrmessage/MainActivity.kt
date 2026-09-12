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
import com.unixity.pwrmessage.data.prefs.UserPrefs
import com.unixity.pwrmessage.data.remote.OnlineUser
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

    override fun onStart() {
        super.onStart()
        SocketManager.isAppVisible = true
    }

    override fun onStop() {
        super.onStop()
        SocketManager.isAppVisible = false
    }
}

@Composable
fun PwrMessageApp() {
    val context = LocalContext.current

    var screen by remember {
        mutableStateOf<Screen>(
            if (UserPrefs.isLoggedIn(context)) Screen.ChatList else Screen.Auth
        )
    }
    var onlineUsers by remember { mutableStateOf<List<OnlineUser>>(emptyList()) }
    var activeChat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeChat) {
        SocketManager.activeChat = activeChat
    }

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
        connectSocket(token, onlineUsersChanged = { onlineUsers = it })
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
                connectSocket(token, onlineUsersChanged = { onlineUsers = it })
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
    onlineUsersChanged: (List<OnlineUser>) -> Unit
) {
    SocketManager.onUserList = { users -> onlineUsersChanged(users) }

    SocketManager.onAuthError = {}
    SocketManager.connect(token)
}
