@file:OptIn(ExperimentalWasmJsInterop::class)

package com.mathlearning.web

import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// simple payload representing OCR result; not serialized with kotlinx,json to avoid
// runtime serializer availability issues. We'll parse manually instead.
data class OcrPayload(
    val fileName: String = "",
    val text: String = "",
    val cancelled: Boolean = false,
)

@JsFun("""
    (onSuccess, onError) => {
        window.mathLearningOcr.pickAndRecognize()
            .then((result) => onSuccess(result))
            .catch((error) => onError(String(error && error.message ? error.message : error)));
    }
""")
private external fun pickAndRecognizeJs(
    onSuccess: (JsString) -> Unit,
    onError: (JsString) -> Unit,
)

suspend fun runBrowserOcr(json: kotlinx.serialization.json.Json): OcrPayload =
    suspendCancellableCoroutine { continuation ->
        pickAndRecognizeJs(
            onSuccess = { payload ->
                try {
                    val element = json.parseToJsonElement(payload.toString()).jsonObject
                    val fileName = element["fileName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val text = element["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    val cancelled = element["cancelled"]?.jsonPrimitive?.booleanOrNull ?: false
                    continuation.resume(OcrPayload(fileName, text, cancelled))
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            onError = { message ->
                continuation.resumeWithException(IllegalStateException(message.toString()))
            },
        )
    }
