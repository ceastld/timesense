package com.cea.timesense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Charcoal,
    secondary = Cream,
    onSecondary = Charcoal,
    background = Charcoal,
    onBackground = Cream,
    surface = CharcoalRaised,
    onSurface = Cream,
    onSurfaceVariant = Muted,
    outline = Hairline,
)

@Composable
fun TimeSenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = TimeSenseTypography,
        content = content,
    )
}
