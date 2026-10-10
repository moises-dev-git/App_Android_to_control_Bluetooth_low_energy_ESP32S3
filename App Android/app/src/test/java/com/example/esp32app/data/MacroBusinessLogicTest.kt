package com.example.esp32app.data

import com.example.esp32app.Macro
import org.junit.Assert.*
import org.junit.Test

class MacroBusinessLogicTest {

    private fun upsertMacro(list: MutableList<Macro>, name: String, content: String): Boolean {
        if (name.isBlank() || content.isBlank()) {
            return false
        }
        val existingIndex = list.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (existingIndex >= 0) {
            list[existingIndex] = Macro(name, content)
        } else {
            list.add(Macro(name, content))
        }
        return true
    }

    @Test
    fun testAddMacroSuccessfully() {
        val list = mutableListOf<Macro>()
        val success = upsertMacro(list, "Copiar", "CTRL+c")

        assertTrue(success)
        assertEquals(1, list.size)
        assertEquals("Copiar", list[0].name)
        assertEquals("CTRL+c", list[0].content)
    }

    @Test
    fun testUpdateExistingMacroCaseInsensitive() {
        val list = mutableListOf(Macro("copiar", "CTRL+c"))
        val success = upsertMacro(list, "COPIAR", "CTRL+C_NOVO")

        assertTrue(success)
        assertEquals(1, list.size)
        assertEquals("COPIAR", list[0].name)
        assertEquals("CTRL+C_NOVO", list[0].content)
    }

    @Test
    fun testRejectBlankMacroNameOrContent() {
        val list = mutableListOf<Macro>()
        assertFalse(upsertMacro(list, "", "CTRL+c"))
        assertFalse(upsertMacro(list, "   ", "CTRL+c"))
        assertFalse(upsertMacro(list, "Copiar", ""))
        assertFalse(upsertMacro(list, "Copiar", "   "))
        assertTrue(list.isEmpty())
    }

    @Test
    fun testDeleteMacro() {
        val m1 = Macro("M1", "C1")
        val m2 = Macro("M2", "C2")
        val list = mutableListOf(m1, m2)

        list.remove(m1)
        assertEquals(1, list.size)
        assertEquals("M2", list[0].name)
    }
}
