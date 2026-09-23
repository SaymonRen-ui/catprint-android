package com.catprint.core.doc

import com.catprint.core.printer.BitOrder
import com.catprint.core.printer.DitherMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Документ: абзацы (каждый со своим выравниванием), картинки,
 * разделители, отступы. Формат .catdoc v2 общий с десктопом.
 */
/**
 * Рамка вокруг блока (картинка/текст). Хранится числом, 0 = нет.
 * Десктоп неизвестные ключи игнорит — туда документ уедет без рамок.
 */
object FrameStyle {
    const val NONE = 0
    const val THIN = 1
    const val THICK = 2
    const val DOUBLE = 3
    const val WAVY = 4
    const val DASHED = 5
    const val DOTTED = 6
    const val ZIGZAG = 7

    fun all(): List<Int> =
        listOf(NONE, THIN, THICK, DOUBLE, WAVY, DASHED, DOTTED, ZIGZAG)

    fun displayName(i: Int): String = when (sanitize(i)) {
        THIN -> "Тонкая"
        THICK -> "Толстая"
        DOUBLE -> "Двойная"
        WAVY -> "Волнистая"
        DASHED -> "Пунктир"
        DOTTED -> "Точки"
        ZIGZAG -> "Зигзаг"
        else -> "Без рамки"
    }

    fun sanitize(i: Int): Int = i.coerceIn(NONE, ZIGZAG)

    /**
     * Внешнее поле с каждой стороны, px: рамка + 6px отступа до контента.
     * Должно накрывать максимальную глубину узора (см. DocRender.framePixel).
     */
    fun margin(i: Int): Int = when (sanitize(i)) {
        THIN -> 8
        THICK -> 11
        DOUBLE -> 13
        WAVY -> 13
        DASHED -> 9
        DOTTED -> 9
        ZIGZAG -> 12
        else -> 0
    }
}

/**
 * Отрезок текста со своим стилем (rich-text внутри абзаца).
 * Стиль абсолютный: шрифт, кегль, Ж/К/Ч.
 */
data class TextRun(
    var text: String = "",
    var fontFamily: String = "sans-serif",
    var fontSize: Float = 24f,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false
) {
    fun sameStyleAs(o: TextRun): Boolean =
        fontFamily == o.fontFamily && fontSize == o.fontSize &&
            bold == o.bold && italic == o.italic && underline == o.underline
}

/** Тогглящийся атрибут стиля (Ж/К/Ч). */
enum class StyleAttr { BOLD, ITALIC, UNDERLINE }

fun TextRun.getAttr(a: StyleAttr): Boolean = when (a) {
    StyleAttr.BOLD -> bold
    StyleAttr.ITALIC -> italic
    StyleAttr.UNDERLINE -> underline
}

fun TextRun.setAttr(a: StyleAttr, v: Boolean) = when (a) {
    StyleAttr.BOLD -> bold = v
    StyleAttr.ITALIC -> italic = v
    StyleAttr.UNDERLINE -> underline = v
}

fun DocBlock.Text.getAttr(a: StyleAttr): Boolean = when (a) {
    StyleAttr.BOLD -> bold
    StyleAttr.ITALIC -> italic
    StyleAttr.UNDERLINE -> underline
}

fun DocBlock.Text.setAttr(a: StyleAttr, v: Boolean) = when (a) {
    StyleAttr.BOLD -> bold = v
    StyleAttr.ITALIC -> italic = v
    StyleAttr.UNDERLINE -> underline = v
}

/** Базовый стиль абзаца как ран (для материализации/схлопывания). */
fun DocBlock.Text.asRun(): TextRun =
    TextRun("", fontFamily, fontSize, bold, italic, underline)

sealed class DocBlock {    /**
     * Стабильный ID для списков UI. НЕ сериализуется, в copy() не участвует.
     */
    var uid: String = UUID.randomUUID().toString()

    /** Выдать дубликату свежий uid. */
    fun withFreshUid(): DocBlock {
        uid = UUID.randomUUID().toString()
        return this
    }

    data class Text(
        var text: String = "",
        var fontFamily: String = "sans-serif",
        var fontSize: Float = 24f,
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        /** 0 слева, 1 по центру, 2 справа (мобильные коды) */
        var alignment: Int = 1,
        var lineHeight: Float? = null,
        var invert: Boolean = false,
        /** Рамка: FrameStyle (0 = нет) */
        var frame: Int = FrameStyle.NONE,
        /**
         * Поотрезочные стили. ПУСТО = однородный абзац, стиль из полей выше.
         * Инвариант: concat(runs.text) == text (пустой текст = пустые раны).
         */
        var runs: MutableList<TextRun> = mutableListOf()
    ) : DocBlock()

    data class Image(
        var pngBase64: String = "",
        var dither: Int = DitherMode.FLOYD_STEINBERG.ordinal,
        var threshold: Int = 128,
        var brightness: Double = 0.0,
        var contrast: Double = 0.0,
        var saturation: Double = 100.0,
        var gamma: Double = 1.0,
        var invert: Boolean = false,
        /** Поворот по часовой: 0/90/180/270. Применяется до обработки. */
        var rotation: Int = 0,
        /** Зеркало по горизонтали (после поворота). */
        var mirror: Boolean = false,
        /** Рамка: FrameStyle (0 = нет) */
        var frame: Int = FrameStyle.NONE,
        /** Обрезка долями 0..1 (после поворота/зеркала): слева, сверху, справа, снизу */
        var cropL: Double = 0.0,
        var cropT: Double = 0.0,
        var cropR: Double = 0.0,
        var cropB: Double = 0.0
    ) : DocBlock()

    /** QR-код: чёрное на белом, по центру. Размер: 0 мал, 1 сред, 2 круп. */
    data class Qr(
        var text: String = "",
        var size: Int = 1
    ) : DocBlock()

    data class Gap(
        var height: Int = 48
    ) : DocBlock()

    data class Divider(
        var style: Int = 0 // 0 solid 1 dashed 2 dotted 3 double
    ) : DocBlock()
}

/**
 * Хирургия поотрезочных стилей. Всё чистое (без Android/VM) — покрыто
 * unit-тестами. Функции НЕ мутируют вход: возвращают новый Text.
 * uid НЕ переносится — это делает вызывающий (VM).
 */

/** Разбить раны по границе pos (строго внутри отрезка). */
fun splitRunsAt(runs: MutableList<TextRun>, pos: Int) {
    if (pos <= 0) return
    var off = 0
    for (i in runs.indices) {
        val len = runs[i].text.length
        if (pos > off && pos < off + len) {
            val r = runs[i]
            val cut = pos - off
            runs[i] = r.copy(text = r.text.substring(0, cut))
            runs.add(i + 1, r.copy(text = r.text.substring(cut)))
            return
        }
        off += len
    }
}

/** Склеить соседей с одинаковым стилем (пустые отрезки выкинуть). */
fun mergeRuns(runs: MutableList<TextRun>) {
    val it = runs.iterator()
    var prev: TextRun? = null
    while (it.hasNext()) {
        val r = it.next()
        if (r.text.isEmpty()) {
            it.remove()
            continue
        }
        if (prev != null && prev.sameStyleAs(r)) {
            prev.text += r.text
            it.remove()
        } else {
            prev = r
        }
    }
}

/**
 * Стиль отрезка, содержащего позицию pos. Наследование влево:
 * на границе берём левый отрезок (как в Word при печати).
 */
fun styleAt(runs: List<TextRun>, pos: Int): TextRun? {
    if (runs.isEmpty()) return null
    var off = 0
    var cand: TextRun? = null
    for (r in runs) {
        if (r.text.isEmpty()) continue
        if (off >= pos && cand != null) break
        cand = r
        off += r.text.length
        if (off > pos) break
    }
    return cand ?: runs.firstOrNull { it.text.isNotEmpty() } ?: runs.firstOrNull()
}

/** Однородный абзац → один ран из базы. Вход не мутирует. */
fun materializeRuns(b: DocBlock.Text): MutableList<TextRun> =
    if (b.runs.isEmpty()) mutableListOf(b.asRun().also { it.text = b.text })
    else b.runs.map { it.copy() }.toMutableList()

/** Совпадает ли стиль рана с базой абзаца. */
fun matchesBase(r: TextRun, b: DocBlock.Text): Boolean =
    r.fontFamily == b.fontFamily && r.fontSize == b.fontSize &&
        r.bold == b.bold && r.italic == b.italic && r.underline == b.underline

/** Схлопнуть к однородному виду, если остался один ран в стиле базы. */
fun collapseRuns(nb: DocBlock.Text) {
    val rs = nb.runs
    if (rs.size == 1 && matchesBase(rs[0], nb)) rs.clear()
}

/**
 * Тоггл атрибута. Диапазон [a,b): null/null или пустой — весь абзац
 * (база + все раны). Умное значение: гасим, только если ВЕЗДЕ включено.
 */
fun toggleStyleIn(
    b: DocBlock.Text, a: Int?, bb: Int?, attr: StyleAttr
): DocBlock.Text {
    val nb = b.copy(runs = b.runs.map { it.copy() }.toMutableList())
    val len = nb.text.length
    if (nb.text.isEmpty() || a == null || bb == null || a >= bb) {
        val v = !(nb.getAttr(attr) &&
            (nb.runs.isEmpty() || nb.runs.all { it.getAttr(attr) }))
        nb.setAttr(attr, v)
        nb.runs.forEach { it.setAttr(attr, v) }
        mergeRuns(nb.runs)
        collapseRuns(nb)
        return nb
    }
    val runs = materializeRuns(nb)
    val A = a.coerceIn(0, len)
    val B = bb.coerceIn(0, len)
    splitRunsAt(runs, A)
    splitRunsAt(runs, B)
    var off = 0
    val covered = mutableListOf<TextRun>()
    for (r in runs) {
        val s = off
        off += r.text.length
        if (s >= A && off <= B && r.text.isNotEmpty()) covered.add(r)
    }
    if (covered.isEmpty()) return b.copy(runs = b.runs.map { it.copy() }.toMutableList())
    val v = !covered.all { it.getAttr(attr) }
    covered.forEach { it.setAttr(attr, v) }
    mergeRuns(runs)
    nb.runs = runs
    collapseRuns(nb)
    return nb
}

/**
 * Установка кегля/шрифта. Диапазон как в toggleStyleIn.
 * setBase/setRun меняют только свой атрибут.
 */
fun setStyleIn(
    b: DocBlock.Text, a: Int?, bb: Int?,
    setBase: (DocBlock.Text) -> Unit,
    setRun: (TextRun) -> Unit
): DocBlock.Text {
    val nb = b.copy(runs = b.runs.map { it.copy() }.toMutableList())
    val len = nb.text.length
    if (nb.text.isEmpty() || a == null || bb == null || a >= bb) {
        setBase(nb)
        nb.runs.forEach(setRun)
        mergeRuns(nb.runs)
        collapseRuns(nb)
        return nb
    }
    val runs = materializeRuns(nb)
    val A = a.coerceIn(0, len)
    val B = bb.coerceIn(0, len)
    splitRunsAt(runs, A)
    splitRunsAt(runs, B)
    var off = 0
    for (r in runs) {
        val s = off
        off += r.text.length
        if (s >= A && off <= B && r.text.isNotEmpty()) setRun(r)
    }
    mergeRuns(runs)
    nb.runs = runs
    collapseRuns(nb)
    return nb
}

/**
 * Подгонка ранов под новый текст (печать/удаление/вставка).
 * Одно связное изменение: общий префикс + общий суффикс сохраняются,
 * вставленное наследует стиль позиции (влево) — или forceStyle,
 * если задан (вооружённый стиль печати).
 * Возвращает новый Text.
 */
fun retypeIn(b: DocBlock.Text, newText: String, forceStyle: TextRun? = null): DocBlock.Text {
    val nb = b.copy(runs = b.runs.map { it.copy() }.toMutableList())
    if (newText == b.text) return nb
    if (b.runs.isEmpty() && forceStyle == null) {
        nb.text = newText
        return nb
    }
    if (newText.isEmpty()) {
        nb.text = ""
        nb.runs.clear()
        return nb
    }
    val old = b.text
    var cp = 0
    while (cp < old.length && cp < newText.length && old[cp] == newText[cp]) cp++
    var cs = 0
    while (cs < old.length - cp && cs < newText.length - cp &&
        old[old.length - 1 - cs] == newText[newText.length - 1 - cs]
    ) cs++
    val delEnd = old.length - cs
    val ins = newText.substring(cp, newText.length - cs)
    val runs = materializeRuns(b)
    splitRunsAt(runs, cp)
    splitRunsAt(runs, delEnd)
    // Выкинуть покрытое [cp, delEnd), вставить ins со стилем влево.
    var off = 0
    val kept = mutableListOf<TextRun>()
    for (r in runs) {
        val s = off
        off += r.text.length
        if (off <= cp || s >= delEnd) kept.add(r)
    }
    if (ins.isNotEmpty()) {
        val st = forceStyle?.copy(text = ins)
            ?: (styleAt(b.runs, cp) ?: b.asRun()).copy(text = ins)
        val nr = st
        // Позиция вставки в kept-координатах: первый отрезок с началом >= cp.
        var at = kept.size
        var o2 = 0
        for (i in kept.indices) {
            if (o2 >= cp) {
                at = i
                break
            }
            o2 += kept[i].text.length
        }
        kept.add(at.coerceIn(0, kept.size), nr)
    }
    mergeRuns(kept)
    nb.text = newText
    nb.runs = kept
    collapseRuns(nb)
    return nb
}

object DocSerializer {
    const val EXTENSION = ".catdoc"

    fun save(blocks: List<DocBlock>): String {
        val arr = JSONArray()
        for (b in blocks) {
            when (b) {
                is DocBlock.Text -> {
                    // Раны как есть: однородный абзац — один ран из базы
                    // (формат файла не меняется), разукрашенный — все.
                    val runsJson = JSONArray()
                    val src = if (b.runs.isEmpty()) {
                        listOf(
                            TextRun(
                                b.text, b.fontFamily, b.fontSize,
                                b.bold, b.italic, b.underline
                            )
                        )
                    } else b.runs
                    for (r in src) {
                        if (r.text.isEmpty()) continue
                        runsJson.put(
                            JSONObject()
                                .put("t", r.text)
                                .put("b", r.bold)
                                .put("i", r.italic)
                                .put("u", r.underline)
                                .put("f", r.fontFamily)
                                .put("s", r.fontSize.toDouble())
                        )
                    }
                    if (runsJson.length() == 0) {
                        runsJson.put(
                            JSONObject()
                                .put("t", "")
                                .put("b", b.bold)
                                .put("i", b.italic)
                                .put("u", b.underline)
                                .put("f", b.fontFamily)
                                .put("s", b.fontSize.toDouble())
                        )
                    }
                    val o = JSONObject()
                        .put("kind", "p")
                        .put("align", toFileAlign(b.alignment))
                        .put("runs", runsJson)
                    // Десктоп хранит множитель: line = px / fontSize
                    val lh = b.lineHeight
                    if (lh != null && lh > 0) {
                        o.put(
                            "line",
                            (lh / b.fontSize.coerceAtLeast(1f)).toDouble()
                        )
                    }
                    if (b.frame != FrameStyle.NONE) o.put("frame", b.frame)
                    arr.put(o)
                }
                is DocBlock.Image -> {
                    val o = JSONObject()
                        .put("kind", "img")
                        .put("png", b.pngBase64)
                        .put("dither", b.dither)
                        .put("threshold", b.threshold)
                        .put("brightness", b.brightness)
                        .put("contrast", b.contrast)
                        .put("saturation", b.saturation)
                        .put("gamma", b.gamma)
                        .put("invert", b.invert)
                        .put("rot", b.rotation)
                        .put("mir", b.mirror)
                        .put("frame", b.frame)
                    if (b.cropL != 0.0 || b.cropT != 0.0 || b.cropR != 0.0 || b.cropB != 0.0) {
                        o.put("crop", JSONArray()
                            .put(b.cropL).put(b.cropT).put(b.cropR).put(b.cropB))
                    }
                    arr.put(o)
                }
                is DocBlock.Qr -> arr.put(JSONObject()
                    .put("kind", "qr")
                    .put("t", b.text)
                    .put("s", b.size.coerceIn(0, 2)))
                is DocBlock.Gap -> arr.put(JSONObject()
                    .put("kind", "gap")
                    .put("h", b.HeightCoerced()))
                is DocBlock.Divider -> arr.put(JSONObject()
                    .put("kind", "div")
                    .put("style", b.style))
            }
        }
        return JSONObject()
            .put("app", "CatPrint")
            .put("version", 2)
            .put("blocks", arr)
            .toString(2)
    }

    fun load(json: String): List<DocBlock> {
        val root = JSONObject(json)
        require(root.optInt("version", -1) == 2) { "Неверная версия документа" }
        val out = mutableListOf<DocBlock>()
        val arr = root.getJSONArray("blocks")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            when (o.optString("kind")) {
                "p" -> {
                    val runs = o.optJSONArray("runs")
                    val parsed = mutableListOf<TextRun>()
                    if (runs != null) {
                        for (j in 0 until runs.length()) {
                            val r = runs.getJSONObject(j)
                            val t = r.optString("t", "")
                            if (t == "\n") {
                                // Перенос без стиля: цепляем к предыдущему
                                // отрезку (невидимый символ, метрики не важны).
                                if (parsed.isEmpty()) {
                                    parsed.add(
                                        TextRun(
                                            "\n", "sans-serif", 24f,
                                            false, false, false
                                        )
                                    )
                                } else {
                                    parsed.last().text += "\n"
                                }
                                continue
                            }
                            parsed.add(
                                TextRun(
                                    text = t,
                                    fontFamily = r.optString("f", "sans-serif")
                                        .substringBefore(",").trim()
                                        .ifBlank { "sans-serif" },
                                    fontSize = r.optDouble("s", 24.0).toFloat(),
                                    bold = r.optBoolean("b", false),
                                    italic = r.optBoolean("i", false),
                                    underline = r.optBoolean("u", false)
                                )
                            )
                        }
                    }
                    mergeRuns(parsed)
                    val sb = StringBuilder()
                    for (r in parsed) sb.append(r.text)
                    val line = if (o.has("line") && !o.isNull("line")) {
                        val s0 = parsed.firstOrNull()?.fontSize ?: 24f
                        (o.optDouble("line", 1.25) * s0).toFloat()
                    } else null
                    if (parsed.size <= 1) {
                        // Однородный абзац — старый путь: стиль в поля, раны пусты.
                        val r0 = parsed.firstOrNull()
                        out.add(
                            DocBlock.Text(
                                text = sb.toString(),
                                fontFamily = r0?.fontFamily ?: "sans-serif",
                                fontSize = r0?.fontSize ?: 24f,
                                bold = r0?.bold == true,
                                italic = r0?.italic == true,
                                underline = r0?.underline == true,
                                alignment = fromFileAlign(o.optInt("align", 2)),
                                lineHeight = line,
                                frame = FrameStyle.sanitize(o.optInt("frame", 0))
                            )
                        )
                    } else {
                        // Разукрашенный: база = первый ран (для панели без выделения).
                        val r0 = parsed.first()
                        out.add(
                            DocBlock.Text(
                                text = sb.toString(),
                                fontFamily = r0.fontFamily,
                                fontSize = r0.fontSize,
                                bold = r0.bold,
                                italic = r0.italic,
                                underline = r0.underline,
                                alignment = fromFileAlign(o.optInt("align", 2)),
                                lineHeight = line,
                                frame = FrameStyle.sanitize(o.optInt("frame", 0)),
                                runs = parsed
                            )
                        )
                    }
                }
                "img" -> {
                    val ca = o.optJSONArray("crop")
                    out.add(
                        DocBlock.Image(
                            pngBase64 = o.optString("png", ""),
                            dither = o.optInt("dither", 1),
                            threshold = o.optInt("threshold", 128),
                            brightness = o.optDouble("brightness", 0.0),
                            contrast = o.optDouble("contrast", 0.0),
                            saturation = o.optDouble("saturation", 100.0),
                            gamma = o.optDouble("gamma", 1.0),
                            invert = o.optBoolean("invert", false),
                            rotation = o.optInt("rot", 0).let {
                                when (it) {
                                    90, 180, 270 -> it
                                    else -> 0
                                }
                            },
                            mirror = o.optBoolean("mir", false),
                            frame = FrameStyle.sanitize(o.optInt("frame", 0)),
                            cropL = ca?.optDouble(0, 0.0) ?: 0.0,
                            cropT = ca?.optDouble(1, 0.0) ?: 0.0,
                            cropR = ca?.optDouble(2, 0.0) ?: 0.0,
                            cropB = ca?.optDouble(3, 0.0) ?: 0.0
                        )
                    )
                }
                "qr" -> out.add(
                    DocBlock.Qr(
                        text = o.optString("t", ""),
                        size = o.optInt("s", 1).coerceIn(0, 2)
                    )
                )
                "gap" -> out.add(DocBlock.Gap(o.optInt("h", 48)))
                "div" -> out.add(DocBlock.Divider(o.optInt("style", 0)))
            }
        }
        return out
    }

    /** Мобильное: 0 слева, 1 по центру, 2 справа. */
    const val ALIGN_LEFT = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_RIGHT = 2

    /** В файл пишем коды десктопа: Left=0, Right=1, Center=2. */
    fun toFileAlign(mobile: Int): Int = when (mobile) {
        ALIGN_LEFT -> 0
        ALIGN_RIGHT -> 1
        else -> 2
    }

    /** Из файла (коды десктопа) в мобильные. */
    fun fromFileAlign(file: Int): Int = when (file) {
        0 -> ALIGN_LEFT
        1 -> ALIGN_RIGHT
        else -> ALIGN_CENTER
    }

    private fun DocBlock.Gap.HeightCoerced() = height.coerceIn(4, 400)
}
