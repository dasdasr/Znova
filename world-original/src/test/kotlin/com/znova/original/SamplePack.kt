package com.znova.original

import com.znova.engine.SampleModule
import com.znova.engine.sim.EngineConfig
import com.znova.engine.sim.GompertzMortality
import com.znova.engine.sim.Pacing
import com.znova.engine.sim.PacingBand
import com.znova.engine.sim.TierBudget
import com.znova.engine.world.WorldPack

object SamplePack : WorldPack {
    override val id = "sample"
    override val displayName = "Sample"
    override val version = 1

    override val schema = OriginalSchema

    override val config =
        EngineConfig(
            pacing =
                Pacing(
                    listOf(
                        PacingBand(
                            untilYears = Int.MAX_VALUE,
                            lengthMonths = 12,
                            budget =
                                TierBudget(
                                    major = 1,
                                    medium = 2,
                                    minor = 3,
                                ),
                            slots = 2,
                        ),
                    ),
                ),
            mortality = GompertzMortality(schema.vitalStat),
            defaultStartYears = 1985..1989,
            legacySlots = 3,
            defaultTitle = "The Untitled",
        )

    override val modules =
        listOf(
            SampleModule,
            SampleMetaModule,
        )
}
