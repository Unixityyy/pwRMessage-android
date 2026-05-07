package com.unixity.pwrmessage.ui.chat

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unixity.pwrmessage.data.local.AppDatabase
import com.unixity.pwrmessage.data.local.ChatEntity
import com.unixity.pwrmessage.data.prefs.UserPrefs
import com.unixity.pwrmessage.data.remote.ApiService
import com.unixity.pwrmessage.data.remote.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onlineUsers: List<String>,
    onChatSelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val api = remember { ApiService.getInstance() }

    var chats by remember { mutableStateOf<List<ChatEntity>>(emptyList()) }
    var blockedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatUsername by remember { mutableStateOf("") }
    var menuExpandedFor by remember { mutableStateOf<String?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }
    var deleteConfirmText by remember { mutableStateOf("") }

    fun showToast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun loadChats() {
        scope.launch {
            withContext(Dispatchers.IO) {
                chats = db.getAllChats()
                blockedUsers = db.getAllBlocked().map { it.username }
            }
        }
    }

    LaunchedEffect(Unit) { loadChats() }

    LaunchedEffect(Unit) {
        SocketManager.chatListUpdateListener = {
            loadChats()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SocketManager.chatListUpdateListener = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("pwRMessage", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showNewChatDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat")
                    }
                    var appMenuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { appMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = appMenuExpanded,
                        onDismissRequest = { appMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Logout", color = Color.Red) },
                            onClick = { appMenuExpanded = false; onLogout() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(chats) { chat ->
                val isBlocked = blockedUsers.contains(chat.username)
                val isOnline = onlineUsers.contains(chat.username)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isBlocked) {
                            scope.launch(Dispatchers.IO) {
                                db.upsertChat(chat.copy(unread = false))
                            }
                            onChatSelected(chat.username)
                            loadChats()
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBlocked)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp)) {
                            if (isOnline && !isBlocked) {
                                Surface(
                                    modifier = Modifier.size(10.dp),
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFF4CAF50)
                                ) {}
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat.username + if (isBlocked) " (Blocked)" else "",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = if (isBlocked) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (chat.unread && !isBlocked) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box {
                            IconButton(onClick = { menuExpandedFor = chat.username }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = menuExpandedFor == chat.username,
                                onDismissRequest = { menuExpandedFor = null }
                            ) {
                                if (isBlocked) {
                                    DropdownMenuItem(
                                        text = { Text("Unblock") },
                                        onClick = {
                                            menuExpandedFor = null
                                            scope.launch(Dispatchers.IO) {
                                                db.unblockUser(chat.username)
                                                loadChats()
                                                showToast("User unblocked.")
                                            }
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Block") },
                                        onClick = {
                                            menuExpandedFor = null
                                            scope.launch(Dispatchers.IO) {
                                                db.blockUser(chat.username)
                                                loadChats()
                                                showToast("User blocked.")
                                            }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete Chat", color = Color.Red) },
                                    onClick = {
                                        menuExpandedFor = null
                                        deleteConfirmText = ""
                                        showDeleteDialogFor = chat.username
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("New Chat") },
            text = {
                OutlinedTextField(
                    value = newChatUsername,
                    onValueChange = { newChatUsername = it },
                    label = { Text("Username") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = newChatUsername.trim()
                    if (target == UserPrefs.getUser(context)) {
                        showToast("Can't chat with yourself.")
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            val token = UserPrefs.getToken(context) ?: return@launch
                            val resp = api.lookupUser(target, "Bearer $token")
                            if (resp.isSuccessful) {
                                withContext(Dispatchers.IO) {
                                    db.upsertChat(ChatEntity(target, false))
                                }
                                loadChats()
                                showNewChatDialog = false
                                newChatUsername = ""
                            } else {
                                showToast("User not found.")
                            }
                        } catch (e: Exception) {
                            showToast("Connection error.")
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    showDeleteDialogFor?.let { username ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("Delete Chat?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type CONFIRM to delete history with $username")
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteConfirmText != "CONFIRM") return@TextButton
                    scope.launch(Dispatchers.IO) {
                        db.deleteChat(username)
                        db.deleteMessagesFor(username)
                        loadChats()
                        showDeleteDialogFor = null
                    }
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) { Text("Cancel") }
            }
        )
    }
}