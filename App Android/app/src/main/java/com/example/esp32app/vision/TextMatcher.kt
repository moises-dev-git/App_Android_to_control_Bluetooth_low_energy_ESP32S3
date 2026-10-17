package com.example.esp32app.vision

import java.text.Normalizer

/** Retângulo em pixels do frame analisado. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun union(other: PixelBox) = PixelBox(
        minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom)
    )
}

data class OcrWord(val text: String, val box: PixelBox)

data class OcrLine(val text: String, val box: PixelBox, val words: List<OcrWord>)

/** Resultado de OCR de um frame, independente da biblioteca usada. */
data class OcrResult(val lines: List<OcrLine>, val frameWidth: Int, val frameHeight: Int)

/**
 * Localiza um texto dentro do resultado do OCR.
 * A comparação ignora maiúsculas, acentos e espaços repetidos.
 */
object TextMatcher {

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun find(result: OcrResult, query: String): ScreenMatch? {
        val target = normalize(query)
        if (target.isEmpty() || result.frameWidth <= 0 || result.frameHeight <= 0) return null

        val box = findExactWord(result, target)
            ?: findWordSpan(result, target)
            ?: result.lines.firstOrNull { normalize(it.text).contains(target) }?.box
            ?: return null

        return ScreenMatch(
            left = box.left.toFloat() / result.frameWidth,
            top = box.top.toFloat() / result.frameHeight,
            right = box.right.toFloat() / result.frameWidth,
            bottom = box.bottom.toFloat() / result.frameHeight,
            label = query
        )
    }

    /** Palavra idêntica à busca tem prioridade (ex.: "OK" não deve casar com "Bloquear"). */
    private fun findExactWord(result: OcrResult, target: String): PixelBox? =
        result.lines.asSequence()
            .flatMap { it.words.asSequence() }
            .firstOrNull { normalize(it.text) == target }
            ?.box

    /** Menor sequência de palavras consecutivas de uma linha que contém a busca. */
    private fun findWordSpan(result: OcrResult, target: String): PixelBox? {
        for (line in result.lines) {
            if (!normalize(line.text).contains(target)) continue
            val words = line.words.map { normalize(it.text) }
            for (length in 1..words.size) {
                for (start in 0..words.size - length) {
                    val joined = words.subList(start, start + length).joinToString(" ")
                    if (joined.contains(target)) {
                        return line.words.subList(start, start + length).map { it.box }.reduce(PixelBox::union)
                    }
                }
            }
        }
        return null
    }
}
