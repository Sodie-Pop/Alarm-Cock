package com.taskalarm.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Night to dawn": deep indigo surfaces with a sunrise-amber primary and a lavender secondary. */
private val DarkColors = darkColorScheme(
  primary = Color(0xffffc27a), onPrimary = Color(0xff3a2300),
  primaryContainer = Color(0xff5a3a0b), onPrimaryContainer = Color(0xffffddb5),
  secondary = Color(0xffc3b8ff), onSecondary = Color(0xff2a1f66),
  secondaryContainer = Color(0xff3b3373), onSecondaryContainer = Color(0xffe3deff),
  tertiary = Color(0xff8fd5c8), onTertiary = Color(0xff003731),
  background = Color(0xff0f1117), onBackground = Color(0xffe6e8f0),
  surface = Color(0xff0f1117), onSurface = Color(0xffe6e8f0),
  surfaceVariant = Color(0xff2b3141), onSurfaceVariant = Color(0xffa6abbc),
  surfaceContainerLowest = Color(0xff0b0d12), surfaceContainerLow = Color(0xff161923),
  surfaceContainer = Color(0xff1b1f2b), surfaceContainerHigh = Color(0xff232836),
  surfaceContainerHighest = Color(0xff2b3141),
  outline = Color(0xff5a6175), outlineVariant = Color(0xff2e3344),
  error = Color(0xffffb4ab), onError = Color(0xff690005),
  errorContainer = Color(0xff93000a), onErrorContainer = Color(0xffffdad6),
)

private val LightColors = lightColorScheme(
  primary = Color(0xff8a5100), onPrimary = Color.White,
  primaryContainer = Color(0xffffdcbc), onPrimaryContainer = Color(0xff2c1600),
  secondary = Color(0xff5b4fa8), onSecondary = Color.White,
  secondaryContainer = Color(0xffe5deff), onSecondaryContainer = Color(0xff170a60),
  tertiary = Color(0xff006b5f), onTertiary = Color.White,
  background = Color(0xfffbf8f4), onBackground = Color(0xff1d1b19),
  surface = Color(0xfffbf8f4), onSurface = Color(0xff1d1b19),
  surfaceVariant = Color(0xffeae1d7), onSurfaceVariant = Color(0xff5b5650),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xfff6f1ec),
  surfaceContainer = Color(0xfff1ebe5), surfaceContainerHigh = Color(0xffebe4dd),
  surfaceContainerHighest = Color(0xffe5ddd5),
  outline = Color(0xff8c857d), outlineVariant = Color(0xffd7cfc6),
  error = Color(0xffba1a1a), onError = Color.White,
  errorContainer = Color(0xffffdad6), onErrorContainer = Color(0xff410002),
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
internal fun TaskAlarmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = AppShapes, content = content)
}

/** Ringing and wake-up screens are always shown on a night sky, whatever the system theme. */
internal val NightSky = Brush.verticalGradient(listOf(Color(0xff090b16), Color(0xff15132d), Color(0xff2a1d3c), Color(0xff4a2a3a)))
internal val DawnSky = Brush.verticalGradient(listOf(Color(0xff12142a), Color(0xff2b2350), Color(0xff6b3f55), Color(0xffc7795a)))

/** Large clock numerals: light weight with tabular figures so digits don't shift as time changes. */
internal fun clockStyle(size: TextUnit) = TextStyle(fontSize = size, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum",
  letterSpacing = (-0.02f * size.value).sp, lineHeight = size * 1.05f)
