package com.znova.engine.rules

import com.znova.engine.content.ContentPack
import com.znova.engine.core.RoleKey
import com.znova.engine.state.LifeState

object TextRenderer {
    private val token = Regex("\\{([a-z0-9_.]+)\\}")

    fun render(
        template: String,
        state: LifeState,
        content: ContentPack,
        bindings: Bindings = Bindings.EMPTY,
    ): String {
        val scene = (state.monthly?.scene ?: Bindings.EMPTY) + bindings
        return token.replace(template) { m ->
            val key = m.groupValues[1]
            when {
                key == "name" -> state.character.name
                key == "age" -> state.character.ageYears.toString()
                key == "year" -> state.currentYear.toString()
                key == "country" -> content.pathOf(state.world.placeId)
                    .firstOrNull { it.kind == "country" }
                    ?.name
                    .orEmpty()
                key == "money" -> state.character.money.toString()
                key.startsWith("npc.") -> scene.resolve(state, RoleKey(key.removePrefix("npc.")))
                    ?.name ?: "someone"
                else -> m.value
            }
        }
    }
}
