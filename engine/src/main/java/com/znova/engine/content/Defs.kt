package com.znova.engine.content

import com.znova.engine.core.AchievementId
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.CountryId
import com.znova.engine.core.EventId
import com.znova.engine.core.FocusId
import com.znova.engine.core.LawKey
import com.znova.engine.core.LegacyId
import com.znova.engine.core.OriginId
import com.znova.engine.core.Rng
import com.znova.engine.core.SkillKey
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.rules.Condition
import com.znova.engine.rules.Effect
import com.znova.engine.rules.Modifier
import com.znova.engine.rules.SkillCheck
import com.znova.engine.state.Tier

interface Unlockable {
    val requiresUnlock: UnlockId?
}

fun Unlockable.isAvailable(unlocks: Set<UnlockId>): Boolean = requiresUnlock == null || requiresUnlock in unlocks

data class WeightRule(
    val condition: Condition,
    val multiplier: Double,
)

data class ChoiceDef(
    val id: ChoiceId,
    val label: String,
    val visibleWhen: List<Condition> = emptyList(),
    val enabledWhen: List<Condition> = emptyList(),
    val lockedHint: String? = null,
    val check: SkillCheck? = null,
    val onSuccess: List<Effect> = emptyList(),
    val onFailure: List<Effect> = emptyList(),
    val resultText: String = "",
    val failText: String? = null,
)

data class EventDef(
    val id: EventId,
    val tier: Tier,
    val text: String,
    val conditions: List<Condition> = emptyList(),
    val weight: Double = 1.0,
    val weightRules: List<WeightRule> = emptyList(),
    val cooldownChapters: Int = 4,
    val maxPerLife: Int = Int.MAX_VALUE,
    val group: String? = null,
    val onTrigger: List<Effect> = emptyList(),
    val choices: List<ChoiceDef> = emptyList(),
    val topics: Set<String> = emptySet(),
    override val requiresUnlock: UnlockId? = null,
) : Unlockable

data class ActivityDef(
    val id: ActivityId,
    val label: String,
    val category: String,
    val conditions: List<Condition> = emptyList(),
    val slotCost: Int = 1,
    val moneyCost: Long = 0,
    val check: SkillCheck? = null,
    val onSuccess: List<Effect> = emptyList(),
    val onFailure: List<Effect> = emptyList(),
    val resultText: String = "",
    val failText: String? = null,
    val resultTier: Tier = Tier.MINOR,
    override val requiresUnlock: UnlockId? = null,
) : Unlockable

data class ThoughtDef(
    val id: String,
    val skill: SkillKey,
    val text: String,
    val conditions: List<Condition> = emptyList(),
    val weight: Double = 1.0,
    val cooldownChapters: Int = 6,
    val effects: List<Effect> = emptyList(),
)

data class NamePool(
    val first: List<String>,
    val last: List<String>,
) {
    fun pick(rng: Rng): String = rng.pick(first) + " " + rng.pick(last)
}

data class CountryDef(
    val id: CountryId,
    val name: String,
    val laws: Set<LawKey>,
    val names: NamePool,
    val startYears: IntRange = 1985..1989,
    val weight: Double = 1.0,
    val onStart: List<Effect> = emptyList(),
    val modifiers: List<Modifier> = emptyList(),
    override val requiresUnlock: UnlockId? = null,
) : Unlockable

data class OriginDef(
    val id: OriginId,
    val name: String,
    val description: String,
    val weight: Double = 1.0,
    val conditions: List<Condition> = emptyList(),
    val effects: List<Effect> = emptyList(),
    override val requiresUnlock: UnlockId? = null,
) : Unlockable

data class LegacyDef(
    val id: LegacyId,
    val name: String,
    val description: String,
    val onStart: List<Effect> = emptyList(),
    val modifiers: List<Modifier> = emptyList(),
    override val requiresUnlock: UnlockId? = null,
) : Unlockable

data class AchievementDef(
    val id: AchievementId,
    val name: String,
    val hidden: Boolean,
    val condition: Condition,
    val unlocks: Set<UnlockId> = emptySet(),
    val hint: String? = null,
    val hintWhen: Condition? = null,
)

data class TitleDef(
    val text: String,
    val priority: Int,
    val conditions: List<Condition>,
)

enum class UnlockKind { START, CONTENT, MECHANIC, LEGACY }

data class UnlockDef(
    val id: UnlockId,
    val kind: UnlockKind,
    val name: String,
    val description: String = "",
)

data class TrackerDef(
    val id: TrackerId,
    val condition: Condition,
)

data class FocusDef(
    val id: FocusId,
    val name: String,
    val description: String = "",
    val topicWeights: Map<String, Double>,
    override val requiresUnlock: UnlockId? = null,
) : Unlockable

data class ObserverNoteDef(
    val id: String,
    val text: String,
    val conditions: List<Condition> = emptyList(),
    val weight: Double = 1.0,
    val cooldownChapters: Int = 8,
    override val requiresUnlock: UnlockId? = null,
) : Unlockable
