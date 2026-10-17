package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BabaRed,
    onPrimary = Color.White,
    primaryContainer = BabaRedLight,
    onPrimaryContainer = BabaRedDark,
    secondary = BabaYellow,
    onSecondary = BabaDark,
    secondaryContainer = BabaYellowLight,
    onSecondaryContainer = BabaDark,
    background = BabaBg,
    surface = Color.White,
    onSurface = BabaDark
)

@Composable
fun BabaJobSheetTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun BabaTradersTheme(
    content: @Composable () -> Unit
) {
    BabaJobSheetTheme(content = content)
}
