package com.example.esp32app.macro

import org.junit.Assert.*
import org.junit.Test

class MacroScriptTest {

    @Test
    fun legacyLinesKeepSamePayloads() {
        val nodes = MacroScript.parse("CTRL+C\nDELAY:200\nTEXT:Olá\nMOVE:500,300")
        assertEquals(
            listOf(
                MacroNode.Send("CMD:CTRL+C", 1),
                MacroNode.Delay(200, 2),
                MacroNode.Send("TXT:Olá", 3),
                MacroNode.Send("CMD:MOVE:500,300", 4)
            ),
            nodes
        )
        assertFalse(MacroScript.usesVision(nodes))
    }

    @Test
    fun plainEndIsStillTheEndKey() {
        // Macros antigas usam "END" para a tecla End; isso não pode virar fim de bloco
        assertEquals(listOf(MacroNode.Send("CMD:END", 1)), MacroScript.parse("END"))
        val nodes = MacroScript.parse("IF_TEXT:x\nEND\nEND_IF")
        assertEquals(listOf(MacroNode.Send("CMD:END", 2)), (nodes.single() as MacroNode.If).thenBranch)
    }

    @Test
    fun commentsAndBlankLinesAreIgnored() {
        val nodes = MacroScript.parse("// abre o menu\n\n   \nENTER")
        assertEquals(listOf(MacroNode.Send("CMD:ENTER", 4)), nodes)
    }

    @Test
    fun waitAndClickCommandsWithDefaultAndCustomTimeout() {
        val nodes = MacroScript.parse("WAIT_TEXT:Salvar\nclick_image:icone,2500\nWAIT_NOT_TEXT:Carregando,0\nMOVE_TEXT:Arquivo")
        assertEquals(MacroNode.WaitFor(VisionTarget.Text("Salvar"), true, MacroScript.DEFAULT_TIMEOUT_MS, 1), nodes[0])
        assertEquals(MacroNode.PointAt(VisionTarget.Image("icone"), true, 2500, 2), nodes[1])
        assertEquals(MacroNode.WaitFor(VisionTarget.Text("Carregando"), false, 0, 3), nodes[2])
        assertEquals(MacroNode.PointAt(VisionTarget.Text("Arquivo"), false, MacroScript.DEFAULT_TIMEOUT_MS, 4), nodes[3])
        assertTrue(MacroScript.usesVision(nodes))
    }

    @Test
    fun commaWithoutNumberBelongsToText() {
        assertEquals(Pair("Sim, continuar", MacroScript.DEFAULT_TIMEOUT_MS), MacroScript.splitTimeout("Sim, continuar"))
        assertEquals(Pair("Sim, continuar", 3000L), MacroScript.splitTimeout("Sim, continuar,3000"))
    }

    @Test
    fun nestedIfElseAndRepeat() {
        val script = """
            REPEAT:3
              IF_TEXT:Erro
                ESC
              ELSE
                IF_NOT_IMAGE:ok
                  STOP
                END_IF
                ENTER
              END_IF
            END_REPEAT
        """.trimIndent()
        val nodes = MacroScript.parse(script)
        assertEquals(1, nodes.size)
        val repeat = nodes[0] as MacroNode.Repeat
        assertEquals(3, repeat.times)
        val ifNode = repeat.body.single() as MacroNode.If
        assertEquals(VisionTarget.Text("Erro"), ifNode.target)
        assertTrue(ifNode.present)
        assertEquals(listOf(MacroNode.Send("CMD:ESC", 3)), ifNode.thenBranch)
        val inner = ifNode.elseBranch[0] as MacroNode.If
        assertEquals(VisionTarget.Image("ok"), inner.target)
        assertFalse(inner.present)
        assertEquals(listOf(MacroNode.Stop(6)), inner.thenBranch)
        assertEquals(MacroNode.Send("CMD:ENTER", 8), ifNode.elseBranch[1])
    }

    @Test
    fun imageNamesCollectsNestedTargets() {
        val nodes = MacroScript.parse("WAIT_IMAGE:a\nREPEAT:2\nIF_IMAGE:b\nCLICK_IMAGE:c\nELSE\nMOVE_IMAGE:a\nEND_IF\nEND_REPEAT\nCLICK_TEXT:x")
        assertEquals(setOf("a", "b", "c"), MacroScript.imageNames(nodes))
    }

    @Test
    fun structuralErrorsReportLine() {
        assertParseError("ENTER\nEND_IF", 2)
        assertParseError("ENTER\nEND_REPEAT", 2)
        assertParseError("IF_TEXT:x\nEND_REPEAT", 2)
        assertParseError("REPEAT:2\nEND_IF", 2)
        assertParseError("ELSE", 1)
        assertParseError("REPEAT:2\nELSE\nEND_REPEAT", 2)
        assertParseError("IF_TEXT:x\nELSE\nELSE\nEND_IF", 3)
        assertParseError("ENTER\nIF_TEXT:x\nTAB", 2)
        assertParseError("REPEAT:0\nEND_REPEAT", 1)
        assertParseError("WAIT_TEXT:", 1)
        assertParseError("WAIT_TEXT:abc,999999999", 1)
    }

    private fun assertParseError(script: String, expectedLine: Int) {
        try {
            MacroScript.parse(script)
            fail("Esperava erro de parse em: $script")
        } catch (e: MacroParseException) {
            assertEquals(expectedLine, e.line)
        }
    }
}
