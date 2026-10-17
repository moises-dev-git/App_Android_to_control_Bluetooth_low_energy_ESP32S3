package com.example.esp32app.vision

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ScreenVision] real: analisa o frame mais recente da captura HDMI. */
class HdmiScreenVision(context: Context) : ScreenVision {

    private val matcher = ImageMatcher(TemplateStore(context.applicationContext))

    override fun isReady(): Boolean = HdmiCapture.hasFreshFrame()

    override suspend fun findText(query: String): ScreenMatch? {
        val frame = HdmiCapture.latestFrame ?: return null
        val result = OcrEngine.recognize(frame)
        return TextMatcher.find(result, query)
    }

    override suspend fun findImage(name: String): ScreenMatch? {
        val frame = HdmiCapture.latestFrame ?: return null
        return withContext(Dispatchers.Default) { matcher.find(frame, name).match }
    }
}
