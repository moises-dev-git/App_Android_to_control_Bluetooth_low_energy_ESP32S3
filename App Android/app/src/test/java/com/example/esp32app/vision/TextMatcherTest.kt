package com.example.esp32app.vision

import org.junit.Assert.*
import org.junit.Test

class TextMatcherTest {

    private fun word(text: String, left: Int, top: Int = 100) = OcrWord(text, PixelBox(left, top, left + text.length * 10, top + 20))

    private fun line(vararg words: OcrWord) = OcrLine(
        words.joinToString(" ") { it.text },
        words.map { it.box }.reduce(PixelBox::union),
        words.toList()
    )

    private val result = OcrResult(
        lines = listOf(
            line(word("Bloquear", 0, 0), word("tela", 90, 0)),
            line(word("Configurações", 0, 100), word("de", 140, 100), word("Exibição", 170, 100)),
            line(word("OK", 500, 500), word("Cancelar", 530, 500))
        ),
        frameWidth = 1000,
        frameHeight = 1000
    )

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals("configuracoes de exibicao", TextMatcher.normalize("  Configurações   de  EXIBIÇÃO "))
    }

    @Test
    fun exactWordWinsOverSubstring() {
        // "ar" aparece dentro de "Bloquear" (1ª linha), mas a palavra exata não existe;
        // já "ok" deve casar com a palavra isolada "OK", não com uma linha qualquer
        val m = TextMatcher.find(result, "ok")!!
        assertEquals(0.5f, m.left, 0.001f)
        assertEquals(0.52f, m.right, 0.001f)
    }

    @Test
    fun multiWordQueryReturnsSpanOfWords() {
        val m = TextMatcher.find(result, "de exibicao")!!
        assertEquals(0.14f, m.left, 0.001f)
        assertEquals(0.25f, m.right, 0.001f)
        assertEquals(0.1f, m.top, 0.001f)
    }

    @Test
    fun partialWordFallsBackToContainingWord() {
        val m = TextMatcher.find(result, "Cancel")!!
        assertEquals(0.53f, m.left, 0.001f)
    }

    @Test
    fun notFoundReturnsNull() {
        assertNull(TextMatcher.find(result, "Salvar"))
        assertNull(TextMatcher.find(result, "   "))
    }
}
