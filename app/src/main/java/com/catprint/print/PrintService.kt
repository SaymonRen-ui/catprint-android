package com.catprint.print

import com.catprint.ble.PrinterConnection
import com.catprint.core.printer.Mxw01Protocol
import kotlinx.coroutines.delay

/**
 * Печать готового растра 384px. Порт десктопного PrintService:
 * A2 → 50мс → A9 → строки по 48 байт одним вызовом (15мс) →
 * дренаж 50мс → AD → 3000мс. Без паддинга: высота строго равна строкам.
 */
class PrintService(private val connection: PrinterConnection) {

    data class Timing(
        val intensity: Byte = 0x5D,
        val lineDelayMs: Long = 15,
        val blockLines: Int = 40,
        val blockPauseMs: Long = 0,
        val splitEnabled: Boolean = false,
        val splitLines: Int = 96,
        val splitPauseMs: Long = 1500,
        /** Адаптивный жар: A2 по группам строк (редкие — горячо). */
        val adaptiveHeat: Boolean = false
    )

    companion object {
        /** Строк в группе адаптивного жара. */
        const val ADAPTIVE_GROUP = 8

        /** Пол таблицы popcount для плотности строк. */
        private val POPCOUNT = IntArray(256) { v ->
            var c = v
            c -= (c shr 1) and 0x55
            c = (c and 0x33) + ((c shr 2) and 0x33)
            ((c + (c shr 4)) and 0x0F)
        }

        /**
         * Автоплотность: плотная заливка (фото) расплывается от жара,
         * поэтому жар снижаем; текст (малая заливка) идёт как настроено.
         * Калибровка на железе: при заливке ~50% эталон сошёлся на 0x50.
         * coverage 0..1, user 0x30..0x7A. Возвращает байт плотности.
         */
        fun autoIntensity(user: Int, coverage: Double): Int {
            if (coverage <= 0.25) return user.coerceIn(0x30, 0x7A)
            val cut = ((coverage - 0.25) * 20).toInt().coerceIn(0, 12)
            return (user - cut).coerceIn(0x30, user)
        }

        /**
         * Жар группы строк по её плотности: редкие и средние тона —
         * полным/высоким жаром (иначе текстура не пропекается и света
         * выбеливаются), плотная заливка — мягко (иначе расплыв).
         * user 0x30..0x7A, coverage 0..1.
         */
        fun groupHeat(user: Int, coverage: Double): Int {
            val u = user.coerceIn(0x30, 0x7A)
            val floor = 0x40
            if (u <= floor || coverage <= 0.30) return u
            val f = ((coverage - 0.30) / (0.65 - 0.30)).coerceIn(0.0, 1.0)
            return (u - f * (u - floor)).toInt().coerceIn(floor, u)
        }

        /** Доля чёрных точек в строке растра. */
        fun lineCoverage(line: ByteArray): Double {
            var n = 0
            for (b in line) n += POPCOUNT[b.toInt() and 0xFF]
            return n.toDouble() / (line.size * 8)
        }
    }

    suspend fun print(
        raster: ByteArray,
        width: Int,
        height: Int,
        timing: Timing = Timing(),
        onProgress: ((Double) -> Unit)? = null,
        onLog: ((String) -> Unit)? = null
    ) {
        require(width == Mxw01Protocol.PRINTER_WIDTH) {
            "Неверная ширина: $width, нужно ${Mxw01Protocol.PRINTER_WIDTH}"
        }
        require(height > 0) { "Высота должна быть больше нуля" }
        require(raster.size == Mxw01Protocol.BYTES_PER_LINE * height) {
            "Растр ${raster.size} байт не соответствует ${height} строкам"
        }
        if (!connection.isConnected) throw IllegalStateException("Принтер не подключён")

        // Непрерывная подача без рывков: если заданы паузы блоками, их бюджет
        // размазываем равномерно по строкам — та же средняя скорость в принтер
        // (та же защита от переполнения), но без полных остановок.
        // Полные остановки убраны: принтер тянет непрерывно, пока буфер полон.
        // Страховка от затыков линка — ниже: после подвисшей строки даём
        // одну дренажную паузу, чтобы головка не рвала на пустом буфере.
        val timing = timing
        val spreadPerLine: Long =
            if (!timing.splitEnabled && timing.blockLines > 0 && timing.blockPauseMs > 0) {
                timing.blockPauseMs / timing.blockLines
            } else {
                0
            }
        if (spreadPerLine > 0) {
            onLog?.invoke(
                "TX: бюджет пауз размазан: +${spreadPerLine}мс/строка, остановок нет"
            )
        }

        val segments = split(raster, height, timing)
        onLog?.invoke(
            "TX: A2=0x${(timing.intensity.toInt() and 0xFF).toString(16).uppercase()}, " +
                "всего строк=$height, заданий=${segments.size}"
        )

        connection.sendCommand(Mxw01Protocol.intensity(timing.intensity))
        delay(50)
        var lastHeat = timing.intensity.toInt() and 0xFF
        if (timing.adaptiveHeat) {
            onLog?.invoke("TX: адаптивный жар: группы по $ADAPTIVE_GROUP строк")
        }

        val grandTotal = segments.sumOf { it.second }
        var done = 0
        var globalLine = 0

        for ((s, seg) in segments.withIndex()) {
            val (bytes, segH) = seg
            require(bytes.size == segH * Mxw01Protocol.BYTES_PER_LINE)

            if (segments.size > 1) {
                onLog?.invoke("TX: задание ${s + 1}/${segments.size}: h=$segH")
            }
            connection.sendCommand(Mxw01Protocol.printRequest(segH))

            val t0 = android.os.SystemClock.elapsedRealtime()
            for (l in 0 until segH) {
                // Адаптив: в начале группы смотрим плотность следующих
                // строк и при нужде переключаем A2 (AE01, без разрыва).
                if (timing.adaptiveHeat && l % ADAPTIVE_GROUP == 0) {
                    val gEnd = minOf(l + ADAPTIVE_GROUP, segH)
                    var n = 0
                    for (i in l * Mxw01Protocol.BYTES_PER_LINE until gEnd * Mxw01Protocol.BYTES_PER_LINE) {
                        n += POPCOUNT[bytes[i].toInt() and 0xFF]
                    }
                    val cov = n.toDouble() /
                        ((gEnd - l) * Mxw01Protocol.BYTES_PER_LINE * 8)
                    val heat = groupHeat(
                        timing.intensity.toInt() and 0xFF, cov
                    )
                    if (heat != lastHeat) {
                        onLog?.invoke(
                            "TX: жар 0x${lastHeat.toString(16).uppercase()} → " +
                                "0x${heat.toString(16).uppercase()} " +
                                "(строки ${globalLine + 1}.., " +
                                "заливка ${(cov * 100).toInt()}%)"
                        )
                        connection.sendCommand(
                            Mxw01Protocol.intensity(heat.toByte())
                        )
                        delay(20)
                        lastHeat = heat
                    }
                }
                // Свежий массив на строку: старый API пишет char.value
                // по ссылке асинхронно, переиспользованный буфер гоняет данные.
                val line = bytes.copyOfRange(
                    l * Mxw01Protocol.BYTES_PER_LINE,
                    (l + 1) * Mxw01Protocol.BYTES_PER_LINE
                )
                val ls = android.os.SystemClock.elapsedRealtime()
                try {
                    connection.sendRaster(line)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Печать оборвалась: задание ${s + 1}/${segments.size}, " +
                            "строка ${l + 1} из $segH: ${e.message}", e
                    )
                }
                val took = android.os.SystemClock.elapsedRealtime() - ls
                if (took > 1000) {
                    // Затык линка: буфер принтера мог подсохнуть — даём одну
                    // дренажную паузу вместо плановых остановок.
                    onLog?.invoke("TX: строка ${l + 1}: затык ${took}мс — пауза 1200мс")
                    delay(1200)
                }
                done++
                globalLine++
                onProgress?.invoke(0.05 + 0.90 * done / grandTotal)

                if (l + 1 < segH) {
                    val gap = timing.lineDelayMs + spreadPerLine
                    if (gap > 0) delay(gap)
                }
            }

            delay(50) // дренаж последней строки перед AD
            val dt = android.os.SystemClock.elapsedRealtime() - t0
            onLog?.invoke(
                "TX: задание ${s + 1}: $segH строк за ${dt}мс " +
                    "(${if (segH > 0) dt / segH else 0}мс/строка)"
            )
            connection.sendCommand(Mxw01Protocol.flush())

            if (s + 1 < segments.size) {
                val pause = maxOf(timing.splitPauseMs, 3000)
                onLog?.invoke("TX: пауза между заданиями ${pause}мс")
                delay(pause)
            } else {
                delay(3000)
            }
        }

        onLog?.invoke("TX: готово, строк контента $height/$height")
        onProgress?.invoke(1.0)
    }

    private fun split(
        raster: ByteArray,
        height: Int,
        timing: Timing
    ): List<Pair<ByteArray, Int>> {
        if (!timing.splitEnabled || timing.splitLines <= 0 || height <= timing.splitLines) {
            return listOf(raster to height)
        }
        val out = mutableListOf<Pair<ByteArray, Int>>()
        val stride = Mxw01Protocol.BYTES_PER_LINE
        var done = 0
        while (done < height) {
            val h = minOf(timing.splitLines, height - done)
            out.add(raster.copyOfRange(done * stride, (done + h) * stride) to h)
            done += h
        }
        return out
    }
}
