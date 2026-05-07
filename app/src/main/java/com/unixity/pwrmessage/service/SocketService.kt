package com.unixity.pwrmessage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unixity.pwrmessage.MainActivity
import com.unixity.pwrmessage.data.local.AppDatabase
import com.unixity.pwrmessage.data.local.ChatEntity
import com.unixity.pwrmessage.data.local.MessageEntity
import com.unixity.pwrmessage.data.prefs.UserPrefs
import com.unixity.pwrmessage.data.remote.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect

class SocketService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val CHANNEL_ID_PERSISTENT = "pwr_socket"
    private val CHANNEL_ID_MESSAGES = "pwr_messages"
    private val NOTIF_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = UserPrefs.getToken(this) ?: return START_NOT_STICKY
        val db = AppDatabase.getInstance(this)

        SocketManager.onMessage = { msg ->
            scope.launch {
                if (db.isBlocked(msg.from)) return@launch
                db.upsertChat(ChatEntity(msg.from, unread = true))
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    SocketManager.chatListUpdateListener?.invoke()
                    SocketManager.messageUpdateListener?.invoke()
                }

                if (msg.type == "image") {
                    val fileName = "img_${System.currentTimeMillis()}"
                    val file = java.io.File(cacheDir, fileName)
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
                showMessageNotification(msg.from, msg.text, msg.type)
            }
        }

        if (!SocketManager.isConnected()) {
            SocketManager.connect(token)
        }

        return START_STICKY
    }

    private fun showMessageNotification(from: String, text: String, type: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(from)
            .setContentText(if (type == "image") "Sent an image" else text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(from.hashCode(), notif)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("pwRMessage")
            .setContentText("Running in background")
            .setContentIntent(pending)
            .build()
    }

    private fun createNotificationChannel() {
        val persistentChannel = NotificationChannel(
            CHANNEL_ID_PERSISTENT,
            "Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val messagesChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(persistentChannel)
        manager.createNotificationChannel(messagesChannel)
    }
}