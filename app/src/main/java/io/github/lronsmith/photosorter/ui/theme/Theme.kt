package io.github.lronsmith.photosorter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The app's colour tokens. All UI colours come from here. */
object PsColors {
    val Background = Color(0xFF0E0F12)
    val Surface = Color(0xFF1A1C21)
    val SurfaceHigh = Color(0xFF22252B)
    val Outline = Color(0xFF3A3E46)
    val Keep = Color(0xFF20B26B)
    val KeepText = Color(0xFF5FE0A0)
    val Trash = Color(0xFFD64545)
    val TrashText = Color(0xFFFF7A7A)
    val Warn = Color(0xFFF0C24A)
    val OnSurfaceDim = Color(0x99FFFFFF)
}

private val DarkColors = darkColorScheme(
    primary = PsColors.Keep,
    onPrimary = Color.White,
    error = PsColors.Trash,
    onError = Color.White,
    background = PsColors.Background,
    onBackground = Color.White,
    surface = PsColors.Surface,
    onSurface = Color.White,
    surfaceVariant = PsColors.SurfaceHigh,
    onSurfaceVariant = PsColors.OnSurfaceDim,
    surfaceContainerLowest = PsColors.Background,
    surfaceContainerLow = PsColors.Surface,
    surfaceContainer = PsColors.Surface,
    surfaceContainerHigh = PsColors.SurfaceHigh,
    surfaceContainerHighest = PsColors.SurfaceHigh,
    outline = PsColors.Outline,
    outlineVariant = PsColors.Outline,
)

/** Dark theme only, regardless of the system setting. */
@Composable
fun PhotoSorterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
