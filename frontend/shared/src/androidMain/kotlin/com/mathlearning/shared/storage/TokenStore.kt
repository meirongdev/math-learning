package com.mathlearning.shared.storage

import android.content.Context
import android.content.SharedPreferences

private lateinit var appContext: Context

fun initTokenStore(context: Context) {
    appContext = context.applicationContext
}

private val prefs: SharedPreferences
    get() = appContext.getSharedPreferences("math_learning_auth", Context.MODE_PRIVATE)

actual fun saveToken(token: String, expiresAt: String) {
    prefs.edit()
        .putString("token", token)
        .putString("expiresAt", expiresAt)
        .apply()
}

actual fun loadToken(): String? = prefs.getString("token", null)

actual fun loadExpiresAt(): String? = prefs.getString("expiresAt", null)

actual fun clearToken() {
    prefs.edit().clear().apply()
}
