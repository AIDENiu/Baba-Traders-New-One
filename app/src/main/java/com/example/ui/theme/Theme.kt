package com.example.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = BabaRed,
    onPrimary = Color.White,
    primaryContainer = BabaRedLight,
    onPrimaryContainer = BabaRedDark,
    secondary = BabaYellowDark,
    onSecondary = BabaDark,
    secondaryContainer = BabaYellowLight,
    onSecondaryContainer = BabaDark,
    tertiary = BabaYellow,
    onTertiary = BabaDark,
    background = BabaBg,
    onBackground = BabaDark,
    surface = BabaSurface,
    onSurface = BabaDark, // Ensures typed input and text are #1A1A1A
    surfaceVariant = BabaLightGray,
    onSurfaceVariant = BabaDark,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = BabaLightGray,
    surfaceContainerHighest = BabaLightGray,
    outline = BabaBorder,
    outlineVariant = BabaBorder,
    error = BabaOverdue,
    onError = Color.White
)

@Composable
fun BabaJobSheetTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(
            LocalContentColor provides BabaDark,
            LocalTextStyle provides TextStyle(
                color = BabaDark,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp
            )
        ) {
            content()
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    BabaJobSheetTheme(darkTheme = false, content = content)
}

