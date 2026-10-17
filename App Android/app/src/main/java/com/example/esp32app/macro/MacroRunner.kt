package com.example.esp32app.macro

import com.example.esp32app.vision.ScreenMapping
import com.example.esp32app.vision.ScreenMatch
import com.example.esp32app.vision.ScreenVision
import kotlinx.coroutines.delay

class MacroAbortedException(message: String) : Exception(message)

/**
 * Executa uma macro já interpretada, passo a passo, a partir do celular.
 *
 * @param sender envia um pacote ao ESP32 e só retorna depois que ele terminou de executar.
 * @param vision acesso à tela do PC (captura HDMI); obrigatório só para comandos de visão.
 * @param mapping posição do monitor capturado na área de trabalho, para o mouse absoluto.
 * @param settleMs tempo mínimo entre o último comando enviado e uma leitura de tela,
 *                 para o PC redesenhar e o frame novo chegar pela placa de captura.
 * @param pollMs intervalo entre tentativas nos comandos WAIT_/CLICK_/MOVE_.
 */
class MacroRunner(
    private val sender: suspend (String) -> Boolean,
    private val vision: ScreenVision?,
    private val mapping: ScreenMapping = ScreenMapping.SINGLE_MONITOR,
    private val onProgress: (String) -> Unit = {},
    private val settleMs: Long = 400,
    private val pollMs: Long = 250,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class StopSignal : Exception()

    private var lastActionAt = 0L

    /** Executa a macro. Lança [MacroAbortedException] quando um passo falha. */
    suspend fun run(nodes: List<MacroNode>) {
        if (MacroScript.usesVision(nodes) && vision == null) {
            throw MacroAbortedException("Esta macro usa comandos de tela, mas a captura HDMI não está disponível")
        }
        try {
            execute(nodes)
        } catch (_: StopSignal) {
            // STOP encerra a macro normalmente
        }
    }

    private suspend fun execute(nodes: List<MacroNode>) {
        for (node in nodes) {
            when (node) {
                is MacroNode.Send -> {
                    onProgress("Linha ${node.line}: ${node.payload.take(40)}")
                    send(node.payload, node.line)
                }
                is MacroNode.Delay -> {
                    onProgress("Linha ${node.line}: aguardando ${node.millis} ms")
                    delay(node.millis)
                }
                is MacroNode.WaitFor -> {
                    val verb = if (node.present) "aparecer" else "sumir"
                    onProgress("Linha ${node.line}: esperando ${node.target.describe()} $verb")
                    val ok = poll(node.timeoutMs) { (find(node.target) != null) == node.present }
                    if (!ok) {
                        throw MacroAbortedException(
                            "Linha ${node.line}: ${node.target.describe()} não chegou a $verb em ${node.timeoutMs} ms"
                        )
                    }
                }
                is MacroNode.PointAt -> {
                    onProgress("Linha ${node.line}: procurando ${node.target.describe()}")
                    var match: ScreenMatch? = null
                    poll(node.timeoutMs) { find(node.target)?.also { match = it } != null }
                    val found = match ?: throw MacroAbortedException(
                        "Linha ${node.line}: ${node.target.describe()} não encontrado na tela em ${node.timeoutMs} ms"
                    )
                    val (x, y) = found.toHidAbsolute(mapping)
                    send("CMD:ABS:$x,$y", node.line)
                    if (node.click) send("CMD:MOUSE_LCLICK", node.line)
                }
                is MacroNode.If -> {
                    onProgress("Linha ${node.line}: verificando ${node.target.describe()}")
                    awaitSettle()
                    val visible = find(node.target) != null
                    execute(if (visible == node.present) node.thenBranch else node.elseBranch)
                }
                is MacroNode.Repeat -> repeat(node.times) { execute(node.body) }
                is MacroNode.Stop -> {
                    onProgress("Linha ${node.line}: STOP")
                    throw StopSignal()
                }
            }
        }
    }

    private suspend fun send(payload: String, line: Int) {
        if (!sender(payload)) {
            throw MacroAbortedException("Linha $line: falha ao enviar comando ao ESP32")
        }
        lastActionAt = clock()
    }

    private suspend fun find(target: VisionTarget): ScreenMatch? {
        val v = vision ?: throw MacroAbortedException("Captura HDMI não disponível")
        if (!v.isReady()) throw MacroAbortedException("Captura HDMI sem imagem. Verifique a placa de captura.")
        return when (target) {
            is VisionTarget.Text -> v.findText(target.query)
            is VisionTarget.Image -> v.findImage(target.name)
        }
    }

    private suspend fun awaitSettle() {
        val remaining = settleMs - (clock() - lastActionAt)
        if (remaining > 0) delay(remaining)
    }

    /** Repete [check] até ser verdadeiro ou o tempo acabar. Sempre tenta ao menos uma vez. */
    private suspend fun poll(timeoutMs: Long, check: suspend () -> Boolean): Boolean {
        awaitSettle()
        val deadline = clock() + timeoutMs
        while (true) {
            if (check()) return true
            if (clock() >= deadline) return false
            delay(pollMs)
        }
    }
}
