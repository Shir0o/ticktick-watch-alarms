package com.shir0o.tickalarm.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** TickTick app credentials and the access token, encrypted with a key kept in the Android Keystore. */
class Credentials(ctx: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "tickalarm_secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        ctx.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var clientId: String
        get() = prefs.getString("clientId", "").orEmpty()
        set(v) = prefs.edit().putString("clientId", v.trim()).apply()

    var clientSecret: String
        get() = prefs.getString("clientSecret", "").orEmpty()
        set(v) = prefs.edit().putString("clientSecret", v.trim()).apply()

    var redirectUri: String
        get() = prefs.getString("redirectUri", null) ?: DEFAULT_REDIRECT
        set(v) = prefs.edit().putString("redirectUri", v.trim().ifEmpty { DEFAULT_REDIRECT }).apply()

    var accessToken: String?
        get() = prefs.getString("accessToken", null)
        set(v) = prefs.edit().putString("accessToken", v).apply()

    var refreshToken: String?
        get() = prefs.getString("refreshToken", null)
        set(v) = prefs.edit().putString("refreshToken", v).apply()

    var expiresAtMillis: Long
        get() = prefs.getLong("expiresAt", 0L)
        set(v) = prefs.edit().putLong("expiresAt", v).apply()

    var pendingState: String?
        get() = prefs.getString("pendingState", null)
        set(v) = prefs.edit().putString("pendingState", v).apply()

    fun saveToken(token: TokenResponse) {
        accessToken = token.accessToken
        if (!token.refreshToken.isNullOrBlank()) refreshToken = token.refreshToken
        expiresAtMillis = token.expiresIn?.let { System.currentTimeMillis() + it * 1000 } ?: 0L
    }

    companion object {
        const val DEFAULT_REDIRECT = "tickalarm://oauth"
    }
}
