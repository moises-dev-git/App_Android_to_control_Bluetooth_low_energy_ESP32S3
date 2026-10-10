package com.example.esp32app.protocol

sealed class MacroStep {
    data class Command(val payload: String) : MacroStep()
    data class Delay(val millis: Long) : MacroStep()
}

data class BleResponse(
    val isSuccess: Boolean,
    val payload: String? = null,
    val temperature: String? = null,
    val raw: String = ""
)

object BleProtocol {

    /**
     * Formata um comando de envio de texto direto para o ESP32.
     * Prefixo do protocolo: "TXT:<texto>"
     */
    fun formatTextCommand(text: String): String {
        require(text.isNotEmpty()) { "O texto a ser enviado não pode ser vazio." }
        return "TXT:$text"
    }

    /**
     * Formata um comando rápido pré-definido (Ex: CHROME, COPY, PASTE, MOUSE_LCLICK, MOUSE_RCLICK).
     * Prefixo do protocolo: "CMD:<comando>"
     */
    fun formatQuickCommand(action: String): String {
        require(action.isNotBlank()) { "A ação rápida não pode ser em branco." }
        return "CMD:$action"
    }

    /**
     * Formata uma combinação de teclas (Ex: CTRL+C, CTRL+ALT+DEL, F5).
     * Prefixo do protocolo: "CMD:KEY:<combo>"
     */
    fun formatKeyCombo(modifiers: List<String>, key: String): String {
        require(key.isNotBlank()) { "A tecla não pode ser em branco." }
        val cleanKey = key.trim().uppercase()
        val combo = if (modifiers.isNotEmpty()) {
            modifiers.joinToString("+") + "+" + cleanKey
        } else {
            cleanKey
        }
        return "CMD:KEY:$combo"
    }

    /**
     * Formata a execução de uma macro com múltiplas etapas/linhas.
     * Prefixo do protocolo: "SEQ:<conteudo>"
     */
    fun formatMacroSequence(sequence: String): String {
        require(sequence.isNotBlank()) { "A sequência de macro não pode ser em branco." }
        return "SEQ:$sequence"
    }

    /**
     * Formata comandos de movimentação de cursor do mouse.
     */
    fun formatMouseMove(x: Int, y: Int, speed: Int = 5, isHuman: Boolean = false): String {
        return if (isHuman) {
            "CMD:MOVE_HUMAN:$x,$y,$speed"
        } else {
            "CMD:MOVE:$x,$y"
        }
    }

    /**
     * Formata um comando de delay em milissegundos.
     * Limites válidos suportados pelo firmware: 1ms a 10000ms.
     */
    fun formatDelay(ms: Int): String {
        require(ms in 1..10000) { "Delay deve estar entre 1 e 10000 ms." }
        return "CMD:DELAY:$ms"
    }

    /**
     * Faz o parse das respostas vindas do ESP32 via notificação BLE TX.
     */
    fun parseResponse(response: String): BleResponse {
        val trimmed = response.trim()
        return when {
            trimmed.startsWith("OK! Received: ") -> {
                BleResponse(
                    isSuccess = true,
                    payload = trimmed.substring("OK! Received: ".length),
                    raw = trimmed
                )
            }
            trimmed.startsWith("TEMP:") -> {
                BleResponse(
                    isSuccess = true,
                    temperature = trimmed.substring("TEMP:".length),
                    raw = trimmed
                )
            }
            else -> {
                BleResponse(
                    isSuccess = false,
                    raw = trimmed
                )
            }
        }
    }

    /**
     * Fragmenta o conteúdo de uma macro em passos individuais e seguros para envio BLE.
     * Suporta macros gigantescas com centenas ou milhares de linhas.
     * Quebra textos longos (TXT:/TEXT:) em pedaços de no máximo [maxChunkSize] caracteres.
     */
    fun splitMacroIntoSteps(macroContent: String, maxChunkSize: Int = 180): List<MacroStep> {
        if (macroContent.isBlank()) return emptyList()

        val steps = mutableListOf<MacroStep>()
        val lines = macroContent.split("\r\n", "\n")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("DELAY:", ignoreCase = true)) {
                val delayMs = line.substring(6).trim().toLongOrNull() ?: 50L
                steps.add(MacroStep.Delay(delayMs.coerceAtLeast(10L)))
            } else if (line.startsWith("TEXT:", ignoreCase = true) || line.startsWith("TXT:", ignoreCase = true)) {
                val prefixLen = if (line.startsWith("TEXT:", ignoreCase = true)) 5 else 4
                val fullText = line.substring(prefixLen)
                if (fullText.isEmpty()) continue

                // Fragmenta texto longo em múltiplos comandos TXT: seguros
                var start = 0
                while (start < fullText.length) {
                    val end = (start + maxChunkSize).coerceAtMost(fullText.length)
                    val chunk = fullText.substring(start, end)
                    steps.add(MacroStep.Command("TXT:$chunk"))
                    start = end
                }
            } else {
                val formatted = if (line.startsWith("CMD:") || line.startsWith("TXT:") || line.startsWith("SEQ:")) {
                    line
                } else {
                    "CMD:$line"
                }
                steps.add(MacroStep.Command(formatted))
            }
        }
        return steps
    }
}
