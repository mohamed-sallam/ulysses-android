package com.ulysses.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ulysses.app.R

// ── Color Palette ──
// A deep, premium dark theme inspired by ocean depths (Ulysses' voyage)

val DeepOcean = Color(0xFF0A0E1A)
val DarkNavy = Color(0xFF111827)
val MidnightBlue = Color(0xFF1E293B)
val SlateBlue = Color(0xFF334155)
val SteelGray = Color(0xFF64748B)
val SilverMist = Color(0xFF94A3B8)
val CloudWhite = Color(0xFFE2E8F0)
val PureWhite = Color(0xFFF8FAFC)

// Accent colors - warm amber/gold for contrast against deep blues
val GoldenAmber = Color(0xFFF59E0B)
val WarmGold = Color(0xFFFBBF24)
val DeepAmber = Color(0xFFD97706)
val SoftAmber = Color(0x33F59E0B)

// Status colors
val BlockedRed = Color(0xFFEF4444)
val SoftRed = Color(0x33EF4444)
val ActiveGreen = Color(0xFF22C55E)
val SoftGreen = Color(0x3322C55E)
val WarningOrange = Color(0xFFF97316)
val InfoBlue = Color(0xFF3B82F6)
val SoftBlue = Color(0x333B82F6)

// Gradient colors
val GradientStart = Color(0xFF1E3A5F)
val GradientEnd = Color(0xFF0F172A)
val AccentGradientStart = Color(0xFFF59E0B)
val AccentGradientEnd = Color(0xFFEF4444)

// Card/Surface
val CardDark = Color(0xFF1A2332)
val CardDarkElevated = Color(0xFF1F2B3D)
val DividerDark = Color(0xFF2D3B4E)

private val DarkColorScheme = darkColorScheme(
    primary = GoldenAmber,
    onPrimary = DeepOcean,
    primaryContainer = DeepAmber,
    onPrimaryContainer = WarmGold,
    secondary = InfoBlue,
    onSecondary = PureWhite,
    secondaryContainer = SoftBlue,
    onSecondaryContainer = InfoBlue,
    tertiary = ActiveGreen,
    onTertiary = DeepOcean,
    background = DeepOcean,
    onBackground = PureWhite,
    surface = DarkNavy,
    onSurface = CloudWhite,
    surfaceVariant = MidnightBlue,
    onSurfaceVariant = SilverMist,
    outline = SlateBlue,
    outlineVariant = DividerDark,
    error = BlockedRed,
    onError = PureWhite,
    errorContainer = SoftRed,
    onErrorContainer = BlockedRed,
)

// ── Typography ──

val InterFamily = FontFamily.SansSerif
val OutfitFamily = FontFamily.SansSerif

val UlyssesTypography = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = Typography().displayMedium.copy(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = Typography().displaySmall.copy(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    ),
    labelMedium = Typography().labelMedium.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
)

@Composable
fun UlyssesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = UlyssesTypography,
        content = content
    )
}
