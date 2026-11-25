package com.znova.presentation.ui.map

import androidx.compose.ui.graphics.Color
import com.znova.ui.map.GeoBox

data class MapAssets(
    val dir: String,
    val worldBox: GeoBox,
    val palette: MapPalette,
)

data class MapPalette(
    val colors: List<Long>,
    val overrides: Map<String, Long> = emptyMap(),
) {
    fun color(id: String?): Long {
        if (id == null) return colors.first()
        return overrides[id]
            ?: colors[(id.hashCode() and Int.MAX_VALUE) % colors.size]
    }
}

object CountryPalette {
    private val colors =
        listOf(
            0xFF7A55EE,
            0xFFB23A30,
            0xFF2F7F6F,
            0xFFC98A1B,
            0xFF2E6DB4,
            0xFF8A4F7D,
            0xFFD05A8B,
            0xFF668C32,
            0xFF1596A3,
            0xFFD16B32,
            0xFF5966A8,
            0xFF747474,
        ).map { Color(it) }
    private val overrides = mapOf<String, Color>() // E.g. "litoria" to Color(0xFF...)

    fun color(id: String?): Color =
        if (id == null) colors[0] else overrides[id] ?: colors[(id.hashCode() and Int.MAX_VALUE) % colors.size]
}
