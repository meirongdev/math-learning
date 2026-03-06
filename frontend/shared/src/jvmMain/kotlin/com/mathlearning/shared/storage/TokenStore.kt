package com.mathlearning.shared.storage

// No-op implementations for JVM (used by unit tests only)

actual fun saveToken(token: String, expiresAt: String) {}

actual fun loadToken(): String? = null

actual fun loadExpiresAt(): String? = null

actual fun clearToken() {}
