@file:Suppress("TooManyFunctions", "LongMethod", "MagicNumber")

package com.znova.engine.life

import com.znova.engine.rules.Bindings
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc

internal class ViewBuilder(private val core: LifeCore) {
    private val config get() = core.config
    private val content get() = core.content

    fun build(state: LifeState): LifeView {
        val m = state.mon()
        val hidden = setOf(Reject.CONDITIONS_NOT_MET, Reject.NOT_AVAILABLE)
        val initiatives =
            content.activities.values
                .filter { it.initiative }
                .map { option(state, it, null, false) }
                .filter { o -> o.available || o.whyNot.none { it.reason in hidden } }
        val people = state.npcs.map { person(state, it) }
        val significant =
            people
                .filter { it.present && it.salience > 0 }
                .sortedByDescending { it.salience }
                .take(config.significantPeople)
        var decision: DecisionPrompt? = null
        var queued = 0
        for ((i, d) in m.pending.withIndex()) {
            val fixed = core.reviveDecision(state, d) ?: continue
            decision = prompt(state, fixed)
            queued = m.pending.size - i - 1
            break
        }
        return LifeView(
            month = m.month,
            date = state.currentDate,
            ageYears = state.character.ageYears,
            ended = state.ending != null,
            slotsTotal = core.slotsTotal(state),
            slotsFree = core.slotsFree(state),
            pursuits = m.pursuits.map { pursuit(state, it) },
            opportunities = m.opportunities.map { o -> option(state, content.activities.getValue(o.activity), o, false) },
            initiatives = initiatives,
            people = people,
            significant = significant,
            feed = m.feed,
            decision = decision,
            queued = queued,
            intent = intent(state),
            lastStop = m.lastStop,
        )
    }

    private fun bindingsOf(o: Opportunity?): Bindings {
        val slot = o?.slot
        val npc = o?.participant
        return if (slot != null && npc != null) Bindings(mapOf(slot to npc)) else Bindings.EMPTY
    }

    private fun whyNot(p: Planning?): List<WhyNot> = listOfNotNull((p as? Planning.No)?.let { WhyNot(it.reason, it.detail) })

    private fun option(
        state: LifeState,
        def: LifeActivityDef,
        opp: Opportunity?,
        offered: Boolean,
    ): StartOptionView {
        val m = state.mon()
        val planning = if (offered) null else core.planStart(state, def.id, opp?.uid, emptyMap())
        val b = bindingsOf(opp)
        val left = opp?.expiresMonth?.let { it - m.month }
        val noSlot = (planning as? Planning.No)?.reason == Reject.NO_FREE_SLOT
        return StartOptionView(
            activity = def.id,
            label = core.render(def.base.label, state, b),
            category = def.base.category,
            slotCost = def.base.slotCost,
            moneyCost = def.base.moneyCost,
            monthlyCost = def.monthlyCost,
            durationMonths = def.durationMonths,
            opportunity = opp?.uid,
            source = opp?.source,
            rarity = opp?.rarity,
            monthsLeft = left,
            expiringSoon = left != null && left <= config.warnMonths,
            participant = opp?.participant,
            participantSlots = def.participants,
            available = offered || planning is Planning.Ok,
            whyNot = whyNot(planning),
            chance = core.breakdown(state, def.base.check, b, core.memoryBonus(state, b)),
            replace = if (noSlot) m.pursuits.map { replaceOption(state, def, opp, it) } else emptyList(),
        )
    }

    private fun replaceOption(
        state: LifeState,
        def: LifeActivityDef,
        opp: Opportunity?,
        p: Pursuit,
    ): ReplaceOption {
        val pdef = content.activities.getValue(p.activity)
        val plan = core.planStart(state, def.id, opp?.uid, emptyMap(), core.slotsFree(state) + p.slots)
        return ReplaceOption(p.uid, core.lossPreview(pdef, p), plan is Planning.Ok, whyNot(plan))
    }

    private fun pursuit(
        state: LifeState,
        p: Pursuit,
    ): PursuitView {
        val def = content.activities.getValue(p.activity)
        return PursuitView(
            uid = p.uid,
            activity = p.activity,
            label = core.render(def.base.label, state, p.participants),
            slots = p.slots,
            progress = p.progress,
            durationMonths = def.durationMonths,
            monthsLeft = def.durationMonths?.let { (it - p.progress).coerceAtLeast(0) },
            participants = p.participants.slots,
            openSlots = def.participants.filter { it.slot !in p.participants.slots },
            monthlyCost = def.monthlyCost,
            chance = core.breakdown(state, def.base.check, p.participants, core.memoryBonus(state, p.participants)),
            leaveLoss = core.lossPreview(def, p),
        )
    }

    private fun person(
        state: LifeState,
        npc: Npc,
    ): PersonView =
        PersonView(
            id = npc.id,
            name = npc.name,
            role = npc.role,
            attitude = npc.attitude,
            tags = npc.tags,
            present = state.isPresent(npc),
            salience = core.salience(state, npc),
            memories = state.mon().memory.filter { it.npc == npc.id }.takeLast(5),
            canInteract = InteractKind.entries.associateWith { core.planInteract(state, npc, it) is InteractPlan.Ok },
        )

    private fun intent(state: LifeState): IntentView? {
        val m = state.mon()
        val i = m.intent ?: return null
        val def = content.activities[i.activity] ?: return null
        return IntentView(
            i.activity,
            core.render(def.base.label, state),
            i.opportunity,
            i.executeMonth,
            (i.executeMonth - m.month).coerceAtLeast(0),
        )
    }

    private fun prompt(
        state: LifeState,
        d: PendingDecision,
    ): DecisionPrompt =
        when (d.kind) {
            DecisionKind.EVENT -> eventPrompt(state, d)
            DecisionKind.OVERFLOW -> overflowPrompt(state, d)
            DecisionKind.SACRIFICE -> sacrificePrompt(state, d)
        }

    private fun eventPrompt(
        state: LifeState,
        d: PendingDecision,
    ): DecisionPrompt {
        val e = content.events.getValue(checkNotNull(d.eventId))
        val scene = core.withScene(state, d.bindings)
        val memory = core.memoryBonus(state, d.bindings)
        val choices =
            e.choices.filter { core.legacy.isVisible(scene, it) }.map { c ->
                ChoiceView(
                    id = c.id.id,
                    label = core.render(c.label, state, d.bindings),
                    enabled = core.legacy.isEnabled(scene, c),
                    lockedHint = c.lockedHint,
                    chance = core.breakdown(state, c.check, d.bindings, memory),
                )
            }
        return DecisionPrompt(
            d.uid,
            d.kind,
            e.id,
            e.tier,
            core.render(e.text, state, d.bindings),
            choices,
            d.bindings.slots.values.toList(),
            null,
        )
    }

    private fun overflowPrompt(
        state: LifeState,
        d: PendingDecision,
    ): DecisionPrompt {
        val offer = checkNotNull(d.offer)
        val m = state.mon()
        val def = content.activities.getValue(offer.activity)
        val offered = option(state, def, offer, true)
        val choices = mutableListOf<ChoiceView>()
        if (m.opportunities.size < m.limits.opportunities) {
            choices += ChoiceView("accept", offered.label, true, null, offered.chance, activity = def.id)
        } else {
            for (o in m.opportunities) {
                val od = content.activities.getValue(o.activity)
                val label = core.render(od.base.label, state, bindingsOf(o))
                choices += ChoiceView("swap:${o.uid}", label, true, null, null, activity = o.activity)
            }
        }
        choices += ChoiceView("decline", "", true, null, null)
        return DecisionPrompt(
            d.uid,
            d.kind,
            null,
            com.znova.engine.state.Tier.MEDIUM,
            "",
            choices,
            listOfNotNull(offer.participant),
            offered,
        )
    }

    private fun sacrificePrompt(
        state: LifeState,
        d: PendingDecision,
    ): DecisionPrompt {
        val choices =
            state.mon().pursuits.map { p ->
                val def = content.activities.getValue(p.activity)
                ChoiceView(
                    p.uid,
                    core.render(def.base.label, state, p.participants),
                    true,
                    null,
                    null,
                    core.lossPreview(def, p),
                    p.activity,
                )
            }
        return DecisionPrompt(d.uid, d.kind, null, com.znova.engine.state.Tier.MEDIUM, "", choices, emptyList(), null)
    }
}
