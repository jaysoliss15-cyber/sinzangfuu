package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MissingPageDarkColorScheme =
  darkColorScheme(
    primary = StudioCyan,
    onPrimary = Color(0xFF001F24),
    primaryContainer = StudioCyanDim,
    onPrimaryContainer = StudioCyanOnContainer,
    secondary = StudioGreen,
    onSecondary = Color(0xFF002114),
    secondaryContainer = StudioGreenDim,
    onSecondaryContainer = StudioGreenOnContainer,
    tertiary = StudioAmber,
    onTertiary = Color(0xFF261800),
    tertiaryContainer = StudioAmberDim,
    onTertiaryContainer = StudioAmber,
    background = StudioBackground,
    onBackground = StudioTextPrimary,
    surface = StudioCard,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioCardElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioBackground,
    surfaceContainerLow = StudioCardDeep,
    surfaceContainer = StudioCard,
    surfaceContainerHigh = StudioCardElevated,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = StudioRed,
    onError = Color(0xFF2B0000),
    errorContainer = StudioRedDim,
    onErrorContainer = StudioRedOnContainer,
  )

val StudioShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
  )

@Composable
fun MissingPageStudioTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = MissingPageDarkColorScheme,
    typography = Typography,
    shapes = StudioShapes,
    content = content,
  )
}
