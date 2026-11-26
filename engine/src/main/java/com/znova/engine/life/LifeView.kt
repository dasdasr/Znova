package com.znova.engine.life

import com.znova.engine.core.ActivityId
import com.znova.engine.core.EventId
import com.znova.engine.core.NpcId
import com.znova.engine.core.RoleKey
import com.znova.engine.core.SkillKey
import com.znova.engine.core.TagKey
import com.znova.engine.state.Tier
import com.znova.engine.state.WorldDate

data class BonusPart(
    val source: String,
    val amount: Int,
)

/** Chance formula: skill + bonuses + modifier + memory vs difficulty. */
data class ChanceBreakdown(
    val skill: SkillKey,
    val difficulty: Int,
    val skillLevel: Int,
    val bonuses: List<BonusPart>,
    val modifierBonus: Int,
    val memoryBonus: Int,
    val chancePercent: Int,
)

data class WhyNot(
    val reason: Reject,
    val detail: String? = null,
)

data class LossPreview(
    val progressLost: Int,
    val progressKept: Int,
    val attitudeLoss: Int,
    val participants: List<NpcId>,
    val restartCooldownMonths: Int,
    val investedMoney: Long,
)

data class ReplaceOption(
    val pursuit: String,
    val loss: LossPreview,
    val canStartAfter: Boolean,
    val whyNot: List<WhyNot>,
)

data class StartOptionView(
    val activity: ActivityId,
    val label: String,
    val category: String,
    val slotCost: Int,
    val moneyCost: Long,
    val monthlyCost: Long,
    val durationMonths: Int?,
    val opportunity: String?,
    val source: String?,
    val rarity: Rarity?,
    val monthsLeft: Int?,
    val expiringSoon: Boolean,
    val participant: NpcId?,
    val participantSlots: List<ParticipantReq>,
    val available: Boolean,
    val whyNot: List<WhyNot>,
    val chance: ChanceBreakdown?,
    val replace: List<ReplaceOption>,
)

data class PursuitView(
    val uid: String,
    val activity: ActivityId,
    val label: String,
    val slots: Int,
    val progress: Int,
    val durationMonths: Int?,
    val monthsLeft: Int?,
    val participants: Map<RoleKey, NpcId>,
    val openSlots: List<ParticipantReq>,
    val monthlyCost: Long,
    val chance: ChanceBreakdown?,
    val leaveLoss: LossPreview,
)

data class PersonView(
    val id: NpcId,
    val name: String,
    val role: RoleKey,
    val attitude: Int,
    val tags: Set<TagKey>,
    val present: Boolean,
    val salience: Int,
    val memories: List<MemoryEntry>,
    val canInteract: Map<InteractKind, Boolean>,
)

data class IntentView(
    val activity: ActivityId,
    val label: String,
    val opportunity: String?,
    val executeMonth: Int,
    val monthsLeft: Int,
)

/** Choice ID depends on decision kind. */
data class ChoiceView(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val lockedHint: String?,
    val chance: ChanceBreakdown?,
    val loss: LossPreview? = null,
    val activity: ActivityId? = null,
)

data class DecisionPrompt(
    val uid: String,
    val kind: DecisionKind,
    val event: EventId?,
    val tier: Tier,
    val text: String,
    val choices: List<ChoiceView>,
    val participants: List<NpcId>,
    val offered: StartOptionView?,
)

data class LifeView(
    val month: Int,
    val date: WorldDate,
    val ageYears: Int,
    val ended: Boolean,
    val slotsTotal: Int,
    val slotsFree: Int,
    val pursuits: List<PursuitView>,
    val opportunities: List<StartOptionView>,
    val initiatives: List<StartOptionView>,
    val people: List<PersonView>,
    val significant: List<PersonView>,
    val feed: List<FeedEntry>,
    val decision: DecisionPrompt?,
    val queued: Int,
    val intent: IntentView?,
    val lastStop: Stop?,
)
