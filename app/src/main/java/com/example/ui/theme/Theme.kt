package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ProxyWarsColorScheme = darkColorScheme(
    primary = HumanCyan,
    onPrimary = OnHumanCyan,
    primaryContainer = HumanCyanContainer,
    onPrimaryContainer = HumanCyanBright,
    secondary = GlitchMagenta,
    onSecondary = OnGlitchMagenta,
    secondaryContainer = GlitchMagentaContainer,
    onSecondaryContainer = GlitchMagentaBright,
    tertiary = GlitchAmber,
    onTertiary = ObsidianBg,
    tertiaryContainer = GlitchAmberContainer,
    onTertiaryContainer = GlitchAmber,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = SlateBorder.copy(alpha = 0.5f),
    error = GlitchMagenta,
    onError = OnGlitchMagenta,
)

private val ProxyWarsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ProxyWarsTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ProxyWarsColorScheme,
        typography = ProxyWarsTypography,
        shapes = ProxyWarsShapes,
        content = content
    )
}
