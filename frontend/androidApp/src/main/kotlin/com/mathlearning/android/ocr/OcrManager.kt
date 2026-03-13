package com.mathlearning.android.ocr

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface OcrState {
    data object Idle : OcrState
    data object Preview : OcrState
    data object Processing : OcrState
    data class Success(val text: String) : OcrState
    data class Error(val message: String) : OcrState
}

class OcrManager {
    private val _state = MutableStateFlow<OcrState>(OcrState.Idle)
    val state: StateFlow<OcrState> = _state

    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val executor = Executors.newSingleThreadExecutor()

    fun showPreview() { _state.value = OcrState.Preview }
    fun dismiss() { _state.value = OcrState.Idle }

    suspend fun captureAndRecognize(imageCapture: ImageCapture) {
        _state.value = OcrState.Processing
        try {
            val imageProxy = suspendCancellableCoroutine { cont ->
                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) { cont.resume(image) }
                    override fun onError(e: ImageCaptureException) { cont.resumeWithException(e) }
                })
            }
            val inputImage = InputImage.fromMediaImage(
                imageProxy.image!!, imageProxy.imageInfo.rotationDegrees,
            )
            // Run both recognizers and merge
            val latinResult = suspendCancellableCoroutine { cont ->
                latinRecognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resume("") }
            }
            val chineseResult = suspendCancellableCoroutine { cont ->
                chineseRecognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resume("") }
            }
            imageProxy.close()
            val combined = if (chineseResult.length > latinResult.length) chineseResult else latinResult
            _state.value = if (combined.isNotBlank()) OcrState.Success(combined)
                else OcrState.Error("No text detected. Try again with a clearer image.")
        } catch (e: Exception) {
            _state.value = OcrState.Error("Capture failed: ${e.message?.take(50)}")
        }
    }
}
