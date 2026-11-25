package com.tag.routine.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tag.routine.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

const val TABULAR_NUMBERS_FEATURE = "tnum"

fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = TABULAR_NUMBERS_FEATURE)

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Modern typography system with clear typographic hierarchy:
 * - Display: Large numeric counters, hero metrics, celebratory callouts
 * - Headline & Title: Section headers, card titles, dialog headings
 * - Body: Primary and secondary narrative and explanatory content
 * - Label: Actionable chips, tags, navigation indicators, and metadata pills
 *
 * Font fallback to Android system fonts preserves clean Persian/Arabic and RTL rendering.
 */
val TagTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
        lineHeightStyle = lineHeightStyle,
    ),
    displayMedium = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
        lineHeightStyle = lineHeightStyle,
    ),
    displaySmall = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineLarge = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = lineHeightStyle,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.5.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        lineHeightStyle = lineHeightStyle,
    ),
)

/**
 * Modern shapes with larger, smoother rounded corners for an elevated and soft tactile aesthetic.
 */
val TagShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
