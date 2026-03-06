package com.mathlearning.shared.storage

expect fun saveToken(token: String, expiresAt: String)

expect fun loadToken(): String?

expect fun loadExpiresAt(): String?

expect fun clearToken()
