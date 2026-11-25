package com.znova.engine.rules

import com.znova.engine.content.ContentPack
import com.znova.engine.core.Balance
import com.znova.engine.core.CounterKey
import com.znova.engine.core.CountryId
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.LawKey
import com.znova.engine.core.NpcId
import com.znova.engine.core.Rng
import com.znova.engine.core.RoleKey
import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.TagKey
import com.znova.engine.core.ViewKey
import com.znova.engine.state.Card
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc
import com.znova.engine.state.Scheduled
import com.znova.engine.state.Tier
import com.znova.engine.state.endLife
import com.znova.engine.state.mapCharacter

class EffectContext(
    val rng: Rng,
    val content: ContentPack,
    val gameModifiers: List<GameModifier>,
)

interface Effect {
    fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState

    val referencedEvents: Set<EventId> get() = emptySet()
}

private fun LifeState.chapterIndex() = currentChapter

data class AddMoney(
    val delta: Long,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter { it.copy(money = it.money + delta) }
}

data class ChangeStat(
    val stat: StatKey,
    val delta: Int,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val d = ctx.gameModifiers.fold(delta) { acc, m -> m.statDelta(state, stat, acc) }
        return state.mapCharacter {
            it.copy(stats = it.stats + (stat to (it.stat(stat) + d).coerceIn(Balance.STAT)))
        }
    }
}

data class ChangeSkill(
    val skill: SkillKey,
    val delta: Int,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter {
        it.copy(skills = it.skills + (skill to (it.skill(skill) + delta).coerceIn(Balance.SKILL)))
    }
}

data class ShiftView(
    val view: ViewKey,
    val delta: Int,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter {
        it.copy(views = it.views + (view to (it.view(view) + delta).coerceIn(Balance.VIEW)))
    }
}

data class RaiseTag(
    val tag: TagKey,
    val by: Int = 1,
    val max: Int = 3,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter { c ->
        val level = (c.tagLevel(tag) + by).coerceIn(0, max)
        val tags = LinkedHashMap(c.tags)
        if (level == 0) tags.remove(tag) else tags[tag] = level
        while (tags.size > Balance.MAX_TAGS) {
            val weakest = tags.entries.filter { it.key != tag }.minByOrNull { it.value } ?: break
            tags.remove(weakest.key)
        }
        c.copy(tags = tags)
    }
}

data class DropTag(
    val tag: TagKey,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter { it.copy(tags = it.tags - tag) }
}

data class SetFlag(
    val flag: FlagKey,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter { it.copy(flags = it.flags + (flag to state.chapterIndex())) }
}

data class ClearFlag(
    val flag: FlagKey,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter { it.copy(flags = it.flags - flag) }
}

data class SetLaw(
    val law: LawKey,
    val enabled: Boolean,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.copy(
        world = state.world.copy(laws = if (enabled) state.world.laws + law else state.world.laws - law),
    )
}

data class MoveCountry(
    val country: CountryId,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val def = ctx.content.countries[country] ?: return state
        return state.copy(world = state.world.copy(countryId = country, laws = def.laws))
    }
}

data class AddNpc(
    val role: RoleKey,
    val attitude: Int = 0,
    val tags: Set<TagKey> = emptySet(),
    val name: String? = null,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val pool = ctx.content.countries[state.world.countryId]?.names
        val npcName = name ?: pool?.pick(ctx.rng) ?: "Stranger"
        val npc = Npc(NpcId("npc-${state.npcs.size + 1}"), npcName, role, attitude, tags)
        return state.copy(npcs = state.npcs + npc)
    }
}

data class ChangeAttitude(
    val role: RoleKey,
    val delta: Int,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val idx = state.npcs.indexOfFirst { it.alive && it.role == role }
        if (idx < 0) return state
        val npc = state.npcs[idx]
        val updated = npc.copy(attitude = (npc.attitude + delta).coerceIn(-10, 10))
        return state.copy(npcs = state.npcs.toMutableList().also { it[idx] = updated })
    }
}

data class KillNpc(
    val role: RoleKey,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val idx = state.npcs.indexOfFirst { it.alive && it.role == role }
        if (idx < 0) return state
        return state.copy(npcs = state.npcs.toMutableList().also { it[idx] = it[idx].copy(alive = false) })
    }
}

data class RecallPastLife(
    val role: RoleKey,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        if (state.pastLives.isEmpty()) return state
        val past = ctx.rng.pick(state.pastLives)
        val npc = Npc(NpcId("npc-${state.npcs.size + 1}"), past.name, role, 0, emptySet())
        return state.copy(npcs = state.npcs + npc)
    }
}

data class Schedule(
    val event: EventId,
    val delayChapters: Int = 1,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.copy(
        scheduled = state.scheduled + Scheduled(event, state.chapterIndex() + delayChapters),
    )

    override val referencedEvents get() = setOf(event)
}

data class AddCard(
    val tier: Tier,
    val text: String,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        val cs = state.chapterState ?: return state
        val card = Card(tier, TextRenderer.render(text, state, ctx.content))
        return state.copy(chapterState = cs.copy(cards = cs.cards + card))
    }
}

data class EndLife(
    val cause: String,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.endLife(cause)
}

data class Sequence(
    val effects: List<Effect>,
) : Effect {
    constructor(vararg effects: Effect) : this(effects.toList())

    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ): LifeState {
        var s = state
        for (e in effects) {
            if (s.ending != null) break
            s = e.apply(s, ctx)
        }
        return s
    }

    override val referencedEvents get() = effects.flatMap { it.referencedEvents }.toSet()
}

data class When(
    val condition: Condition,
    val then: Effect,
    val otherwise: Effect? = null,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = if (condition.test(state)) then.apply(state, ctx) else otherwise?.apply(state, ctx) ?: state

    override val referencedEvents get() = setOfNotNull(then, otherwise).flatMap { it.referencedEvents }.toSet()
}

data class Chance(
    val p: Double,
    val then: Effect,
    val otherwise: Effect? = null,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = if (ctx.rng.chance(p)) then.apply(state, ctx) else otherwise?.apply(state, ctx) ?: state

    override val referencedEvents get() = setOfNotNull(then, otherwise).flatMap { it.referencedEvents }.toSet()
}

data class AddCounter(
    val counter: CounterKey,
    val delta: Int = 1,
) : Effect {
    override fun apply(
        state: LifeState,
        ctx: EffectContext,
    ) = state.mapCharacter {
        it.copy(counters = it.counters + (counter to (it.counter(counter) + delta)))
    }
}
