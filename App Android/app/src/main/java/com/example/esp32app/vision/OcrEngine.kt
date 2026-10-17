package com.example.esp32app.vision

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR offline com ML Kit (modelo latino embutido no APK; não depende do Google Play Services).
 * Guarda o resultado do último frame para não reprocessar o mesmo frame várias vezes.
 */
object OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var cachedFrame: Frame? = null
    private var cachedResult: OcrResult? = null

    suspend fun recognize(frame: Frame): OcrResult {
        synchronized(this) {
            if (cachedFrame === frame) cachedResult?.let { return it }
        }
        // A cópia/conversão do frame (vários MB) fica fora da main thread
        val image = withContext(Dispatchers.Default) {
            InputImage.fromByteArray(frame.toNv21(), frame.width, frame.height, 0, InputImage.IMAGE_FORMAT_NV21)
        }
        val text = suspendCancellableCoroutine<Text> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val result = OcrResult(
            lines = text.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(
                        text = line.text,
                        box = PixelBox(box.left, box.top, box.right, box.bottom),
                        words = line.elements.mapNotNull { element ->
                            element.boundingBox?.let { OcrWord(element.text, PixelBox(it.left, it.top, it.right, it.bottom)) }
                        }
                    )
                }
            },
            frameWidth = frame.width,
            frameHeight = frame.height
        )
        synchronized(this) {
            cachedFrame = frame
            cachedResult = result
        }
        return result
    }
}
