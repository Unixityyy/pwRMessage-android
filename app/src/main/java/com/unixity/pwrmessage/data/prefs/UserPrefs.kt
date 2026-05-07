package com.unixity.pwrmessage.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object UserPrefs {
    private const val PREFS_FILE = "pwr_prefs"
    private const val KEY_TOKEN = "pwr_token"
    private const val KEY_USER = "pwr_user"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuth(context: Context, token: String, user: String) {
        getPrefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user)
            .apply()
    }

    fun getToken(context: Context): String? =
        getPrefs(context).getString(KEY_TOKEN, null)

    fun getUser(context: Context): String? =
        getPrefs(context).getString(KEY_USER, null)

    fun isLoggedIn(context: Context): Boolean =
        getToken(context) != null && getUser(context) != null

    fun clear(context: Context) =
        getPrefs(context).edit().clear().apply()
}