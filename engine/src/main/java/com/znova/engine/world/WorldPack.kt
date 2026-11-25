package com.znova.engine.world

import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.ContentModule
import com.znova.engine.content.ContentPack
import com.znova.engine.sim.EngineConfig
import com.znova.engine.sim.GameEngine

interface WorldPack {
    val id: String
    val displayName: String
    val version: Int
    val schema: WorldSchema
    val config: EngineConfig
    val modules: List<ContentModule>

    fun content(): ContentPack = ContentBuilder(id, schema).also { b -> modules.forEach(b::install) }.build()

    fun engine() = GameEngine(content(), config)
}

class PackRegistry(
    packs: List<WorldPack>,
) {
    private val byId = packs.associateBy { it.id }

    init {
        require(byId.size == packs.size) { "Duplicate pack ids" }
    }

    val all: List<WorldPack> get() = byId.values.toList()

    operator fun get(id: String) = byId[id] ?: error("Unknown pack $id")
}
