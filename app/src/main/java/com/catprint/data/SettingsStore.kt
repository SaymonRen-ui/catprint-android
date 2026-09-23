package com.catprint.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.catprint.core.printer.BitOrder

/** Настройки приложения (зеркало десктопного AppSettings). */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("catprint", Context.MODE_PRIVATE)

    var darkTheme: Int // 0 система, 1 светлая, 2 тёмная
        get() = prefs.getInt("theme", 0)
        set(v) = prefs.edit { putInt("theme", v) }

    var intensity: Int // байт 0x30..0x7A
        get() = prefs.getInt("intensity", 0x5D)
        set(v) = prefs.edit { putInt("intensity", v) }

    var copies: Int // экземпляров 1..10
        get() = prefs.getInt("copies", 1).coerceIn(1, 10)
        set(v) = prefs.edit { putInt("copies", v.coerceIn(1, 10)) }

    /** Автоплотность: снижать жар на плотной заливке (фото). */
    var autoDensity: Boolean
        get() = prefs.getBoolean("autodensity", true)
        set(v) = prefs.edit { putBoolean("autodensity", v) }

    /** Адаптивный жар: A2 по группам строк (редкие — горячо). */
    var adaptiveHeat: Boolean
        get() = prefs.getBoolean("adaptiveheat", true)
        set(v) = prefs.edit { putBoolean("adaptiveheat", v) }

    var bitOrder: BitOrder
        get() = if (prefs.getInt("bitorder", 0) == 1) BitOrder.MSB_FIRST
        else BitOrder.LSB_FIRST
        set(v) = prefs.edit { putInt("bitorder", if (v == BitOrder.MSB_FIRST) 1 else 0) }

    var lineDelayMs: Long
        get() = prefs.getLong("linedelay", 15)
        set(v) = prefs.edit { putLong("linedelay", v) }

    var blockLines: Int
        get() = prefs.getInt("blocklines", 40)
        set(v) = prefs.edit { putInt("blocklines", v) }

    var blockPauseMs: Long
        get() = prefs.getLong("blockpause", 0)
        set(v) = prefs.edit { putLong("blockpause", v) }

    var splitEnabled: Boolean
        get() = prefs.getBoolean("split", false)
        set(v) = prefs.edit { putBoolean("split", v) }

    var splitLines: Int
        get() = prefs.getInt("splitlines", 96)
        set(v) = prefs.edit { putInt("splitlines", v) }

    var splitPauseMs: Long
        get() = prefs.getLong("splitpause", 1500)
        set(v) = prefs.edit { putLong("splitpause", v) }

    /** Разовая миграция: деление давало протяжки-разрывы, выключаем. */
    var splitFixApplied: Boolean
        get() = prefs.getBoolean("split_fix1", false)
        set(v) = prefs.edit { putBoolean("split_fix1", v) }

    var recentFonts: List<String>
        get() = prefs.getStringSet("recent_fonts", emptySet())?.toList() ?: emptyList()
        set(v) = prefs.edit { putStringSet("recent_fonts", v.take(5).toSet()) }

    var recentEmojis: List<String>
        get() = prefs.getStringSet("recent_emoji", emptySet())?.toList() ?: emptyList()
        set(v) = prefs.edit { putStringSet("recent_emoji", v.take(16).toSet()) }

    var lastDeviceAddress: String?
        get() = prefs.getString("last_addr", null)
        set(v) = prefs.edit { putString("last_addr", v) }

    var lastDeviceName: String
        get() = prefs.getString("last_name", "") ?: ""
        set(v) = prefs.edit { putString("last_name", v) }
}
