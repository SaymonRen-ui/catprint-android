package com.catprint.core.printer

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Шрифты приложения.
 * Windows-ключи маппятся на метрические клоны с кириллицей:
 * Arial -> Arimo, Times New Roman -> Tinos,
 * Courier New -> Cousine, Calibri -> Carlito.
 * На десктопе те же ключи открываются настоящими шрифтами Windows.
 * Плюс pixel/hand/display и монохромный Noto Emoji (ч/б глифы
 * вместо цветных системных, которые после порога дают «призраков»).
 *
 * API 29+: цепочка [шрифт ключа?, NotoEmoji] + системный фолбэк —
 * эмодзи ч/б везде (печать, превью, редактор, пикер).
 * Ниже — старый путь (ассет без эмодзи + TypefaceSpan на 28).
 */
object AppFonts {
    const val KEY_SANS = "sans-serif"
    const val KEY_SERIF = "serif"
    const val KEY_MONO = "monospace"
    const val KEY_ARIAL = "Arial"
    const val KEY_TIMES = "Times New Roman"
    const val KEY_COURIER = "Courier New"
    const val KEY_CALIBRI = "Calibri"
    const val KEY_SEGOE = "Segoe UI"
    const val KEY_TAHOMA = "Tahoma"
    const val KEY_CONSOLAS = "Consolas"
    const val KEY_COMIC = "Comic Sans MS"
    const val KEY_PIXEL = "pixel"
    const val KEY_HAND = "hand"
    const val KEY_DISPLAY = "display"

    val allKeys = listOf(
        KEY_ARIAL, KEY_TIMES, KEY_COURIER, KEY_CALIBRI,
        KEY_SEGOE, KEY_TAHOMA, KEY_CONSOLAS, KEY_COMIC,
        KEY_SANS, KEY_SERIF, KEY_MONO,
        KEY_PIXEL, KEY_HAND, KEY_DISPLAY
    )

    val displayNames = mapOf(
        KEY_ARIAL to "Arial",
        KEY_TIMES to "Times New Roman",
        KEY_COURIER to "Courier New",
        KEY_CALIBRI to "Calibri",
        KEY_SEGOE to "Segoe UI",
        KEY_TAHOMA to "Tahoma",
        KEY_CONSOLAS to "Consolas",
        KEY_COMIC to "Comic Sans MS",
        KEY_SANS to "Стандарт",
        KEY_SERIF to "С засечками",
        KEY_MONO to "Моно",
        KEY_PIXEL to "Пиксель",
        KEY_HAND to "От руки",
        KEY_DISPLAY to "Заголовок"
    )

    private const val EMOJI_ASSET = "fonts/NotoEmoji.ttf"

    @Volatile
    private var app: Context? = null

    fun init(context: Context) {
        if (app == null) app = context.applicationContext
    }

    private val cache = mutableMapOf<String, Typeface?>()

    @Synchronized
    private fun asset(path: String): Typeface? =
        cache.getOrPut(path) {
            try {
                val a = app ?: return null
                Typeface.createFromAsset(a.assets, path)
            } catch (e: Exception) {
                null
            }
        }

    private fun assetPathFor(key: String): String? = when (key) {
        KEY_ARIAL -> "fonts/Arimo.ttf"
        KEY_TIMES -> "fonts/Tinos-Regular.ttf"
        KEY_COURIER -> "fonts/Cousine-Regular.ttf"
        KEY_CALIBRI -> "fonts/Carlito-Regular.ttf"
        KEY_SEGOE -> "fonts/OpenSans.ttf"
        KEY_TAHOMA -> "fonts/PTSans-Regular.ttf"
        KEY_CONSOLAS -> "fonts/RobotoMono.ttf"
        KEY_COMIC -> "fonts/Neucha.ttf"
        KEY_PIXEL -> "fonts/PressStart2P-Regular.ttf"
        KEY_HAND -> "fonts/MarckScript-Regular.ttf"
        KEY_DISPLAY -> "fonts/RussoOne-Regular.ttf"
        else -> null
    }

    private fun systemNameFor(key: String): String = when (key.lowercase()) {
        "serif", KEY_TIMES.lowercase() -> "serif"
        "monospace", KEY_COURIER.lowercase(), KEY_CONSOLAS.lowercase() -> "monospace"
        else -> "sans-serif"
    }

    /** Базовый шрифт текста для ключа (null = системный по умолчанию). */
    fun text(key: String): Typeface? {
        assetPathFor(key)?.let { return asset(it) }
        return when (key.lowercase()) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> null
        }
    }

    /** Монохромные эмодзи для печати. */
    fun emoji(): Typeface? = asset(EMOJI_ASSET)

    /**
     * Прогрев в фоне при старте: цепочки копируют ассеты в кэш,
     * на главном потоке это вешает первый кадр на секунды.
     */
    fun warmup() {
        try {
            emoji()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (k in allKeys) {
                    try {
                        chain(k)
                    } catch (e: Exception) {
                    }
                }
            } else {
                for (k in allKeys) text(k)
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Цепочка [шрифт ключа?, NotoEmoji] + системный фолбэк. API 29+.
     * Один Typeface на всё: текст своим шрифтом, эмодзи ч/б.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Synchronized
    fun chain(key: String): Typeface? {
        val ck = "chain:$key"
        cache[ck]?.let { return it }
        val result = try {
            buildChain(key)
        } catch (e: Exception) {
            null
        }
        cache[ck] = result
        return result
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildChain(key: String): Typeface? {
        val ctx = app ?: return null
        val fonts = mutableListOf<android.graphics.fonts.Font>()
        assetPathFor(key)?.let { copyToCache(ctx, it) }?.let {
            fonts.add(android.graphics.fonts.Font.Builder(it).build())
        }
        copyToCache(ctx, EMOJI_ASSET)?.let {
            fonts.add(android.graphics.fonts.Font.Builder(it).build())
        }
        if (fonts.isEmpty()) return null
        val family = android.graphics.fonts.FontFamily.Builder(fonts.first()).apply {
            for (i in 1 until fonts.size) addFont(fonts[i])
        }.build()
        return android.graphics.Typeface.CustomFallbackBuilder(family)
            .setSystemFallback(systemNameFor(key))
            .build()
    }

    private fun copyToCache(ctx: Context, assetPath: String): File? {
        return try {
            val out = File(ctx.cacheDir, "fonts/" + assetPath.substringAfterLast('/'))
            if (!out.exists()) {
                out.parentFile?.mkdirs()
                ctx.assets.open(assetPath).use { ins ->
                    out.outputStream().use { ins.copyTo(it) }
                }
            }
            out.takeIf { it.exists() }
        } catch (e: Exception) {
            null
        }
    }
}
