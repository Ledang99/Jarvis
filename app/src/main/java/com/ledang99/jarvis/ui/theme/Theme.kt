package com.ledang99.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy950 = Color(0xFF08111F)
val Navy900 = Color(0xFF0D1B2D)
val Navy800 = Color(0xFF14243A)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Teal300 = Color(0xFF5EEAD4)
val Blue400 = Color(0xFF60A5FA)
val Amber400 = Color(0xFFFBBF24)
val Red400 = Color(0xFFF87171)

private val JarvisColors = darkColorScheme(
    primary = Teal300,
    onPrimary = Navy950,
    secondary = Blue400,
    onSecondary = Navy950,
    background = Navy950,
    onBackground = Color(0xFFF8FAFC),
    surface = Navy900,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate300,
    outline = Color(0xFF334155),
    error = Red400,
)

private val JarvisTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColors,
        typography = JarvisTypography,
        content = content,
    )
}
