package com.znova.engine.sim

import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.NamePool
import com.znova.engine.content.OriginDef
import com.znova.engine.content.PlaceDef
import com.znova.engine.core.OriginId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.ViewKey
import com.znova.engine.meta.LifeSetup
import com.znova.engine.state.LifeState
import com.znova.engine.state.WorldDate
import com.znova.engine.world.SkillDef
import com.znova.engine.world.StatDef
import com.znova.engine.world.ViewDef
import com.znova.engine.world.WorldSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CalendarTest {
    private val schema =
        WorldSchema(
            stats = listOf(StatDef(StatKey("vital"), "Vital")),
            skills =
                listOf(
                    SkillDef(SkillKey("wit"), "Wit"),
                    SkillDef(SkillKey("grit"), "Grit"),
                ),
            views = listOf(ViewDef(ViewKey("order"), "Order")),
            vitalStat = StatKey("vital"),
        )

    private val content =
        ContentBuilder("calendar-test", schema)
            .place(
                PlaceDef(
                    id = PlaceId("test"),
                    kind = "country",
                    name = "Test",
                    startable = true,
                    laws = emptySet(),
                    names = NamePool(listOf("Ann"), listOf("Bee")),
                ),
            ).origin(OriginDef(OriginId("plain"), "Plain", ""))
            .build()

    private fun engine(
        lengthMonths: Int,
        mortality: MortalityModel = MortalityModel { _, _ -> 0.0 },
    ) = GameEngine(
        content,
        EngineConfig(
            pacing =
                Pacing(
                    listOf(
                        PacingBand(
                            untilYears = Int.MAX_VALUE,
                            lengthMonths = lengthMonths,
                            budget = TierBudget(major = 0, medium = 0, minor = 0),
                            slots = 1,
                        ),
                    ),
                ),
            mortality = mortality,
            defaultStartYears = 2000..2000,
        ),
    )

    private fun GameEngine.begin(start: WorldDate): LifeState = startLife(LifeSetup(seed = 1, startDate = start))

    private fun GameEngine.pass(state: LifeState): LifeState = commitActivities(openChapter(state), emptyList())

    @Test
    fun `new life starts on the start date`() {
        val state = engine(1).begin(WorldDate(1999, 12))

        assertEquals(WorldDate(1999, 12), state.currentDate)
        assertEquals(0, state.worldMonthsElapsed)
    }

    @Test
    fun `one month chapters cross the year boundary`() {
        val e = engine(1)
        var s = e.begin(WorldDate(1999, 12))

        s = e.pass(s)
        assertEquals(WorldDate(2000, 1), s.currentDate)

        repeat(11) { s = e.pass(s) }
        assertEquals(WorldDate(2000, 12), s.currentDate)

        s = e.pass(s)
        assertEquals(WorldDate(2001, 1), s.currentDate)
    }

    @Test
    fun `multi month chapters accumulate on the calendar`() {
        val e = engine(5)
        var s = e.begin(WorldDate(2000, 11))

        s = e.pass(s)
        assertEquals(WorldDate(2001, 4), s.currentDate)
        s = e.pass(s)
        assertEquals(WorldDate(2001, 9), s.currentDate)
        s = e.pass(s)
        assertEquals(WorldDate(2002, 2), s.currentDate)
    }

    @Test
    fun `world months always equal age months for a newborn`() {
        val e = engine(7)
        var s = e.begin(WorldDate(2000, 1))

        repeat(6) {
            s = e.pass(s)
            assertEquals(s.character.ageMonths, s.worldMonthsElapsed)
        }
    }

    @Test
    fun `the calendar and age stay consistent when the life ends`() {
        val e = engine(12, mortality = MortalityModel { _, _ -> 1.0 })
        val s = e.pass(e.begin(WorldDate(2000, 1)))

        assertNotNull(s.ending)
        assertEquals(s.character.ageMonths, s.worldMonthsElapsed)
    }
}
