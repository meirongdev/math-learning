# Session Persistence (Stay Logged In After Refresh)

## Problem

`MathApi.token` is an in-memory field (`var token: String? = null`). Every page refresh
recreates the Kotlin/Wasm runtime, resetting the field to `null` and forcing the user to
log in again — even though the JWT is still valid for up to 24 hours (`app.jwt.expiration-seconds: 86400`).

## Root Cause

```
Page refresh
  └── Wasm runtime restarts
        └── MathApi() re-instantiated
              └── token = null  ← session lost
```

`App.kt` starts with `var isLoggedIn = false` unconditionally, so the auth screen always
shows first.

---

## Proposed Fix

### 1. Persist the token in `localStorage`

Kotlin/Wasm can call browser APIs via JS interop. Add a thin wrapper in the `wasmJsMain`
source set:

```kotlin
// webApp/src/wasmJsMain/kotlin/com/mathlearning/web/TokenStore.kt
package com.mathlearning.web

internal actual fun saveToken(token: String, expiresAt: String) {
    js("localStorage.setItem('auth_token', token)")
    js("localStorage.setItem('auth_expires_at', expiresAt)")
}

internal actual fun loadToken(): String? =
    js("localStorage.getItem('auth_token')") as? String

internal actual fun loadExpiresAt(): String? =
    js("localStorage.getItem('auth_expires_at')") as? String

internal actual fun clearToken() {
    js("localStorage.removeItem('auth_token')")
    js("localStorage.removeItem('auth_expires_at')")
}
```

Use `expect`/`actual` so the shared module and JVM tests compile cleanly:

```kotlin
// shared/src/commonMain/kotlin/com/mathlearning/shared/storage/TokenStore.kt
expect internal fun saveToken(token: String, expiresAt: String)
expect internal fun loadToken(): String?
expect internal fun loadExpiresAt(): String?
expect internal fun clearToken()
```

Provide no-op `actual` implementations for the `jvmMain` target (used only by unit tests).

### 2. Save token on login

In `MathApi.login()`, after receiving the response:

```kotlin
suspend fun login(email: String, password: String): LoginResponse {
    // ... existing HTTP call ...
    token = loginResponse.token
    saveToken(loginResponse.token, loginResponse.expiresAt)   // <-- add this
    return loginResponse
}
```

### 3. Restore session on app start

In `App.kt`, replace the hardcoded `false` with a token check:

```kotlin
@Composable
fun App() {
    AppTheme {
        Surface(...) {
            val api = remember { MathApi() }

            // Restore token from localStorage on first composition
            var isLoggedIn by remember {
                val stored = loadToken()
                val expiresAt = loadExpiresAt()
                val valid = stored != null && expiresAt != null
                    && Instant.parse(expiresAt) > Clock.System.now()
                if (valid) api.token = stored
                mutableStateOf(valid)
            }

            if (isLoggedIn) {
                MathTutorScreen(api) {
                    clearToken()       // <-- clear on logout
                    api.token = null
                    isLoggedIn = false
                }
            } else {
                AuthScreen(api) { isLoggedIn = true }
            }
        }
    }
}
```

### 4. Handle token expiry mid-session (401 response)

Wrap API calls to intercept 401 and trigger logout:

```kotlin
// MathApi — add a helper
private fun HttpResponse.requireSuccess(onUnauthorized: () -> Unit = {}) {
    if (status == HttpStatusCode.Unauthorized) {
        clearToken()
        token = null
        onUnauthorized()
        throw RuntimeException("Session expired. Please log in again.")
    }
    if (!status.isSuccess()) throw RuntimeException("Server returned $status")
}
```

Or install a response interceptor in the Ktor `HttpClient`:

```kotlin
HttpClient {
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) {
                clearToken()
                throw UnauthorizedException()
            }
        }
    }
}
```

Catch `UnauthorizedException` in `MathTutorScreen` and set `isLoggedIn = false`.

---

## Implementation Checklist

### Frontend – shared module

- [ ] Add `expect` declarations for `saveToken`, `loadToken`, `loadExpiresAt`, `clearToken`
      in `shared/src/commonMain/kotlin/com/mathlearning/shared/storage/TokenStore.kt`
- [ ] Add no-op `actual` implementations in `shared/src/jvmMain/kotlin/.../storage/`
- [ ] Call `saveToken` in `MathApi.login()` after storing `token`

### Frontend – webApp

- [ ] Add `actual` implementations backed by `localStorage` in
      `webApp/src/wasmJsMain/kotlin/com/mathlearning/web/TokenStore.kt`
- [ ] Update `App()` to restore `isLoggedIn` from stored token
- [ ] Call `clearToken()` in the logout lambda
- [ ] Add 401 interceptor to Ktor client or handle `UnauthorizedException` at composable level

### Backend

No changes required. JWT validation is stateless and already handles expired tokens
by returning 401.

---

## Security Notes

| Concern | Mitigation |
|---------|-----------|
| Token in `localStorage` is readable by JS | Acceptable for this app (no third-party scripts); use `httpOnly` cookie if higher security needed in future |
| Token stolen via XSS | CSP header on the server (`Content-Security-Policy: default-src 'self'`) |
| Long-lived token | Default 24 h; reduce to 1–2 h if desired via `app.jwt.expiration-seconds` config |
| Refresh after expiry | Implement refresh-token flow (out of scope for now) |

---

## Dependencies to Add

`kotlinx-datetime` is required for `Instant.parse` and `Clock.System.now()`:

```kotlin
// shared/build.gradle.kts — commonMain dependencies
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
```

---

## Out of Scope

- Refresh token / silent re-auth flow
- Remember-me checkbox (current behaviour is always persist for token lifetime)
- Multi-tab logout synchronisation (`storage` event listener)
