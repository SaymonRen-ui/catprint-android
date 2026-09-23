package com.catprint

import com.catprint.core.doc.DocBlock
import com.catprint.core.doc.DocSerializer
import com.catprint.core.printer.DitherMode
import com.catprint.core.printer.ImageOptions
import com.catprint.core.printer.ImageTransform
import com.catprint.core.printer.PhotoPipeline
import com.catprint.core.printer.QrRender
import com.catprint.print.PrintService
import org.junit.Assert.*
import org.junit.Test

class PrintExtrasTest {

    @Test
    fun qr_empty_isNull() {
        assertNull(QrRender.render("", 192))
        assertNull(QrRender.render("   ", 128))
    }

    @Test
    fun qr_squareWithQuietZone() {
        val r = QrRender.render("https://example.com", 192)!!
        assertEquals(r.width, r.height)
        // Углы — тихая зона (белые)
        assertFalse(r.black[0])
        assertFalse(r.black[r.width - 1])
        assertFalse(r.black[(r.height - 1) * r.width])
        // Внутри есть чёрное
        assertTrue(r.black.any { it })
        // Не разъехался
        assertTrue(r.width in 21..256)
    }

    @Test
    fun qr_cyrillic_encodes() {
        val r = QrRender.render("Привет мир", 128)!!
        assertTrue(r.width in 21..192)
        assertTrue(r.black.any { it })
    }

    @Test
    fun qr_sizesGrow() {
        val s = QrRender.render("123", QrRender.targetPx(0))!!
        val m = QrRender.render("123", QrRender.targetPx(1))!!
        val l = QrRender.render("123", QrRender.targetPx(2))!!
        assertTrue(s.width <= m.width && m.width <= l.width)
    }

    @Test
    fun qr_roundtrip() {
        val blocks = listOf(DocBlock.Qr("WIFI:S:Home;T:WPA;P:12345678;;", 2))
        val t = DocSerializer.load(DocSerializer.save(blocks)).single() as DocBlock.Qr
        assertEquals("WIFI:S:Home;T:WPA;P:12345678;;", t.text)
        assertEquals(2, t.size)
    }

    @Test
    fun image_rotMirror_roundtrip() {
        val blocks = listOf(DocBlock.Image(pngBase64 = "eA==", rotation = 90, mirror = true))
        val t = DocSerializer.load(DocSerializer.save(blocks)).single() as DocBlock.Image
        assertEquals(90, t.rotation)
        assertTrue(t.mirror)
    }

    @Test
    fun autoDensity_textUnchanged() {
        // Текст (малая заливка) — как настроено
        assertEquals(0x5D, PrintService.autoIntensity(0x5D, 0.05))
        assertEquals(0x5D, PrintService.autoIntensity(0x5D, 0.25))
    }

    @Test
    fun autoDensity_photoReduced() {
        // Плотное фото — жар вниз, но в границах.
        // Калибровка: при заливке ~50% сошлось на 0x50 при базе 0x55.
        val v = PrintService.autoIntensity(0x55, 0.51)
        assertTrue(v < 0x55 && v >= 0x30)
        assertEquals(0x50, PrintService.autoIntensity(0x55, 0.51))
        // Полная заливка — максимальное снижение 12
        assertEquals(0x5D - 12, PrintService.autoIntensity(0x5D, 1.0))
        // Ниже минимума не уходим
        assertEquals(0x30, PrintService.autoIntensity(0x35, 1.0))
    }

    @Test
    fun pipeline_compensation_currentlyOff() {
        // Компенсация выключена (A/B против ПК): Floyd(128) даёт ~50%.
        val w = 384
        val h = 32
        val argb = IntArray(w * h) { 0xFF808080.toInt() }
        val res = PhotoPipeline.process(
            argb, w, h,
            ImageOptions(dither = DitherMode.FLOYD_STEINBERG)
        )
        val frac = res.black.count { it }.toDouble() / res.black.size
        assertTrue("floyd mids fraction=$frac", frac in 0.35..0.65)
    }

    @Test
    fun pipeline_threshold_ignoresCompensation() {
        // Порог — побитово точный: серый 100 чёрный, 200 белый, как раньше
        val w = 384
        val h = 8
        val dark = PhotoPipeline.process(
            IntArray(w * h) { 0xFF646464.toInt() }, w, h,
            ImageOptions(dither = DitherMode.THRESHOLD, threshold = 128)
        )
        val light = PhotoPipeline.process(
            IntArray(w * h) { 0xFFC8C8C8.toInt() }, w, h,
            ImageOptions(dither = DitherMode.THRESHOLD, threshold = 128)
        )
        assertTrue(dark.black.all { it })
        assertTrue(light.black.none { it })
    }

    @Test
    fun groupHeat_sparseGetsFullHeat() {
        // Редкие и средние тона (лицо, света) — полным жаром,
        // иначе текстура не пропекается
        assertEquals(0x5D, PrintService.groupHeat(0x5D, 0.05))
        assertEquals(0x5D, PrintService.groupHeat(0x5D, 0.15))
        assertEquals(0x5D, PrintService.groupHeat(0x5D, 0.30))
        assertEquals(0x40, PrintService.groupHeat(0x40, 0.0))
    }

    @Test
    fun groupHeat_denseGoesCool() {
        // Плотная заливка — мягко, иначе расплыв
        assertEquals(0x40, PrintService.groupHeat(0x5D, 0.8))
        assertEquals(0x40, PrintService.groupHeat(0x5D, 0.65))
        // Ниже пола не уходим и выше базы не прыгаем
        assertEquals(0x40, PrintService.groupHeat(0x40, 0.9))
        assertEquals(0x30, PrintService.groupHeat(0x30, 0.9))
        // Середина интерполируется
        val mid = PrintService.groupHeat(0x7A, 0.475)
        assertTrue(mid in 0x41..0x79)
    }

    @Test
    fun previewDropsIsolatedDots() {
        val r = com.catprint.core.doc.DocRender.Rendered(BooleanArray(10 * 10), 10, 10)
        r.black[5 * 10 + 5] = true // одиночка
        r.black[2 * 10 + 2] = true // пара
        r.black[2 * 10 + 3] = true
        r.black[8 * 10 + 0] = true // угол, одиночка
        val out = com.catprint.core.doc.DocRender.dropUnfired(r)
        assertFalse(out[5 * 10 + 5])
        assertFalse(out[8 * 10 + 0])
        assertTrue(out[2 * 10 + 2])
        assertTrue(out[2 * 10 + 3])
    }

    @Test
    fun transform_mirrorTwice_identity() {
        val px = intArrayOf(1, 2, 3, 4, 5, 6)
        val back = ImageTransform.mirror(
            ImageTransform.mirror(ImageTransform.Img(px, 3, 2))
        )
        assertArrayEquals(px, back.px)
    }

    @Test
    fun transform_rotate90() {
        // 2x3: [1 2 / 3 4 / 5 6] -> 3x2: [5 3 1 / 6 4 2]
        val r = ImageTransform.rotate(ImageTransform.Img(intArrayOf(1, 2, 3, 4, 5, 6), 2, 3), 90)
        assertEquals(3, r.w)
        assertEquals(2, r.h)
        assertArrayEquals(intArrayOf(5, 3, 1, 6, 4, 2), r.px)
    }

    @Test
    fun transform_rotate180() {
        val r = ImageTransform.rotate(ImageTransform.Img(intArrayOf(1, 2, 3, 4), 2, 2), 180)
        assertArrayEquals(intArrayOf(4, 3, 2, 1), r.px)
    }

    @Test
    fun transform_fourQuarterTurns_identity() {
        val px = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        var img = ImageTransform.Img(px, 4, 2)
        repeat(4) { img = ImageTransform.rotate(img, 90) }
        assertEquals(4, img.w)
        assertEquals(2, img.h)
        assertArrayEquals(px, img.px)
    }
}
