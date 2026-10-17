package com.example.esp32app.vision

import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Localiza um modelo de imagem no frame com OpenCV (matchTemplate, correlação normalizada)
 * sobre a imagem em tons de cinza.
 */
class ImageMatcher(private val store: TemplateStore) {

    companion object {
        private const val TAG = "ImageMatcher"

        /** Similaridade mínima (0..1) para considerar o modelo encontrado. */
        const val DEFAULT_THRESHOLD = 0.80

        /** Frames mais largos que isso são reduzidos antes da busca, por desempenho. */
        private const val WORK_WIDTH = 960

        private val initialized: Boolean by lazy {
            OpenCVLoader.initLocal().also { if (!it) Log.e(TAG, "Falha ao carregar OpenCV") }
        }
    }

    /** Cache de modelos em cinza, invalidado quando o arquivo muda. */
    private val cache = mutableMapOf<String, Pair<Long, Mat>>()

    data class Result(val match: ScreenMatch?, val bestScore: Double)

    fun find(frame: Frame, name: String, threshold: Double = DEFAULT_THRESHOLD): Result {
        check(initialized) { "OpenCV não pôde ser carregado" }
        val info = store.get(name) ?: throw IllegalArgumentException("Modelo de imagem \"$name\" não existe")
        val template = loadGray(info) ?: throw IllegalStateException("Não foi possível ler o modelo \"$name\"")

        val gray = Mat(frame.height, frame.width, CvType.CV_8UC1)
        gray.put(0, 0, frame.nv12, 0, frame.width * frame.height)

        // Ajusta o modelo se a captura estiver em resolução diferente da usada ao recortá-lo
        val sourceScale = if (info.sourceWidth > 0) frame.width.toDouble() / info.sourceWidth else 1.0
        val workScale = minOf(1.0, WORK_WIDTH.toDouble() / frame.width)

        val frameWork = Mat()
        val templWork = Mat()
        val result = Mat()
        try {
            Imgproc.resize(gray, frameWork, Size(), workScale, workScale, Imgproc.INTER_AREA)
            val tScale = sourceScale * workScale
            Imgproc.resize(template, templWork, Size(), tScale, tScale, Imgproc.INTER_AREA)
            if (templWork.cols() < 4 || templWork.rows() < 4 ||
                templWork.cols() > frameWork.cols() || templWork.rows() > frameWork.rows()
            ) {
                return Result(null, 0.0)
            }

            Imgproc.matchTemplate(frameWork, templWork, result, Imgproc.TM_CCOEFF_NORMED)
            val mm = Core.minMaxLoc(result)
            if (mm.maxVal < threshold) return Result(null, mm.maxVal)

            val fw = frameWork.cols().toFloat()
            val fh = frameWork.rows().toFloat()
            val left = mm.maxLoc.x.toFloat()
            val top = mm.maxLoc.y.toFloat()
            return Result(
                ScreenMatch(
                    left = left / fw,
                    top = top / fh,
                    right = (left + templWork.cols()) / fw,
                    bottom = (top + templWork.rows()) / fh,
                    label = name,
                    score = mm.maxVal.toFloat()
                ),
                mm.maxVal
            )
        } finally {
            gray.release()
            frameWork.release()
            templWork.release()
            result.release()
        }
    }

    private fun loadGray(info: TemplateInfo): Mat? = synchronized(cache) {
        val modified = info.imageFile.lastModified()
        cache[info.name]?.let { (stamp, mat) -> if (stamp == modified) return mat else mat.release() }
        val bitmap = store.loadBitmap(info) ?: return null
        val rgba = Mat()
        val gray = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        cache[info.name] = Pair(modified, gray)
        gray
    }
}
