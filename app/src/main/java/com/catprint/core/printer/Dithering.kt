package com.catprint.core.printer

/** Порядок бит внутри байта строки. По умолчанию LSB — как ждёт MXW01. */
enum class BitOrder { LSB_FIRST, MSB_FIRST }

enum class DitherMode(val displayName: String) {
    THRESHOLD("Порог"),
    FLOYD_STEINBERG("Флойд–Стейнберг"),
    ATKINSON("Аткинсон"),
    BAYER_4X4("Байер 4×4"),
    BAYER_8X8("Байер 8×8"),
    JARVIS("Джарвис"),
    STUCKI("Стуки"),
    BURKES("Берк"),
    SIERRA("Сьерра"),
    RANDOM("Случайный");

    companion object {
        fun fromOrdinalSafe(i: Int): DitherMode =
            values().getOrElse(i) { FLOYD_STEINBERG }
    }
}

/**
 * Дизеринги. Порт десктопного Dithering один в один.
 * Вход — gray 0..255 построчно, выход — black (true = печатать).
 */
object Dithering {

    fun apply(
        gray: FloatArray,
        width: Int,
        height: Int,
        mode: DitherMode,
        threshold: Int
    ): BooleanArray = when (mode) {
        DitherMode.THRESHOLD -> threshold(gray, width, height, threshold)
        DitherMode.FLOYD_STEINBERG -> kernel(
            gray, width, height,
            arrayOf(
                Triple(1, 0, 7f), Triple(-1, 1, 3f),
                Triple(0, 1, 5f), Triple(1, 1, 1f)
            ), 16f
        )
        DitherMode.ATKINSON -> atkinson(gray, width, height)
        DitherMode.BAYER_4X4 -> bayer(gray, width, height, BAYER_4X4, 4)
        DitherMode.BAYER_8X8 -> bayer(gray, width, height, BAYER_8X8, 8)
        DitherMode.JARVIS -> kernel(
            gray, width, height,
            arrayOf(
                Triple(1, 0, 7f), Triple(2, 0, 5f),
                Triple(-2, 1, 3f), Triple(-1, 1, 5f), Triple(0, 1, 7f),
                Triple(1, 1, 5f), Triple(2, 1, 3f),
                Triple(-2, 2, 1f), Triple(-1, 2, 3f), Triple(0, 2, 5f),
                Triple(1, 2, 3f), Triple(2, 2, 1f)
            ), 48f
        )
        DitherMode.STUCKI -> kernel(
            gray, width, height,
            arrayOf(
                Triple(1, 0, 8f), Triple(2, 0, 4f),
                Triple(-2, 1, 2f), Triple(-1, 1, 4f), Triple(0, 1, 8f),
                Triple(1, 1, 4f), Triple(2, 1, 2f),
                Triple(-2, 2, 1f), Triple(-1, 2, 2f), Triple(0, 2, 4f),
                Triple(1, 2, 2f), Triple(2, 2, 1f)
            ), 42f
        )
        DitherMode.BURKES -> kernel(
            gray, width, height,
            arrayOf(
                Triple(1, 0, 8f), Triple(2, 0, 4f),
                Triple(-2, 1, 2f), Triple(-1, 1, 4f), Triple(0, 1, 8f),
                Triple(1, 1, 4f), Triple(2, 1, 2f)
            ), 32f
        )
        DitherMode.SIERRA -> kernel(
            gray, width, height,
            arrayOf(
                Triple(1, 0, 5f), Triple(2, 0, 3f),
                Triple(-2, 1, 2f), Triple(-1, 1, 4f), Triple(0, 1, 5f),
                Triple(1, 1, 4f), Triple(2, 1, 2f),
                Triple(-1, 2, 2f), Triple(0, 2, 3f), Triple(1, 2, 2f)
            ), 32f
        )
        DitherMode.RANDOM -> random(gray, width, height, threshold)
    }

    private fun threshold(
        gray: FloatArray, w: Int, h: Int, threshold: Int
    ): BooleanArray =
        BooleanArray(w * h) { i -> gray[i] < threshold }

    private fun kernel(
        src: FloatArray, w: Int, h: Int,
        kernel: Array<Triple<Int, Int, Float>>, divisor: Float
    ): BooleanArray {
        val g = src.clone()
        val black = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val old = g[y * w + x]
                val b = old < 128f
                black[y * w + x] = b
                val err = (old - (if (b) 0f else 255f)) / divisor
                for ((dx, dy, weight) in kernel) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) {
                        g[ny * w + nx] += err * weight
                    }
                }
            }
        }
        return black
    }

    private fun atkinson(src: FloatArray, w: Int, h: Int): BooleanArray {
        val g = src.clone()
        val black = BooleanArray(w * h)
        fun spread(x: Int, y: Int, err: Float) {
            if (x in 0 until w && y in 0 until h) g[y * w + x] += err
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val old = g[y * w + x]
                val b = old < 128f
                black[y * w + x] = b
                val err = (old - (if (b) 0f else 255f)) / 8f
                spread(x + 1, y, err)
                spread(x + 2, y, err)
                spread(x - 1, y + 1, err)
                spread(x, y + 1, err)
                spread(x + 1, y + 1, err)
                spread(x, y + 2, err)
            }
        }
        return black
    }

    private val BAYER_4X4 = arrayOf(
        intArrayOf(0, 8, 2, 10),
        intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9),
        intArrayOf(15, 7, 13, 5)
    )

    private val BAYER_8X8 = arrayOf(
        intArrayOf(0, 32, 8, 40, 2, 34, 10, 42),
        intArrayOf(48, 16, 56, 24, 50, 18, 58, 26),
        intArrayOf(12, 44, 4, 36, 14, 46, 6, 38),
        intArrayOf(60, 28, 52, 20, 62, 30, 54, 22),
        intArrayOf(3, 35, 11, 43, 1, 33, 9, 41),
        intArrayOf(51, 19, 59, 27, 49, 17, 57, 25),
        intArrayOf(15, 47, 7, 39, 13, 45, 5, 37),
        intArrayOf(63, 31, 55, 23, 61, 29, 53, 21)
    )

    private fun bayer(
        gray: FloatArray, w: Int, h: Int,
        matrix: Array<IntArray>, n: Int
    ): BooleanArray {
        val black = BooleanArray(w * h)
        val scale = 255f / (n * n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val t = (matrix[y % n][x % n] + 0.5f) * scale
                black[y * w + x] = gray[y * w + x] < t
            }
        }
        return black
    }

    private fun random(
        gray: FloatArray, w: Int, h: Int, threshold: Int
    ): BooleanArray {
        // Фиксированный seed: превью и печать совпадают.
        val rnd = java.util.Random(1234)
        return BooleanArray(w * h) { i ->
            gray[i] + (rnd.nextDouble() - 0.5) * 96.0 < threshold
        }
    }
}
