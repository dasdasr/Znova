@file:Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "NestedBlockDepth")

package com.znova.engine.life

import com.znova.engine.content.isAvailable
import com.znova.engine.core.EventId
import com.znova.engine.core.Rng
import com.znova.engine.rules.Bindings
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc
import com.znova.engine.state.Tier
import com.znova.engine.state.endLife
import com.znova.engine.state.mapCharacter

private class ReactionCandidate(
    val def: NpcReactionDef,
    val npc: Npc,
    val b: Bindings,
    val w: Double,
)

private class GenCandidate(
    val gen: OpportunityGen,
    val b: Bindings,
)

private class EventCandidate(
    val e: LifeEventDef,
    val b: Bindings,
    val w: Double,
)

private class IntentCandidate(
    val def: LifeActivityDef,
    val opp: String?,
    val w: Double,
)

/**
 * Runs one month in fixed steps. All randomness uses the state-restored RNG.
 * Death immediately aborts the remaining steps.
 */
internal class MonthRunner(
    private val core: LifeCore,
    start: LifeState,
) {
    private val rng = Rng(start.seed, start.rngDraws)
    private var s: LifeState = start
    private val reasons = LinkedHashSet<StopReason>()
    private var slotFreed = false
    private val config get() = core.config
    private val content get() = core.content
    private val month: Int get() = s.mon().month

    fun run(): TickResult {
        if (s.ending != null) return TickResult(s, false, Stop(s.mon().month, listOf(StopReason.DEATH)))
        s = core.pruneQueue(s)
        if (s.mon().pending.isNotEmpty()) {
            return TickResult(s, false, Stop(s.mon().month, listOf(StopReason.DECISION_NEEDED)))
        }
        runSteps()
        if (s.ending != null) {
            reasons += StopReason.DEATH
        } else {
            s = core.pruneQueue(s)
            if (s.mon().pending.isNotEmpty()) reasons += StopReason.DECISION_NEEDED
            if (slotFreed && core.slotsFree(s) > 0) reasons += StopReason.SLOT_FREED
        }
        val stop = if (reasons.isEmpty()) null else Stop(month, reasons.toList())
        s = s.mapMonthly { it.copy(lastStop = stop) }.copy(rngDraws = rng.draws)
        return TickResult(s, true, stop)
    }

    private fun runSteps(): Boolean =
        calendar() && income() && pursuits() && people() && opportunities() && events() && intent() && closing()

    private fun calendar(): Boolean {
        s = s.mapMonthly { m ->
            val next = m.month + 1
            m.copy(month = next, cooldowns = m.cooldowns.filterValues { until -> until > next })
        }
        s = s.copy(worldMonthsElapsed = s.worldMonthsElapsed + 1).mapCharacter { it.copy(ageMonths = it.ageMonths + 1) }
        val overfull = core.slotsUsed(s) > core.slotsTotal(s)
        if (overfull && s.mon().pending.none { it.kind == DecisionKind.SACRIFICE }) {
            val (allocated, uid) = s.allocate("d")
            s = allocated.mapMonthly {
                it.copy(pending = it.pending + PendingDecision(uid, DecisionKind.SACRIFICE, month = it.month))
            }
        }
        return s.ending == null
    }

    private fun income(): Boolean {
        s = core.apply(s, content.upkeep, rng, Bindings.EMPTY)
        return s.ending == null
    }

    private fun pursuits(): Boolean {
        for (uid in s.mon().pursuits.map { it.uid }) {
            val p = s.mon().pursuits.firstOrNull { it.uid == uid } ?: continue
            if (!stepPursuit(p)) return false
        }
        return true
    }

    private fun replace(p: Pursuit) {
        s = s.mapMonthly { m -> m.copy(pursuits = m.pursuits.map { if (it.uid == p.uid) p else it }) }
    }

    private fun forcedEnd(
        p: Pursuit,
        kind: String,
    ) {
        s = core.leavePursuit(s, rng, p, false, kind)
        slotFreed = true
    }

    private fun stepPursuit(start: Pursuit): Boolean {
        val def = content.activities.getValue(start.activity)
        var p = start
        val lost = p.participants.slots.filterValues { !s.isPresentId(it) }.keys
        if (lost.isNotEmpty()) {
            val required = def.participants.any { it.slot in lost && it.required }
            if (def.onParticipantLost == LossRule.END || required) {
                forcedEnd(p, "pursuit.partner_lost")
                return s.ending == null
            }
            p = p.copy(participants = p.participants.without(lost))
            replace(p)
        }
        val scene = core.withScene(s, p.participants)
        if (!def.continueWhen.all { it.test(scene) }) {
            forcedEnd(p, "pursuit.stopped")
            return s.ending == null
        }
        if (def.monthlyCost > 0) {
            val affordable = s.character.money >= def.monthlyCost || def.onUnpaid == UnpaidPolicy.DEBT
            if (affordable) {
                s = s.mapCharacter { it.copy(money = it.money - def.monthlyCost) }
            } else if (def.onUnpaid == UnpaidPolicy.DROP) {
                forcedEnd(p, "pursuit.unpaid")
                return s.ending == null
            } else {
                s = s.withFeed(FeedEntry(month, Tier.MINOR, "", def.id.id, "pursuit.paused"))
                return true
            }
        }
        p = p.copy(progress = p.progress + 1)
        replace(p)
        s = core.apply(s, def.onMonth, rng, p.participants)
        if (s.ending != null) return false
        val duration = def.durationMonths
        if (duration != null && p.progress >= duration) complete(p, def)
        return s.ending == null
    }

    private fun complete(
        p: Pursuit,
        def: LifeActivityDef,
    ) {
        val bonus = core.memoryBonus(s, p.participants)
        val result = def.base.check?.let { core.resolveCheck(s, rng, it, p.participants, bonus) }
        val success = result?.success ?: true
        val effects = if (success) def.base.onSuccess else def.base.onFailure
        val text = if (success) def.base.resultText else (def.base.failText ?: def.base.resultText)
        s = s.mapMonthly { m -> m.copy(pursuits = m.pursuits.filter { it.uid != p.uid }) }
        s = core.markSeen(s, "done:${def.id.id}")
        val type = if (success) MemoryTypes.SHARED_SUCCESS else MemoryTypes.SHARED_FAILURE
        for (id in p.participants.slots.values) s = s.remember(MemoryEntry(id, type, month, def.id.id))
        s = core.apply(s, effects, rng, p.participants)
        slotFreed = true
        if (s.ending != null) return
        val kind = if (success) "pursuit.success" else "pursuit.failure"
        s = s.withFeed(
            FeedEntry(month, def.base.resultTier, core.render(text, s, p.participants), def.id.id, kind, result),
        )
        if (success) s = core.apply(s, def.opens.map { OpenOpportunity(it) }, rng, p.participants)
        if (def.base.resultTier == Tier.MAJOR) reasons += StopReason.MAJOR_EVENT
    }

    private fun people(): Boolean {
        repeat(config.maxNpcActionsPerMonth) {
            if (s.ending != null) return false
            reactOnce()
        }
        if (s.ending != null) return false
        for (o in s.mon().opportunities) {
            val fixed = core.fixOpportunity(s, o)
            if (fixed == o) continue
            s = s.mapMonthly { m ->
                m.copy(opportunities = m.opportunities.mapNotNull { if (it.uid == o.uid) fixed else it })
            }
            if (fixed == null) s = s.withFeed(FeedEntry(month, Tier.MINOR, "", o.activity.id, "opportunity.lost"))
        }
        return true
    }

    private fun reactOnce() {
        val m = s.mon()
        val cands = ArrayList<ReactionCandidate>()
        for (def in content.reactions.values) {
            for (npc in s.npcs) {
                if (!core.fits(s, npc, def.who)) continue
                if ((m.cooldowns["react:${def.id}:${npc.id.id}"] ?: 0) > m.month) continue
                val b = Bindings(mapOf(def.who.slot to npc.id))
                val scene = core.withScene(s, b)
                if (!def.conditions.all { it.test(scene) }) continue
                var w = def.weight
                for (r in def.weightRules) if (r.condition.test(scene)) w *= r.multiplier
                if (w > 0.0) cands += ReactionCandidate(def, npc, b, w)
            }
        }
        val pick = rng.weighted(cands) { it.w } ?: return
        if (!rng.chance(pick.def.chance)) return
        val def = pick.def
        val until = m.month + def.cooldownMonths
        s = s.mapMonthly { it.copy(cooldowns = it.cooldowns + ("react:${def.id}:${pick.npc.id.id}" to until)) }
        val kind: String
        when (val act = def.act) {
            is NpcAct.Offer -> {
                kind = "npc.offer"
                s = core.apply(s, listOf(OpenOpportunity(act.open)), rng, pick.b)
            }
            is NpcAct.Raise -> {
                kind = "npc.raise"
                raise(act.event, pick.b)
            }
            is NpcAct.Apply -> {
                kind = "npc.apply"
                s = core.apply(s, act.effects, rng, pick.b)
            }
            NpcAct.Depart -> {
                kind = "npc.depart"
                s = s.mapMonthly { it.copy(away = it.away + pick.npc.id) }
            }
        }
        if (s.ending != null) return
        val offerWithText = def.act is NpcAct.Offer && def.act.open.text.isNotBlank()
        val text = core.render(def.text, s, pick.b)
        if (!offerWithText && (text.isNotBlank() || def.tier == Tier.MAJOR)) {
            s = s.withFeed(FeedEntry(m.month, def.tier, text, def.id, kind))
        }
        if (def.tier == Tier.MAJOR) reasons += StopReason.MAJOR_EVENT
    }

    private fun raise(
        id: EventId,
        preset: Bindings,
    ) {
        val e = content.events[id] ?: return
        val b = core.bindRoles(s, e.roles, preset) ?: return
        val scene = core.withScene(s, b)
        if (!e.conditions.all { it.test(scene) } || !core.hasUsableChoice(scene, e)) return
        triggerEvent(e, b)
    }

    private fun triggerEvent(
        e: LifeEventDef,
        b: Bindings,
    ) {
        s = core.markSeen(s, e.id.id)
        val (applied, scene) = core.runEffects(s, e.onTrigger, rng, b)
        s = applied
        if (s.ending != null) return
        if (e.choices.isEmpty()) {
            s = s.withFeed(FeedEntry(month, e.tier, core.render(e.text, s, scene), e.id.id, "event"))
            if (e.tier == Tier.MAJOR) reasons += StopReason.MAJOR_EVENT
        } else {
            val (allocated, uid) = s.allocate("d")
            s = allocated.mapMonthly {
                it.copy(pending = it.pending + PendingDecision(uid, DecisionKind.EVENT, e.id, scene, it.month))
            }
        }
    }

    private fun opportunities(): Boolean {
        val m = s.mon()
        for (o in m.opportunities) {
            val expires = o.expiresMonth ?: continue
            if (m.month >= expires) {
                s = s.mapMonthly { st -> st.copy(opportunities = st.opportunities.filter { it.uid != o.uid }) }
                val tier = if (o.rarity == Rarity.RARE) Tier.MEDIUM else Tier.MINOR
                s = s.withFeed(FeedEntry(m.month, tier, "", o.activity.id, "opportunity.expired"))
            } else if (o.rarity == Rarity.RARE && !o.warned && expires - m.month <= config.warnMonths) {
                s = s.mapMonthly { st ->
                    st.copy(opportunities = st.opportunities.map { if (it.uid == o.uid) it.copy(warned = true) else it })
                }
                s = s.withFeed(FeedEntry(m.month, Tier.MEDIUM, "", o.activity.id, "opportunity.expiring"))
                reasons += StopReason.RARE_EXPIRING
            }
        }
        val room = s.mon().opportunities.size < s.mon().limits.opportunities
        if (room && rng.chance(config.opportunityChance)) generate()
        return true
    }

    private fun generate() {
        val m = s.mon()
        val cands = ArrayList<GenCandidate>()
        for (g in content.opportunityGens.values) {
            val act = content.activities[g.open.activity] ?: continue
            if ((m.cooldowns["gen:${g.id}"] ?: 0) > m.month) continue
            if (!act.base.isAvailable(s.unlocks)) continue
            if (m.pursuits.any { it.activity == g.open.activity }) continue
            if (m.opportunities.any { it.activity == g.open.activity }) continue
            var b = Bindings.EMPTY
            val req = g.participant
            if (req != null) {
                val npc = core.pickParticipant(s, req, emptyList()) ?: continue
                b = b.with(req.slot, npc.id)
            }
            if (g.conditions.all { it.test(core.withScene(s, b)) }) cands += GenCandidate(g, b)
        }
        val pick = rng.weighted(cands) { it.gen.weight } ?: return
        val until = m.month + pick.gen.cooldownMonths
        s = s.mapMonthly { it.copy(cooldowns = it.cooldowns + ("gen:${pick.gen.id}" to until)) }
        s = core.apply(s, listOf(OpenOpportunity(pick.gen.open)), rng, pick.b)
    }

    private fun events(): Boolean {
        val m = s.mon()
        val picked = HashSet<EventId>()
        val groups = HashSet<String>()
        val due = m.scheduled.filter { it.dueMonth <= m.month }
        s = s.mapMonthly { st -> st.copy(scheduled = st.scheduled.filter { it.dueMonth > m.month }) }
        for (d in due) {
            if (s.ending != null) return false
            val e = content.events[d.event] ?: continue
            val b = core.bindRoles(s, e.roles, d.bindings)
            val scene = b?.let { core.withScene(s, it) }
            val ok = b != null && scene != null && e.conditions.all { it.test(scene) } && core.hasUsableChoice(scene, e)
            if (b != null && ok) {
                picked += e.id
                e.group?.let { groups += it }
                triggerEvent(e, b)
            } else if (d.policy == MissedPolicy.POSTPONE && d.postponeLeft > 0) {
                val again = d.copy(dueMonth = m.month + 1, postponeLeft = d.postponeLeft - 1)
                s = s.mapMonthly { st -> st.copy(scheduled = st.scheduled + again) }
            }
        }
        for (tier in Tier.entries) {
            if (s.ending != null) return false
            if (rng.chance(config.eventRates.of(tier))) pickRandomEvent(tier, picked, groups)
        }
        return s.ending == null
    }

    private fun pickRandomEvent(
        tier: Tier,
        picked: MutableSet<EventId>,
        groups: MutableSet<String>,
    ) {
        val cands = ArrayList<EventCandidate>()
        for (e in content.events.values) {
            if (e.tier != tier || e.id in picked || e.weight <= 0.0) continue
            if (e.group != null && e.group in groups) continue
            val b = core.bindRoles(s, e.roles, Bindings.EMPTY) ?: continue
            if (!core.eventEligible(s, e, b)) continue
            val w = core.eventWeight(s, e, b)
            if (w > 0.0) cands += EventCandidate(e, b, w)
        }
        val pick = rng.weighted(cands) { it.w } ?: return
        picked += pick.e.id
        pick.e.group?.let { groups += it }
        triggerEvent(pick.e, pick.b)
    }

    private fun intent(): Boolean {
        val m = s.mon()
        val current = m.intent
        if (current != null) {
            if (m.month >= current.executeMonth) executeIntent(current)
            return s.ending == null
        }
        if (m.pending.isNotEmpty() || core.slotsFree(s) <= 0) return true
        if (rng.chance(config.intentChance)) createIntent()
        return true
    }

    private fun executeIntent(intent: Intent) {
        s = s.mapMonthly { it.copy(intent = null) }
        val plan = core.planStart(s, intent.activity, intent.opportunity, emptyMap())
        if (plan is Planning.Ok) {
            s = core.startPursuit(s, rng, plan.plan)
            s = s.withFeed(FeedEntry(month, Tier.MINOR, "", intent.activity.id, "intent.started"))
        } else {
            s = s.withFeed(FeedEntry(month, Tier.MINOR, "", intent.activity.id, "intent.dropped"))
        }
    }

    private fun intentWeight(def: LifeActivityDef): Double {
        var w = def.intentWeight
        for ((tag, f) in def.intentTags) w *= (1.0 + f * s.character.tagLevel(tag)).coerceAtLeast(MIN_FACTOR)
        for ((view, f) in def.intentViews) w *= (1.0 + f * s.character.view(view)).coerceAtLeast(MIN_FACTOR)
        for (r in def.intentRules) if (r.condition.test(s)) w *= r.multiplier
        return w.coerceAtLeast(0.0)
    }

    private fun createIntent() {
        val m = s.mon()
        val cands = ArrayList<IntentCandidate>()
        for (def in content.activities.values) {
            val uids = ArrayList<String?>()
            if (def.initiative) uids += null
            m.opportunities.filter { it.activity == def.id }.forEach { uids += it.uid }
            for (uid in uids) {
                if (core.planStart(s, def.id, uid, emptyMap()) is Planning.Ok) {
                    cands += IntentCandidate(def, uid, intentWeight(def))
                }
            }
        }
        if (cands.isEmpty()) return
        val impulsive = rng.chance(config.impulsiveChance)
        val pick = (if (impulsive) rng.pick(cands) else rng.weighted(cands) { it.w }) ?: return
        val intent = Intent(pick.def.id, pick.opp, m.month, m.month + config.intentDelayMonths, true)
        s = s.mapMonthly { it.copy(intent = intent) }
            .withFeed(FeedEntry(m.month, Tier.MINOR, "", pick.def.id.id, "intent.announced"))
        reasons += StopReason.NEW_INTENT
    }

    private fun closing(): Boolean {
        s = core.legacy.vitals(s)
        if (s.ending == null) s = core.legacy.updateTrackers(s)
        if (s.ending == null && rng.chance(config.base.mortality.deathChance(s, 1))) s = s.endLife("natural")
        return s.ending == null
    }

    private companion object {
        const val MIN_FACTOR = 0.05
    }
}
