package com.catprint

import com.catprint.core.doc.DocBlock
import com.catprint.core.doc.DocSerializer
import com.catprint.core.printer.AppFonts
import org.junit.Assert.*
import org.junit.Test

class DocModelTest {

    @Test
    fun lineHeight_roundtrip_asFactor() {
        val blocks = listOf(
            DocBlock.Text("привет", fontSize = 24f, lineHeight = 30f)
        )
        val loaded = DocSerializer.load(DocSerializer.save(blocks))
        val t = loaded.single() as DocBlock.Text
        assertEquals(30f, t.lineHeight!!, 0.01f)
    }

    @Test
    fun lineHeight_null_staysNull() {
        val blocks = listOf(DocBlock.Text("тест", fontSize = 24f))
        val loaded = DocSerializer.load(DocSerializer.save(blocks))
        val t = loaded.single() as DocBlock.Text
        assertNull(t.lineHeight)
    }

    @Test
    fun textFields_surviveRoundtrip() {
        val blocks = listOf(
            DocBlock.Text(
                "Тест 123 😀", fontFamily = "Arial", fontSize = 36f,
                bold = true, italic = true, underline = true,
                alignment = 0, lineHeight = 54f
            )
        )
        val loaded = DocSerializer.load(DocSerializer.save(blocks))
        val t = loaded.single() as DocBlock.Text
        assertEquals("Тест 123 😀", t.text)
        assertEquals("Arial", t.fontFamily)
        assertEquals(36f, t.fontSize, 0.001f)
        assertTrue(t.bold && t.italic && t.underline)
        assertEquals(0, t.alignment)
        assertEquals(54f, t.lineHeight!!, 0.01f)
    }

    @Test
    fun align_mobileFileMapping() {
        // Мобильные коды 0/1/2 <-> файловые 0/2/1 (Left/Center/Right)
        assertEquals(0, DocSerializer.toFileAlign(0))
        assertEquals(2, DocSerializer.toFileAlign(1))
        assertEquals(1, DocSerializer.toFileAlign(2))
        assertEquals(0, DocSerializer.fromFileAlign(0))
        assertEquals(1, DocSerializer.fromFileAlign(2))
        assertEquals(2, DocSerializer.fromFileAlign(1))
        assertEquals(1, DocSerializer.fromFileAlign(9))
    }

    @Test
    fun fonts_keysDistinctAndNamed() {
        val keys = AppFonts.allKeys
        assertTrue(keys.size >= 10)
        assertEquals(keys.size, keys.toSet().size)
        for (k in keys) {
            assertNotNull("Нет отображаемого имени: $k", AppFonts.displayNames[k])
        }
    }

    @Test
    fun fonts_windowsKeysPresent() {
        for (k in listOf("Arial", "Times New Roman", "Courier New", "Calibri",
            "Segoe UI", "Tahoma", "Consolas", "Comic Sans MS")) {
            assertTrue("Нет ключа $k", AppFonts.allKeys.contains(k))
        }
    }
}
