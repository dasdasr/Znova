package com.znova.original

import com.znova.engine.sim.EngineConfig
import com.znova.engine.sim.GompertzMortality
import com.znova.engine.world.WorldPack

object OriginalPack : WorldPack {
    override val id = "original"
    override val displayName = "Znova Original"
    override val version = 1
    override val schema = OriginalSchema
    override val config =
        EngineConfig(
            pacing = OriginalPacing,
            mortality = GompertzMortality(schema.vitalStat),
            defaultStartYears = 1985..1989,
            legacySlots = 3,
            defaultTitle = "The Untitled",
        )
    override val modules = listOf(OriginalWorld, BaseContent)
}
