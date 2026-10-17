package com.example.esp32app.vision

/**
 * Região encontrada na tela do PC, em coordenadas normalizadas (0.0 a 1.0)
 * relativas ao frame capturado da saída HDMI.
 */
data class ScreenMatch(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String = "",
    val score: Float = 1f
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /**
     * Converte o centro da região para o espaço do mouse absoluto HID (0..32767),
     * usado pelo comando ABS:x,y do firmware. [mapping] diz onde o monitor capturado
     * fica na área de trabalho quando o PC tem mais de um monitor.
     */
    fun toHidAbsolute(mapping: ScreenMapping = ScreenMapping.SINGLE_MONITOR): Pair<Int, Int> =
        mapping.toHid(centerX, centerY)

    companion object {
        const val HID_ABS_MAX = 32767

        fun toHidAxis(normalized: Float): Int =
            (normalized.coerceIn(0f, 1f) * HID_ABS_MAX).toInt().coerceIn(0, HID_ABS_MAX)
    }
}

/**
 * Fonte de "visão" da tela do PC usada pelo interpretador de macros.
 * A implementação real usa a captura HDMI; os testes usam implementações falsas.
 */
interface ScreenVision {
    /** true quando há frames recentes disponíveis para análise. */
    fun isReady(): Boolean

    /** Procura um texto na tela (sem diferenciar maiúsculas e acentos). */
    suspend fun findText(query: String): ScreenMatch?

    /** Procura um modelo de imagem salvo pelo nome. */
    suspend fun findImage(name: String): ScreenMatch?
}
