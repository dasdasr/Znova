package com.znova.engine.life

import com.znova.engine.core.NpcId
import com.znova.engine.core.Rng
import com.znova.engine.meta.ConcludeResult
import com.znova.engine.meta.LifeSetup
import com.znova.engine.meta.LifeSummary
import com.znova.engine.meta.MetaProfile
import com.znova.engine.rules.Bindings
import com.znova.engine.sim.GameEngine
import com.znova.engine.state.ChronicleEntry
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc
import com.znova.engine.state.mapCharacter

/** The monthly life cycle. Pure functions over [LifeState]; the chapter engine keeps working next to it. */
class LifeSimEngine(
    val content: LifeContent,
    val config: LifeConfig,
) {
    private val legacy = GameEngine(content.pack, config.base)
    private val core = LifeCore(content, config, legacy)
    private val executor = Executor(core)
    private val viewer = ViewBuilder(core)

    /** Scenario skills and views are absolute levels. Scenario people get ids after the ones the start created. */
    fun start(
        setup: LifeSetup,
        meta: MetaProfile = MetaProfile(),
    ): LifeState {
        val scenario = setup.scenario?.let { content.scenarios[it] ?: error("Unknown scenario: $it") }
        val base = legacy.startLife(setup, meta)
        val rng = Rng(setup.seed, base.rngDraws)
        val range = scenario?.startAgeYears ?: config.defaultStartAgeYears
        val age =
            setup.startAgeYears
                ?: if (range.first == range.last) range.first else range.first + rng.nextInt(range.last - range.first + 1)
        require(age >= 0) { "Start age must not be negative" }
        var s =
            base.copy(
                character = base.character.copy(ageMonths = age * MONTHS_PER_YEAR),
                monthly = MonthlyState(limits = config.limits()),
            )
        if (scenario != null) s = applyScenario(s, scenario, age, rng)
        return s.copy(rngDraws = rng.draws)
    }

    private fun applyScenario(
        state: LifeState,
        sc: LifeScenario,
        age: Int,
        rng: Rng,
    ): LifeState {
        val schema = content.pack.schema
        var s =
            state.mapCharacter { c ->
                c.copy(
                    skills = c.skills + sc.skills.mapValues { (k, v) -> schema.clampSkill(k, v) },
                    views = c.views + sc.views.mapValues { (k, v) -> schema.clampView(k, v) },
                    tags = c.tags + sc.tags,
                    flags = c.flags + sc.flags.associateWith { 0 },
                    money = sc.money ?: c.money,
                )
            }
        for (p in sc.people) {
            val name = p.name ?: content.pack.namesOf(s.world.placeId)?.pick(rng) ?: "Stranger"
            val npc = Npc(NpcId("npc-${s.npcs.size + 1}"), name, p.role, p.attitude, p.tags)
            s = s.copy(npcs = s.npcs + npc)
            for (mem in p.memories) {
                s = s.remember(MemoryEntry(npc.id, mem.type, -mem.monthsAgo, "scenario", mem.weight))
            }
        }
        val past = sc.past.map { ChronicleEntry(-(age - it.ageYears) * MONTHS_PER_YEAR, it.ageYears, it.tier, it.text) }
        s = s.copy(chronicle = s.chronicle + past)
        return core.apply(s, sc.effects, rng, Bindings.EMPTY)
    }

    /** Exactly one month, or nothing if a decision is pending or the life has ended. */
    fun tick(state: LifeState): TickResult = MonthRunner(core, state).run()

    fun execute(
        state: LifeState,
        command: PlayerCommand,
    ): CommandResult = executor.execute(state, command)

    fun view(state: LifeState): LifeView = viewer.build(state)

    fun summarize(state: LifeState): LifeSummary = legacy.summarize(state)

    fun conclude(
        meta: MetaProfile,
        state: LifeState,
    ): ConcludeResult = legacy.conclude(meta, state)

    fun validate(): List<String> = LifeValidator(content).run()

    private companion object {
        const val MONTHS_PER_YEAR = 12
    }
}
