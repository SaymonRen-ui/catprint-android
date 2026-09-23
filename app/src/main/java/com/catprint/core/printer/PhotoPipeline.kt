package com.catprint.core.printer

import kotlin.math.roundToInt

/**
 * Упаковка 1-битной матрицы в байты принтера.
 * LSB_FIRST: пиксель x — бит (x%8), как ждёт MXW01.
 */
object Raster {
    const val PRINTER_WIDTH = 384

    fun pack(black: BooleanArray, width: Int, height: Int, order: BitOrder): ByteArray {
        val stride = (width + 7) / 8
        val data = ByteArray(stride * height)
        val msb = order == BitOrder.MSB_FIRST
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!black[y * width + x]) continue
                val idx = y * stride + x / 8
                data[idx] = (data[idx].toInt() or (
                    if (msb) (0x80 shr (x % 8))
                    else (1 shl (x % 8))
                    )).toByte()
            }
        }
        return data
    }
}

/** Настройки обработки картинки (зеркало десктопного ImageOptions). */
data class ImageOptions(
    var brightness: Double = 0.0,   // -100..100
    var contrast: Double = 0.0,     // -100..100
    var saturation: Double = 100.0, // 0..200, 100 = норма
    var dither: DitherMode = DitherMode.FLOYD_STEINBERG,
    var threshold: Int = 128,
    var gamma: Double = 1.0,
    var invert: Boolean = false,
    var bitOrder: BitOrder = BitOrder.LSB_FIRST
)

/**
 * Чистые целочисленные трансформы ARGB (покрыты тестами).
 * Зеркало — горизонтальный флип, поворот — по часовой.
 */
object ImageTransform {
    data class Img(val px: IntArray, val w: Int, val h: Int)

    fun mirror(src: Img): Img {
        val out = IntArray(src.px.size)
        for (y in 0 until src.h) {
            for (x in 0 until src.w) {
                out[x + y * src.w] = src.px[(src.w - 1 - x) + y * src.w]
            }
        }
        return Img(out, src.w, src.h)
    }

    fun rotate(src: Img, deg: Int): Img {
        when ((deg % 360 + 360) % 360) {
            90 -> {
                val nw = src.h
                val nh = src.w
                val out = IntArray(nw * nh)
                for (y in 0 until nh) {
                    for (x in 0 until nw) {
                        out[x + y * nw] = src.px[y + (src.h - 1 - x) * src.w]
                    }
                }
                return Img(out, nw, nh)
            }
            180 -> {
                val out = IntArray(src.px.size)
                for (y in 0 until src.h) {
                    for (x in 0 until src.w) {
                        out[x + y * src.w] =
                            src.px[(src.w - 1 - x) + (src.h - 1 - y) * src.w]
                    }
                }
                return Img(out, src.w, src.h)
            }
            270 -> {
                val nw = src.h
                val nh = src.w
                val out = IntArray(nw * nh)
                for (y in 0 until nh) {
                    for (x in 0 until nw) {
                        out[x + y * nw] = src.px[(src.w - 1 - y) + x * src.w]
                    }
                }
                return Img(out, nw, nh)
            }
            else -> return src
        }
    }
}

/**
 * Пайплайн фото: вход — ARGB пиксели построчно.
 * Выход — 1-битная матрица шириной 384 (пропорции сохранены).
 */
object PhotoPipeline {
    const val PRINTER_WIDTH = 384
    const val MAX_HEIGHT = 2000

    /**
     * Компенсация принтера: инверсия измеренной тон-кривой
     * (скан эталона на 0x50, Floyd). ВЫКЛЮЧЕНА: A/B-тест против ПК
     * (без компенсации) показал, что она вредит — скан-метрология
     * была кривой. Оставлена кодом на случай перепроверки.
     */
    private const val COMP_ENABLED = false
    private val PRINT_COMP_LUT = intArrayOf(
        0, 3, 3, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 8, 8, 9,
        9, 10, 10, 11, 11, 12, 12, 13, 14, 15, 16, 17, 17, 19, 20, 21,
        22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 38,
        39, 40, 41, 42, 43, 44, 45, 46, 46, 47, 48, 49, 50, 50, 51, 52,
        53, 53, 54, 55, 56, 56, 57, 57, 58, 59, 59, 60, 61, 61, 62, 62,
        63, 63, 63, 64, 64, 65, 65, 66, 66, 67, 67, 67, 68, 68, 68, 69,
        69, 69, 70, 70, 70, 71, 71, 72, 72, 72, 72, 73, 73, 73, 73, 74,
        74, 74, 74, 75, 75, 75, 75, 76, 76, 76, 76, 77, 77, 77, 78, 78,
        78, 78, 79, 79, 79, 79, 80, 80, 80, 80, 81, 81, 81, 82, 82, 82,
        82, 83, 83, 83, 84, 84, 85, 85, 85, 86, 86, 87, 87, 87, 88, 88,
        89, 89, 89, 90, 90, 91, 91, 91, 92, 92, 93, 93, 94, 94, 95, 95,
        96, 96, 97, 97, 98, 98, 99, 100, 100, 101, 101, 102, 103, 103, 104, 105,
        105, 106, 107, 107, 108, 109, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118,
        119, 120, 121, 122, 123, 124, 125, 126, 128, 129, 130, 131, 132, 134, 135, 136,
        138, 139, 141, 143, 144, 146, 148, 149, 151, 152, 154, 156, 158, 161, 163, 165,
        168, 175, 182, 190, 197, 204, 210, 216, 222, 229, 235, 240, 245, 250, 254, 255
    )

    data class Result(val black: BooleanArray, val width: Int, val height: Int)

    data class Sized(val px: IntArray, val w: Int, val h: Int)

    fun process(
        argb: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        options: ImageOptions
    ): Result {
        val s = resizeToWidth(argb, srcWidth, srcHeight)
        return processSized(s.px, s.w, s.h, options)
    }

    /**
     * Ресайз до ширины печати. От настроек не зависит — результат можно
     * кэшировать между тиками слайдеров. Размеры — как WPF TransformedBitmap
     * (round-half-up: замер 1081→541, 66.67→67).
     */
    fun resizeToWidth(
        argb: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int = PRINTER_WIDTH
    ): Sized {
        // Пошагово вдвое против муара
        var w = srcWidth
        var h = srcHeight
        var px = argb
        while (w > targetWidth * 2) {
            val half = halve(px, w, h)
            px = half.px
            w = half.w
            h = half.h
        }
        if (w != targetWidth) {
            val scale = targetWidth.toDouble() / w
            val nw = targetWidth
            // Как WPF TransformedBitmap: округление до ближайшего
            // (замер: 66.67->67, 67.33->67), НЕ усечение.
            val nh = maxOf(1, (h * scale).roundToInt())
            px = scaleBilinear(px, w, h, nw, nh)
            w = nw
            h = nh
        }
        return Sized(px, w, h)
    }

    /** Adjustments + gray + дизеринг (+ инверсия) на готовом ресайзе. */
    fun processSized(
        px: IntArray,
        w: Int,
        h: Int,
        options: ImageOptions
    ): Result {
        require(h <= MAX_HEIGHT) { "Слишком высокое изображение: $h (макс $MAX_HEIGHT)" }

        // 2. Adjustments -> gray
        val brightnessOffset = options.brightness * 2.55
        val contrastC = options.contrast * 2.55
        val contrastFactor =
            (259.0 * (contrastC + 255.0)) / (255.0 * (259.0 - contrastC))
        val sat = (options.saturation / 100.0).coerceIn(0.0, 2.0)
        val gamma = options.gamma.coerceIn(0.2, 3.0)
        val useGamma = kotlin.math.abs(gamma - 1.0) > 0.001

        val gray = FloatArray(w * h)
        for (i in px.indices) {
            val alpha = ((px[i] shr 24) and 0xFF) / 255.0
            var r = ((px[i] shr 16) and 0xFF).toDouble()
            var g = ((px[i] shr 8) and 0xFF).toDouble()
            var b = (px[i] and 0xFF).toDouble()

            // Прозрачность кладём на белый (иначе прозрачное = чёрное)
            if (alpha < 1.0) {
                r = r * alpha + 255.0 * (1.0 - alpha)
                g = g * alpha + 255.0 * (1.0 - alpha)
                b = b * alpha + 255.0 * (1.0 - alpha)
            }

            if (kotlin.math.abs(sat - 1.0) > 0.001) {
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                r = lum + (r - lum) * sat
                g = lum + (g - lum) * sat
                b = lum + (b - lum) * sat
            }

            r = (r - 128.0) * contrastFactor + 128.0 + brightnessOffset
            g = (g - 128.0) * contrastFactor + 128.0 + brightnessOffset
            b = (b - 128.0) * contrastFactor + 128.0 + brightnessOffset

            if (useGamma) {
                r = 255.0 * Math.pow(r.coerceIn(0.0, 255.0) / 255.0, gamma)
                g = 255.0 * Math.pow(g.coerceIn(0.0, 255.0) / 255.0, gamma)
                b = 255.0 * Math.pow(b.coerceIn(0.0, 255.0) / 255.0, gamma)
            }

            gray[i] = (0.299 * r.coerceIn(0.0, 255.0) +
                0.587 * g.coerceIn(0.0, 255.0) +
                0.114 * b.coerceIn(0.0, 255.0)).toFloat()
        }

        // 2b. Компенсация принтера (кроме точного THRESHOLD).
        // См. COMP_ENABLED: сейчас выключена.
        if (COMP_ENABLED && options.dither != DitherMode.THRESHOLD) {
            for (i in gray.indices) {
                gray[i] = PRINT_COMP_LUT[gray[i].toInt().coerceIn(0, 255)].toFloat()
            }
        }

        // 3. Дизеринг (+ инверсия)
        var black = Dithering.apply(gray, w, h, options.dither, options.threshold)
        if (options.invert) {
            black = BooleanArray(black.size) { !black[it] }
        }
        return Result(black, w, h)
    }

    private fun halve(px: IntArray, w: Int, h: Int): Sized {
        // Как WPF ScaleTransform(0.5): 1081->541, 769->385 (round-half-up).
        // Размеры берём из результата, а не усечением — иначе страйд
        // массива и счётчик разъедутся на нечётных ширинах.
        val nw = maxOf(1, (w * 0.5).roundToInt())
        val nh = maxOf(1, (h * 0.5).roundToInt())
        return Sized(scaleBilinear(px, w, h, nw, nh), nw, nh)
    }

    private fun scaleBilinear(
        px: IntArray, w: Int, h: Int, nw: Int, nh: Int
    ): IntArray {
        fun channel(p: Int, shift: Int) = ((p shr shift) and 0xFF).toDouble()

        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            for (x in 0 until nw) {
                val sx = ((x + 0.5) * w / nw - 0.5).coerceIn(0.0, (w - 1).toDouble())
                val sy = ((y + 0.5) * h / nh - 0.5).coerceIn(0.0, (h - 1).toDouble())
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val x1 = minOf(x0 + 1, w - 1)
                val y1 = minOf(y0 + 1, h - 1)
                val fx = sx - x0
                val fy = sy - y0

                var a = 0.0
                var r = 0.0
                var g = 0.0
                var b = 0.0
                val corners = arrayOf(
                    Triple(x0, y0, (1 - fx) * (1 - fy)),
                    Triple(x1, y0, fx * (1 - fy)),
                    Triple(x0, y1, (1 - fx) * fy),
                    Triple(x1, y1, fx * fy)
                )
                for ((cx, cy, weight) in corners) {
                    val p = px[cy * w + cx]
                    a += channel(p, 24) * weight
                    r += channel(p, 16) * weight
                    g += channel(p, 8) * weight
                    b += channel(p, 0) * weight
                }
                out[y * nw + x] =
                    ((a.roundToInt() and 0xFF) shl 24) or
                        ((r.roundToInt() and 0xFF) shl 16) or
                        ((g.roundToInt() and 0xFF) shl 8) or
                        (b.roundToInt() and 0xFF)
            }
        }
        return out
    }
}
