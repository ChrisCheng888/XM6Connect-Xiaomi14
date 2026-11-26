package com.xm6.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val FallbackLightColors = lightColorScheme(
    primary = Color(0xFF2558B8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0D2963),
    secondary = Color(0xFF4F5E7B),
    secondaryContainer = Color(0xFFDAE2F2),
    onSecondaryContainer = Color(0xFF141C2B),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF111622),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111622),
    surfaceVariant = Color(0xFFEAEFF9),
    onSurfaceVariant = Color(0xFF616B7F),
    outline = Color(0xFFC3CCE0),
    outlineVariant = Color(0xFFE3E8F2)
)

private val FallbackDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF06214D),
    primaryContainer = Color(0xFF1A3566),
    onPrimaryContainer = Color(0xFFDAE7FF),
    secondary = Color(0xFFB7C5E0),
    secondaryContainer = Color(0xFF29354B),
    onSecondaryContainer = Color(0xFFDAE2F2),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFEEF2FA),
    surface = Color(0xFF141923),
    onSurface = Color(0xFFEEF2FA),
    surfaceVariant = Color(0xFF1A2130),
    onSurfaceVariant = Color(0xFF8F9BB3),
    outline = Color(0xFF3C475E),
    outlineVariant = Color(0xFF232C3F)
)

fun resolveXm6ColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean = true
): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> FallbackDarkColors
    else -> FallbackLightColors
}

@Composable
fun Xm6Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = resolveXm6ColorScheme(context, darkTheme, dynamicColor)

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
