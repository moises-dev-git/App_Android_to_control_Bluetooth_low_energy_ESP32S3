package com.example.esp32app.vision

import android.content.Context

/**
 * Onde o monitor capturado pela placa HDMI fica dentro da área de trabalho do PC.
 *
 * O SO espalha o mouse absoluto HID (0..32767) pela área de trabalho inteira, que com
 * vários monitores é maior que a tela capturada. Ex.: monitor principal 1920x1200 à
 * esquerda e o capturado 1920x1080 à direita → área 3840x1200, monitor em x=1920, y=0.
 *
 * Todas as medidas são em pixels do PC e a posição é contada a partir do canto superior
 * esquerdo da área de trabalho inteira (por isso nunca é negativa, mesmo no Windows).
 * Com um único monitor, área e monitor têm o mesmo tamanho e a posição é 0,0.
 */
data class ScreenMapping(
    val desktopWidth: Int,
    val desktopHeight: Int,
    val monitorX: Int,
    val monitorY: Int,
    val monitorWidth: Int,
    val monitorHeight: Int
) {
    val isSingleMonitor: Boolean
        get() = monitorX == 0 && monitorY == 0 && monitorWidth == desktopWidth && monitorHeight == desktopHeight

    /** Mensagem do problema, ou null se a configuração é utilizável. */
    fun validationError(): String? = when {
        desktopWidth <= 0 || desktopHeight <= 0 -> "O tamanho da área de trabalho deve ser maior que zero"
        monitorWidth <= 0 || monitorHeight <= 0 -> "O tamanho do monitor capturado deve ser maior que zero"
        monitorX < 0 || monitorY < 0 -> "A posição do monitor não pode ser negativa"
        monitorX + monitorWidth > desktopWidth -> "O monitor passa da largura da área de trabalho"
        monitorY + monitorHeight > desktopHeight -> "O monitor passa da altura da área de trabalho"
        else -> null
    }

    /** Converte um ponto normalizado (0..1) do frame capturado para o mouse absoluto HID. */
    fun toHid(normalizedX: Float, normalizedY: Float): Pair<Int, Int> = Pair(
        axis(normalizedX, monitorX, monitorWidth, desktopWidth),
        axis(normalizedY, monitorY, monitorHeight, desktopHeight)
    )

    private fun axis(normalized: Float, offset: Int, size: Int, total: Int): Int {
        val pixel = offset + normalized.coerceIn(0f, 1f) * size
        return ScreenMatch.toHidAxis(pixel / total)
    }

    companion object {
        /** Um único monitor: o frame capturado ocupa toda a faixa do mouse absoluto. */
        val SINGLE_MONITOR = ScreenMapping(1920, 1080, 0, 0, 1920, 1080)
    }
}

/** Guarda a calibração do monitor capturado nas preferências do app. */
object ScreenMappingStore {
    private const val PREF_NAME = "screen_mapping"

    fun load(context: Context): ScreenMapping {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val d = ScreenMapping.SINGLE_MONITOR
        val mapping = ScreenMapping(
            desktopWidth = prefs.getInt("desktop_w", d.desktopWidth),
            desktopHeight = prefs.getInt("desktop_h", d.desktopHeight),
            monitorX = prefs.getInt("monitor_x", d.monitorX),
            monitorY = prefs.getInt("monitor_y", d.monitorY),
            monitorWidth = prefs.getInt("monitor_w", d.monitorWidth),
            monitorHeight = prefs.getInt("monitor_h", d.monitorHeight)
        )
        return if (mapping.validationError() == null) mapping else d
    }

    fun save(context: Context, mapping: ScreenMapping) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt("desktop_w", mapping.desktopWidth)
            .putInt("desktop_h", mapping.desktopHeight)
            .putInt("monitor_x", mapping.monitorX)
            .putInt("monitor_y", mapping.monitorY)
            .putInt("monitor_w", mapping.monitorWidth)
            .putInt("monitor_h", mapping.monitorHeight)
            .apply()
    }
}
