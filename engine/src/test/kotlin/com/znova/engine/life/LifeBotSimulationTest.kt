package com.znova.engine.life

import com.znova.engine.life.testpack.TestWorld
import kotlin.test.Test

class LifeBotSimulationTest {

    private val engine = TestWorld.engine()

    @Test
    fun `run bot simulations and print reports`() {
        val runsPerKind = 30
        val maxMonths = 360

        for (kind in BotKind.values()) {
            val runs = (1L..runsPerKind).map { seed ->
                LifeBot(
                    engine = engine,
                    kind = kind,
                    seed = seed,
                ).run(
                    setup = TestWorld.setup(seed, 18),
                    maxMonths = maxMonths,
                )
            }

            println()
            println(BotReport.render("${kind.name} — $runsPerKind runs", runs))
        }
    }
}
