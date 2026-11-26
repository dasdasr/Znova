@file:Suppress("TooManyFunctions", "LongParameterList", "ReturnCount", "CyclomaticComplexMethod", "LongMethod")

package com.znova.engine.life

import com.znova.engine.content.isAvailable
import com.znova.engine.core.ActivityId
import com.znova.engine.core.NpcId
import com.znova.engine.core.Rng
import com.znova.engine.core.RoleKey
import com.znova.engine.rules.Bindings
import com.znova.engine.rules.CheckMath
import com.znova.engine.rules.CheckResult
import com.znova.engine.rules.Effect
import com.znova.engine.rules.SkillCheck
import com.znova.engine.rules.TextRenderer
import com.znova.engine.sim.GameEngine
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc
import com.znova.engine.state.SeenRecord
import com.znova.engine.state.Tier
import com.znova.engine.state.mapCharacter
import kotlin.math.abs

internal data class StartPlan(
    val def: LifeActivityDef,
    val opportunity: Opportunity?,
    val bindings: Bindings,
)

internal sealed interface Planning {
    data class Ok(val plan: StartPlan) : Planning

    data class No(
        val reason: Reject,
        val detail: String? = null,
    ) : Planning
}

internal sealed interface InteractPlan {
    data class Ok(
        val def: InteractionDef,
        val bindings: Bindings,
        val key: String,
    ) : InteractPlan

    data class No(val reason: Reject) : InteractPlan
}

/** Rules of the monthly life cycle. */
internal class LifeCore(
    val content: LifeContent,
    val config: LifeConfig,
    val legacy: GameEngine,
) {
    private val pack get() = content.pack

    fun render(
        text: String,
        state: LifeState,
        bindings: Bindings = Bindings.EMPTY,
    ): String = TextRenderer.render(text, state, pack, bindings)

    fun slotsTotal(state: LifeState): Int {
        val base = config.slotsFor(state.character.ageYears)
        return legacy.modifiersFor(state).fold(base) { acc, m -> m.activitySlots(state, acc) }
    }

    fun slotsUsed(state: LifeState): Int = state.mon().pursuits.sumOf { it.slots }

    fun slotsFree(state: LifeState): Int = (slotsTotal(state) - slotsUsed(state)).coerceAtLeast(0)

    fun withScene(
        state: LifeState,
        scene: Bindings,
    ): LifeState = state.mapMonthly { it.copy(scene = scene) }

   fun runEffects(
        state: LifeState,
        effects: List<Effect>,
        rng: Rng,
        bindings: Bindings,
    ): Pair<LifeState, Bindings> {
        val previous = state.scene()
        var s = withScene(state, previous + bindings)
        s = legacy.applyAll(s, effects, rng, bindings)
        val scene = s.scene()
        return legacy.vitals(withScene(s, previous)) to scene
    }

    fun apply(
        state: LifeState,
        effects: List<Effect>,
        rng: Rng,
        bindings: Bindings,
    ): LifeState = runEffects(state, effects, rng, bindings).first

    fun markSeen(
        state: LifeState,
        key: String,
    ): LifeState {
        val rec = state.seen[key]
        val record = SeenRecord((rec?.count ?: 0) + 1, state.mon().month)
        return state.copy(seen = state.seen + (key to record))
    }

    fun changeAttitude(
        state: LifeState,
        id: NpcId,
        delta: Int,
    ): LifeState =
        state.copy(
            npcs = state.npcs.map {
                if (it.id == id) it.copy(attitude = (it.attitude + delta).coerceIn(-10, 10)) else it
            },
        )

    fun fits(
        state: LifeState,
        npc: Npc,
        req: ParticipantReq,
    ): Boolean =
        state.isPresent(npc) &&
            npc.role == req.role &&
            npc.attitude in req.minAttitude..req.maxAttitude &&
            (req.tag == null || req.tag in npc.tags)

    fun refuses(
        state: LifeState,
        npc: Npc,
    ): Boolean =
        state.memoryWeight(npc.id, MemoryTypes.ABANDONED, config.memoryWindowMonths) >= config.refuseAbandonWeight

   fun salience(
        state: LifeState,
        npc: Npc,
    ): Int {
        val m = state.mon()
        val recent = m.memory
            .filter { it.npc == npc.id && m.month - it.month <= config.memoryWindowMonths }
            .sumOf { it.weight }
        val inPursuit = if (m.pursuits.any { p -> npc.id in p.participants.slots.values }) 3 else 0
        val inOffer = if (m.opportunities.any { it.participant == npc.id }) 2 else 0
        return abs(npc.attitude) + recent + inPursuit + inOffer
    }

  fun pickParticipant(
        state: LifeState,
        req: ParticipantReq,
        taken: Collection<NpcId>,
    ): Npc? =
        state.npcs
            .filter { it.id !in taken && fits(state, it, req) }
            .maxWithOrNull(compareBy { salience(state, it) })

   fun bindRoles(
        state: LifeState,
        reqs: List<ParticipantReq>,
        preset: Bindings,
    ): Bindings? {
        var b = preset
        for (r in reqs) {
            val existing = b.slots[r.slot]
            if (existing != null) {
                if (state.isPresentId(existing)) continue
                if (r.required) return null
                b = b.without(setOf(r.slot))
                continue
            }
            val npc = pickParticipant(state, r, b.slots.values)
            if (npc != null) b = b.with(r.slot, npc.id) else if (r.required) return null
        }
        return b
    }

    fun hasUsableChoice(
        scene: LifeState,
        e: LifeEventDef,
    ): Boolean = e.choices.isEmpty() || e.choices.any { legacy.isVisible(scene, it) && legacy.isEnabled(scene, it) }

    fun eventEligible(
        state: LifeState,
        e: LifeEventDef,
        b: Bindings,
    ): Boolean {
        if (!e.isAvailable(state.unlocks)) return false
        val rec = state.seen[e.id.id]
        val month = state.mon().month
        if (rec != null && (month - rec.lastChapter <= e.cooldownMonths || rec.count >= e.maxPerLife)) return false
        val scene = withScene(state, b)
        return e.conditions.all { it.test(scene) } && hasUsableChoice(scene, e)
    }

    fun eventWeight(
        state: LifeState,
        e: LifeEventDef,
        b: Bindings,
    ): Double {
        val scene = withScene(state, b)
        var w = e.weight
        for (r in e.weightRules) if (r.condition.test(scene)) w *= r.multiplier
        val legacyDef = e.asLegacy()
        for (m in legacy.modifiersFor(scene)) w = m.eventWeight(scene, legacyDef, w)
        return w
    }

    fun memoryBonus(
        state: LifeState,
        b: Bindings,
    ): Int =
        b.slots.values
            .sumOf { id ->
                val good = state.memoryWeight(id, MemoryTypes.SHARED_SUCCESS, config.memoryWindowMonths)
                val bad = state.memoryWeight(id, MemoryTypes.ABANDONED, config.memoryWindowMonths) +
                    state.memoryWeight(id, MemoryTypes.SHARED_FAILURE, config.memoryWindowMonths)
                (good - bad).coerceIn(-1, 2)
            }.coerceIn(-3, 3)

    fun resolveCheck(
        state: LifeState,
        rng: Rng,
        check: SkillCheck,
        b: Bindings,
        memory: Int,
    ): CheckResult {
        val scene = withScene(state, b)
        val extra = legacy.modifiersFor(scene).sumOf { it.checkBonus(scene, check) } + memory
        return CheckMath.resolve(check, scene, extra, rng)
    }

    fun breakdown(
        state: LifeState,
        check: SkillCheck?,
        b: Bindings,
        memory: Int = 0,
    ): ChanceBreakdown? {
        check ?: return null
        val scene = withScene(state, b)
        val parts = check.bonuses.mapIndexedNotNull { i, bonus ->
            if (bonus.condition.test(scene)) BonusPart("bonus#$i", bonus.bonus) else null
        }
        val modifiers = legacy.modifiersFor(scene).sumOf { it.checkBonus(scene, check) }
        val level = scene.character.skill(check.skill)
        val total = level + parts.sumOf { it.amount } + modifiers + memory
        return ChanceBreakdown(
            check.skill,
            check.difficulty,
            level,
            parts,
            modifiers,
            memory,
            CheckMath.chancePercent(total, check.difficulty),
        )
    }

    private fun startRejection(
        state: LifeState,
        def: LifeActivityDef,
        opportunityUid: String?,
        opp: Opportunity?,
        freeSlots: Int,
    ): Planning.No? {
        val m = state.mon()
        return when {
            opportunityUid != null && (opp == null || opp.activity != def.id) ->
                Planning.No(Reject.UNKNOWN_OPPORTUNITY)
            opp == null && !def.initiative -> Planning.No(Reject.NOT_AVAILABLE)
            !def.base.isAvailable(state.unlocks) -> Planning.No(Reject.NOT_AVAILABLE)
            m.pursuits.any { it.activity == def.id } -> Planning.No(Reject.ALREADY_RUNNING)
            !def.repeatable && state.seenCount("done:${def.id.id}") > 0 -> Planning.No(Reject.NOT_REPEATABLE)
            (m.cooldowns[def.id.id] ?: 0) > m.month -> Planning.No(Reject.ON_COOLDOWN)
            def.base.slotCost > freeSlots -> Planning.No(Reject.NO_FREE_SLOT)
            else -> null
        }
    }

    fun participantProblem(
        state: LifeState,
        npc: Npc,
        req: ParticipantReq,
    ): Reject? =
        when {
            !state.isPresent(npc) -> Reject.NPC_NOT_PRESENT
            !fits(state, npc, req) -> Reject.PARTICIPANT_UNFIT
            refuses(state, npc) -> Reject.PARTICIPANT_REFUSES
            else -> null
        }

    fun planStart(
        state: LifeState,
        activity: ActivityId,
        opportunityUid: String?,
        requested: Map<RoleKey, NpcId>,
        freeSlots: Int = slotsFree(state),
    ): Planning {
        val def = content.activities[activity] ?: return Planning.No(Reject.UNKNOWN_ACTIVITY)
        val opp = opportunityUid?.let { uid -> state.mon().opportunities.firstOrNull { it.uid == uid } }
        startRejection(state, def, opportunityUid, opp, freeSlots)?.let { return it }

        var b = Bindings(requested)
        val oppSlot = opp?.slot
        val oppNpc = opp?.participant
        if (oppSlot != null && oppNpc != null) b = b.with(oppSlot, oppNpc)
        for (key in requested.keys) {
            if (def.participants.none { it.slot == key }) return Planning.No(Reject.SLOT_NOT_FOUND, key.id)
        }
        val used = HashSet<NpcId>()
        for (req in def.participants) {
            val id = b.slots[req.slot]
            if (id == null) {
                if (req.required) return Planning.No(Reject.PARTICIPANT_MISSING, req.slot.id)
                continue
            }
            val npc = state.npcs.firstOrNull { it.id == id } ?: return Planning.No(Reject.UNKNOWN_NPC, id.id)
            val problem = participantProblem(state, npc, req)
            if (problem != null) return Planning.No(problem, req.slot.id)
            if (!used.add(id)) return Planning.No(Reject.PARTICIPANT_UNFIT, req.slot.id)
        }
        val scene = withScene(state, b)
        return when {
            !def.base.conditions.all { it.test(scene) } -> Planning.No(Reject.CONDITIONS_NOT_MET)
            state.character.money < def.base.moneyCost -> Planning.No(Reject.NOT_ENOUGH_MONEY)
            else -> Planning.Ok(StartPlan(def, opp, b))
        }
    }

    fun startPursuit(
        state: LifeState,
        rng: Rng,
        plan: StartPlan,
    ): LifeState {
        val def = plan.def
        val month = state.mon().month
        val oppUid = plan.opportunity?.uid
        var s = state.mapMonthly { m -> m.copy(opportunities = m.opportunities.filter { it.uid != oppUid }) }
        s = s.mapCharacter { it.copy(money = it.money - def.base.moneyCost) }
        val (allocated, uid) = s.allocate("p")
        s = allocated
        val carried = s.mon().carried[def.id] ?: 0
        val pursuit = Pursuit(uid, def.id, def.base.slotCost, month, carried, plan.bindings, def.base.moneyCost)
        s = s.mapMonthly { it.copy(pursuits = it.pursuits + pursuit, carried = it.carried - def.id) }
        s = markSeen(s, "activity:${def.id.id}")
        for (id in plan.bindings.slots.values) {
            s = s.remember(MemoryEntry(id, MemoryTypes.INVITED, month, def.id.id))
            s = changeAttitude(s, id, config.inviteAttitudeGain)
        }
        s = s.withFeed(FeedEntry(month, Tier.MINOR, "", def.id.id, "pursuit.started"))
        return apply(s, def.onStart, rng, plan.bindings)
    }

    fun lossPreview(
        def: LifeActivityDef,
        p: Pursuit,
    ): LossPreview {
        val kept = p.progress * def.leave.keepPercent / 100
        return LossPreview(
            progressLost = p.progress - kept,
            progressKept = kept,
            attitudeLoss = def.leave.attitudeLoss,
            participants = p.participants.slots.values.toList(),
            restartCooldownMonths = def.leave.restartCooldownMonths,
            investedMoney = p.invested,
        )
    }

   fun leavePursuit(
        state: LifeState,
        rng: Rng,
        pursuit: Pursuit,
        voluntary: Boolean,
        kind: String,
    ): LifeState {
        val def = content.activities.getValue(pursuit.activity)
        val month = state.mon().month
        val keep = pursuit.progress * def.leave.keepPercent / 100
        var s = state.mapMonthly { m ->
            m.copy(
                pursuits = m.pursuits.filter { it.uid != pursuit.uid },
                carried = if (keep > 0) m.carried + (def.id to keep) else m.carried - def.id,
                cooldowns = if (voluntary && def.leave.restartCooldownMonths > 0) {
                    m.cooldowns + (def.id.id to month + def.leave.restartCooldownMonths)
                } else {
                    m.cooldowns
                },
            )
        }
        if (voluntary) {
            for (id in pursuit.participants.slots.values) {
                s = s.remember(MemoryEntry(id, MemoryTypes.ABANDONED, month, def.id.id))
                s = changeAttitude(s, id, -def.leave.attitudeLoss)
            }
        }
        s = s.withFeed(FeedEntry(month, Tier.MINOR, "", def.id.id, kind))
        return apply(s, def.leave.effects, rng, pursuit.participants)
    }

    fun fixOpportunity(
        state: LifeState,
        o: Opportunity,
    ): Opportunity? {
        val who = o.participant ?: return o
        if (state.isPresentId(who)) return o
        return if (o.onLost == LossRule.END) null else o.copy(participant = null, slot = null)
    }

   fun reviveDecision(
        state: LifeState,
        d: PendingDecision,
    ): PendingDecision? {
        when (d.kind) {
            DecisionKind.EVENT -> {
                val e = d.eventId?.let { content.events[it] } ?: return null
                var b = d.bindings
                val lost = e.roles.filter { r -> b.slots[r.slot]?.let { id -> !state.isPresentId(id) } == true }
                if (lost.isNotEmpty()) {
                    if (e.onParticipantLost == LossRule.END || lost.any { it.required }) return null
                    b = b.without(lost.map { it.slot }.toSet())
                }
                val scene = withScene(state, b)
                val valid = e.stillValid.all { it.test(scene) } && hasUsableChoice(scene, e)
                return if (valid) d.copy(bindings = b) else null
            }
            DecisionKind.OVERFLOW -> {
                val offer = d.offer ?: return null
                val fixed = fixOpportunity(state, offer) ?: return null
                return d.copy(offer = fixed)
            }
            DecisionKind.SACRIFICE -> return if (slotsUsed(state) > slotsTotal(state)) d else null
        }
    }

   fun pruneQueue(state: LifeState): LifeState {
        var s = state
        while (true) {
            val head = s.mon().pending.firstOrNull() ?: return s
            val fixed = reviveDecision(s, head)
            if (fixed != null) return s.mapMonthly { it.copy(pending = listOf(fixed) + it.pending.drop(1)) }
            val source = head.eventId?.id ?: head.kind.name
            s = s.mapMonthly { it.copy(pending = it.pending.drop(1)) }
                .withFeed(FeedEntry(s.mon().month, Tier.MINOR, "", source, "decision.dropped"))
        }
    }

    fun planInteract(
        state: LifeState,
        npc: Npc,
        kind: InteractKind,
    ): InteractPlan {
        if (!state.isPresent(npc)) return InteractPlan.No(Reject.NPC_NOT_PRESENT)
        val m = state.mon()
        val key = "${npc.id.id}:${kind.name}"
        val last = m.interactions[key]
        if (last != null && m.memory.none { it.npc == npc.id && it.month > last }) {
            return InteractPlan.No(Reject.NOTHING_NEW)
        }
        val b = Bindings(mapOf(LifeRoles.TARGET to npc.id, npc.role to npc.id))
        val scene = withScene(state, b)
        val def = content.interactions.firstOrNull { d ->
            d.kind == kind && (d.npcRole == null || d.npcRole == npc.role) && d.conditions.all { it.test(scene) }
        } ?: return InteractPlan.No(Reject.NOT_AVAILABLE)
        return InteractPlan.Ok(def, b, key)
    }
}
