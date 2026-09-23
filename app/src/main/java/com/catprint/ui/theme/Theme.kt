package com.catprint.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Indigo = Color(0xFF2563EB)
private val IndigoDark = Color(0xFF5B8DEF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    surface = Color(0xFFF3F4F8),
    onSurface = Color(0xFF1A1D24),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE2E5EC)
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    surface = Color(0xFF14161C),
    onSurface = Color(0xFFEDEFF4),
    surfaceVariant = Color(0xFF1C1F27),
    onSurfaceVariant = Color(0xFF9AA0AE),
    outline = Color(0xFF31353F)
)

/** mode: 0 система, 1 светлая, 2 тёмная. */
@Composable
fun CatPrintTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        !dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Тумблер с заметным ВЫКЛ-состоянием: стандартный Material в темноте
 * сливается с фоном (едва видный контур). Серый трек + светлый бегунок
 * читаются в обеих темах. ВКЛ-состояние — стандартное.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            uncheckedThumbColor = Color(0xFFE0E0E0),
            uncheckedTrackColor = Color(0xFF616161),
            uncheckedBorderColor = Color(0xFFBDBDBD)
        )
    )
}
