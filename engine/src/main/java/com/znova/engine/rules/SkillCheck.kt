package com.znova.engine.rules

import com.znova.engine.core.Rng
import com.znova.engine.core.SkillKey
import com.znova.engine.state.LifeState

data class ConditionalBonus(
    val condition: Condition,
    val bonus: Int,
)

data class SkillCheck(
    val skill: SkillKey,
    val difficulty: Int,
    val bonuses: List<ConditionalBonus> = emptyList(),
)

data class CheckResult(
    val skill: SkillKey,
    val difficulty: Int,
    val skillLevel: Int,
    val bonus: Int,
    val roll: Int,
    val total: Int,
    val success: Boolean,
)

object CheckMath {
    fun resolve(
        check: SkillCheck,
        state: LifeState,
        extraBonus: Int,
        rng: Rng,
    ): CheckResult {
        val level = state.character.skill(check.skill)
        val bonus = bonusFor(check, state) + extraBonus
        val roll = rng.nextInt(6) + 1 + rng.nextInt(6) + 1
        val total = roll + level + bonus
        val success =
            when (roll) {
                12 -> true
                2 -> false
                else -> total >= check.difficulty
            }
        return CheckResult(check.skill, check.difficulty, level, bonus, roll, total, success)
    }

    fun bonusFor(
        check: SkillCheck,
        state: LifeState,
    ): Int = check.bonuses.filter { it.condition.test(state) }.sumOf { it.bonus }

    fun chancePercent(
        modifier: Int,
        difficulty: Int,
    ): Int {
        var wins = 0
        for (a in 1..6) {
            for (b in 1..6) {
                val roll = a + b
                val ok =
                    when (roll) {
                        12 -> true
                        2 -> false
                        else -> roll + modifier >= difficulty
                    }
                if (ok) wins++
            }
        }
        return wins * 100 / 36
    }
}
