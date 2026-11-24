package com.znova.engine.sim

import com.znova.engine.core.*
import com.znova.engine.state.Card
import com.znova.engine.state.Phase
import com.znova.engine.state.Tier

data class ChapterView(
    val index: Int,
    val ageYears: Int,
    val lengthMonths: Int,
    val phase: Phase,
    val cards: List<Card>,
    val decisions: List<DecisionView>,
    val activities: List<ActivityView>,
    val slots: Int,
)

data class CheckPreview(val skill: SkillKey, val difficulty: Int, val chancePercent: Int)

data class DecisionView(val eventId: EventId, val tier: Tier, val text: String, val choices: List<ChoiceView>)

data class ChoiceView(
    val id: ChoiceId,
    val label: String,
    val enabled: Boolean,
    val lockedHint: String?,
    val check: CheckPreview?,
)

data class ActivityView(
    val id: ActivityId,
    val label: String,
    val category: String,
    val slotCost: Int,
    val moneyCost: Long,
    val enabled: Boolean,
    val check: CheckPreview?,
)
