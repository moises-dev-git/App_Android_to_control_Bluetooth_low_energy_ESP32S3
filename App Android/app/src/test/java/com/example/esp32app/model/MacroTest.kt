package com.example.esp32app.model

import com.example.esp32app.Macro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MacroTest {

    @Test
    fun testMacroCreationAndGetters() {
        val macro = Macro("Abrir Chrome", "WIN+r\nDELAY:300\nchrome\nENTER")
        assertEquals("Abrir Chrome", macro.name)
        assertEquals("WIN+r\nDELAY:300\nchrome\nENTER", macro.content)
    }

    @Test
    fun testMacroToStringReturnsName() {
        val macro = Macro("Copiar e Colar", "CTRL+c\nDELAY:50\nCTRL+v")
        assertEquals("Copiar e Colar", macro.toString())
    }

    @Test
    fun testMacroWithPortugueseAccentsAndSpecialCharacters() {
        val macroName = "Automação com Acentuação: Ótimo & Rápido!"
        val content = "TXT:Olá, mundo! Ação e Reação."
        val macro = Macro(macroName, content)

        assertEquals(macroName, macro.name)
        assertEquals(content, macro.content)
        assertEquals(macroName, macro.toString())
    }

    @Test
    fun testMacroWithEmptyValues() {
        val macro = Macro("", "")
        assertNotNull(macro.name)
        assertNotNull(macro.content)
        assertEquals("", macro.name)
        assertEquals("", macro.content)
        assertEquals("", macro.toString())
    }
}
