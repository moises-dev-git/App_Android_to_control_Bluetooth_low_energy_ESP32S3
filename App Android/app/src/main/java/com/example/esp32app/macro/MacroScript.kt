package com.example.esp32app.macro

import com.example.esp32app.protocol.BleProtocol
import com.example.esp32app.protocol.MacroStep

/** Alvo de um comando de visão: um texto (OCR) ou um modelo de imagem salvo. */
sealed class VisionTarget {
    data class Text(val query: String) : VisionTarget()
    data class Image(val name: String) : VisionTarget()

    fun describe(): String = when (this) {
        is Text -> "texto \"$query\""
        is Image -> "imagem \"$name\""
    }
}

/** Nós da macro já interpretada. [line] é a linha (1-based) no editor. */
sealed class MacroNode {
    abstract val line: Int

    /** Pacote enviado ao ESP32 (comandos legados: teclas, texto, mouse...). */
    data class Send(val payload: String, override val line: Int) : MacroNode()

    data class Delay(val millis: Long, override val line: Int) : MacroNode()

    /** WAIT_TEXT / WAIT_NOT_TEXT / WAIT_IMAGE / WAIT_NOT_IMAGE */
    data class WaitFor(
        val target: VisionTarget,
        val present: Boolean,
        val timeoutMs: Long,
        override val line: Int
    ) : MacroNode()

    /** MOVE_TEXT / CLICK_TEXT / MOVE_IMAGE / CLICK_IMAGE */
    data class PointAt(
        val target: VisionTarget,
        val click: Boolean,
        val timeoutMs: Long,
        override val line: Int
    ) : MacroNode()

    /** IF_TEXT / IF_NOT_TEXT / IF_IMAGE / IF_NOT_IMAGE ... [ELSE ...] END_IF */
    data class If(
        val target: VisionTarget,
        val present: Boolean,
        val thenBranch: List<MacroNode>,
        val elseBranch: List<MacroNode>,
        override val line: Int
    ) : MacroNode()

    /** REPEAT:n ... END_REPEAT */
    data class Repeat(val times: Int, val body: List<MacroNode>, override val line: Int) : MacroNode()

    /** STOP: encerra a macro com sucesso. */
    data class Stop(override val line: Int) : MacroNode()
}

class MacroParseException(val line: Int, message: String) : Exception("Linha $line: $message")

/**
 * Interpreta o texto de uma macro.
 *
 * Linhas que não são comandos de visão/controle continuam sendo enviadas ao ESP32
 * exatamente como antes (via [BleProtocol.splitMacroIntoSteps]).
 * Linhas iniciadas por "//" são comentários.
 *
 * Os blocos fecham com END_IF / END_REPEAT (e não "END"), porque "END" já é a tecla End.
 */
object MacroScript {

    const val DEFAULT_TIMEOUT_MS = 10_000L
    const val MAX_TIMEOUT_MS = 600_000L
    const val MAX_REPEAT = 10_000

    private val waitCommands = mapOf(
        "WAIT_TEXT:" to Pair(true, true),
        "WAIT_NOT_TEXT:" to Pair(true, false),
        "WAIT_IMAGE:" to Pair(false, true),
        "WAIT_NOT_IMAGE:" to Pair(false, false)
    )
    private val pointCommands = mapOf(
        "CLICK_TEXT:" to Pair(true, true),
        "MOVE_TEXT:" to Pair(true, false),
        "CLICK_IMAGE:" to Pair(false, true),
        "MOVE_IMAGE:" to Pair(false, false)
    )
    private val ifCommands = mapOf(
        "IF_TEXT:" to Pair(true, true),
        "IF_NOT_TEXT:" to Pair(true, false),
        "IF_IMAGE:" to Pair(false, true),
        "IF_NOT_IMAGE:" to Pair(false, false)
    )

    /** Bloco aberto durante o parse (IF ou REPEAT). */
    private class OpenBlock(val header: String, val line: Int) {
        val thenBranch = mutableListOf<MacroNode>()
        val elseBranch = mutableListOf<MacroNode>()
        var inElse = false
        var target: VisionTarget? = null
        var present = true
        var times = 0

        fun current(): MutableList<MacroNode> = if (inElse) elseBranch else thenBranch
    }

    fun parse(content: String): List<MacroNode> {
        val root = mutableListOf<MacroNode>()
        val stack = ArrayDeque<OpenBlock>()
        fun current(): MutableList<MacroNode> = stack.lastOrNull()?.current() ?: root

        content.split("\r\n", "\n").forEachIndexed { index, rawLine ->
            val lineNo = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed
            val upper = line.uppercase()

            when {
                upper == "END_IF" || upper == "END_REPEAT" -> {
                    val block = stack.lastOrNull()
                    val isIf = upper == "END_IF"
                    if (block == null || (block.target != null) != isIf) {
                        throw MacroParseException(
                            lineNo,
                            if (isIf) "END_IF sem IF_... correspondente" else "END_REPEAT sem REPEAT correspondente"
                        )
                    }
                    stack.removeLast()
                    current().add(close(block))
                }
                upper == "ELSE" -> {
                    val block = stack.lastOrNull()
                    if (block == null || block.target == null) {
                        throw MacroParseException(lineNo, "ELSE fora de um bloco IF_...")
                    }
                    if (block.inElse) throw MacroParseException(lineNo, "ELSE repetido no mesmo IF")
                    block.inElse = true
                }
                upper == "STOP" -> current().add(MacroNode.Stop(lineNo))
                upper.startsWith("REPEAT:") -> {
                    val times = line.substringAfter(':').trim().toIntOrNull()
                    if (times == null || times < 1 || times > MAX_REPEAT) {
                        throw MacroParseException(lineNo, "REPEAT precisa de um número entre 1 e $MAX_REPEAT")
                    }
                    stack.addLast(OpenBlock("REPEAT", lineNo).apply { this.times = times })
                }
                else -> {
                    val ifCmd = ifCommands.entries.firstOrNull { upper.startsWith(it.key) }
                    val waitCmd = waitCommands.entries.firstOrNull { upper.startsWith(it.key) }
                    val pointCmd = pointCommands.entries.firstOrNull { upper.startsWith(it.key) }
                    when {
                        ifCmd != null -> {
                            val arg = line.substring(ifCmd.key.length).trim()
                            stack.addLast(OpenBlock(ifCmd.key, lineNo).apply {
                                target = makeTarget(ifCmd.value.first, arg, lineNo)
                                present = ifCmd.value.second
                            })
                        }
                        waitCmd != null -> {
                            val (arg, timeout) = splitTimeout(line.substring(waitCmd.key.length), lineNo)
                            current().add(
                                MacroNode.WaitFor(makeTarget(waitCmd.value.first, arg, lineNo), waitCmd.value.second, timeout, lineNo)
                            )
                        }
                        pointCmd != null -> {
                            val (arg, timeout) = splitTimeout(line.substring(pointCmd.key.length), lineNo)
                            current().add(
                                MacroNode.PointAt(makeTarget(pointCmd.value.first, arg, lineNo), pointCmd.value.second, timeout, lineNo)
                            )
                        }
                        else -> BleProtocol.splitMacroIntoSteps(line).forEach { step ->
                            current().add(
                                when (step) {
                                    is MacroStep.Command -> MacroNode.Send(step.payload, lineNo)
                                    is MacroStep.Delay -> MacroNode.Delay(step.millis, lineNo)
                                }
                            )
                        }
                    }
                }
            }
        }

        stack.lastOrNull()?.let {
            val end = if (it.target != null) "END_IF" else "END_REPEAT"
            throw MacroParseException(it.line, "bloco ${it.header.trimEnd(':')} sem $end")
        }
        return root
    }

    /** true se a macro usa algum comando que depende da captura HDMI. */
    fun usesVision(nodes: List<MacroNode>): Boolean = nodes.any { node ->
        when (node) {
            is MacroNode.WaitFor, is MacroNode.PointAt -> true
            is MacroNode.If -> true
            is MacroNode.Repeat -> usesVision(node.body)
            else -> false
        }
    }

    /** Nomes dos modelos de imagem usados pela macro (para validar antes de executar). */
    fun imageNames(nodes: List<MacroNode>): Set<String> {
        val names = mutableSetOf<String>()
        fun collect(target: VisionTarget) {
            if (target is VisionTarget.Image) names.add(target.name)
        }
        fun walk(list: List<MacroNode>) {
            for (node in list) when (node) {
                is MacroNode.WaitFor -> collect(node.target)
                is MacroNode.PointAt -> collect(node.target)
                is MacroNode.If -> { collect(node.target); walk(node.thenBranch); walk(node.elseBranch) }
                is MacroNode.Repeat -> walk(node.body)
                else -> Unit
            }
        }
        walk(nodes)
        return names
    }

    private fun close(block: OpenBlock): MacroNode =
        if (block.header == "REPEAT") {
            MacroNode.Repeat(block.times, block.thenBranch.toList(), block.line)
        } else {
            MacroNode.If(block.target!!, block.present, block.thenBranch.toList(), block.elseBranch.toList(), block.line)
        }

    private fun makeTarget(isText: Boolean, arg: String, lineNo: Int): VisionTarget {
        if (arg.isBlank()) {
            throw MacroParseException(lineNo, if (isText) "informe o texto a procurar" else "informe o nome do modelo de imagem")
        }
        return if (isText) VisionTarget.Text(arg) else VisionTarget.Image(arg)
    }

    /**
     * Separa o tempo limite opcional no final: "Salvar,5000" -> ("Salvar", 5000).
     * Se o trecho após a última vírgula não for número, a vírgula faz parte do texto.
     */
    fun splitTimeout(raw: String, lineNo: Int = 0): Pair<String, Long> {
        val text = raw.trim()
        val comma = text.lastIndexOf(',')
        if (comma > 0) {
            val timeout = text.substring(comma + 1).trim().toLongOrNull()
            if (timeout != null) {
                if (timeout < 0 || timeout > MAX_TIMEOUT_MS) {
                    throw MacroParseException(lineNo, "tempo limite deve estar entre 0 e $MAX_TIMEOUT_MS ms")
                }
                return Pair(text.substring(0, comma).trim(), timeout)
            }
        }
        return Pair(text, DEFAULT_TIMEOUT_MS)
    }
}
