package com.znova.engine.life

import com.znova.engine.sim.EngineConfig
import com.znova.engine.state.Tier

data class TierRates(
    val major: Double,
    val medium: Double,
    val minor: Double,
) {
    fun of(tier: Tier): Double =
        when (tier) {
            Tier.MAJOR -> major
            Tier.MEDIUM -> medium
            Tier.MINOR -> minor
        }
}

data class SlotBand(
    val untilYears: Int,
    val slots: Int,
)

data class LifeConfig(
    val base: EngineConfig,
    val slotBands: List<SlotBand> = listOf(SlotBand(Int.MAX_VALUE, 3)),
    val eventRates: TierRates = TierRates(major = 0.03, medium = 0.14, minor = 0.3),
    val opportunityChance: Double = 0.25,
    val maxOpportunities: Int = 5,
    val warnMonths: Int = 1,
    val intentChance: Double = 0.2,
    val intentDelayMonths: Int = 1,
    val impulsiveChance: Double = 0.25,
    val maxNpcActionsPerMonth: Int = 1,
    val significantPeople: Int = 4,
    val memoryWindowMonths: Int = 24,
    val refuseAbandonWeight: Int = 4,
    val inviteAttitudeGain: Int = 1,
    val feedSize: Int = 40,
    val memorySize: Int = 80,
    val defaultStartAgeYears: IntRange = 18..18,
) {
    init {
        require(slotBands.isNotEmpty()) { "slotBands must not be empty" }
        require(intentDelayMonths >= 1) { "An intent cannot run earlier than in a month" }
        require(maxOpportunities >= 1) { "maxOpportunities must be positive" }
    }

    fun slotsFor(ageYears: Int): Int = (slotBands.firstOrNull { ageYears < it.untilYears } ?: slotBands.last()).slots

    fun limits(): MonthlyLimits = MonthlyLimits(maxOpportunities, feedSize, memorySize)
}
