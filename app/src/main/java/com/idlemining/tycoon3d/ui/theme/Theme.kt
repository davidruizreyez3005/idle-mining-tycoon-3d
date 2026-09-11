package com.idlemining.tycoon3d.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryGreenDim,
    onPrimaryContainer = OnPrimaryDark,
    secondary = Gold,
    onSecondary = OnPrimaryDark,
    secondaryContainer = GoldDim,
    onSecondaryContainer = OnPrimaryDark,
    background = DeepGreen,
    onBackground = HudText,
    surface = PanelDark,
    onSurface = HudText,
    surfaceVariant = PanelLight,
    onSurfaceVariant = HudTextDim,
    error = ErrorRed,
    onError = OnPrimaryDark,
)

private val LightColors = lightColorScheme(
    primary = PrimaryGreenDeep,
    onPrimary = OnPrimaryDark,
    secondary = GoldDeep,
    onSecondary = OnPrimaryDark,
)

@Composable
fun MiningTycoonTheme(
    darkTheme: Boolean = true, // a mining game reads best dark
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
