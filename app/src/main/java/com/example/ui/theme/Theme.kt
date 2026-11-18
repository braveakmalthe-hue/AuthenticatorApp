package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.model.ThemeMode

private val DarkColorScheme =
  darkColorScheme(
    primary = AuthDarkPrimary,
    onPrimary = AuthDarkOnPrimary,
    primaryContainer = AuthDarkPrimaryContainer,
    onPrimaryContainer = AuthDarkOnPrimaryContainer,
    background = AuthDarkBackground,
    onBackground = AuthDarkOnBackground,
    surface = AuthDarkSurface,
    onSurface = AuthDarkOnSurface,
    surfaceVariant = AuthDarkSurfaceVariant,
    onSurfaceVariant = AuthDarkOnSurfaceVariant,
    outline = AuthDarkOutline,
    outlineVariant = AuthDarkDivider,
    error = AuthDarkError,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AuthLightPrimary,
    onPrimary = AuthLightOnPrimary,
    primaryContainer = AuthLightPrimaryContainer,
    onPrimaryContainer = AuthLightOnPrimaryContainer,
    background = AuthLightBackground,
    onBackground = AuthLightOnBackground,
    surface = AuthLightSurface,
    onSurface = AuthLightOnSurface,
    surfaceVariant = AuthLightSurfaceVariant,
    onSurfaceVariant = AuthLightOnSurfaceVariant,
    outline = AuthLightOutline,
    outlineVariant = AuthLightDivider,
    error = AuthLightError,
  )

@Composable
fun AuthenticatorTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val darkTheme =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
