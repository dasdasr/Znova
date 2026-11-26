package com.znova.engine.life

import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.life.testpack.Act
import com.znova.engine.life.testpack.Evt
import com.znova.engine.life.testpack.Role
import com.znova.engine.life.testpack.Skills
import com.znova.engine.life.testpack.Stats
import com.znova.engine.life.testpack.TestWorld
import com.znova.engine.life.testpack.Unlocks
import com.znova.engine.meta.LifeSetup
import com.znova.engine.meta.MetaProfile
import com.znova.engine.rules.Bindings
import com.znova.engine.state.LifeState
import com.znova.engine.state.WorldDate
import com.znova.engine.state.endLife
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LifeSimEngineTest {
    private val quiet = TestWorld.engine(TestWorld.quiet())
    private val intentful = TestWorld.engine(TestWorld.quiet().copy(intentChance = 1.0))

    private fun fresh(
        seed: Long = 1,
        age: Int = 18,
        engine: LifeSimEngine = quiet,
    ): LifeState = engine.start(TestWorld.setup(seed, age))

    private fun LifeSimEngine.ok(
        s: LifeState,
        cmd: PlayerCommand,
    ): LifeState {
        val r = execute(s, cmd)
        when (r) {
            is CommandResult.Ok -> return r.state
            is CommandResult.Rejected -> error(
                """
            Command was rejected.
            Command: $cmd
            Reason: ${r.reason}
            Detail: ${r.detail}
            State age: ${s.character.ageMonths / 12}
            Month: ${s.mon().month}
            Pursuits: ${s.mon().pursuits}
            """.trimIndent()
            )
        }
    }

    private fun LifeSimEngine.rejected(
        s: LifeState,
        cmd: PlayerCommand,
        reason: Reject,
    ) {
        val r = execute(s, cmd)
        assertTrue(r is CommandResult.Rejected, "Expected a rejection but got $r")
        r as CommandResult.Rejected
        assertEquals(reason, r.reason)
        assertEquals(s, r.state, "A rejected command must not change the state")
    }

    private fun withDecision(
        s: LifeState,
        event: EventId,
        bindings: Bindings = Bindings.EMPTY,
    ) = s.mapMonthly {
        it.copy(pending = it.pending + PendingDecision("d900", DecisionKind.EVENT, event, bindings, it.month))
    }

    private fun withOpp(
        s: LifeState,
        o: Opportunity,
    ) = s.mapMonthly { it.copy(opportunities = it.opportunities + o) }

    private fun LifeState.npc(name: String) = npcs.first { it.name == name }

    @Test
    fun `start supports age scenario people and past`() {
        val s = fresh(7, 21)
        assertEquals(21, s.character.ageYears)
        assertEquals(2, s.character.skill(Skills.INTELLECT))
        assertEquals(60, s.character.money)
        assertEquals(listOf("Anya", "Boris", "Mr. Volkov"), s.npcs.map { it.name })
        assertEquals(0, s.mon().month)
        assertTrue(s.chronicle.single().chapter < 0)
        val random = quiet.start(LifeSetup(seed = 3, scenario = "city_youth"))
        assertTrue(random.character.ageYears in 14..25)
    }

    @Test
    fun `calendar crosses the year boundary`() {
        val start = LifeSetup(seed = 1, scenario = "city_youth", startAgeYears = 18, startDate = WorldDate(1999, 12))
        var s = quiet.start(start)
        assertEquals(WorldDate(1999, 12), s.currentDate)
        s = quiet.tick(s).state
        assertEquals(WorldDate(2000, 1), s.currentDate)
        repeat(12) { s = quiet.tick(s).state }
        assertEquals(WorldDate(2001, 1), s.currentDate)
    }

    @Test
    fun `tick advances exactly one month`() {
        val s = fresh()
        val t = quiet.tick(s)
        assertTrue(t.advanced)
        assertEquals(s.character.ageMonths + 1, t.state.character.ageMonths)
        assertEquals(s.worldMonthsElapsed + 1, t.state.worldMonthsElapsed)
        assertEquals(s.mon().month + 1, t.state.mon().month)
    }

    @Test
    fun `a pending decision blocks the tick until it is resolved`() {
        val s = withDecision(fresh(), Evt.ILLNESS)
        val blocked = quiet.tick(s)
        assertTrue(!blocked.advanced)
        assertEquals(listOf(StopReason.DECISION_NEEDED), blocked.stop!!.reasons)
        assertEquals(s.mon().month, blocked.state.mon().month)
        quiet.rejected(s, PlayerCommand.Start(Act.REST_HOME), Reject.DECISION_PENDING)
        val view = quiet.view(s)
        assertEquals("d900", view.decision!!.uid)
        assertTrue(view.decision!!.choices.any { it.enabled })
        val resolved = quiet.ok(s, PlayerCommand.Resolve("d900", ChoiceId("bed_rest")))
        assertTrue(quiet.tick(resolved).advanced)
    }

    @Test
    fun `a pursuit takes a slot progresses and completes`() {
        var s = quiet.ok(fresh(), PlayerCommand.Start(Act.REST_HOME))
        val body = s.character.stat(Stats.BODY)
        assertEquals(1, s.mon().pursuits.size)
        s = quiet.tick(s).state
        assertEquals(1, s.mon().pursuits.single().progress)
        val last = quiet.tick(s)
        assertTrue(last.state.mon().pursuits.isEmpty())
        assertEquals(1, last.state.seenCount("done:rest_home"))
        assertTrue(StopReason.SLOT_FREED in last.stop!!.reasons)
        assertTrue(last.state.character.stat(Stats.BODY) >= body + 4)
    }

    @Test
    fun `dropping costs progress and blocks an instant restart`() {
        var s = fresh().let {
            it.copy(character = it.character.copy(money = 500))
        }

        s = quiet.ok(s, PlayerCommand.Start(Act.TRAIN_BODY))

        repeat(2) {
            s = quiet.tick(s).state
        }

        val pursuit = s.mon().pursuits.single()
        val progressBeforeDrop = pursuit.progress
        val expectedCarried = progressBeforeDrop * 50 / 100

        s = quiet.ok(s, PlayerCommand.Drop(pursuit.uid))

        assertTrue(s.mon().pursuits.isEmpty())
        assertEquals(
            expectedCarried.takeIf { it > 0 },
            s.mon().carried[Act.TRAIN_BODY],
        )

        quiet.rejected(
            s,
            PlayerCommand.Start(Act.TRAIN_BODY),
            Reject.ON_COOLDOWN,
        )

        repeat(2) {
            s = quiet.tick(s).state
        }

        quiet.rejected(
            s,
            PlayerCommand.Start(Act.TRAIN_BODY),
            Reject.ON_COOLDOWN,
        )

        s = quiet.tick(s).state
        s = quiet.ok(s, PlayerCommand.Start(Act.TRAIN_BODY))

        assertEquals(1, s.mon().pursuits.single().progress)
    }

    @Test
    fun `an unpaid pursuit pauses and resumes when money is back`() {
        var s = quiet.ok(fresh(), PlayerCommand.Start(Act.STUDY_EVENING))
        s = s.copy(character = s.character.copy(money = 0))
        s = quiet.tick(s).state
        assertEquals(0, s.mon().pursuits.single().progress)
        assertTrue(s.mon().feed.any { it.kind == "pursuit.paused" })
        s = s.copy(character = s.character.copy(money = 100))
        s = quiet.tick(s).state
        assertEquals(1, s.mon().pursuits.single().progress)
        assertEquals(50, s.character.money)
    }

    @Test
    fun `slots are limited and replace is atomic`() {
        var s = fresh()
        s = quiet.ok(s, PlayerCommand.Start(Act.REST_HOME))
        s = quiet.ok(s, PlayerCommand.Start(Act.TRAIN_BODY))
        s = quiet.ok(s, PlayerCommand.Start(Act.STUDY_EVENING))
        quiet.rejected(s, PlayerCommand.Start(Act.READ_LIBRARY), Reject.NO_FREE_SLOT)
        val option = quiet.view(s).initiatives.first { it.activity == Act.READ_LIBRARY }
        assertEquals(3, option.replace.size)
        val uid = s.mon().pursuits.first().uid
        quiet.rejected(s, PlayerCommand.Replace(uid, Act.JOB_FACTORY), Reject.NOT_AVAILABLE)
        val replaced = quiet.ok(s, PlayerCommand.Replace(uid, Act.READ_LIBRARY))
        assertTrue(replaced.mon().pursuits.any { it.activity == Act.READ_LIBRARY })
        assertTrue(replaced.mon().pursuits.none { it.uid == uid })
    }


    @Test
    fun `fewer slots force the player to choose a sacrifice`() {
        val config = TestWorld.quiet().copy(
            slotBands = listOf(
                SlotBand(untilYears = 60, slots = 3),
                SlotBand(untilYears = Int.MAX_VALUE, slots = 2),
            ),
        )
        val engine = TestWorld.engine(config)

        var s = engine.start(TestWorld.setup(seed = 1L, age = 59))

        s = engine.ok(s, PlayerCommand.Start(Act.REST_HOME))
        s = engine.ok(s, PlayerCommand.Start(Act.TRAIN_BODY))
        s = engine.ok(s, PlayerCommand.Start(Act.STUDY_EVENING))

        // Advance to the next year.
        s = s.copy(
            character = s.character.copy(ageMonths = 60 * 12),
        )

        assertEquals(60, s.character.ageMonths / 12)
        assertEquals(2, config.slotsFor(s.character.ageMonths / 12))
        assertEquals(3, s.mon().pursuits.sumOf { it.slots })

        val t = engine.tick(s)

        val decision = assertNotNull(
            engine.view(t.state).decision,
            "Expected a sacrifice decision after exceeding the slot limit",
        )

        assertEquals(DecisionKind.SACRIFICE, decision.kind)
        assertEquals(3, decision.choices.size)

        val after = engine.ok(
            t.state,
            PlayerCommand.Resolve(
                decision.uid,
                ChoiceId(decision.choices.first().id),
            ),
        )

        assertEquals(2, after.mon().pursuits.size)
        assertNull(engine.view(after).decision)
    }


    @Test
    fun `a rare opportunity warns once and then expires`() {
        val s0 = fresh()
        val rare = Opportunity("o900", Act.STUDIO_CONTRACT, "test", Rarity.RARE, s0.mon().month + 2)
        val t1 = quiet.tick(withOpp(s0, rare))
        assertTrue(StopReason.RARE_EXPIRING in t1.stop!!.reasons)
        assertTrue(t1.state.mon().opportunities.single().warned)
        val t2 = quiet.tick(t1.state)
        assertTrue(t2.state.mon().opportunities.isEmpty())
        assertTrue(t2.state.mon().feed.any { it.kind == "opportunity.expired" })
        assertTrue(t2.stop == null || StopReason.RARE_EXPIRING !in t2.stop!!.reasons)
    }

    @Test
    fun `a full list turns a rare offer into a decision and drops common ones`() {
        var s = fresh()
        repeat(5) { i ->
            s = withOpp(s, Opportunity("o$i", Act.JOB_SHOP, "test", Rarity.COMMON, null))
        }
        val common = Opportunity("c1", Act.JOB_FACTORY, "test", Rarity.COMMON, null)
        assertEquals(5, s.offer(common, "").mon().opportunities.size)
        assertTrue(s.offer(common, "").mon().pending.isEmpty())
        val rare = Opportunity("r1", Act.STUDIO_CONTRACT, "test", Rarity.RARE, null)
        val queued = s.offer(rare, "")
        assertEquals(5, queued.mon().opportunities.size)
        val decision = quiet.view(queued).decision!!
        assertEquals(DecisionKind.OVERFLOW, decision.kind)
        assertTrue(decision.choices.any { it.id == "decline" })
        val swapped = quiet.ok(queued, PlayerCommand.Resolve(decision.uid, ChoiceId("swap:o0")))
        assertTrue(swapped.mon().opportunities.any { it.uid == "r1" })
        assertTrue(swapped.mon().opportunities.none { it.uid == "o0" })
        val declined = quiet.ok(queued, PlayerCommand.Resolve(decision.uid, ChoiceId("decline")))
        assertEquals(5, declined.mon().opportunities.size)
    }

    @Test
    fun `reasons of one month merge into one stop and do not repeat`() {
        val s0 = fresh(engine = intentful)
        val rare = Opportunity("o900", Act.STUDIO_CONTRACT, "test", Rarity.RARE, s0.mon().month + 2)
        val t1 = intentful.tick(withOpp(s0, rare))
        val reasons = t1.stop!!.reasons
        assertTrue(StopReason.RARE_EXPIRING in reasons && StopReason.NEW_INTENT in reasons)
        assertEquals(reasons.toSet().size, reasons.size)
        val t2 = intentful.tick(t1.state)
        val again = t2.stop?.reasons.orEmpty()
        assertTrue(StopReason.RARE_EXPIRING !in again && StopReason.NEW_INTENT !in again)
    }

    @Test
    fun `an intent is visible ahead can be dismissed and never overwrites a busy slot`() {
        val s0 = fresh(engine = intentful)
        val t1 = intentful.tick(s0)
        val intent = assertNotNull(t1.state.mon().intent)
        assertTrue(intent.executeMonth >= intent.createdMonth + 1)
        assertNotNull(intentful.view(t1.state).intent)
        val dismissed = intentful.ok(t1.state, PlayerCommand.DismissIntent)
        assertNull(dismissed.mon().intent)
        intentful.rejected(dismissed, PlayerCommand.DismissIntent, Reject.NO_INTENT)

        var busy = t1.state
        busy = intentful.ok(busy, PlayerCommand.Start(Act.REST_HOME))
        busy = intentful.ok(busy, PlayerCommand.Start(Act.TRAIN_BODY))
        busy = intentful.ok(busy, PlayerCommand.Start(Act.STUDY_EVENING))
        val before = busy.mon().pursuits.map { it.uid }
        val t2 = intentful.tick(busy)
        assertEquals(before, t2.state.mon().pursuits.map { it.uid })
        assertNull(t2.state.mon().intent)

        var full = fresh(age = 14, engine = intentful)
        full = intentful.ok(full, PlayerCommand.Start(Act.REST_HOME))
        full = intentful.ok(full, PlayerCommand.Start(Act.TRAIN_BODY))
        assertNull(intentful.tick(full).state.mon().intent)
    }

    @Test
    fun `a dying participant ends the dependent pursuit`() {
        val s0 = fresh()
        val mentor = s0.npc("Mr. Volkov")
        val opp = Opportunity("o900", Act.MENTOR_LESSONS, "test", Rarity.UNCOMMON, null, mentor.id, Role.MENTOR)
        val s1 = quiet.ok(withOpp(s0, opp), PlayerCommand.Start(Act.MENTOR_LESSONS, "o900"))
        assertEquals(mentor.id, s1.mon().pursuits.single().participants.slots[Role.MENTOR])
        val dead = s1.copy(npcs = s1.npcs.map { if (it.id == mentor.id) it.copy(alive = false) else it })
        val t = quiet.tick(dead)
        assertTrue(t.state.mon().pursuits.isEmpty())
        assertTrue(StopReason.SLOT_FREED in t.stop!!.reasons)
        assertTrue(t.state.mon().feed.any { it.kind == "pursuit.partner_lost" })
    }

    @Test
    fun `a decision whose participant died is dropped and the rest of the world goes on`() {
        val s0 = fresh()
        val anya = s0.npc("Anya")
        val queued = withDecision(s0, Evt.QUARREL, Bindings(mapOf(Role.ALLY to anya.id)))
        val dead = queued.copy(npcs = queued.npcs.map { if (it.id == anya.id) it.copy(alive = false) else it })
        assertNull(quiet.view(dead).decision)
        quiet.rejected(dead, PlayerCommand.Resolve("d900", ChoiceId("apologize")), Reject.UNKNOWN_DECISION)
        assertTrue(quiet.tick(dead).advanced)
    }

    @Test
    fun `with two friends only the bound participant changes`() {
        val s0 = fresh()
        val anya = s0.npc("Anya")
        val boris = s0.npc("Boris")
        val s1 = withDecision(s0, Evt.QUARREL, Bindings(mapOf(Role.ALLY to boris.id)))
        val s2 = quiet.ok(s1, PlayerCommand.Resolve("d900", ChoiceId("apologize")))
        assertEquals(boris.attitude + 2, s2.npc("Boris").attitude)
        assertEquals(anya.attitude, s2.npc("Anya").attitude)
        assertTrue(s2.mon().memory.any { it.npc == boris.id && it.type == "apologized" })
        assertTrue(s2.mon().memory.none { it.npc == anya.id && it.type == "apologized" })
    }

    @Test
    fun `memory changes odds and can make a friend refuse`() {
        val s0 = fresh()
        val anya = s0.npc("Anya")
        val opp = Opportunity("o900", Act.GARAGE_BAND, "test", Rarity.UNCOMMON, null, anya.id, Role.ALLY)
        val base = withOpp(s0.mapMonthly { it.copy(memory = emptyList()) }, opp)
        val plain = quiet.view(base).opportunities.single()
        assertEquals(0, plain.chance!!.memoryBonus)
        val liked = base.remember(MemoryEntry(anya.id, MemoryTypes.SHARED_SUCCESS, 0, "test"))
        val better = quiet.view(liked).opportunities.single()
        assertEquals(1, better.chance!!.memoryBonus)
        assertTrue(better.chance!!.chancePercent >= plain.chance!!.chancePercent)
        val burned = base.remember(MemoryEntry(anya.id, MemoryTypes.ABANDONED, 0, "test", 4))
        val refused = quiet.view(burned).opportunities.single()
        assertTrue(!refused.available)
        assertEquals(Reject.PARTICIPANT_REFUSES, refused.whyNot.single().reason)
    }

    @Test
    fun `interaction without a new reason does not repeat its effect`() {
        val s0 = fresh()
        val anya = s0.npc("Anya")
        val s1 = quiet.ok(s0, PlayerCommand.Interact(anya.id, InteractKind.TALK))
        assertEquals(anya.attitude + 1, s1.npc("Anya").attitude)
        quiet.rejected(s1, PlayerCommand.Interact(anya.id, InteractKind.TALK), Reject.NOTHING_NEW)
        val s2 = quiet.tick(s1).state
        quiet.rejected(s2, PlayerCommand.Interact(anya.id, InteractKind.TALK), Reject.NOTHING_NEW)
        val s3 = s2.remember(MemoryEntry(anya.id, "visit", s2.mon().month, "test"))
        assertEquals(
            anya.attitude + 2,
            quiet.ok(s3, PlayerCommand.Interact(anya.id, InteractKind.TALK)).npc("Anya").attitude
        )
    }

    @Test
    fun `significant people come first`() {
        val s0 = fresh().remember(MemoryEntry(fresh().npc("Boris").id, "x", 0, "t", 5))
        val view = quiet.view(s0)
        assertTrue(view.significant.size in 1..4)
        assertEquals("Boris", view.significant.first().name)
    }

    @Test
    fun `view does not change state`() {
        val s = fresh()
        assertEquals(s, s.also { quiet.view(it) })
        assertEquals(quiet.view(s), quiet.view(s))
    }

    @Test
    fun `death ends the month at once and the vital stat is checked after effects`() {
        val s0 = fresh()
        val frail = s0.copy(character = s0.character.copy(stats = s0.character.stats + (Stats.BODY to 0)))
        val t = quiet.tick(frail)
        assertEquals("body_failure", t.state.ending!!.cause)
        assertTrue(StopReason.DEATH in t.stop!!.reasons)
        val again = quiet.tick(t.state)
        assertTrue(!again.advanced)
    }

    @Test
    fun `concluding a life opens content for the next one`() {
        val dead = fresh().endLife("natural")
        val result = quiet.conclude(MetaProfile(), dead)
        assertTrue(Unlocks.SCRAPBOOK in result.meta.unlocks)
        assertTrue(result.newAchievements.any { it.id.id == "mortal" })
        val next = quiet.start(TestWorld.setup(2, 18), result.meta)
        assertTrue(Unlocks.SCRAPBOOK in next.unlocks)
        assertEquals(1, result.meta.archive.size)
        assertNotNull(quiet.summarize(dead).title)
    }
}
