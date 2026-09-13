package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RoyalNavyLight,
    onPrimary = Color.White,
    secondary = AmberGoldLight,
    onSecondary = Color.Black,
    tertiary = EmeraldLight,
    background = RoyalNavyDark,
    surface = Color(0xFF1E293B),
    onSurface = Color.White,
    error = CrimsonError
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalNavy,
    onPrimary = Color.White,
    secondary = AmberGold,
    onSecondary = Color.White,
    tertiary = EmeraldSuccess,
    background = SlateBackground,
    surface = CardSurface,
    onSurface = TextPrimary,
    error = CrimsonError
)

@Composable
fun AlWissamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
