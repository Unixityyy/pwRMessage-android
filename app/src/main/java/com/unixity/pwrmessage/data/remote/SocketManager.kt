package com.unixity.pwrmessage.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

data class IncomingMessage(
    val from: String,
    val text: String,
    val type: String
)

object SocketManager {
    var chatListUpdateListener: (() -> Unit)? = null
    var messageUpdateListener: (() -> Unit)? = null
    private var socket: Socket? = null

    var onUserList: ((List<String>) -> Unit)? = null
    var onMessage: ((IncomingMessage) -> Unit)? = null
    var onAuthError: (() -> Unit)? = null

    fun connect(token: String) {
        val options = IO.Options().apply {
            auth = mapOf("token" to "Bearer $token")
            transports = arrayOf("websocket")
        }

        socket = IO.socket(URI.create(WS_URL), options)

        socket?.on(Socket.EVENT_CONNECT) {
            println("Socket connected")
        }

        socket?.on("user_list") { args ->
            val arr = args[0] as? org.json.JSONArray ?: return@on
            val users = (0 until arr.length()).map { arr.getString(it) }
            onUserList?.invoke(users)
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