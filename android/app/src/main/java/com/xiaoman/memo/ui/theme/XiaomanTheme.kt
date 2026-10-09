package com.xiaoman.memo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun XiaomanTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val xm = if (dark) DarkXm else LightXm
    CompositionLocalProvider(LocalXm provides xm) {
        MaterialTheme(colorScheme = scheme, typography = XmTypography, content = content)
    }
}
