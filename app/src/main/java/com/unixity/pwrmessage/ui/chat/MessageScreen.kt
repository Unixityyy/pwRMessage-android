package com.unixity.pwrmessage.ui.chat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.unixity.pwrmessage.data.local.AppDatabase
import com.unixity.pwrmessage.data.local.MessageEntity
import com.unixity.pwrmessage.data.remote.SocketManager
import kotlinx.coroutines.launch
import java.io.InputStream
import android.util.Base64
import androidx.compose.material.icons.filled.CameraAlt
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    chatWith: String,
    onlineUsers: List<String>,
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<MessageEntity>() }
    var messageText by remember { mutableStateOf("") }
    val isOnline = onlineUsers.contains(chatWith)

    fun showToast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun loadMessages() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                db.getMessagesFor(chatWith)
            }
            println("DEBUG: loaded ${result.size} messages for $chatWith")
            result.forEach { println("DEBUG: msg=${it.text} type=${it.type}") }
            messages.clear()
            messages.addAll(result)
        }
    }

    // Reload messages when a new one arrives
    LaunchedEffect(chatWith) {
        loadMessages()
        SocketManager.messageUpdateListener = {
            loadMessages()
        }
    }

    DisposableEffect(chatWith) {
        onDispose {
            SocketManager.messageUpdateListener = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (!isOnline) {
            showToast("$chatWith is offline. Cannot send images.")
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = readBytes(context, uri)
                if (bytes.size > 5 * 1024 * 1024) {
                    showToast("Image too large (Max 5MB)")
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUrl = "data:$mimeType;base64,$base64"

                // Save to file
                val fileName = "img_${System.currentTimeMillis()}"
                val file = java.io.File(context.cacheDir, fileName)
                file.writeText(dataUrl)

                SocketManager.sendMessage(chatWith, dataUrl, "image")
                db.insertMessage(
                    MessageEntity(
                        chatWith = chatWith,
                        text = file.absolutePath, // store path not data
                        type = "sent_image",
                        time = System.currentTimeMillis()
                    )
                )
                loadMessages()
            } catch (e: Exception) {
                showToast("Failed to send image.")
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        if (!isOnline) {
            showToast("$chatWith is offline. Cannot send images.")
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            try {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                val bytes = stream.toByteArray()
                if (bytes.size > 5 * 1024 * 1024) {
                    showToast("Image too large (Max 5MB)")
                    return@launch
                }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64"
                val fileName = "img_${System.currentTimeMillis()}"
                val file = java.io.File(context.cacheDir, fileName)
                file.writeText(dataUrl)
                SocketManager.sendMessage(chatWith, dataUrl, "image")
                db.insertMessage(
                    MessageEntity(
                        chatWith = chatWith,
                        text = file.absolutePath,
                        type = "sent_image",
                        time = System.currentTimeMillis()
                    )
                )
                loadMessages()
            } catch (e: Exception) {
                showToast("Failed to send image.")
            }
        }
    }

    fun sendMessage() {
        val text = messageText.trim()
        if (text.isEmpty()) return
        if (!isOnline) {
            showToast("$chatWith is offline.")
            return
        }
        SocketManager.sendMessage(chatWith, text, "text")
        scope.launch {
            db.insertMessage(
                MessageEntity(
                    chatWith = chatWith,
                    text = text,
                    type = "sent",
                    time = System.currentTimeMillis()
                )
            )
            loadMessages()
            messageText = ""
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) cameraLauncher.launch(null)
        else showToast("Camera permission denied")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chatWith, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            fontSize = 12.sp,
                            color = if (isOnline) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = isOnline
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Send Image",
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = {
                            if (hasCameraPermission) cameraLauncher.launch(null)
                            else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        enabled = isOnline
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Take Photo",
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(if (isOnline) "Type a message..." else "User is offline")
                        },
                        enabled = isOnline,
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendMessage() },
                        enabled = isOnline && messageText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (isOnline && messageText.isNotBlank())
                                MaterialTheme.colorScheme.primary else Color.Gray
                        )
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { msg ->
                val isSent = msg.type.startsWith("sent")
                val isImage = msg.type.contains("image")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                color = if (isSent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (isSent) 16.dp else 4.dp,
                                    bottomEnd = if (isSent) 4.dp else 16.dp
                                )
                            )
                            .padding(10.dp)
                    ) {
                        if (isImage) {
                            val imageBytes = remember(msg.text) {
                                try {
                                    val raw = if (msg.text.startsWith("/")) {
                                        java.io.File(msg.text).readText()
                                    } else {
                                        msg.text
                                    }
                                    val base64 = raw.substringAfter("base64,")
                                    android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                                } catch (e: Exception) { null }
                            }
                            if (imageBytes != null) {
                                AsyncImage(
                                    model = imageBytes,
                                    contentDescription = "Image message",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                )
                            } else {
                                Text("Failed to load image", color = Color.Gray)
                            }
                        } else {
                            Text(
                                text = msg.text,
                                color = if (isSent) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readBytes(context: Context, uri: Uri): ByteArray {
    val stream: InputStream = context.contentResolver.openInputStream(uri)
        ?: throw Exception("Cannot open file")
    return stream.use { it.readBytes() }
}