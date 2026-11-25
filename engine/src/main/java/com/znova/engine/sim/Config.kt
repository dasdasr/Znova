package com.znova.engine.sim

import com.znova.engine.core.Rng
import com.znova.engine.core.StatKey
import com.znova.engine.state.LifeState
import com.znova.engine.state.Tier
import kotlin.math.exp
import kotlin.math.pow

data class TierBudget(
    val major: Int,
    val medium: Int,
    val minor: Int,
    val majorChance: Double = 0.6,
) {
    fun roll(
        tier: Tier,
        rng: Rng,
    ): Int =
        when (tier) {
            Tier.MAJOR -> (0 until major).count { rng.chance(majorChance) }
            Tier.MEDIUM -> medium
            Tier.MINOR -> minor
        }
}

data class PacingBand(
    val untilYears: Int,
    val lengthMonths: Int,
    val budget: TierBudget,
    val slots: Int,
)

class Pacing(
    private val bands: List<PacingBand>,
) {
    fun bandFor(ageYears: Int): PacingBand = bands.firstOrNull { ageYears < it.untilYears } ?: bands.last()
}

fun interface MortalityModel {
    fun deathChance(
        state: LifeState,
        months: Int,
    ): Double
}

class GompertzMortality(
    private val vital: StatKey,
) : MortalityModel {
    override fun deathChance(
        state: LifeState,
        months: Int,
    ): Double {
        val years = state.character.ageMonths / 12.0
        val body = state.character.stat(vital)
        val bodyFactor = (2.0 - body / 50.0).coerceIn(0.4, 3.0)
        val perYear = (0.00005 * exp(0.1 * years) * bodyFactor).coerceIn(0.0, 0.95)
        return 1.0 - (1.0 - perYear).pow(months / 12.0)
    }
}

data class EngineConfig(
    val pacing: Pacing,
    val mortality: MortalityModel,
    val defaultStartYears: IntRange,
    val legacySlots: Int = 3,
    val thoughtChance: Double = 0.7,
    val observerChance: Double = 0.15,
    val maxPastLives: Int = 10,
    val defaultTitle: String = "The Untitled",
    val highlightsInSummary: Int = 8,
)
