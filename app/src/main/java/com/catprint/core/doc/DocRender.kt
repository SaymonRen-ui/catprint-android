package com.catprint.core.doc

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.catprint.core.printer.AppFonts
import com.catprint.core.printer.BitOrder
import com.catprint.core.printer.DitherMode
import com.catprint.core.printer.ImageOptions
import com.catprint.core.printer.PhotoPipeline
import com.catprint.core.printer.Raster

/**
 * Склейка блоков в 1-битный растр 384px.
 * Текст — через StaticLayout с выравниванием абзаца,
 * картинки — через общий PhotoPipeline.
 */
/**
 * Поотрезочные Ж/К флагами Paint: жирность — чёрной обводкой
 * (~6% кегля, порог 128 переживает), курсив — наклоном.
 * Состояние ставится ЯВНО в обе стороны общим кодом (measure и draw):
 * отрезки покрывают текст полностью, протечек между ними нет.
 */
private class RunPaintSpan(
    private val bold: Boolean,
    private val italic: Boolean,
    private val refSize: Float
) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) = applyTo(ds)
    override fun updateMeasureState(ds: TextPaint) = applyTo(ds)

    private fun applyTo(ds: TextPaint) {
        if (bold) {
            ds.style = Paint.Style.FILL_AND_STROKE
            ds.strokeWidth = (refSize * 0.055f).coerceAtLeast(0.8f)
            ds.strokeJoin = Paint.Join.ROUND
        } else {
            ds.style = Paint.Style.FILL
            ds.strokeWidth = 0f
        }
        ds.textSkewX = if (italic) -0.25f else 0f
    }
}

object DocRender {

    const val PAPER_WIDTH = 384
    const val PADDING = 8
    const val MAX_HEIGHT = 4200

    data class Rendered(
        val black: BooleanArray,
        val width: Int,
        val height: Int
    ) {
        fun packed(order: BitOrder): ByteArray =
            Raster.pack(black, width, height, order)

        val heightMm: Double get() = height / 8.0
    }

    fun render(blocks: List<DocBlock>): Rendered {
        require(blocks.isNotEmpty()) { "Документ пуст" }

        // Замер + кэш картинок за один проход
        val heights = measureAll(blocks)
        val fullH = heights.sum()
        require(fullH > 0) { "Документ пуст" }
        require(fullH <= MAX_HEIGHT) { "Документ слишком длинный ($fullH > $MAX_HEIGHT)" }

        val bitmap = Bitmap.createBitmap(PAPER_WIDTH, fullH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 0
        var pi = 0
        for (block in blocks) {
            when (block) {
                is DocBlock.Text -> {
                    val m = textMargin(block)
                    val (layout, ulines) = makeLayout(block, PAPER_WIDTH - m * 2)
                    canvas.save()
                    canvas.translate(m.toFloat(), (y + m).toFloat())
                    layout.draw(canvas)
                    drawUlines(canvas, layout, ulines)
                    canvas.restore()
                    drawFrameMask(canvas, 0f, y.toFloat(), PAPER_WIDTH, heights[pi], block.frame)
                    y += heights[pi]
                    pi++
                }
                is DocBlock.Image -> {
                    val bmp = imageCache[pi]
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, 0f, y.toFloat(), null)
                        y += bmp.height
                    }
                    pi++
                }
                is DocBlock.Gap -> {
                    y += heights[pi]
                    pi++
                }
                is DocBlock.Qr -> {
                    val bmp = imageCache[pi]
                    if (bmp != null) {
                        canvas.drawBitmap(
                            bmp,
                            ((PAPER_WIDTH - bmp.width) / 2).toFloat(),
                            y.toFloat(), null
                        )
                        y += bmp.height
                    }
                    pi++
                }
                is DocBlock.Divider -> {
                    drawDivider(canvas, y, block.style)
                    y += heights[pi]
                    pi++
                }
            }
        }

        // В 1 бит порогом (текст/линии чёткие, картинки уже в ЧБ)
        val px = IntArray(PAPER_WIDTH * fullH)
        bitmap.getPixels(px, 0, PAPER_WIDTH, 0, 0, PAPER_WIDTH, fullH)
        val black = BooleanArray(px.size) { i ->
            val p = px[i]
            val lum = 0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF)
            lum < 128
        }
        return Rendered(black, PAPER_WIDTH, fullH)
    }

    /** Поле текста: обычное PADDING, с рамкой — поле рамки. */
    private fun textMargin(block: DocBlock.Text): Int {
        val m = FrameStyle.margin(block.frame)
        return if (m == 0) PADDING else m
    }

    /**
     * Маска рамки w×h (true = чёрное). Единый узор для картинок (массив)
     * и текста (битмап-маска на канву) — расходятся только носителем.
     */
    private fun frameMask(w: Int, h: Int, frame: Int): BooleanArray {
        val out = BooleanArray(w * h)
        val f = FrameStyle.sanitize(frame)
        if (f == FrameStyle.NONE) return out
        val m = FrameStyle.margin(f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // Внутри полей — контент, не трогаем.
                if (x >= m && x < w - m && y >= m && y < h - m) continue
                if (framePixel(x, y, w, h, f)) out[y * w + x] = true
            }
        }
        return out
    }

    /** Чёрный ли пиксель рамки в (x, y). e — глубина от ближайшего края. */
    private fun framePixel(x: Int, y: Int, w: Int, h: Int, f: Int): Boolean {
        val e = minOf(minOf(x, y), minOf(w - 1 - x, h - 1 - y))
        // Координата вдоль ближайшего края (для волн/пунктира).
        val t = if (minOf(y, h - 1 - y) <= minOf(x, w - 1 - x)) x else y
        return when (f) {
            FrameStyle.THIN -> e in 6..7
            FrameStyle.THICK -> e in 6..10
            FrameStyle.DOUBLE -> e in 6..7 || e in 11..12
            // Волна: осевая 8 ± 3, период 24, толщина ~3.
            FrameStyle.WAVY ->
                kotlin.math.abs(e - (8 + 3 * kotlin.math.sin(t * Math.PI / 12))) < 1.5
            // Пунктир 10/6, толщина 3.
            FrameStyle.DASHED ->
                e in 6..8 && (t % 16 + 16) % 16 < 10
            // Точки 3px с шагом 8.
            FrameStyle.DOTTED ->
                e in 6..8 && (t % 8 + 8) % 8 < 3
            // Зигзаг: треугольная волна 0..3 вокруг глубины 7, период 16.
            FrameStyle.ZIGZAG -> {
                val tri = kotlin.math.abs(((t % 16 + 16) % 16) - 8) / 8.0 * 3.0
                kotlin.math.abs(e - (7 + tri)) < 1.2
            }
            else -> false
        }
    }

    /** Та же маска на канву (текст): чёрное через ALPHA_8, фон прозрачный. */
    private fun drawFrameMask(
        canvas: Canvas, left: Float, top: Float, w: Int, h: Int, frame: Int
    ) {
        if (FrameStyle.sanitize(frame) == FrameStyle.NONE) return
        val mask = frameMask(w, h, frame)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val px = IntArray(mask.size) { i ->
            if (mask[i]) 0xFF000000.toInt() else 0
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }
        canvas.drawBitmap(bmp, left, top, paint)
        if (!bmp.isRecycled) bmp.recycle()
    }

    private val imageCache = mutableMapOf<Int, Bitmap>()

    /** Кэш последнего декодированного исходника (для слайдеров). */
    private data class SrcCache(
        val key: String,
        val px: IntArray,
        val w: Int,
        val h: Int
    )

    @Volatile
    private var srcCache: SrcCache? = null

    /** Кэш ресайза до ширины печати (от слайдеров не зависит). */
    private data class SizedCache(
        val key: String,
        val sized: PhotoPipeline.Sized
    )

    @Volatile
    private var sizedCache: SizedCache? = null

    fun clearCache() {
        imageCache.values.forEach { if (!it.isRecycled) it.recycle() }
        imageCache.clear()
    }

    private fun measureAll(blocks: List<DocBlock>): List<Int> {
        clearCache()
        val out = mutableListOf<Int>()
        var pi = 0
        for (block in blocks) {
            when (block) {
                is DocBlock.Text -> {
                    val m = textMargin(block)
                    out.add(makeLayout(block, PAPER_WIDTH - m * 2).first.height + m * 2)
                    pi++
                }
                is DocBlock.Image -> {
                    val bmp = decodeImage(block)
                    if (bmp != null) {
                        imageCache[pi] = bmp
                        out.add(bmp.height)
                    }
                    pi++
                }
                is DocBlock.Gap -> {
                    out.add(block.height.coerceIn(4, 400))
                    pi++
                }
                is DocBlock.Qr -> {
                    val bmp = qrBitmap(block)
                    if (bmp != null) {
                        imageCache[pi] = bmp
                        out.add(bmp.height)
                    } else {
                        out.add(0)
                    }
                    pi++
                }
                is DocBlock.Divider -> {
                    out.add(20)
                    pi++
                }
            }
        }
        return out
    }

    /** Подчёркнутый отрезок в координатах layout + его кегль. */
    private data class ULine(val start: Int, val end: Int, val sizePx: Float)

    private fun makeLayout(block: DocBlock.Text, width: Int): Pair<Layout, List<ULine>> {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = block.fontSize
            // База нейтральная: Ж/К/Ч каждого отрезка задаёт RunPaintSpan.
            // (Спаны покрывают текст полностью — состояние не протекает.)
            typeface = resolveTypeface(block.fontFamily)
        }
        val align = when (block.alignment) {
            DocSerializer.ALIGN_LEFT -> Layout.Alignment.ALIGN_NORMAL
            DocSerializer.ALIGN_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        // Единый путь: однородный абзац = один ран из базы.
        val src = if (block.runs.isEmpty()) {
            listOf(
                com.catprint.core.doc.TextRun(
                    block.text.ifEmpty { " " },
                    block.fontFamily, block.fontSize,
                    block.bold, block.italic, block.underline
                )
            )
        } else {
            val t = if (block.text.isEmpty()) " " else block.text
            var off = 0
            block.runs.map { r ->
                val take = (t.length - off).coerceAtLeast(0)
                val s = r.text.take(take)
                off += s.length
                r.copy(text = s)
            }.filter { it.text.isNotEmpty() }.ifEmpty {
                listOf(
                    com.catprint.core.doc.TextRun(
                        " ", block.fontFamily, block.fontSize,
                        block.bold, block.italic, block.underline
                    )
                )
            }
        }
        val base = StringBuilder()
        for (r in src) base.append(r.text)
        val full = base.toString()
        // Ч/б эмодзи ТОЛЬКО на эмодзи-диапазонах. На весь текст спан
        // ставить нельзя: NotoEmoji содержит и обычные символы —
        // они затенили бы шрифт.
        val spanned = android.text.SpannableString(full)
        val ulines = mutableListOf<ULine>()
        var off = 0
        for (r in src) {
            val s = off
            val e = off + r.text.length
            off = e
            spanned.setSpan(
                android.text.style.TypefaceSpan(resolveTypeface(r.fontFamily)),
                s, e,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spanned.setSpan(
                android.text.style.AbsoluteSizeSpan(r.fontSize.toInt(), false),
                s, e,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spanned.setSpan(
                RunPaintSpan(r.bold, r.italic, r.fontSize),
                s, e,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // Подчёркивание рисуем САМИ прямоугольником (см. ниже):
            // UnderlineSpan на цепочке [шрифт, NotoEmoji] берёт метрики
            // эмодзи-шрифта и кладёт линию наверх глифов.
            if (r.underline) ulines.add(ULine(s, e, r.fontSize))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AppFonts.emoji()?.let { et ->
                for (run in emojiRuns(full)) {
                    spanned.setSpan(
                        android.text.style.TypefaceSpan(et),
                        run.first, run.last + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        val builder = StaticLayout.Builder.obtain(
            spanned,
            0, spanned.length, paint, width
        )
            .setAlignment(align)
            .setIncludePad(true)
        block.lineHeight?.let {
            if (it > 0) builder.setLineSpacing(0f, it / block.fontSize.coerceAtLeast(1f))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        }
        return builder.build() to ulines
    }

    /**
     * Подчёркивания отрезков: чёрные прямоугольники под базовой линией.
     * Канва уже сдвинута под layout (тот же трансформ, что у layout.draw).
     */
    private fun drawUlines(canvas: Canvas, layout: Layout, ulines: List<ULine>) {
        if (ulines.isEmpty()) return
        val p = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }
        for (u in ulines) {
            if (u.start >= u.end || u.start >= layout.text.length) continue
            val e = u.end.coerceAtMost(layout.text.length)
            val firstLine = layout.getLineForOffset(u.start)
            val lastLine = layout.getLineForOffset((e - 1).coerceAtLeast(u.start))
            val th = (u.sizePx / 14f).coerceAtLeast(1.5f)
            for (ln in firstLine..lastLine) {
                val ls = layout.getLineStart(ln)
                val le = layout.getLineEnd(ln)
                val s = maxOf(u.start, ls)
                val ee = minOf(e, le)
                if (s >= ee) continue
                var x0 = layout.getPrimaryHorizontal(s)
                var x1 = layout.getPrimaryHorizontal(ee)
                if (x1 < x0) {
                    val t = x0
                    x0 = x1
                    x1 = t
                }
                if (x1 - x0 < 1f) continue
                val y = layout.getLineBaseline(ln) + u.sizePx * 0.08f
                canvas.drawRect(x0, y, x1, y + th, p)
            }
        }
    }

    /** Диапазоны эмодзи в CHAR-индексах (для TypefaceSpan). */
    private fun emojiRuns(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var i = 0
        val n = text.length
        var runStart = -1
        fun isEmojiBase(cp: Int): Boolean =
            (cp in 0x1F000..0x1FAFF) || // пиктограммы
                (cp in 0x2600..0x27BF) || // символы/дингбаты
                (cp in 0x2B00..0x2BFF) ||
                (cp in 0xFE00..0xFE0F) || // вариационные селекторы
                (cp in 0x1F1E6..0x1F1FF) || // региональные индикаторы
                (cp in 0x1F3FB..0x1F3FF) || // тона кожи
                (cp in 0xE0020..0xE007F) || // теги
                cp == 0x200D || cp == 0x20E3 // ZWJ, keycap
        fun isKeycapBase(cp: Int): Boolean =
            cp == '#'.code || cp == '*'.code || (cp in '0'.code..'9'.code)
        while (i < n) {
            val cp = Character.codePointAt(text, i)
            val len = Character.charCount(cp)
            var emoji = isEmojiBase(cp)
            if (!emoji && isKeycapBase(cp)) {
                // keycap: база + [FE0F] + 20E3 в пределах 3 кодпоинтов
                var j = i + len
                var k = 0
                while (j < n && k < 3) {
                    val c2 = Character.codePointAt(text, j)
                    if (c2 == 0x20E3) {
                        emoji = true
                        break
                    }
                    if (c2 != 0xFE0F && c2 != 0xFE0E) break
                    j += Character.charCount(c2)
                    k++
                }
            }
            if (emoji) {
                if (runStart < 0) runStart = i
            } else if (runStart >= 0) {
                out.add(runStart until i)
                runStart = -1
            }
            i += len
        }
        if (runStart >= 0) out.add(runStart until n)
        return out
    }

    @SuppressLint("NewApi")
    private fun resolveTypeface(family: String): Typeface {
        // API 29+: цепочка [шрифт, NotoEmoji] — эмодзи сразу ч/б.
        val chained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                AppFonts.chain(family)
            } catch (e: Exception) {
                null
            }
        } else null
        val base = chained
            ?: AppFonts.text(family) ?: when (family.lowercase()) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        // Всегда NORMAL: Ж/К синтезирует Paint в makeLayout.
        return Typeface.create(base, Typeface.NORMAL)
    }

    private fun drawDivider(canvas: Canvas, y: Int, style: Int) {
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 3f
            isAntiAlias = false
            when (style) {
                1 -> pathEffect = DashPathEffect(floatArrayOf(14f, 9f), 0f)
                2 -> pathEffect = DashPathEffect(floatArrayOf(3f, 7f), 0f)
            }
        }
        if (style == 3) {
            canvas.drawLine(8f, (y + 6).toFloat(), 376f, (y + 6).toFloat(), paint)
            canvas.drawLine(8f, (y + 14).toFloat(), 376f, (y + 14).toFloat(), paint)
        } else {
            canvas.drawLine(8f, (y + 10).toFloat(), 376f, (y + 10).toFloat(), paint)
        }
    }

    private fun decodeImage(block: DocBlock.Image): Bitmap? {
        val res = renderSized(block) ?: return null
        return try {
            val out = Bitmap.createBitmap(res.width, res.height, Bitmap.Config.ARGB_8888)
            val outPx = IntArray(res.black.size) { i ->
                if (res.black[i]) Color.BLACK else Color.WHITE
            }
            out.setPixels(outPx, 0, res.width, 0, 0, res.width, res.height)
            out
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Обрезка долями 0..1 с каждой стороны. Санитизация: больше 90%
     * с одной оси не отдаём (пропорционально ужимаем), минимум 1px.
     */
    private fun cropImg(
        img: com.catprint.core.printer.ImageTransform.Img,
        l: Double, t: Double, r: Double, b: Double
    ): com.catprint.core.printer.ImageTransform.Img {
        if (l <= 0 && t <= 0 && r <= 0 && b <= 0) return img
        var cl = l.coerceIn(0.0, 0.8)
        var cr = r.coerceIn(0.0, 0.8)
        var ct = t.coerceIn(0.0, 0.8)
        var cb = b.coerceIn(0.0, 0.8)
        val hw = cl + cr
        if (hw >= 0.9) {
            val k = 0.9 / hw
            cl *= k
            cr *= k
        }
        val hh = ct + cb
        if (hh >= 0.9) {
            val k = 0.9 / hh
            ct *= k
            cb *= k
        }
        val x0 = (cl * img.w).toInt().coerceIn(0, img.w - 1)
        val x1 = (img.w - cr * img.w).toInt().coerceIn(x0 + 1, img.w)
        val y0 = (ct * img.h).toInt().coerceIn(0, img.h - 1)
        val y1 = (img.h - cb * img.h).toInt().coerceIn(y0 + 1, img.h)
        val nw = x1 - x0
        val nh = y1 - y0
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            img.px.copyInto(out, y * nw, (y0 + y) * img.w + x0, (y0 + y) * img.w + x0 + nw)
        }
        return com.catprint.core.printer.ImageTransform.Img(out, nw, nh)
    }

    /** QR в ч/б Bitmap (кладётся в imageCache как картинки). */
    private fun qrBitmap(block: DocBlock.Qr): Bitmap? {
        val res = com.catprint.core.printer.QrRender.render(
            block.text,
            com.catprint.core.printer.QrRender.targetPx(block.size)
        ) ?: return null
        val out = Bitmap.createBitmap(res.width, res.height, Bitmap.Config.ARGB_8888)
        val px = IntArray(res.black.size) { i ->
            if (res.black[i]) Color.BLACK else Color.WHITE
        }
        out.setPixels(px, 0, res.width, 0, 0, res.width, res.height)
        return out
    }

    /** Превью блока-картинки для редактора (ч/б Bitmap). */
    fun previewImage(block: DocBlock.Image): Bitmap? =
        previewInto(block, null)

    /**
     * То же превью, но с переиспользованием битмапа: если reuse тех же
     * размеров — пиксели перезаписываются в него (ноль аллокаций на тик).
     */
    fun previewInto(block: DocBlock.Image, reuse: Bitmap?): Bitmap? {
        val res = renderSized(block) ?: return null
        val out = if (reuse != null && !reuse.isRecycled &&
            reuse.width == res.width && reuse.height == res.height
        ) {
            reuse
        } else {
            Bitmap.createBitmap(res.width, res.height, Bitmap.Config.ARGB_8888)
        }
        val outPx = IntArray(res.black.size) { i ->
            if (res.black[i]) Color.BLACK else Color.WHITE
        }
        out.setPixels(outPx, 0, res.width, 0, 0, res.width, res.height)
        return out
    }

    /**
     * Сквозной прогон блока без сборки Bitmap: исходник (+кэш) → ресайз
     * (+кэш) → adjustments → дизеринг. Чистая математика для превью.
     */
    private fun renderSized(block: DocBlock.Image): PhotoPipeline.Result? {
        if (block.pngBase64.isBlank()) return null
        return try {
            val key = block.pngBase64 + "|r" + block.rotation + "m" + block.mirror +
                "|c" + block.cropL + "," + block.cropT + "," +
                block.cropR + "," + block.cropB
            val snap = srcCache
            val (px0, w0, h0) = if (snap != null && snap.key == key) {
                Triple(snap.px, snap.w, snap.h)
            } else {
                val bytes = Base64.decode(block.pngBase64, Base64.DEFAULT)
                val src = android.graphics.BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size
                ) ?: return null
                val sw = src.width
                val sh = src.height
                val spx = IntArray(sw * sh)
                src.getPixels(spx, 0, sw, 0, 0, sw, sh)
                if (!src.isRecycled) src.recycle()
                var img = com.catprint.core.printer.ImageTransform.Img(spx, sw, sh)
                if (block.rotation != 0) {
                    img = com.catprint.core.printer.ImageTransform.rotate(
                        img, block.rotation
                    )
                }
                if (block.mirror) {
                    img = com.catprint.core.printer.ImageTransform.mirror(img)
                }
                // Обрезка — по уже повёрнутому/отзеркаленному кадру:
                // пользователь режет то, что видит в превью.
                img = cropImg(img, block.cropL, block.cropT, block.cropR, block.cropB)
                srcCache = SrcCache(key, img.px, img.w, img.h)
                Triple(img.px, img.w, img.h)
            }
            val (px, w, h) = Triple(px0, w0, h0)

            // Рамка съедает поля: контент ужмём до внутренней ширины,
            // рамку дорисуем вокруг (превью, холст и печать — из одного места).
            val m = FrameStyle.margin(block.frame)
            val targetW = PAPER_WIDTH - m * 2
            val rkey = "$key|w$targetW"
            val rsnap = sizedCache
            val sized = if (rsnap != null && rsnap.key == rkey) {
                rsnap.sized
            } else {
                val s = PhotoPipeline.resizeToWidth(px, w, h, targetW)
                sizedCache = SizedCache(rkey, s)
                s
            }

            val opts = ImageOptions(
                brightness = block.brightness,
                contrast = block.contrast,
                saturation = block.saturation,
                dither = DitherMode.fromOrdinalSafe(block.dither),
                threshold = block.threshold,
                gamma = block.gamma,
                invert = block.invert
            )
            val res = PhotoPipeline.processSized(sized.px, sized.w, sized.h, opts)
            if (m == 0) return res
            val fw = PAPER_WIDTH
            val fh = res.height + m * 2
            val out = BooleanArray(fw * fh) // false = белое поле
            for (y in 0 until res.height) {
                res.black.copyInto(out, (y + m) * fw + m, y * res.width, (y + 1) * res.width)
            }
            val mask = frameMask(fw, fh, block.frame)
            for (i in out.indices) if (mask[i]) out[i] = true
            PhotoPipeline.Result(out, fw, fh)
        } catch (e: Exception) {
            null
        }
    }

    /** Превью QR-блока для редактора. */
    fun previewQr(block: DocBlock.Qr): Bitmap? = qrBitmap(block)

    /** Доля чёрных точек растра 0..1 (для автоплотности). */
    fun coverageOf(rendered: Rendered): Double {
        if (rendered.black.isEmpty()) return 0.0
        var n = 0
        for (b in rendered.black) if (b) n++
        return n.toDouble() / rendered.black.size
    }

    /**
     * Честное превью: одиночная точка без соседей в окне 3x3
     * головкой не пропечатывается (ей не хватает жара) — на бумаге
     * там будет белое. Убираем такие из ПОКАЗА (печать не трогаем).
     */
    fun dropUnfired(rendered: Rendered): BooleanArray {
        val w = rendered.width
        val h = rendered.height
        val src = rendered.black
        val out = src.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!src[y * w + x]) continue
                var neighbors = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx !in 0 until w) continue
                        if (src[yy * w + xx]) neighbors++
                    }
                }
                if (neighbors < 2) out[y * w + x] = false
            }
        }
        return out
    }
}
