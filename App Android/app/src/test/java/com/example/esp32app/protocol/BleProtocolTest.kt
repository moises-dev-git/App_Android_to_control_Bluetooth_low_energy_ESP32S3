package com.example.esp32app.protocol

import org.junit.Assert.*
import org.junit.Test

class BleProtocolTest {

    @Test
    fun testFormatTextCommandSuccess() {
        val cmd = BleProtocol.formatTextCommand("Hello ESP32!")
        assertEquals("TXT:Hello ESP32!", cmd)
    }

    @Test
    fun testFormatTextCommandWithPortugueseAccents() {
        val text = "Olá! Teste de Acentuação com Português: coração, ação, vovô & maçã."
        val cmd = BleProtocol.formatTextCommand(text)
        assertEquals("TXT:$text", cmd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFormatTextCommandFailsOnEmpty() {
        BleProtocol.formatTextCommand("")
    }

    @Test
    fun testFormatQuickCommand() {
        assertEquals("CMD:CHROME", BleProtocol.formatQuickCommand("CHROME"))
        assertEquals("CMD:COPY", BleProtocol.formatQuickCommand("COPY"))
        assertEquals("CMD:PASTE", BleProtocol.formatQuickCommand("PASTE"))
        assertEquals("CMD:MOUSE_LCLICK", BleProtocol.formatQuickCommand("MOUSE_LCLICK"))
        assertEquals("CMD:MOUSE_RCLICK", BleProtocol.formatQuickCommand("MOUSE_RCLICK"))
    }

    @Test
    fun testFormatKeyComboSingleKey() {
        val cmd = BleProtocol.formatKeyCombo(emptyList(), "f5")
        assertEquals("CMD:KEY:F5", cmd)
    }

    @Test
    fun testFormatKeyComboWithSingleModifier() {
        val cmd = BleProtocol.formatKeyCombo(listOf("CTRL"), "c")
        assertEquals("CMD:KEY:CTRL+C", cmd)
    }

    @Test
    fun testFormatKeyComboWithMultipleModifiers() {
        val cmd = BleProtocol.formatKeyCombo(listOf("CTRL", "ALT"), "del")
        assertEquals("CMD:KEY:CTRL+ALT+DEL", cmd)
    }

    @Test
    fun testFormatKeyComboWithSpecialKeyAndAllModifiers() {
        val cmd = BleProtocol.formatKeyCombo(listOf("CTRL", "ALT", "SHIFT", "WIN"), "ENTER")
        assertEquals("CMD:KEY:CTRL+ALT+SHIFT+WIN+ENTER", cmd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFormatKeyComboFailsOnBlankKey() {
        BleProtocol.formatKeyCombo(listOf("CTRL"), "   ")
    }

    @Test
    fun testFormatMacroSequence() {
        val sequence = "WIN+r\nDELAY:300\nchrome\nENTER"
        val cmd = BleProtocol.formatMacroSequence(sequence)
        assertEquals("SEQ:$sequence", cmd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFormatMacroSequenceFailsOnBlank() {
        BleProtocol.formatMacroSequence("   ")
    }

    @Test
    fun testFormatMouseMoveStandard() {
        val cmd = BleProtocol.formatMouseMove(100, 200, speed = 5, isHuman = false)
        assertEquals("CMD:MOVE:100,200", cmd)
    }

    @Test
    fun testFormatMouseMoveHumanLike() {
        val cmd = BleProtocol.formatMouseMove(350, -150, speed = 8, isHuman = true)
        assertEquals("CMD:MOVE_HUMAN:350,-150,8", cmd)
    }

    @Test
    fun testFormatDelay() {
        val cmd = BleProtocol.formatDelay(500)
        assertEquals("CMD:DELAY:500", cmd)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFormatDelayFailsOnZeroOrNegative() {
        BleProtocol.formatDelay(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFormatDelayFailsOnExceedingLimit() {
        BleProtocol.formatDelay(15000)
    }

    @Test
    fun testParseResponseAck() {
        val parsed = BleProtocol.parseResponse("OK! Received: TXT:Hello")
        assertTrue(parsed.isSuccess)
        assertEquals("TXT:Hello", parsed.payload)
    }

    @Test
    fun testParseResponseTemperature() {
        val parsed = BleProtocol.parseResponse("TEMP:43.5C")
        assertTrue(parsed.isSuccess)
        assertEquals("43.5C", parsed.temperature)
    }

    @Test
    fun testSplitMacroIntoStepsSimple() {
        val sequence = "CTRL+c\nENTER"
        val steps = BleProtocol.splitMacroIntoSteps(sequence)
        assertEquals(2, steps.size)
        assertTrue(steps[0] is MacroStep.Command)
        assertEquals("CMD:CTRL+c", (steps[0] as MacroStep.Command).payload)
        assertTrue(steps[1] is MacroStep.Command)
        assertEquals("CMD:ENTER", (steps[1] as MacroStep.Command).payload)
    }

    @Test
    fun testSplitMacroIntoStepsWithDelaysAndPrefixes() {
        val sequence = "WIN+r\nDELAY:350\nTXT:chrome\nENTER"
        val steps = BleProtocol.splitMacroIntoSteps(sequence)
        assertEquals(4, steps.size)
        assertEquals("CMD:WIN+r", (steps[0] as MacroStep.Command).payload)
        assertEquals(350L, (steps[1] as MacroStep.Delay).millis)
        assertEquals("TXT:chrome", (steps[2] as MacroStep.Command).payload)
        assertEquals("CMD:ENTER", (steps[3] as MacroStep.Command).payload)
    }

    @Test
    fun testSplitMacroHugeTextIntoSafeChunks() {
        val longText = "TEXT:" + "A".repeat(350)
        val steps = BleProtocol.splitMacroIntoSteps(longText, maxChunkSize = 150)
        assertEquals(3, steps.size)
        assertEquals("TXT:" + "A".repeat(150), (steps[0] as MacroStep.Command).payload)
        assertEquals("TXT:" + "A".repeat(150), (steps[1] as MacroStep.Command).payload)
        assertEquals("TXT:" + "A".repeat(50), (steps[2] as MacroStep.Command).payload)
    }

    @Test
    fun testSplitMacroGiantMacroSupportsHundredsOfSteps() {
        val sb = StringBuilder()
        for (i in 1..200) {
            sb.append("CTRL+Z\nDELAY:50\n")
        }
        val steps = BleProtocol.splitMacroIntoSteps(sb.toString())
        assertEquals(400, steps.size)
        for (step in steps) {
            if (step is MacroStep.Command) {
                assertTrue(step.payload.length < 50)
            }
        }
    }
}

