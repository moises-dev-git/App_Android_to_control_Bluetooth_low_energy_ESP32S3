package com.example.esp32app.data

import com.example.esp32app.Macro
import org.junit.Assert.*
import org.junit.Test

class MacroSerializerTest {

    @Test
    fun testSerializeEmptyList() {
        val json = MacroSerializer.serialize(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun testSerializeAndDeserializeRoundTrip() {
        val original = listOf(
            Macro("Copiar", "CTRL+c"),
            Macro("Colar", "CTRL+v"),
            Macro("Chrome", "WIN+r\nDELAY:300\nchrome\nENTER")
        )

        val json = MacroSerializer.serialize(original)
        val deserialized = MacroSerializer.deserialize(json)

        assertEquals(3, deserialized.size)
        assertEquals("Copiar", deserialized[0].name)
        assertEquals("CTRL+c", deserialized[0].content)
        assertEquals("Colar", deserialized[1].name)
        assertEquals("CTRL+v", deserialized[1].content)
        assertEquals("Chrome", deserialized[2].name)
        assertEquals("WIN+r\nDELAY:300\nchrome\nENTER", deserialized[2].content)
    }

    @Test
    fun testSerializationWithSpecialCharactersAndAccents() {
        val original = listOf(
            Macro("Ação & Coração", "TXT:Olá mundo! 100% testado."),
            Macro("Aspas e Linhas", "TXT:Texto com \"aspas\" e \nnova linha")
        )

        val json = MacroSerializer.serialize(original)
        val deserialized = MacroSerializer.deserialize(json)

        assertEquals(2, deserialized.size)
        assertEquals("Ação & Coração", deserialized[0].name)
        assertEquals("TXT:Olá mundo! 100% testado.", deserialized[0].content)
        assertEquals("Aspas e Linhas", deserialized[1].name)
        assertEquals("TXT:Texto com \"aspas\" e \nnova linha", deserialized[1].content)
    }

    @Test
    fun testDeserializeNullOrEmptyString() {
        val fromNull = MacroSerializer.deserialize(null)
        assertTrue(fromNull.isEmpty())

        val fromEmpty = MacroSerializer.deserialize("")
        assertTrue(fromEmpty.isEmpty())

        val fromBlank = MacroSerializer.deserialize("   ")
        assertTrue(fromBlank.isEmpty())
    }

    @Test
    fun testDeserializeMalformedJsonDoesNotCrash() {
        val malformed = "{ this is not valid json }"
        val result = MacroSerializer.deserialize(malformed)
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testDeserializeMissingFieldsFallback() {
        val json = """[{"name":"Apenas Nome"}, {"content":"Apenas Conteudo"}]"""
        val result = MacroSerializer.deserialize(json)
        assertEquals(2, result.size)
        assertEquals("Apenas Nome", result[0].name)
        assertEquals("", result[0].content)
        assertEquals("Unnamed", result[1].name)
        assertEquals("Apenas Conteudo", result[1].content)
    }
}
