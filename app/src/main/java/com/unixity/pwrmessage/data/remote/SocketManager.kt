package com.unixity.pwrmessage.data.remote

import com.unixity.pwrmessage.ui.chat.ClientNamesManager
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

data class IncomingMessage(
    val from: String,
    val text: String,
    val type: String
)

data class OnlineUser(
    val user: String,
    val clientId: String
)

object SocketManager {
    var chatListUpdateListener: (() -> Unit)? = null
    var messageUpdateListener: (() -> Unit)? = null
    var activeChat: String? = null
    var isAppVisible: Boolean = false

    private var socket: Socket? = null

    var onUserList: ((List<OnlineUser>) -> Unit)? = null
    var onMessage: ((IncomingMessage) -> Unit)? = null
    var onAuthError: (() -> Unit)? = null

    fun connect(token: String) {
        val options = IO.Options().apply {
            auth = mapOf("token" to "Bearer $token", "clientId" to "unixity-android")
            transports = arrayOf("websocket")
        }

        socket = IO.socket(URI.create(WS_URL), options)

        socket?.on(Socket.EVENT_CONNECT) {
            println("Socket connected")
        }

        socket?.on("user_list") { args ->
            val arr = args[0] as? org.json.JSONArray ?: return@on
            val users = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                OnlineUser(
                    user = obj.optString("user"),
                    clientId = obj.optString("clientId", "stock")
                )
            }

            onUserList?.invoke(users)

            val unknownIds = users.map { it.clientId }.distinct()
                .filterNot { ClientNamesManager.getFriendlyNameOrNull(it) }
            unknownIds.forEach { id ->
                ClientNamesManager.ensureKnown(id) {
                    onUserList?.invoke(users)
                }
            }
        }

        socket?.on("msg") { args ->
            val data = args[0] as? JSONObject ?: return@on
            val msg = IncomingMessage(
                from = data.optString("from"),
                text = data.optString("text"),
                type = data.optString("type", "received")
            )
            onMessage?.invoke(msg)
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args[0]?.toString() ?: ""
            if (error.contains("Auth error")) {
                onAuthError?.invoke()
            }
        }

        socket?.connect()
    }

    fun sendMessage(to: String, text: String, type: String = "text") {
        val data = JSONObject().apply {
            put("to", to)
            put("text", text)
            put("type", type)
        }
        socket?.emit("direct_message", data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun isConnected() = socket?.connected() == true
}