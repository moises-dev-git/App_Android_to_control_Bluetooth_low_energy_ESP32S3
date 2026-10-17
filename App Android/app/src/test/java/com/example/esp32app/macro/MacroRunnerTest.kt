package com.example.esp32app.macro

import com.example.esp32app.vision.ScreenMapping
import com.example.esp32app.vision.ScreenMatch
import com.example.esp32app.vision.ScreenVision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MacroRunnerTest {

    /** Tela falsa: textos/imagens visíveis podem mudar depois de cada comando enviado. */
    private class FakeVision : ScreenVision {
        val texts = mutableMapOf<String, ScreenMatch>()
        val images = mutableMapOf<String, ScreenMatch>()
        var ready = true
        var lookups = 0

        override fun isReady() = ready
        override suspend fun findText(query: String): ScreenMatch? { lookups++; return texts[query] }
        override suspend fun findImage(name: String): ScreenMatch? { lookups++; return images[name] }
    }

    private val sent = mutableListOf<String>()
    private val vision = FakeVision()

    private fun runner(onSend: (String) -> Unit = {}, sendOk: Boolean = true) = MacroRunner(
        sender = { sent.add(it); onSend(it); sendOk },
        vision = vision,
        settleMs = 0,
        pollMs = 5
    )

    private fun run(script: String, r: MacroRunner = runner()) = runBlocking { r.run(MacroScript.parse(script)) }

    @Test
    fun clickTextMovesToCenterAndClicks() {
        vision.texts["OK"] = ScreenMatch(0.5f, 0.5f, 0.7f, 0.6f)
        run("CLICK_TEXT:OK")
        val (x, y) = ScreenMatch(0.5f, 0.5f, 0.7f, 0.6f).toHidAbsolute()
        assertEquals(listOf("CMD:ABS:$x,$y", "CMD:MOUSE_LCLICK"), sent)
    }

    @Test
    fun clickTextUsesCapturedMonitorPositionOnMultiMonitorDesktop() {
        // Monitor capturado à direita de outro: o centro dele fica em 3/4 da largura da área
        val mapping = ScreenMapping(3840, 1200, 1920, 0, 1920, 1080)
        vision.texts["OK"] = ScreenMatch(0.4f, 0.4f, 0.6f, 0.6f)
        val r = MacroRunner(sender = { sent.add(it); true }, vision = vision, mapping = mapping, settleMs = 0, pollMs = 5)
        run("CLICK_TEXT:OK", r)
        assertEquals(listOf("CMD:ABS:24575,14745", "CMD:MOUSE_LCLICK"), sent)
    }

    @Test
    fun moveImageOnlyMoves() {
        vision.images["logo"] = ScreenMatch(0f, 0f, 0f, 0f)
        run("MOVE_IMAGE:logo")
        assertEquals(listOf("CMD:ABS:0,0"), sent)
    }

    @Test
    fun ifElseChoosesBranchByScreenContent() {
        vision.texts["Erro"] = ScreenMatch(0f, 0f, 1f, 1f)
        run("IF_TEXT:Erro\nESC\nELSE\nENTER\nEND_IF\nIF_NOT_TEXT:Erro\nTAB\nEND_IF")
        assertEquals(listOf("CMD:ESC"), sent)
    }

    @Test
    fun waitTextSucceedsWhenTextAppearsLater() {
        val r = MacroRunner(
            sender = { sent.add(it); true },
            vision = object : ScreenVision {
                var calls = 0
                override fun isReady() = true
                override suspend fun findText(query: String) =
                    if (++calls >= 3) ScreenMatch(0f, 0f, 1f, 1f) else null
                override suspend fun findImage(name: String): ScreenMatch? = null
            },
            settleMs = 0, pollMs = 5
        )
        run("WAIT_TEXT:Pronto,2000\nENTER", r)
        assertEquals(listOf("CMD:ENTER"), sent)
    }

    @Test
    fun waitTimeoutAbortsMacroAndStopsSending() {
        try {
            run("WAIT_TEXT:Nunca,50\nENTER")
            fail("Esperava MacroAbortedException")
        } catch (e: MacroAbortedException) {
            assertTrue(e.message!!.contains("Linha 1"))
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun waitNotTextWaitsForDisappearance() {
        vision.texts["Carregando"] = ScreenMatch(0f, 0f, 1f, 1f)
        val r = runner(onSend = {})
        Thread { Thread.sleep(30); vision.texts.clear() }.start()
        run("WAIT_NOT_TEXT:Carregando,2000\nENTER", r)
        assertEquals(listOf("CMD:ENTER"), sent)
    }

    @Test
    fun repeatAndStop() {
        run("REPEAT:3\nTAB\nEND_REPEAT\nENTER\nSTOP\nESC")
        assertEquals(listOf("CMD:TAB", "CMD:TAB", "CMD:TAB", "CMD:ENTER"), sent)
    }

    @Test
    fun screenChangesAfterCommandAreSeenByNextCheck() {
        // Simula o PC abrindo um diálogo depois do ENTER
        val r = runner(onSend = { if (it == "CMD:ENTER") vision.texts["Salvar"] = ScreenMatch(0f, 0f, 1f, 1f) })
        run("ENTER\nIF_TEXT:Salvar\nCTRL+S\nEND_IF", r)
        assertEquals(listOf("CMD:ENTER", "CMD:CTRL+S"), sent)
    }

    @Test
    fun visionMacroWithoutCaptureFailsBeforeSendingAnything() {
        val r = MacroRunner(sender = { sent.add(it); true }, vision = null)
        try {
            run("ENTER\nWAIT_TEXT:x", r)
            fail("Esperava MacroAbortedException")
        } catch (_: MacroAbortedException) {
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun captureWithoutFramesAborts() {
        vision.ready = false
        try {
            run("IF_TEXT:x\nENTER\nEND_IF")
            fail("Esperava MacroAbortedException")
        } catch (_: MacroAbortedException) {
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun sendFailureAborts() {
        try {
            run("ENTER\nTAB", runner(sendOk = false))
            fail("Esperava MacroAbortedException")
        } catch (e: MacroAbortedException) {
            assertTrue(e.message!!.contains("Linha 1"))
        }
        assertEquals(listOf("CMD:ENTER"), sent)
    }

    @Test
    fun hidAbsoluteConversionClampsToRange() {
        assertEquals(0, ScreenMatch.toHidAxis(-0.5f))
        assertEquals(32767, ScreenMatch.toHidAxis(1.5f))
        assertEquals(16383, ScreenMatch.toHidAxis(0.5f))
    }
}
