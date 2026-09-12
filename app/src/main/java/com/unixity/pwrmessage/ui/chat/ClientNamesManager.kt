package com.unixity.pwrmessage.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ClientNamesManager {
    private const val URL = "https://cdn.teampwr.dev/pwrmessage/client-ids.json"

    @Volatile private var cache: Map<String, String> = emptyMap()
    @Volatile private var isFetching = false

    private val httpClient = OkHttpClient()

    fun getFriendlyName(clientId: String): String = cache[clientId] ?: clientId

    private fun isKnown(clientId: String) = cache.containsKey(clientId)
    fun ensureKnown(clientId: String, onUpdated: (() -> Unit)? = null) {
        if (isKnown(clientId) || isFetching) {
            onUpdated?.invoke()
            return
        }

        isFetching = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(URL).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val obj = JSONObject(body)
                        val newMap = mutableMapOf<String, String>()
                        obj.keys().forEach { key -> newMap[key] = obj.getString(key) }
                        cache = newMap
                    }
                }
            } catch (e: Exception) {
                println("Failed to fetch client-ids.json: ${e.message}")
            } finally {
                isFetching = false
                withContext(Dispatchers.Main) { onUpdated?.invoke() }
            }
        }
    }

    fun getFriendlyNameOrNull(clientId: String): Boolean = cache.containsKey(clientId)
}