@file:Suppress("TooManyFunctions", "MagicNumber")

package com.znova.engine.life

import com.znova.engine.core.UnlockId
import com.znova.engine.rules.AddCard
import com.znova.engine.rules.AllOf
import com.znova.engine.rules.AnyOf
import com.znova.engine.rules.Chance
import com.znova.engine.rules.Condition
import com.znova.engine.rules.Effect
import com.znova.engine.rules.FlagOlderThan
import com.znova.engine.rules.Not
import com.znova.engine.rules.Schedule
import com.znova.engine.rules.Sequence
import com.znova.engine.rules.SetFlag
import com.znova.engine.rules.SkillCheck
import com.znova.engine.rules.When

internal class LifeValidator(private val c: LifeContent) {
    private val errors = ArrayList<String>()
    private val opened = HashSet<com.znova.engine.core.ActivityId>()

    fun run(): List<String> {
        errors += c.pack.validate()
        c.activities.values.forEach(::activity)
        c.events.values.forEach(::event)
        c.opportunityGens.values.forEach(::gen)
        c.reactions.values.forEach(::reaction)
        c.interactions.forEachIndexed(::interaction)
        c.scenarios.values.forEach(::scenario)
        effects("upkeep", c.upkeep)
        pack()
        c.activities.values
            .filter { !it.initiative && it.id !in opened }
            .forEach { errors += "activity ${it.id.id} can never be started: nobody opens it" }
        return errors
    }

    // ---------- deep walkers ----------

    private fun badEffect(e: Effect): String? =
        when (e) {
            is SetFlag -> "SetFlag counts in chapters (use MarkFlag)"
            is Schedule -> "Schedule counts in chapters (use ScheduleIn)"
            is AddCard -> "AddCard writes to a chapter and is lost (use Note)"
            else -> null
        }

    private fun walk(
        owner: String,
        e: Effect,
    ) {
        badEffect(e)?.let { errors += "$owner: $it" }
        if (e is LifeEffect) {
            e.refs.events.filter { it !in c.events }.forEach { errors += "$owner references a non-existent life event ${it.id}" }
        }
        when (e) {
            is OpenOpportunity -> spec(owner, e.open)
            is Sequence -> e.effects.forEach { walk(owner, it) }
            is When -> {
                cond(owner, e.condition)
                walk(owner, e.then)
                e.otherwise?.let { walk(owner, it) }
            }
            is Chance -> {
                walk(owner, e.then)
                e.otherwise?.let { walk(owner, it) }
            }
            else -> Unit
        }
    }

    private fun effects(
        owner: String,
        list: List<Effect>,
    ) = list.forEach { walk(owner, it) }

    private fun cond(
        owner: String,
        k: Condition,
    ) {
        if (k is FlagOlderThan) errors += "$owner: FlagOlderThan counts in chapters (use FlagOlderThanMonths)"
        when (k) {
            is AllOf -> k.parts.forEach { cond(owner, it) }
            is AnyOf -> k.parts.forEach { cond(owner, it) }
            is Not -> cond(owner, k.inner)
            else -> Unit
        }
    }

    private fun conds(
        owner: String,
        list: List<Condition>,
    ) = list.forEach { cond(owner, it) }

    private fun check(
        owner: String,
        check: SkillCheck?,
    ) = check?.bonuses?.forEach { cond(owner, it.condition) }

    private fun unlock(
        owner: String,
        id: UnlockId?,
    ) {
        if (id != null && id !in c.pack.unlocks) errors += "$owner references an undeclared unlock ${id.id}"
    }

    private fun spec(
        owner: String,
        s: OpenSpec,
    ) {
        opened += s.activity
        val act = c.activities[s.activity]
        if (act == null) {
            errors += "$owner opens a non-existent activity ${s.activity.id}"
            return
        }
        val slot = s.slot
        if (slot != null && act.participants.none { it.slot == slot }) {
            errors += "$owner opens ${s.activity.id} with slot ${slot.id}, which the activity does not declare"
        }
        if (s.ttlMonths != null && s.ttlMonths < 1) errors += "$owner: opportunity ttl must be positive"
    }

    // ---------- definitions ----------

    private fun activity(a: LifeActivityDef) {
        val o = "activity ${a.id.id}"
        conds(o, a.base.conditions)
        conds(o, a.continueWhen)
        a.intentRules.forEach { cond(o, it.condition) }
        check(o, a.base.check)
        effects(o, a.base.onSuccess + a.base.onFailure + a.onStart + a.onMonth + a.leave.effects)
        a.opens.forEach { spec(o, it) }
        if (a.base.slotCost < 1) errors += "$o: slotCost must be at least 1"
        if (a.durationMonths != null && a.durationMonths < 1) errors += "$o: duration must be positive"
        if (a.leave.keepPercent !in 0..100) errors += "$o: keepPercent must be within 0..100"
        if (a.base.moneyCost < 0 || a.monthlyCost < 0) errors += "$o: costs must not be negative"
        if (a.participants.map { it.slot }.toSet().size != a.participants.size) errors += "$o: duplicate slots"
        if (a.initiative && a.participants.any { it.required }) errors += "$o: an initiative cannot require participants"
        unlock(o, a.base.requiresUnlock)
    }

    private fun event(e: LifeEventDef) {
        val o = "event ${e.id.id}"
        conds(o, e.conditions)
        conds(o, e.stillValid)
        e.weightRules.forEach { cond(o, it.condition) }
        effects(o, e.onTrigger)
        e.choices.forEach { ch ->
            val oc = "$o/${ch.id.id}"
            conds(oc, ch.visibleWhen)
            conds(oc, ch.enabledWhen)
            check(oc, ch.check)
            effects(oc, ch.onSuccess + ch.onFailure)
        }
        if (e.choices.isNotEmpty() && e.choices.none { it.visibleWhen.isEmpty() && it.enabledWhen.isEmpty() }) {
            errors += "$o: no unconditional choice (player may get stuck)"
        }
        if (e.choices.map { it.id }.toSet().size != e.choices.size) errors += "$o: duplicate choice IDs"
        if (e.roles.map { it.slot }.toSet().size != e.roles.size) errors += "$o: duplicate role slots"
        if (e.cooldownMonths < 0) errors += "$o: cooldown must not be negative"
        unlock(o, e.requiresUnlock)
    }

    private fun gen(g: OpportunityGen) {
        val o = "opportunity source ${g.id}"
        conds(o, g.conditions)
        spec(o, g.open)
        if (g.open.rarity == Rarity.RARE) errors += "$o: rare opportunities must come from events, not from the generator"
        if (g.open.slot != null && g.participant?.slot != g.open.slot) {
            errors += "$o: slot ${g.open.slot.id} needs a participant requirement with the same slot"
        }
    }

    private fun reaction(r: NpcReactionDef) {
        val o = "reaction ${r.id}"
        conds(o, r.conditions)
        r.weightRules.forEach { cond(o, it.condition) }
        when (val act = r.act) {
            is NpcAct.Offer -> {
                spec(o, act.open)
                if (act.open.slot != null && act.open.slot != r.who.slot) {
                    errors += "$o: an offer may only use the reacting NPC's slot ${r.who.slot.id}"
                }
            }
            is NpcAct.Raise -> if (act.event !in c.events) errors += "$o raises a non-existent event ${act.event.id}"
            is NpcAct.Apply -> effects(o, act.effects)
            NpcAct.Depart -> Unit
        }
    }

    private fun interaction(
        index: Int,
        d: InteractionDef,
    ) {
        val o = "interaction #$index ${d.kind}"
        conds(o, d.conditions)
        effects(o, d.effects)
    }

    private fun scenario(s: LifeScenario) {
        val o = "scenario ${s.id}"
        if (s.startAgeYears.isEmpty() || s.startAgeYears.first < 0) errors += "$o: invalid start age range"
        s.skills.keys.filter { it !in c.pack.schema.skills }.forEach { errors += "$o: unknown skill ${it.id}" }
        s.views.keys.filter { it !in c.pack.schema.views }.forEach { errors += "$o: unknown view ${it.id}" }
        effects(o, s.effects)
    }

    private fun pack() {
        c.pack.achievements.values.forEach { a ->
            cond("achievement ${a.id.id}", a.condition)
            a.hintWhen?.let { cond("achievement ${a.id.id}", it) }
        }
        c.pack.titles.forEachIndexed { i, t -> conds("title #$i", t.conditions) }
        c.pack.trackers.values.forEach { cond("tracker ${it.id.id}", it.condition) }
        c.pack.places.values.forEach { effects("place ${it.id.id} start", it.onStart) }
        c.pack.legacies.values.forEach { effects("legacy ${it.id.id} start", it.onStart) }
        c.pack.origins.values.forEach { effects("origin ${it.id.id}", it.effects) }
    }
}
