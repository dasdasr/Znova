package com.znova.engine.rules

import com.znova.engine.core.CounterKey
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.LawKey
import com.znova.engine.core.LegacyId
import com.znova.engine.core.OriginId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.RoleKey
import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.TagKey
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.core.ViewKey
import com.znova.engine.state.LifeState

fun interface Condition {
    fun test(state: LifeState): Boolean
}

infix fun Condition.and(other: Condition): Condition = AllOf(listOf(this, other))

infix fun Condition.or(other: Condition): Condition = AnyOf(listOf(this, other))

operator fun Condition.not(): Condition = Not(this)

object Always : Condition {
    override fun test(state: LifeState) = true
}

data class AllOf(
    val parts: List<Condition>,
) : Condition {
    override fun test(state: LifeState) = parts.all { it.test(state) }
}

data class AnyOf(
    val parts: List<Condition>,
) : Condition {
    override fun test(state: LifeState) = parts.any { it.test(state) }
}

data class Not(
    val inner: Condition,
) : Condition {
    override fun test(state: LifeState) = !inner.test(state)
}

data class AgeBetween(
    val minYears: Int = 0,
    val maxYears: Int = Int.MAX_VALUE,
) : Condition {
    override fun test(state: LifeState) = state.character.ageYears in minYears..maxYears
}

data class MoneyBetween(
    val min: Long = Long.MIN_VALUE,
    val max: Long = Long.MAX_VALUE,
) : Condition {
    override fun test(state: LifeState) = state.character.money in min..max
}

data class StatBetween(
    val stat: StatKey,
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
) : Condition {
    override fun test(state: LifeState) = state.character.stat(stat) in min..max
}

data class SkillAtLeast(
    val skill: SkillKey,
    val level: Int,
) : Condition {
    override fun test(state: LifeState) = state.character.skill(skill) >= level
}

data class ViewBetween(
    val view: ViewKey,
    val min: Int = -10,
    val max: Int = 10,
) : Condition {
    override fun test(state: LifeState) = state.character.view(view) in min..max
}

data class HasTag(
    val tag: TagKey,
    val minLevel: Int = 1,
) : Condition {
    override fun test(state: LifeState) = state.character.tagLevel(tag) >= minLevel
}

data class HasFlag(
    val flag: FlagKey,
) : Condition {
    override fun test(state: LifeState) = state.character.hasFlag(flag)
}

data class FlagOlderThan(
    val flag: FlagKey,
    val chapters: Int,
) : Condition {
    override fun test(state: LifeState): Boolean {
        val set = state.character.flags[flag] ?: return false
        return state.currentChapter - set >= chapters
    }
}

data class InPlace(
    val place: PlaceId,
) : Condition {
    override fun test(state: LifeState) = place in state.world.placePath
}

data class PlaceHasTag(
    val tag: String,
) : Condition {
    override fun test(state: LifeState) = tag in state.world.placeTags
}

data class PlaceAxisAtLeast(
    val axis: String,
    val value: Int,
) : Condition {
    override fun test(state: LifeState) = (state.world.placeAxes[axis] ?: 0) >= value
}

data class LawActive(
    val law: LawKey,
    val active: Boolean = true,
) : Condition {
    override fun test(state: LifeState) = (law in state.world.laws) == active
}

data class HasNpc(
    val role: RoleKey,
    val minAttitude: Int = Int.MIN_VALUE,
    val maxAttitude: Int = Int.MAX_VALUE,
) : Condition {
    override fun test(state: LifeState) =
        state.npcs.any { it.alive && it.role == role && it.attitude in minAttitude..maxAttitude }
}

data class HasLegacy(
    val id: LegacyId,
) : Condition {
    override fun test(state: LifeState) = id in state.legacies
}

data class HasUnlock(
    val id: UnlockId,
) : Condition {
    override fun test(state: LifeState) = id in state.unlocks
}

data class EventSeen(
    val id: EventId,
    val atLeast: Int = 1,
) : Condition {
    override fun test(state: LifeState) = state.seenCount(id.id) >= atLeast
}

data class EndedBy(
    val cause: String,
) : Condition {
    override fun test(state: LifeState) = state.ending?.cause == cause
}

data class CounterAtLeast(
    val counter: CounterKey,
    val value: Int,
) : Condition {
    override fun test(state: LifeState) = state.character.counter(counter) >= value
}

data class TrackerAtLeast(
    val id: TrackerId,
    val current: Int = 0,
    val best: Int = 0,
    val total: Int = 0,
) : Condition {
    override fun test(state: LifeState): Boolean {
        val t = state.trackers[id] ?: return current <= 0 && best <= 0 && total <= 0
        return t.current >= current && t.best >= best && t.total >= total
    }
}

data class HasOrigin(
    val id: OriginId,
) : Condition {
    override fun test(state: LifeState) = state.origin == id
}
