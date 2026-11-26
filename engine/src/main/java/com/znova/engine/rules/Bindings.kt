package com.znova.engine.rules

import com.znova.engine.core.NpcId
import com.znova.engine.core.RoleKey
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc

/** Maps a role inside a scene (pursuit, event, interaction) to a concrete NPC. Data only. */
data class Bindings(val slots: Map<RoleKey, NpcId> = emptyMap()) {
    operator fun plus(other: Bindings): Bindings = Bindings(slots + other.slots)

    fun with(
        role: RoleKey,
        id: NpcId,
    ): Bindings = Bindings(slots + (role to id))

    fun without(roles: Set<RoleKey>): Bindings = Bindings(slots - roles)

    companion object {
        val EMPTY = Bindings()
    }
}

/** A bound NPC wins (even if dead, so texts can mention them); otherwise the legacy "first living NPC with the role". */
fun Bindings.resolve(
    state: LifeState,
    role: RoleKey,
): Npc? {
    val bound = slots[role]
    if (bound != null) return state.npcs.firstOrNull { it.id == bound }
    return state.npcs.firstOrNull { it.alive && it.role == role }
}
