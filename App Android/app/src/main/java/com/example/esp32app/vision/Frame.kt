package com.example.esp32app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

/**
 * Frame bruto da placa de captura.
 *
 * A libuvc entrega YUV420 semi-planar no layout NV12 (U antes de V), embora o
 * callback da libausbc o rotule como NV21. O plano Y (primeiros width*height bytes)
 * é usado direto como escala de cinza para OCR e busca de imagens.
 */
class Frame(
    val nv12: ByteArray,
    val width: Int,
    val height: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /** Converte para NV21 (formato aceito por ML Kit e YuvImage) trocando U e V. */
    fun toNv21(): ByteArray {
        val out = nv12.copyOf()
        var i = width * height
        while (i + 1 < out.size) {
            val u = out[i]
            out[i] = out[i + 1]
            out[i + 1] = u
            i += 2
        }
        return out
    }

    /** Bitmap colorido para exibição/recorte de modelos. */
    fun toBitmap(jpegQuality: Int = 85): Bitmap? {
        val yuv = YuvImage(toNv21(), ImageFormat.NV21, width, height, null)
        val stream = ByteArrayOutputStream()
        if (!yuv.compressToJpeg(Rect(0, 0, width, height), jpegQuality, stream)) return null
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
