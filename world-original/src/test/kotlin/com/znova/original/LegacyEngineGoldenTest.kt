package com.znova.original

import com.znova.engine.core.LegacyId
import com.znova.engine.core.Rng
import com.znova.engine.meta.LifeSetup
import com.znova.engine.state.LifeState
import com.znova.engine.state.Phase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pins the observable behaviour of the legacy chapter engine, including the
 * exact order of Rng draws. The golden file is recorded on the first run.
 */
class LegacyEngineGoldenTest {
    private val engine = SamplePack.engine()
    private val goldenFile = File("src/test/resources/golden/legacy-engine.txt")

    private fun autoplay(
        seed: Long,
        legacies: List<LegacyId>,
    ): LifeState {
        val bot = Rng(seed xor 0x5DEECE66DL)
        var s = engine.startLife(LifeSetup(seed, legacies = legacies))
        var guard = 0
        while (s.ending == null) {
            check(guard++ < 400) { "Life does not end (seed=$seed)" }
            s = engine.openChapter(s)
            while (s.chapterState!!.phase == Phase.DECISIONS) {
                val d = engine.view(s).decisions.first()
                val options = d.choices.filter { it.enabled }
                s = engine.choose(s, d.eventId, options[bot.nextInt(options.size)].id)
            }
            if (s.chapterState!!.phase == Phase.ACTIVITIES) {
                val view = engine.view(s)
                val picked = shuffled(view.activities.filter { it.enabled }, bot).take(view.slots)
                s = engine.commitActivities(s, picked.map { it.id })
            }
        }
        return s
    }

    private fun <T> shuffled(
        items: List<T>,
        rng: Rng,
    ): List<T> {
        val m = items.toMutableList()
        for (i in m.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val t = m[i]
            m[i] = m[j]
            m[j] = t
        }
        return m
    }

    private fun fingerprint(
        label: String,
        seed: Long,
        legacies: List<LegacyId>,
    ): String {
        val s = autoplay(seed, legacies)
        val c = s.character
        val body =
            listOf(
                c.ageMonths,
                c.money,
                c.stats,
                c.skills,
                c.views,
                c.tags,
                c.flags,
                c.counters,
                s.npcs,
                s.seen,
                s.trackers,
                s.scheduled,
                s.chronicle,
                s.ending,
                s.chapter,
                s.rngDraws,
                s.worldMonthsElapsed,
            ).joinToString("|")
        return "$label seed=$seed cause=${s.ending?.cause} chapters=${s.chapter} " +
            "age=${c.ageMonths} draws=${s.rngDraws} hash=${body.hashCode()}"
    }

    @Test
    fun `legacy engine reproduces the recorded lives`() {
        val plain = (1L..20L).map { fingerprint("plain", it, emptyList()) }
        val nervous =
            (1L..10L).map {
                fingerprint("nervous_mind", it, listOf(LegacyId("nervous_mind")))
            }
        val actual = (plain + nervous).joinToString("\n", postfix = "\n")

        if (!goldenFile.exists()) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeText(actual)
            fail("Golden recorded at ${goldenFile.absolutePath}. Commit it and re-run.")
        }
        assertEquals(goldenFile.readText().replace("\r\n", "\n"), actual)
    }
}
