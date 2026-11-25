package com.znova.original

import com.znova.engine.Flags
import com.znova.engine.core.ActivityId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.PlaceId
import com.znova.engine.meta.LifeSetup
import com.znova.engine.state.LifeState
import com.znova.engine.state.Phase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmigrationGateTest {
    private val engine = SamplePack.engine()
    private val emigrate = ActivityId("emigrate")
    private val seeds = 1L..40L

    private fun prepared(
        seed: Long,
        place: String,
        flags: Set<FlagKey>,
    ): LifeState {
        val fresh = engine.startLife(LifeSetup(seed = seed, place = PlaceId(place)))
        return fresh.copy(
            character =
                fresh.character.copy(
                    ageMonths = 30 * 12,
                    money = 500,
                    flags = fresh.character.flags + flags.associateWith { 0 },
                ),
        )
    }

    private fun reachActivities(start: LifeState): LifeState? {
        var s = engine.openChapter(start)
        while (s.ending == null && s.chapterState!!.phase == Phase.DECISIONS) {
            val d = engine.view(s).decisions.first()
            s = engine.choose(s, d.eventId, d.choices.first { it.enabled }.id)
        }
        return s.takeIf { it.ending == null && it.chapterState!!.phase == Phase.ACTIVITIES }
    }

    private fun reached(
        place: String,
        flags: Set<FlagKey>,
    ): List<LifeState> =
        seeds.mapNotNull { reachActivities(prepared(it, place, flags)) }.also {
            assertTrue(it.size >= MIN_REACHED, "Too few lives reached activities: ${it.size}")
        }

    private fun LifeState.emigrateView() = engine.view(this).activities.firstOrNull { it.id == emigrate }

    @Test
    fun `closed borders without a smuggler block emigration`() {
        reached("tsentral", setOf(Flags.THOUGHT_LEAVE)).forEach {
            assertTrue(it.character.hasFlag(Flags.THOUGHT_LEAVE))
            assertFalse(it.character.hasFlag(Flags.KNOWS_SMUGGLER))
            assertNull(it.emigrateView())
        }
    }

    @Test
    fun `closed borders with a smuggler allow emigration`() {
        reached("tsentral", setOf(Flags.THOUGHT_LEAVE, Flags.KNOWS_SMUGGLER)).forEach {
            val view = assertNotNull(it.emigrateView())
            assertTrue(view.enabled)
        }
    }

    @Test
    fun `without the thought to leave there is no emigration even with a smuggler`() {
        val lives =
            reached("tsentral", setOf(Flags.KNOWS_SMUGGLER))
                .filterNot { it.character.hasFlag(Flags.THOUGHT_LEAVE) }
        assertTrue(lives.size >= MIN_WITHOUT_THOUGHT, "Too few lives without the thought: ${lives.size}")
        lives.forEach { assertNull(it.emigrateView()) }
    }

    @Test
    fun `open borders allow emigration once the thought has come`() {
        reached("pribrezhye", setOf(Flags.THOUGHT_LEAVE)).forEach {
            val view = assertNotNull(it.emigrateView())
            assertTrue(view.enabled)
        }
    }

    private companion object {
        const val MIN_REACHED = 10
        const val MIN_WITHOUT_THOUGHT = 3
    }
}
