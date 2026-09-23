package com.catprint

import com.catprint.core.printer.BitOrder
import com.catprint.core.printer.DitherMode
import com.catprint.core.printer.Dithering
import com.catprint.core.printer.Mxw01Protocol
import com.catprint.core.printer.PhotoPipeline
import com.catprint.core.printer.ImageOptions
import com.catprint.core.printer.Raster
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {

    @Test
    fun crc8_singleByte_matchesDesktop() {
        // Сверено с десктопом (python): crc([0x5D]) = 0x94
        assertEquals(0x94.toByte(), Mxw01Protocol.calculateCrc8(byteArrayOf(0x5D)))
    }

    @Test
    fun crc8_empty_isZero() {
        assertEquals(0x00.toByte(), Mxw01Protocol.calculateCrc8(byteArrayOf()))
    }

    @Test
    fun intensityCommand_format() {
        // 22 21 A2 00 01 00 5D CRC FF, CRC([5D]) = 0x94
        val cmd = Mxw01Protocol.intensity(0x5D)
        assertArrayEquals(
            byteArrayOf(
                0x22, 0x21, 0xA2.toByte(), 0x00,
                0x01, 0x00, 0x5D, 0x94.toByte(), 0xFF.toByte()
            ),
            cmd
        )
    }

    @Test
    fun printRequest_height264() {
        // h=264 (0x0108): lo=0x08 hi=0x01, ширина 48 (0x30 0x00), хвост 00 00
        val cmd = Mxw01Protocol.printRequest(264)
        assertArrayEquals(
            byteArrayOf(
                0x22, 0x21, 0xA9.toByte(), 0x00,
                0x04, 0x00, 0x08, 0x01, 0x30, 0x00, 0x00, 0x00
            ),
            cmd
        )
    }

    @Test
    fun flushCommand_format() {
        val cmd = Mxw01Protocol.flush()
        assertArrayEquals(
            byteArrayOf(
                0x22, 0x21, 0xAD.toByte(), 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00
            ),
            cmd
        )
    }

    @Test
    fun statusRequest_format() {
        val cmd = Mxw01Protocol.statusRequest()
        assertEquals(0xA1.toByte(), cmd[2])
        assertEquals(9, cmd.size)
    }

    @Test
    fun parseBattery_knownPacket() {
        // Реальный ответ принтера при 85%: единственный байт 0x55 на позиции 9
        val packet = byteArrayOf(
            0x22, 0x21, 0xA1.toByte(), 0x03, 0x0A, 0x00, 0x00, 0x00, 0x00,
            0x55, 0x24, 0x00, 0x00, 0x00, 0xC5.toByte(), 0x00, 0x00
        )
        assertEquals(85, Mxw01Protocol.parseStatusBattery(packet))
    }

    @Test
    fun parseBattery_rejectsGarbage() {
        assertNull(Mxw01Protocol.parseStatusBattery(byteArrayOf()))
        assertNull(Mxw01Protocol.parseStatusBattery(ByteArray(17)))
        // 0xC8 = 200, вне 0..100
        val bad = byteArrayOf(
            0x22, 0x21, 0xA1.toByte(), 0x03, 0x0A, 0x00, 0x00, 0x00, 0x00,
            0xC8.toByte(), 0x24, 0x00, 0x00, 0x00, 0xC5.toByte(), 0x00, 0x00
        )
        assertNull(Mxw01Protocol.parseStatusBattery(bad))
    }

    @Test
    fun packLsb_bitOrder() {
        // Пиксели [1,0,1,0,0,0,0,0] -> биты 0 и 2 -> 0x05
        val black = booleanArrayOf(true, false, true, false, false, false, false, false)
        val packed = Raster.pack(black, 8, 1, BitOrder.LSB_FIRST)
        assertArrayEquals(byteArrayOf(0x05), packed)
    }

    @Test
    fun packMsb_bitOrder() {
        // Те же пиксели MSB: 0x80 | 0x20 = 0xA0
        val black = booleanArrayOf(true, false, true, false, false, false, false, false)
        val packed = Raster.pack(black, 8, 1, BitOrder.MSB_FIRST)
        assertArrayEquals(byteArrayOf(0xA0.toByte()), packed)
    }

    @Test
    fun packRowStride() {
        // 384 точки = 48 байт на строку
        val black = BooleanArray(384 * 2)
        val packed = Raster.pack(black, 384, 2, BitOrder.LSB_FIRST)
        assertEquals(96, packed.size)
    }
}

class DitherTest {

    @Test
    fun threshold_splitsAt128() {
        val gray = floatArrayOf(0f, 127f, 128f, 200f, 255f)
        val black = Dithering.apply(gray, 5, 1, DitherMode.THRESHOLD, 128)
        assertArrayEquals(
            booleanArrayOf(true, true, false, false, false),
            black
        )
    }

    @Test
    fun allModes_outputBinaryAndSized() {
        val w = 64
        val h = 48
        val gray = FloatArray(w * h) { i -> ((i * 255) / (w * h)).toFloat() }
        for (mode in DitherMode.values()) {
            val black = Dithering.apply(gray, w, h, mode, 128)
            assertEquals("size $mode", w * h, black.size)
        }
    }

    @Test
    fun floydSteinberg_midGray_aboutHalfBlack() {
        val w = 100
        val h = 100
        val gray = FloatArray(w * h) { 128f }
        val black = Dithering.apply(gray, w, h, DitherMode.FLOYD_STEINBERG, 128)
        val ratio = black.count { it }.toDouble() / black.size
        assertTrue("ratio=$ratio", ratio in 0.35..0.65)
    }

    @Test
    fun pipeline_resizesTo384() {
        val w = 800
        val h = 600
        val px = IntArray(w * h) { -1 } // белый
        val res = PhotoPipeline.process(px, w, h, ImageOptions())
        assertEquals(384, res.width)
        assertEquals(288, res.height)
        assertEquals(384 * 288, res.black.size)
        // Белое остаётся белым при любом дизеринге
        assertFalse(res.black.any { it })
    }

    @Test
    fun pipeline_blackStaysBlack() {
        val px = IntArray(384 * 10) { 0xFF000000.toInt() }
        val res = PhotoPipeline.process(px, 384, 10, ImageOptions())
        assertTrue(res.black.all { it })
    }

    @Test
    fun pipeline_transparentBecomesWhite() {
        // Полностью прозрачный чёрный -> белый (композит на белом)
        val px = IntArray(384 * 10) { 0x00000000 }
        val res = PhotoPipeline.process(px, 384, 10, ImageOptions())
        assertFalse(res.black.any { it })
    }
}
