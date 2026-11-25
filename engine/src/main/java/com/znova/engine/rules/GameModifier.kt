package com.znova.engine.rules

import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.EventDef
import com.znova.engine.core.StatKey
import com.znova.engine.state.LifeState

interface GameModifier {
    fun eventWeight(
        state: LifeState,
        event: EventDef,
        weight: Double,
    ): Double = weight

    fun checkBonus(
        state: LifeState,
        check: SkillCheck,
    ): Int = 0

    fun statDelta(
        state: LifeState,
        stat: StatKey,
        delta: Int,
    ): Int = delta

    fun activitySlots(
        state: LifeState,
        slots: Int,
    ): Int = slots

    fun choiceVisible(
        state: LifeState,
        choice: ChoiceDef,
        visible: Boolean,
    ): Boolean = visible

    fun choiceEnabled(
        state: LifeState,
        choice: ChoiceDef,
        enabled: Boolean,
    ): Boolean = enabled
}
