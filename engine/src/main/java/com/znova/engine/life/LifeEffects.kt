@file:Suppress("TooManyFunctions")

package com.znova.engine.life

import com.znova.engine.core.ActivityId
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.NpcId
import com.znova.engine.core.RoleKey
import com.znova.engine.core.TagKey
import com.znova.engine.rules.Bindings
import com.znova.engine.rules.Condition
import com.znova.engine.rules.Effect
import com.znova.engine.rules.EffectContext
import com.znova.engine.rules.TextRenderer
import com.znova.engine.rules.resolve
import com.znova.engine.state.Card
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc
import com.znova.engine.state.Tier
import com.znova.engine.state.mapCharacter

internal fun LifeState.npcAt(slot: RoleKey): Npc? = scene().resolve(this, slot)

private fun EffectContext.npcAt(
    state: LifeState,
    slot: RoleKey,
): Npc? = (state.scene() + bindings).resolve(state, slot)

data class NpcAttitudeBetween(
    val slot: RoleKey,
    val min: Int = -10,
    val max: Int = 10,
) : Condition {
    override fun test(state: LifeState): Boolean = state.npcAt(slot)?.let { it.alive && it.attitude in min..max } == true
}

data class NpcPresent(val slot: RoleKey) : Condition {
    override fun test(state: LifeState): Boolean = state.npcAt(slot)?.let { state.isPresent(it) } == true
}

data class NpcHasTagAt(
    val slot: RoleKey,
    val tag: TagKey,
) : Condition {
    override fun test(state: LifeState): Boolean = state.npcAt(slot)?.let { tag in it.tags } == true
}

data class NpcRemembers(
    val slot: RoleKey?,
    val type: String,
    val minWeight: Int = 1,
    val withinMonths: Int? = null,
) : Condition {
    override fun test(state: LifeState): Boolean {
        if (state.monthly == null) return false
        val id = if (slot == null) null else (state.npcAt(slot) ?: return false).id
        return state.memoryWeight(id, type, withinMonths) >= minWeight
    }
}

data class FlagOlderThanMonths(
    val flag: FlagKey,
    val months: Int,
) : Condition {
    override fun test(state: LifeState): Boolean {
        val set = state.character.flags[flag] ?: return false
        return (state.monthly?.month ?: return false) - set >= months
    }
}

data class MonthAtLeast(val months: Int) : Condition {
    override fun test(state: LifeState): Boolean = (state.monthly?.month ?: 0) >= months
}

data class HasPursuit(val activity: ActivityId) : Condition {
    override fun test(state: LifeState): Boolean = state.monthly?.pursuits?.any { it.activity == activity } == true
}

data class PursuitProgressAtLeast(
    val activity: ActivityId,
    val months: Int,
) : Condition {
    override fun test(state: LifeState): Boolean =
        state.monthly?.pursuits?.any { it.activity == activity && it.progress >= months } == true
}

data class OpportunityOpen(val activity: ActivityId) : Condition {
    override fun test(state: LifeState): Boolean = state.monthly?.opportunities?.any { it.activity == activity } == true
}

data class ActivityDone(
    val activity: ActivityId,
    val atLeast: Int = 1,
) : Condition {
    override fun test(state: LifeState): Boolean = state.seenCount("done:${activity.id}") >= atLeast
}

data class LifeRefs(
    val events: Set<EventId> = emptySet(),
    val activities: Set<ActivityId> = emptySet(),
) {
    companion object {
        val NONE = LifeRefs()
    }
}

interface LifeEffect : Effect {
    val refs: LifeRefs get() = LifeRefs.NONE
}

data class Note(
    val tier: Tier,
    val text: String,
) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val rendered = TextRenderer.render(text, state, ctx.content, ctx.bindings)
        val cs = state.chapterState
        return when {
            cs != null -> state.copy(chapterState = cs.copy(cards = cs.cards + Card(tier, rendered)))
            state.monthly != null -> state.withFeed(FeedEntry(state.mon().month, tier, rendered, "note"))
            else -> state
        }
    }
}

data class MarkFlag(val flag: FlagKey) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState =
        state.mapCharacter { it.copy(flags = it.flags + (flag to (state.monthly?.month ?: state.currentChapter))) }
}

data class ScheduleIn(
    val event: EventId,
    val delayMonths: Int = 1,
    val policy: MissedPolicy = MissedPolicy.CANCEL,
    val postponeLimit: Int = 3,
) : LifeEffect {
    override val refs get() = LifeRefs(events = setOf(event))

    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val b = state.scene() + ctx.bindings
        return state.mapMonthly {
            it.copy(scheduled = it.scheduled + ScheduledEvent(event, it.month + delayMonths, b, policy, postponeLimit))
        }
    }
}

data class OpenOpportunity(val open: OpenSpec) : LifeEffect {
    override val refs get() = LifeRefs(activities = setOf(open.activity))

    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val m = state.monthly ?: return state
        val slot = open.slot
        val npc = if (slot == null) null else ctx.npcAt(state, slot)?.takeIf { state.isPresent(it) }
        if (slot != null && npc == null) return state
        if (m.opportunities.any { it.activity == open.activity && it.participant == npc?.id }) return state
        val (s, uid) = state.allocate("o")
        val opp =
            Opportunity(
                uid = uid,
                activity = open.activity,
                source = open.source,
                rarity = open.rarity,
                expiresMonth = open.ttlMonths?.let { m.month + it },
                participant = npc?.id,
                slot = slot,
                onLost = open.onLost,
            )
        return s.offer(opp, TextRenderer.render(open.text, s, ctx.content, state.scene() + ctx.bindings))
    }
}

data class ChangeAttitudeOf(
    val slot: RoleKey,
    val delta: Int,
) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val npc = ctx.npcAt(state, slot)?.takeIf { it.alive } ?: return state
        return state.copy(
            npcs =
                state.npcs.map {
                    if (it.id == npc.id) it.copy(attitude = (it.attitude + delta).coerceIn(-10, 10)) else it
                },
        )
    }
}

data class KillNpcAt(val slot: RoleKey) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val npc = ctx.npcAt(state, slot) ?: return state
        return state.copy(npcs = state.npcs.map { if (it.id == npc.id) it.copy(alive = false) else it })
    }
}

data class DepartNpc(val slot: RoleKey) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val npc = ctx.npcAt(state, slot)?.takeIf { state.isPresent(it) } ?: return state
        return state.mapMonthly { it.copy(away = it.away + npc.id) }
    }
}

data class Remember(
    val slot: RoleKey?,
    val type: String,
    val weight: Int = 1,
    val source: String = "",
) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        if (state.monthly == null) return state
        val id = if (slot == null) null else (ctx.npcAt(state, slot) ?: return state).id
        return state.remember(MemoryEntry(id, type, state.mon().month, source, weight))
    }
}

data class TagNpc(
    val slot: RoleKey,
    val tag: TagKey,
) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val npc = ctx.npcAt(state, slot) ?: return state
        return state.copy(npcs = state.npcs.map { if (it.id == npc.id) it.copy(tags = it.tags + tag) else it })
    }
}

data class MeetNpc(
    val role: RoleKey,
    val slot: RoleKey = role,
    val attitude: Int = 0,
    val tags: Set<TagKey> = emptySet(),
    val name: String? = null,
) : LifeEffect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val npcName = name ?: ctx.content.namesOf(state.world.placeId)?.pick(ctx.rng) ?: "Stranger"
        val npc = Npc(NpcId("npc-${state.npcs.size + 1}"), npcName, role, attitude, tags)
        val added = state.copy(npcs = state.npcs + npc)
        if (added.monthly == null) return added
        val met = added.remember(MemoryEntry(npc.id, MemoryTypes.MET, added.mon().month, "meet", 1))
        return met.mapMonthly { it.copy(scene = it.scene + Bindings(mapOf(slot to npc.id))) }
    }
}

object LifeEnded : Condition {
    override fun test(state: LifeState): Boolean = state.ending != null
}
