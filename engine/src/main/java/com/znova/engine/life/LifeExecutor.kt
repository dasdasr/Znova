@file:Suppress("TooManyFunctions", "LongMethod", "ReturnCount", "CyclomaticComplexMethod")

package com.znova.engine.life

import com.znova.engine.core.Rng
import com.znova.engine.state.LifeState
import com.znova.engine.state.Tier

private sealed interface Out {
    data class Done(val state: LifeState) : Out

    data class No(
        val reason: Reject,
        val detail: String? = null,
    ) : Out
}

/** Executes player commands. A rejected command returns the exact state it was given. */
internal class Executor(private val core: LifeCore) {
    fun execute(
        state: LifeState,
        cmd: PlayerCommand,
    ): CommandResult {
        if (state.monthly == null) return CommandResult.Rejected(state, Reject.NOT_MONTHLY)
        if (state.ending != null) return CommandResult.Rejected(state, Reject.LIFE_ENDED)
        val s = core.pruneQueue(state)
        val rng = Rng(s.seed, s.rngDraws)
        val blocked = cmd !is PlayerCommand.Resolve && s.mon().pending.isNotEmpty()
        val out = if (blocked) Out.No(Reject.DECISION_PENDING) else dispatch(s, rng, cmd)
        return when (out) {
            is Out.Done -> CommandResult.Ok(out.state.copy(rngDraws = rng.draws))
            is Out.No -> CommandResult.Rejected(state, out.reason, out.detail)
        }
    }

    private fun dispatch(
        s: LifeState,
        rng: Rng,
        cmd: PlayerCommand,
    ): Out =
        when (cmd) {
            is PlayerCommand.Start -> startOut(s, rng, core.planStart(s, cmd.activity, cmd.opportunity, cmd.participants))
            is PlayerCommand.Replace -> replace(s, rng, cmd)
            is PlayerCommand.Drop -> drop(s, rng, cmd)
            is PlayerCommand.Invite -> invite(s, cmd)
            is PlayerCommand.Interact -> interact(s, rng, cmd)
            is PlayerCommand.Resolve -> resolve(s, rng, cmd)
            PlayerCommand.DismissIntent -> dismiss(s)
        }

    private fun pursuitOf(
        s: LifeState,
        uid: String,
    ): Pursuit? = s.mon().pursuits.firstOrNull { it.uid == uid }

    private fun startOut(
        s: LifeState,
        rng: Rng,
        planning: Planning,
    ): Out =
        when (planning) {
            is Planning.No -> Out.No(planning.reason, planning.detail)
            is Planning.Ok -> Out.Done(core.startPursuit(s, rng, planning.plan))
        }

    /** Atomic: leave + start. If the start is impossible after leaving, nothing changes. */
    private fun replace(
        s: LifeState,
        rng: Rng,
        c: PlayerCommand.Replace,
    ): Out {
        val p = pursuitOf(s, c.pursuit) ?: return Out.No(Reject.UNKNOWN_PURSUIT)
        val left = core.leavePursuit(s, rng, p, true, "pursuit.replaced")
        if (left.ending != null) return Out.Done(left)
        return startOut(left, rng, core.planStart(left, c.activity, c.opportunity, c.participants))
    }

    private fun drop(
        s: LifeState,
        rng: Rng,
        c: PlayerCommand.Drop,
    ): Out {
        val p = pursuitOf(s, c.pursuit) ?: return Out.No(Reject.UNKNOWN_PURSUIT)
        return Out.Done(core.leavePursuit(s, rng, p, true, "pursuit.dropped"))
    }

    private fun invite(
        s: LifeState,
        c: PlayerCommand.Invite,
    ): Out {
        val p = pursuitOf(s, c.pursuit) ?: return Out.No(Reject.UNKNOWN_PURSUIT)
        val def = core.content.activities.getValue(p.activity)
        val req = def.participants.firstOrNull { it.slot == c.slot } ?: return Out.No(Reject.SLOT_NOT_FOUND, c.slot.id)
        if (p.participants.slots.containsKey(c.slot) || c.npc in p.participants.slots.values) {
            return Out.No(Reject.SLOT_TAKEN, c.slot.id)
        }
        val npc = s.npcs.firstOrNull { it.id == c.npc } ?: return Out.No(Reject.UNKNOWN_NPC, c.npc.id)
        core.participantProblem(s, npc, req)?.let { return Out.No(it, c.slot.id) }
        val month = s.mon().month
        val updated = p.copy(participants = p.participants.with(c.slot, npc.id))
        var t = s.mapMonthly { m -> m.copy(pursuits = m.pursuits.map { if (it.uid == p.uid) updated else it }) }
        t = t.remember(MemoryEntry(npc.id, MemoryTypes.INVITED, month, def.id.id))
        t = core.changeAttitude(t, npc.id, core.config.inviteAttitudeGain)
        return Out.Done(t.withFeed(FeedEntry(month, Tier.MINOR, "", def.id.id, "pursuit.invited")))
    }

    private fun interact(
        s: LifeState,
        rng: Rng,
        c: PlayerCommand.Interact,
    ): Out {
        val npc = s.npcs.firstOrNull { it.id == c.npc } ?: return Out.No(Reject.UNKNOWN_NPC, c.npc.id)
        val plan =
            when (val p = core.planInteract(s, npc, c.kind)) {
                is InteractPlan.No -> return Out.No(p.reason)
                is InteractPlan.Ok -> p
            }
        val month = s.mon().month
        var t = core.apply(s, plan.def.effects, rng, plan.bindings)
        if (t.ending != null) return Out.Done(t)
        plan.def.remember?.let {
            t = t.remember(MemoryEntry(npc.id, it, month, "interact", plan.def.memoryWeight))
        }
        val text = core.render(plan.def.resultText, t, plan.bindings)
        t = t.mapMonthly { it.copy(interactions = it.interactions + (plan.key to month)) }
        return Out.Done(t.withFeed(FeedEntry(month, Tier.MINOR, text, "interact", "interact")))
    }

    private fun dismiss(s: LifeState): Out {
        val intent = s.mon().intent ?: return Out.No(Reject.NO_INTENT)
        val month = s.mon().month
        val t = s.mapMonthly { it.copy(intent = null) }
        return Out.Done(t.withFeed(FeedEntry(month, Tier.MINOR, "", intent.activity.id, "intent.dismissed")))
    }

    private fun resolve(
        s: LifeState,
        rng: Rng,
        c: PlayerCommand.Resolve,
    ): Out {
        val pending = s.mon().pending
        val head = pending.firstOrNull() ?: return Out.No(Reject.UNKNOWN_DECISION)
        if (head.uid != c.decision) {
            return Out.No(if (pending.any { it.uid == c.decision }) Reject.NOT_CURRENT else Reject.UNKNOWN_DECISION)
        }
        return when (head.kind) {
            DecisionKind.EVENT -> resolveEvent(s, rng, head, c)
            DecisionKind.OVERFLOW -> resolveOverflow(s, head, c)
            DecisionKind.SACRIFICE -> resolveSacrifice(s, rng, c)
        }
    }

    private fun resolveEvent(
        s: LifeState,
        rng: Rng,
        head: PendingDecision,
        c: PlayerCommand.Resolve,
    ): Out {
        val e = core.content.events.getValue(checkNotNull(head.eventId))
        val choice = e.choices.firstOrNull { it.id.id == c.choice.id } ?: return Out.No(Reject.UNKNOWN_CHOICE)
        val scene = core.withScene(s, head.bindings)
        if (!(core.legacy.isVisible(scene, choice) && core.legacy.isEnabled(scene, choice))) {
            return Out.No(Reject.CHOICE_UNAVAILABLE)
        }
        val month = s.mon().month
        var t = s.mapMonthly { it.copy(pending = it.pending.drop(1)) }
        val memory = core.memoryBonus(t, head.bindings)
        val result = choice.check?.let { core.resolveCheck(t, rng, it, head.bindings, memory) }
        val success = result?.success ?: true
        val effects = if (success) choice.onSuccess else choice.onFailure
        val text = if (success) choice.resultText else (choice.failText ?: choice.resultText)
        t = core.apply(t, effects, rng, head.bindings)
        if (t.ending == null) {
            val rendered = core.render(text, t, head.bindings)
            if (rendered.isNotBlank() || result != null) {
                t = t.withFeed(FeedEntry(month, e.tier, rendered, e.id.id, "decision", result))
            }
        }
        return Out.Done(t)
    }

    /** Choice ids: "accept" (only if there is room), "swap:<opportunity uid>", "decline". */
    private fun resolveOverflow(
        s: LifeState,
        head: PendingDecision,
        c: PlayerCommand.Resolve,
    ): Out {
        val offer = head.offer ?: return Out.No(Reject.UNKNOWN_DECISION)
        val m = s.mon()
        val id = c.choice.id
        val base = s.mapMonthly { it.copy(pending = it.pending.drop(1)) }
        val note = { kind: String -> FeedEntry(m.month, Tier.MINOR, "", offer.activity.id, kind) }
        return when {
            id == "decline" -> Out.Done(base.withFeed(note("opportunity.declined")))
            id == "accept" ->
                if (m.opportunities.size < m.limits.opportunities) {
                    Out.Done(
                        base.mapMonthly { it.copy(opportunities = it.opportunities + offer) }
                            .withFeed(note("opportunity.accepted")),
                    )
                } else {
                    Out.No(Reject.CHOICE_UNAVAILABLE)
                }
            id.startsWith("swap:") -> {
                val uid = id.removePrefix("swap:")
                if (m.opportunities.none { it.uid == uid }) {
                    Out.No(Reject.UNKNOWN_OPPORTUNITY)
                } else {
                    Out.Done(
                        base.mapMonthly { st ->
                            st.copy(opportunities = st.opportunities.filter { it.uid != uid } + offer)
                        }.withFeed(note("opportunity.swapped")),
                    )
                }
            }
            else -> Out.No(Reject.UNKNOWN_CHOICE)
        }
    }

    /** The choice id is the uid of the pursuit to give up. If still too many, a new decision is queued. */
    private fun resolveSacrifice(
        s: LifeState,
        rng: Rng,
        c: PlayerCommand.Resolve,
    ): Out {
        val p = pursuitOf(s, c.choice.id) ?: return Out.No(Reject.UNKNOWN_CHOICE)
        var t = s.mapMonthly { it.copy(pending = it.pending.drop(1)) }
        t = core.leavePursuit(t, rng, p, false, "pursuit.sacrificed")
        if (t.ending == null && core.slotsUsed(t) > core.slotsTotal(t)) {
            val (allocated, uid) = t.allocate("d")
            t = allocated.mapMonthly {
                it.copy(pending = listOf(PendingDecision(uid, DecisionKind.SACRIFICE, month = it.month)) + it.pending)
            }
        }
        return Out.Done(t)
    }
}
