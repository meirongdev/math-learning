@file:OptIn(ExperimentalWasmJsInterop::class)

package com.mathlearning.shared.storage

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.toJsString

@JsFun("(k, v) => { localStorage.setItem(k, v); }")
private external fun jsSetItem(k: JsString, v: JsString)

@JsFun("(k) => { return localStorage.getItem(k); }")
private external fun jsGetItem(k: JsString): JsString?

@JsFun("(k) => { localStorage.removeItem(k); }")
private external fun jsRemoveItem(k: JsString)

actual fun saveToken(token: String, expiresAt: String) {
    jsSetItem("auth_token".toJsString(), token.toJsString())
    jsSetItem("auth_expires_at".toJsString(), expiresAt.toJsString())
}

actual fun loadToken(): String? = jsGetItem("auth_token".toJsString())?.toString()

actual fun loadExpiresAt(): String? = jsGetItem("auth_expires_at".toJsString())?.toString()

actual fun clearToken() {
    jsRemoveItem("auth_token".toJsString())
    jsRemoveItem("auth_expires_at".toJsString())
}
