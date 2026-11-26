package com.znova.engine.life

import com.znova.engine.content.ActivityDef
import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.ContentPack
import com.znova.engine.content.EventDef
import com.znova.engine.content.Unlockable
import com.znova.engine.content.WeightRule
import com.znova.engine.core.ActivityId
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.RoleKey
import com.znova.engine.core.SkillKey
import com.znova.engine.core.TagKey
import com.znova.engine.core.UnlockId
import com.znova.engine.core.ViewKey
import com.znova.engine.rules.Condition
import com.znova.engine.rules.Effect
import com.znova.engine.state.Tier

/** Requirement for an NPC filling a role (slot) in an activity, event or reaction. */
data class ParticipantReq(
    val slot: RoleKey,
    val npcRole: RoleKey? = null,
    val required: Boolean = false,
    val minAttitude: Int = -10,
    val maxAttitude: Int = 10,
    val tag: TagKey? = null,
) {
    /** The role the NPC itself must have. */
    val role: RoleKey get() = npcRole ?: slot
}

/** What leaving an activity costs. Unspent progress is partly kept and can be resumed after the cooldown. */
data class LeaveRule(
    val effects: List<Effect> = emptyList(),
    val keepPercent: Int = 0,
    val restartCooldownMonths: Int = 3,
    val attitudeLoss: Int = 0,
)

/** Describes an opportunity that something opens. */
data class OpenSpec(
    val activity: ActivityId,
    val rarity: Rarity = Rarity.COMMON,
    val ttlMonths: Int? = 6,
    val source: String = "",
    val slot: RoleKey? = null,
    val onLost: LossRule = LossRule.END,
    val text: String = "",
)

/**
 * Wraps the legacy ActivityDef: base.conditions = start conditions, base.moneyCost = one-time price,
 * base.check = the single check at the end, base.onSuccess / onFailure = completion effects.
 */
data class LifeActivityDef(
    val base: ActivityDef,
    val durationMonths: Int? = null,
    val initiative: Boolean = false,
    val continueWhen: List<Condition> = emptyList(),
    val repeatable: Boolean = true,
    val monthlyCost: Long = 0,
    val onUnpaid: UnpaidPolicy = UnpaidPolicy.PAUSE,
    val onStart: List<Effect> = emptyList(),
    val onMonth: List<Effect> = emptyList(),
    val leave: LeaveRule = LeaveRule(),
    val participants: List<ParticipantReq> = emptyList(),
    val onParticipantLost: LossRule = LossRule.CONTINUE,
    val opens: List<OpenSpec> = emptyList(),
    val intentWeight: Double = 1.0,
    val intentTags: Map<TagKey, Double> = emptyMap(),
    val intentViews: Map<ViewKey, Double> = emptyMap(),
    val intentRules: List<WeightRule> = emptyList(),
) {
    val id: ActivityId get() = base.id
}

/** weight = 0.0 means "only scheduled or raised by chains and NPCs". */
data class LifeEventDef(
    val id: EventId,
    val tier: Tier,
    val text: String,
    val roles: List<ParticipantReq> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val stillValid: List<Condition> = emptyList(),
    val weight: Double = 1.0,
    val weightRules: List<WeightRule> = emptyList(),
    val cooldownMonths: Int = 6,
    val maxPerLife: Int = Int.MAX_VALUE,
    val group: String? = null,
    val onTrigger: List<Effect> = emptyList(),
    val choices: List<ChoiceDef> = emptyList(),
    val onParticipantLost: LossRule = LossRule.END,
    val topics: Set<String> = emptySet(),
    override val requiresUnlock: UnlockId? = null,
) : Unlockable {
    fun asLegacy(): EventDef =
        EventDef(
            id = id,
            tier = tier,
            text = text,
            weight = weight,
            group = group,
            topics = topics,
            requiresUnlock = requiresUnlock,
        )
}

/** Source of found opportunities. Only COMMON and UNCOMMON: important ones come from events and chains. */
data class OpportunityGen(
    val id: String,
    val open: OpenSpec,
    val conditions: List<Condition> = emptyList(),
    val weight: Double = 1.0,
    val cooldownMonths: Int = 3,
    val participant: ParticipantReq? = null,
)

sealed interface NpcAct {
    data class Offer(val open: OpenSpec) : NpcAct

    data class Raise(val event: EventId) : NpcAct

    data class Apply(val effects: List<Effect>) : NpcAct

    data object Depart : NpcAct
}

/** An NPC proposes, asks, takes offence or leaves. In scope: SELF and the NPC's own role are bound to them. */
data class NpcReactionDef(
    val id: String,
    val who: ParticipantReq,
    val act: NpcAct,
    val conditions: List<Condition> = emptyList(),
    val weight: Double = 1.0,
    val weightRules: List<WeightRule> = emptyList(),
    val chance: Double = 0.2,
    val cooldownMonths: Int = 6,
    val text: String = "",
    val tier: Tier = Tier.MEDIUM,
)

/** The first interaction of the kind whose role and conditions fit wins. In scope: TARGET is the NPC. */
data class InteractionDef(
    val kind: InteractKind,
    val npcRole: RoleKey? = null,
    val conditions: List<Condition> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val remember: String? = null,
    val memoryWeight: Int = 1,
    val resultText: String = "",
)

data class ScenarioPerson(
    val role: RoleKey,
    val attitude: Int = 0,
    val tags: Set<TagKey> = emptySet(),
    val name: String? = null,
    val memories: List<ScenarioMemory> = emptyList(),
)

data class ScenarioMemory(
    val type: String,
    val monthsAgo: Int = 0,
    val weight: Int = 1,
)

data class ScenarioPast(
    val ageYears: Int,
    val tier: Tier,
    val text: String,
)

data class LifeScenario(
    val id: String,
    val startAgeYears: IntRange = 18..18,
    val skills: Map<SkillKey, Int> = emptyMap(),
    val views: Map<ViewKey, Int> = emptyMap(),
    val tags: Map<TagKey, Int> = emptyMap(),
    val flags: Set<FlagKey> = emptySet(),
    val money: Long? = null,
    val people: List<ScenarioPerson> = emptyList(),
    val past: List<ScenarioPast> = emptyList(),
    val effects: List<Effect> = emptyList(),
)

class LifeContent internal constructor(
    val pack: ContentPack,
    val activities: Map<ActivityId, LifeActivityDef>,
    val events: Map<EventId, LifeEventDef>,
    val opportunityGens: Map<String, OpportunityGen>,
    val reactions: Map<String, NpcReactionDef>,
    val interactions: List<InteractionDef>,
    val scenarios: Map<String, LifeScenario>,
    val upkeep: List<Effect>,
)

fun interface LifeModule {
    fun register(builder: LifeContentBuilder)
}

class LifeContentBuilder(val pack: ContentPack) {
    private val activities = LinkedHashMap<ActivityId, LifeActivityDef>()
    private val events = LinkedHashMap<EventId, LifeEventDef>()
    private val gens = LinkedHashMap<String, OpportunityGen>()
    private val reactions = LinkedHashMap<String, NpcReactionDef>()
    private val interactions = mutableListOf<InteractionDef>()
    private val scenarios = LinkedHashMap<String, LifeScenario>()
    private val upkeep = mutableListOf<Effect>()

    fun activity(a: LifeActivityDef) =
        apply { require(activities.put(a.id, a) == null) { "Duplicate life activity: ${a.id.id}" } }

    fun event(e: LifeEventDef) = apply { require(events.put(e.id, e) == null) { "Duplicate life event: ${e.id.id}" } }

    fun opportunity(g: OpportunityGen) =
        apply { require(gens.put(g.id, g) == null) { "Duplicate opportunity source: ${g.id}" } }

    fun reaction(r: NpcReactionDef) =
        apply { require(reactions.put(r.id, r) == null) { "Duplicate NPC reaction: ${r.id}" } }

    fun interaction(i: InteractionDef) = apply { interactions += i }

    fun scenario(s: LifeScenario) = apply { require(scenarios.put(s.id, s) == null) { "Duplicate scenario: ${s.id}" } }

    fun upkeep(e: Effect) = apply { upkeep += e }

    fun install(module: LifeModule) = apply { module.register(this) }

    fun build() =
        LifeContent(
            pack,
            activities.toMap(),
            events.toMap(),
            gens.toMap(),
            reactions.toMap(),
            interactions.toList(),
            scenarios.toMap(),
            upkeep.toList(),
        )
}
