package com.catprint.core.printer

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR в 1-битную матрицу для термопечати.
 * Матрица + quiet zone 4 модуля, масштаб целым коэффициентом —
 * код остаётся сканируемым. Чёрное на белом, без инверсии.
 */
object QrRender {
    fun targetPx(size: Int): Int = when (size.coerceIn(0, 2)) {
        0 -> 128
        2 -> 256
        else -> 192
    }

    data class Result(val black: BooleanArray, val width: Int, val height: Int)

    /** null — пустой текст или не влез в QR. */
    fun render(text: String, targetPx: Int): Result? {
        if (text.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0
            )
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val mw = m.width
            if (mw <= 0) return null
            val q = 4
            val total = mw + q * 2
            val k = maxOf(1, targetPx / total)
            val w = total * k
            val black = BooleanArray(w * w)
            for (y in 0 until total) {
                for (x in 0 until total) {
                    val v = x >= q && x < q + mw && y >= q && y < q + mw &&
                        m.get(x - q, y - q)
                    if (v) {
                        for (dy in 0 until k) {
                            for (dx in 0 until k) {
                                black[(y * k + dy) * w + (x * k + dx)] = true
                            }
                        }
                    }
                }
            }
            Result(black, w, w)
        } catch (e: Exception) {
            null
        }
    }
}
