package com.znova.engine.sim

import com.znova.engine.core.Rng
import com.znova.engine.core.Stats
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

    companion object {
        val DEFAULT =
            Pacing(
                listOf(
                    PacingBand(8, 24, TierBudget(1, 1, 2), slots = 1),
                    PacingBand(18, 12, TierBudget(1, 2, 2), slots = 1),
                    PacingBand(40, 9, TierBudget(1, 2, 3), slots = 2),
                    PacingBand(Int.MAX_VALUE, 12, TierBudget(1, 2, 3), slots = 2),
                ),
            )
    }
}

fun interface MortalityModel {
    fun deathChance(
        state: LifeState,
        months: Int,
    ): Double
}

object GompertzMortality : MortalityModel {
    override fun deathChance(
        state: LifeState,
        months: Int,
    ): Double {
        val years = state.character.ageMonths / 12.0
        val body = state.character.stat(Stats.BODY)
        val bodyFactor = (2.0 - body / 50.0).coerceIn(0.4, 3.0)
        val perYear = (0.00005 * exp(0.1 * years) * bodyFactor).coerceIn(0.0, 0.95)
        return 1.0 - (1.0 - perYear).pow(months / 12.0)
    }
}

data class EngineConfig(
    val pacing: Pacing = Pacing.DEFAULT,
    val mortality: MortalityModel = GompertzMortality,
    val legacySlots: Int = 3,
    val startSkillPoints: Int = 6,
    val startStat: Int = 60,
    val thoughtChance: Double = 0.7,
    val observerChance: Double = 0.15,
    val maxPastLives: Int = 10,
    val defaultTitle: String = "The Untitled",
    val highlightsInSummary: Int = 8,
)
