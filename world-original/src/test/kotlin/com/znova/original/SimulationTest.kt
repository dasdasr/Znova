package com.znova.original

import com.znova.engine.Flags
import com.znova.engine.core.LegacyId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.Rng
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.meta.LifeSetup
import com.znova.engine.meta.MetaProfile
import com.znova.engine.state.LifeState
import com.znova.engine.state.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationTest {

    private val engine = SamplePack.engine()

    private fun autoplay(
        seed: Long,
        legacies: List<LegacyId> = emptyList(),
    ): LifeState {
        val bot = Rng(seed xor 0x5DEECE66DL)
        var s = engine.startLife(LifeSetup(seed, legacies = legacies))
        var guard = 0

        while (s.ending == null) {
            check(guard++ < 400) {
                "Life does not end (seed=$seed)"
            }

            s = engine.openChapter(s)

            while (s.chapterState!!.phase == Phase.DECISIONS) {
                val d = engine.view(s).decisions.first()
                val options = d.choices.filter { it.enabled }

                s = engine.choose(
                    s,
                    d.eventId,
                    options[bot.nextInt(options.size)].id,
                )
            }

            if (s.chapterState!!.phase == Phase.ACTIVITIES) {
                val view = engine.view(s)
                val picked = view.activities
                    .filter { it.enabled }
                    .shuffledBy(bot)
                    .take(view.slots)

                s = engine.commitActivities(s, picked.map { it.id })
            }
        }

        return s
    }

    private fun <T> List<T>.shuffledBy(rng: Rng): List<T> {
        val m = toMutableList()

        for (i in m.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val t = m[i]
            m[i] = m[j]
            m[j] = t
        }

        return m
    }

    @Test
    fun `content is valid`() {
        assertEquals(emptyList(), engine.content.validate())
    }

    @Test
    fun `same seed gives identical life`() {
        assertEquals(autoplay(42), autoplay(42))
    }

    @Test
    fun `one hundred lives end and differ`() {
        val lives = (1L..100L).map { autoplay(it) }
        val titles = lives.map { engine.summarize(it).title }.toSet()
        val causes = lives.map { it.ending!!.cause }.toSet()
        val chronicles = lives.map { life ->
            life.chronicle.map { it.text }
        }.toSet()

        println("titles=$titles causes=$causes")
        println(
            "lengths=${lives.map { it.chapter }.sorted().let {
                "${it.first()}..${it.last()}"
            }}"
        )

        assertTrue(
            chronicles.size > 95,
            "Lives are too similar: ${chronicles.size}",
        )
        assertTrue(titles.size >= 2)
    }

    @Test
    fun `closed borders prevent emigration unless connected`() {
        val s = autoplay(7)
        assertNotNull(s.ending)

        val fresh = engine.startLife(
            LifeSetup(
                seed = 7,
                place = PlaceId("tsentral"),
            ),
        ).let {
            it.copy(
                character = it.character.copy(
                    ageMonths = 30 * 12,
                    money = 500,
                ),
            )
        }

        val view = engine.view(
            engine.openChapter(fresh).let { st ->
                var x = st

                while (x.chapterState!!.phase == Phase.DECISIONS) {
                    val d = engine.view(x).decisions.first()
                    x = engine.choose(
                        x,
                        d.eventId,
                        d.choices.first { it.enabled }.id,
                    )
                }

                x
            },
        )

        if (view.phase == Phase.ACTIVITIES) {
            assertTrue(
                view.activities.none { it.id.id == "emigrate" },
            )
        }
    }

    @Test
    fun `concluding a life unlocks content and archives it`() {
        val result = engine.conclude(MetaProfile(), autoplay(3))
        assertEquals(1, result.meta.archive.size)
    }

    @Test
    fun `laboratory shows only unlocked content`() {
        val locked = engine.laboratory(MetaProfile())

        assertTrue(locked.legacies.none { it.id.id == "debtor" })
        assertTrue(locked.legacies.any { it.id.id == "weak_heart" })

        val open = engine.laboratory(
            MetaProfile(
                unlocks = setOf(UnlockId("legacy_debtor")),
            ),
        )

        assertTrue(open.legacies.any { it.id.id == "debtor" })
    }

    @Test
    fun `legacy modifies the starting conditions`() {
        val meta = MetaProfile(
            unlocks = setOf(UnlockId("legacy_debtor")),
        )

        val s = engine.startLife(
            LifeSetup(
                seed = 5,
                legacies = listOf(LegacyId("debtor")),
            ),
            meta,
        )

        assertTrue(s.character.hasFlag(Flags.IN_DEBT))
        assertTrue(s.legacies.contains(LegacyId("debtor")))

        val plain = engine.startLife(
            LifeSetup(
                seed = 5,
                legacies = listOf(LegacyId("debtor")),
            ),
        )

        assertTrue(plain.legacies.isEmpty())
    }

    @Test
    fun `trackers and unlocks work throughout a life`() {
        val meta = MetaProfile(
            unlocks = setOf(UnlockId("legacy_debtor")),
        )

        var s = engine.startLife(
            LifeSetup(
                seed = 11,
                legacies = listOf(LegacyId("debtor")),
            ),
            meta,
        )

        repeat(4) {
            if (s.ending == null) {
                s = engine.openChapter(s)

                while (s.chapterState!!.phase == Phase.DECISIONS) {
                    val d = engine.view(s).decisions.first()
                    s = engine.choose(
                        s,
                        d.eventId,
                        d.choices.first { it.enabled }.id,
                    )
                }

                if (s.chapterState!!.phase == Phase.ACTIVITIES) {
                    s = engine.commitActivities(s, emptyList())
                }
            }
        }

        if (s.ending == null) {
            assertTrue(
                (s.trackers[TrackerId("debt")]?.best ?: 0) >= 3,
            )
        }
    }
}
