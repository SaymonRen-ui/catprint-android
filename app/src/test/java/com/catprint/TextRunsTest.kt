package com.catprint

import com.catprint.core.doc.DocBlock
import com.catprint.core.doc.DocSerializer
import com.catprint.core.doc.StyleAttr
import com.catprint.core.doc.TextRun
import com.catprint.core.doc.mergeRuns
import com.catprint.core.doc.retypeIn
import com.catprint.core.doc.setStyleIn
import com.catprint.core.doc.splitRunsAt
import com.catprint.core.doc.styleAt
import com.catprint.core.doc.toggleStyleIn
import org.junit.Assert.*
import org.junit.Test

class TextRunsTest {

    private fun tb(text: String) = DocBlock.Text(text = text, fontSize = 24f)

    @Test
    fun split_middle() {
        val rs = mutableListOf(TextRun("abcd"))
        splitRunsAt(rs, 2)
        assertEquals(listOf("ab", "cd"), rs.map { it.text })
    }

    @Test
    fun split_edges_noop() {
        val rs = mutableListOf(TextRun("abcd"))
        splitRunsAt(rs, 0)
        splitRunsAt(rs, 4)
        splitRunsAt(rs, 9)
        assertEquals(1, rs.size)
        assertEquals("abcd", rs[0].text)
    }

    @Test
    fun merge_identical() {
        val rs = mutableListOf(
            TextRun("a", bold = true),
            TextRun("b", bold = true),
            TextRun("c"),
            TextRun("", bold = true)
        )
        mergeRuns(rs)
        assertEquals(2, rs.size)
        assertEquals("ab", rs[0].text)
        assertTrue(rs[0].bold)
        assertEquals("c", rs[1].text)
    }

    @Test
    fun styleAt_inheritsLeft() {
        val rs = listOf(
            TextRun("ab", fontSize = 48f),
            TextRun("cd", fontSize = 64f)
        )
        assertEquals(48f, styleAt(rs, 0)!!.fontSize)
        assertEquals(48f, styleAt(rs, 1)!!.fontSize)
        assertEquals(48f, styleAt(rs, 2)!!.fontSize)
        assertEquals(64f, styleAt(rs, 3)!!.fontSize)
        assertEquals(64f, styleAt(rs, 4)!!.fontSize)
    }

    @Test
    fun toggle_range_onOff() {
        var b = tb("Test")
        b = toggleStyleIn(b, 0, 1, StyleAttr.BOLD)
        assertEquals(2, b.runs.size)
        assertTrue(b.runs[0].bold)
        assertEquals("T", b.runs[0].text)
        assertFalse(b.runs[1].bold)
        // Повторный тап по тому же отрезку — снять.
        b = toggleStyleIn(b, 0, 1, StyleAttr.BOLD)
        assertTrue(b.runs.isEmpty())
        assertFalse(b.bold)
    }

    @Test
    fun toggle_range_smart_allOffOnlyIfAllOn() {
        var b = tb("Test")
        b = toggleStyleIn(b, 0, 2, StyleAttr.BOLD) // T,e bold
        b = toggleStyleIn(b, 1, 3, StyleAttr.BOLD) // e,s: e уже, s станет
        assertEquals(2, b.runs.size)
        assertEquals("Tes", b.runs[0].text)
        assertTrue(b.runs[0].bold)
        assertEquals("t", b.runs[1].text)
        assertFalse(b.runs[1].bold)
        // А теперь всё покрытое жирное — тап гасит.
        b = toggleStyleIn(b, 0, 3, StyleAttr.BOLD)
        assertTrue(b.runs.isEmpty())
        assertFalse(b.bold)
    }

    @Test
    fun toggle_wholeBlock() {
        var b = tb("Test")
        b = toggleStyleIn(b, null, null, StyleAttr.ITALIC)
        assertTrue(b.italic)
        assertTrue(b.runs.isEmpty())
        b = toggleStyleIn(b, 0, 0, StyleAttr.ITALIC) // пустой диапазон = весь
        assertFalse(b.italic)
    }

    @Test
    fun setSize_range() {
        var b = tb("Test")
        b = setStyleIn(b, 0, 1, { it.fontSize = 48f }, { it.fontSize = 48f })
        assertEquals(48f, b.runs[0].fontSize)
        assertEquals(24f, b.runs[1].fontSize)
        assertEquals("Test", b.text)
    }

    @Test
    fun setSize_whole_collapses() {
        var b = tb("Test")
        b = setStyleIn(b, 0, 1, { it.fontSize = 48f }, { it.fontSize = 48f })
        assertEquals(2, b.runs.size)
        b = setStyleIn(b, null, null, { it.fontSize = 48f }, { it.fontSize = 48f })
        assertTrue(b.runs.isEmpty())
        assertEquals(48f, b.fontSize)
    }

    @Test
    fun retype_insertInheritsLeft() {
        var b = tb("Test")
        b = toggleStyleIn(b, 0, 1, StyleAttr.BOLD) // T жирная
        b = retypeIn(b, "Txest")
        assertEquals("Txest", b.text)
        assertEquals(2, b.runs.size)
        assertEquals("Tx", b.runs[0].text)
        assertTrue(b.runs[0].bold) // вставка после T — жирная, слилась с ней
        assertEquals("est", b.runs[1].text)
        assertFalse(b.runs[1].bold)
    }

    @Test
    fun retype_deleteTrims() {
        var b = tb("Test")
        b = toggleStyleIn(b, 0, 1, StyleAttr.BOLD)
        b = retypeIn(b, "est")
        assertEquals("est", b.text)
        assertTrue(b.runs.isEmpty()) // остался однородный кусок
    }

    @Test
    fun retype_clearResets() {
        var b = tb("Test")
        b = toggleStyleIn(b, 0, 1, StyleAttr.BOLD)
        b = retypeIn(b, "")
        assertEquals("", b.text)
        assertTrue(b.runs.isEmpty())
    }

    @Test
    fun retype_forceStyle_overridesInherit() {
        var b = tb("Test") // однородный, наследовать нечего
        val force = TextRun(fontSize = 48f, bold = true)
        b = retypeIn(b, "Test!", force)
        assertEquals("Test!", b.text)
        assertEquals(2, b.runs.size)
        assertEquals("Test", b.runs[0].text)
        assertFalse(b.runs[0].bold)
        assertEquals("!", b.runs[1].text)
        assertTrue(b.runs[1].bold)
        assertEquals(48f, b.runs[1].fontSize)
    }

    @Test
    fun multiRun_roundtrip() {
        var b = tb("Test")
        b = setStyleIn(b, 0, 1, { it.fontSize = 48f }, { it.fontSize = 48f })
        b = toggleStyleIn(b, 1, 2, StyleAttr.BOLD)
        assertEquals(3, b.runs.size)
        val loaded = DocSerializer.load(DocSerializer.save(listOf(b)))
        val t = loaded.single() as DocBlock.Text
        assertEquals("Test", t.text)
        assertEquals(3, t.runs.size)
        assertEquals("T", t.runs[0].text)
        assertEquals(48f, t.runs[0].fontSize)
        assertEquals("e", t.runs[1].text)
        assertTrue(t.runs[1].bold)
        assertEquals("st", t.runs[2].text)
        assertFalse(t.runs[2].bold)
    }

    @Test
    fun uniform_savesSingleRun_compat() {
        val b = tb("Hi")
        val json = DocSerializer.save(listOf(b))
        assertTrue(json.contains("\"runs\""))
        val t = DocSerializer.load(json).single() as DocBlock.Text
        assertTrue(t.runs.isEmpty())
        assertEquals("Hi", t.text)
    }
}
