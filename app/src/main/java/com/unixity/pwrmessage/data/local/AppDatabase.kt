package com.unixity.pwrmessage.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

// --- Data classes ---

data class ChatEntity(val username: String, val unread: Boolean = false)
data class MessageEntity(val id: Int = 0, val chatWith: String, val text: String, val type: String, val time: Long)
data class BlockedEntity(val username: String)

// --- Database ---

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "pwrmessage.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS chats (username TEXT PRIMARY KEY, unread INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, chatWith TEXT NOT NULL, text TEXT NOT NULL, type TEXT NOT NULL, time INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS blocked (username TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // --- Chat ---
    fun upsertChat(chat: ChatEntity) {
        val cv = ContentValues().apply {
            put("username", chat.username)
            put("unread", if (chat.unread) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("chats", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllChats(): List<ChatEntity> {
        val result = mutableListOf<ChatEntity>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM chats", null)
        while (cursor.moveToNext()) {
            result.add(ChatEntity(
                username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
                unread = cursor.getInt(cursor.getColumnIndexOrThrow("unread")) == 1
            ))
        }
        cursor.close()
        return result
    }

    fun deleteChat(username: String) {
        writableDatabase.delete("chats", "username = ?", arrayOf(username))
    }

    // --- Messages ---
    fun insertMessage(msg: MessageEntity) {
        val cv = ContentValues().apply {
            put("chatWith", msg.chatWith)
            put("text", msg.text)
            put("type", msg.type)
            put("time", msg.time)
        }
        writableDatabase.insert("messages", null, cv)
    }

    fun getMessagesFor(username: String): List<MessageEntity> {
        val result = mutableListOf<MessageEntity>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE chatWith = ? ORDER BY time ASC",
            arrayOf(username)
        )
        while (cursor.moveToNext()) {
            result.add(MessageEntity(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                chatWith = cursor.getString(cursor.getColumnIndexOrThrow("chatWith")),
                text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                time = cursor.getLong(cursor.getColumnIndexOrThrow("time"))
            ))
        }
        cursor.close()
        return result
    }

    fun deleteMessagesFor(username: String) {
        writableDatabase.delete("messages", "chatWith = ?", arrayOf(username))
    }

    // --- Blocked ---
    fun blockUser(username: String) {
        val cv = ContentValues().apply { put("username", username) }
        writableDatabase.insertWithOnConflict("blocked", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun unblockUser(username: String) {
        writableDatabase.delete("blocked", "username = ?", arrayOf(username))
    }

    fun getAllBlocked(): List<BlockedEntity> {
        val result = mutableListOf<BlockedEntity>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM blocked", null)
        while (cursor.moveToNext()) {
            result.add(BlockedEntity(cursor.getString(cursor.getColumnIndexOrThrow("username"))))
        }
        cursor.close()
        return result
    }

    fun isBlocked(username: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM blocked WHERE username = ?", arrayOf(username)
        )
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count > 0
    }

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}