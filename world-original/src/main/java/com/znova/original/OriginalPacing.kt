package com.znova.original

import com.znova.engine.sim.Pacing
import com.znova.engine.sim.PacingBand
import com.znova.engine.sim.TierBudget

val OriginalPacing =
    Pacing(
        listOf(
            PacingBand(8, 24, TierBudget(1, 1, 2), slots = 1),
            PacingBand(18, 12, TierBudget(1, 2, 2), slots = 1),
            PacingBand(40, 9, TierBudget(1, 2, 3), slots = 2),
            PacingBand(Int.MAX_VALUE, 12, TierBudget(1, 2, 3), slots = 2),
        ),
    )
