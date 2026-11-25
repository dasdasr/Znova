package com.znova.presentation.ui.map

import com.znova.engine.content.PackMapUi
import com.znova.original.OriginalMapUi
import com.znova.original.OriginalTheme
import com.znova.ui.map.GeoBox

interface PackPresentation {
    val map: MapAssets?
    val theme: PackTheme
    val mapUi: PackMapUi
}

object OriginalPresentation : PackPresentation {
    override val theme = OriginalTheme

    override val mapUi: PackMapUi
        get() = OriginalMapUi.value

    override val map =
        MapAssets(
            dir = "packs/original/map",
            worldBox =
                GeoBox(
                    north = 65.0,
                    south = 40.0,
                    east = 32.0,
                    west = 10.0,
                ),
            palette =
                MapPalette(
                    colors =
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
                        ),
                ),
        )
}

val packPresentations: Map<String, PackPresentation> =
    mapOf(
        "original" to OriginalPresentation,
    )
