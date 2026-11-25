package com.znova.presentation.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class PackTheme(
    val paper: Color = Color(0xFFEFE6D2),
    val card: Color = Color(0xFFF8F2E4),
    val ink: Color = Color(0xFF29241F),
    val muted: Color = Color(0xFF756B5E),
    val accent: Color = Color(0xFFA6402F),
    val good: Color = Color(0xFF59694A),
    val bad: Color = Color(0xFFA6402F),
    val titleFont: FontFamily = FontFamily.Serif,
    val bodyFont: FontFamily = FontFamily.Serif,
    val labelFont: FontFamily = FontFamily.Monospace,
    val sheetCorner: Dp = 18.dp,
    val tabCorner: Dp = 10.dp,
    val cardCorner: Dp = 8.dp,
    val chipCorner: Dp = 4.dp,
)
