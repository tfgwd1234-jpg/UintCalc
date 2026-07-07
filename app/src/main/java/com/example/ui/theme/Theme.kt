package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PastelPrimary,
    onPrimary = PastelOnPrimary,
    primaryContainer = PastelPrimaryContainer,
    onPrimaryContainer = PastelOnPrimaryContainer,
    secondary = PastelSecondary,
    secondaryContainer = PastelSecondaryContainer,
    onSecondaryContainer = PastelOnSecondaryContainer,
    background = PastelBackgroundDark,
    surface = PastelSurfaceDark,
    onBackground = PastelOnSurfaceDark,
    onSurface = PastelOnSurfaceDark,
    surfaceVariant = PastelSurfaceVariantDark,
    onSurfaceVariant = PastelOnSurfaceVariantDark,
    outline = PastelOutlineDark,
    outlineVariant = PastelOutlineVariantDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PastelPrimary,
    onPrimary = PastelOnPrimary,
    primaryContainer = PastelPrimaryContainer,
    onPrimaryContainer = PastelOnPrimaryContainer,
    secondary = PastelSecondary,
    secondaryContainer = PastelSecondaryContainer,
    onSecondaryContainer = PastelOnSecondaryContainer,
    background = PastelBackgroundLight,
    surface = PastelSurfaceLight,
    onBackground = PastelOnSurfaceLight,
    onSurface = PastelOnSurfaceLight,
    surfaceVariant = PastelSurfaceVariantLight,
    onSurfaceVariant = PastelOnSurfaceVariantLight,
    outline = PastelOutlineLight,
    outlineVariant = PastelOutlineVariantLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  // Set dynamicColor to false by default to showcase the Bento design system style
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
