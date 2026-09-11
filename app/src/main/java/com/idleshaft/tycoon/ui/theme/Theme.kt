package com.idleshaft.tycoon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val GameColorScheme = darkColorScheme(
    primary = Color(0xFFFFC93C),
    onPrimary = Color(0xFF241A00),
    secondary = Color(0xFF6FDCFF),
    onSecondary = Color(0xFF002A33),
    tertiary = Color(0xFF7CB342),
    onTertiary = Color(0xFF0E1F05),
    background = Color(0xFF0B1622),
    onBackground = Color(0xFFE8F0F6),
    surface = Color(0xE6132333),
    onSurface = Color(0xFFE8F0F6),
    surfaceVariant = Color(0xFF1B2C3C),
    onSurfaceVariant = Color(0xFFB7C6D2),
    error = Color(0xFFFF5C5C),
    onError = Color(0xFF2B0000),
)

private val GameTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun IdleShaftTheme(content: @Composable () -> Unit) {
    // The game art direction is a vibrant dusk-toned world — always dark scheme.
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography = GameTypography,
        content = content,
    )
}
