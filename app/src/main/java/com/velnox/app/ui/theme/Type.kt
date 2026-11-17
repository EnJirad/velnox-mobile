package com.velnox.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// §8 Type scale: 11 / 12 / 14 / 16 / 18 / 20 / 24 / 30.
// 14sp is the default body size for the whole system. Nothing below 11sp.
// Font stack is Inter + Noto Sans Thai/Myanmar; Android resolves the
// Noto CJK/Thai system faces through FontFamily.Default, so no font
// binaries are bundled in this first slice.

private val stacked = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val Text2xs = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.2.sp,
)

val TextXs = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val TextSm = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

val TextBase = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)

val TextLg = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 26.sp,
)

val TextXl = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
)

val Text2xl = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.3).sp,
)

val Text3xl = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.6).sp,
)

val VelnoxTypography = Typography(
    displayLarge = Text3xl.copy(lineHeightStyle = stacked),
    headlineLarge = Text3xl.copy(lineHeightStyle = stacked),
    headlineMedium = Text2xl.copy(lineHeightStyle = stacked),
    headlineSmall = TextXl.copy(lineHeightStyle = stacked),
    titleLarge = TextLg.copy(lineHeightStyle = stacked),
    titleMedium = TextBase.copy(fontWeight = FontWeight.SemiBold, lineHeightStyle = stacked),
    titleSmall = TextSm.copy(fontWeight = FontWeight.SemiBold, lineHeightStyle = stacked),
    bodyLarge = TextBase.copy(lineHeightStyle = stacked),
    bodyMedium = TextSm.copy(lineHeightStyle = stacked),
    bodySmall = TextXs.copy(lineHeightStyle = stacked),
    labelLarge = TextSm.copy(fontWeight = FontWeight.Medium, lineHeightStyle = stacked),
    labelMedium = TextXs.copy(fontWeight = FontWeight.Medium, lineHeightStyle = stacked),
    labelSmall = Text2xs.copy(lineHeightStyle = stacked),
)
