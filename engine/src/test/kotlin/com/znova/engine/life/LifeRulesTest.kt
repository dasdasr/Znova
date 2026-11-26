
package com.znova.engine.life

import com.znova.engine.core.CounterKey
import com.znova.engine.core.Rng
import com.znova.engine.core.TrackerId
import com.znova.engine.life.testpack.Evt
import com.znova.engine.life.testpack.Flag
import com.znova.engine.life.testpack.TestWorld
import com.znova.engine.rules.AddCounter
import com.znova.engine.rules.AllOf
import com.znova.engine.rules.Always
import com.znova.engine.rules.AnyOf
import com.znova.engine.rules.Condition
import com.znova.engine.rules.EffectContext
import com.znova.engine.rules.FlagOlderThan
import com.znova.engine.rules.HasFlag
import com.znova.engine.rules.Not
import com.znova.engine.rules.Sequence
import com.znova.engine.rules.SetFlag
import com.znova.engine.rules.TrackerAtLeast
import com.znova.engine.rules.When
import com.znova.engine.state.LifeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LifeRulesTest {

    private val engine = TestWorld.engine()
    private val content = TestWorld.content

    private fun fresh(seed: Long = 1): LifeState =
        engine.start(TestWorld.setup(seed))

    private fun context(seed: Long = 1) =
        EffectContext(
            rng = Rng(seed),
            content = TestWorld.pack,
            gameModifiers = emptyList(),
        )

    private fun Condition.check(state: LifeState): Boolean = test(state)

    @Test
    fun `always condition is true`() {
        assertTrue(Always.test(fresh()))
    }

    @Test
    fun `all of requires every condition`() {
        val state = fresh()

        assertTrue(
            AllOf(
                listOf(
                    Always,
                    Not(HasFlag(Flag.EMPLOYED)),
                ),
            ).test(state),
        )

        assertFalse(
            AllOf(
                listOf(
                    Always,
                    HasFlag(Flag.EMPLOYED),
                ),
            ).test(state),
        )
    }

    @Test
    fun `any of requires at least one condition`() {
        val state = fresh()

        assertTrue(
            AnyOf(
                listOf(
                    HasFlag(Flag.EMPLOYED),
                    Always,
                ),
            ).test(state),
        )

        assertFalse(
            AnyOf(
                listOf(
                    HasFlag(Flag.EMPLOYED),
                    Not(Always),
                ),
            ).test(state),
        )
    }

    @Test
    fun `not negates a condition`() {
        val state = fresh()

        assertTrue(Not(HasFlag(Flag.EMPLOYED)).test(state))
        assertFalse(Not(Always).test(state))
    }

    @Test
    fun `set flag makes it available to conditions`() {
        val state = fresh()
        val updated = SetFlag(Flag.EMPLOYED).apply(state, context())

        assertTrue(HasFlag(Flag.EMPLOYED).test(updated))
        assertFalse(HasFlag(Flag.DEGREE).test(updated))
    }

    @Test
    fun `flag older than checks elapsed months`() {
        val state = fresh()
        val marked = MarkFlag(Flag.EMPLOYED).apply(state, context())

        assertFalse(FlagOlderThanMonths(Flag.EMPLOYED, 1).test(marked))

        val later = marked.copy(
            monthly = marked.monthly!!.copy(
                month = marked.monthly.month + 1,
            ),
        )

        assertTrue(FlagOlderThanMonths(Flag.EMPLOYED, 1).test(later))
        assertFalse(FlagOlderThanMonths(Flag.EMPLOYED, 2).test(later))
    }

    @Test
    fun `flag older than is false when flag is missing`() {
        assertFalse(
            FlagOlderThan(Flag.EMPLOYED, 1).test(fresh()),
        )
    }

    @Test
    fun `sequence applies effects in order`() {
        val state = fresh()
        val sequence = Sequence(
            SetFlag(Flag.EMPLOYED),
            SetFlag(Flag.DEGREE),
        )

        val updated = sequence.apply(state, context())

        assertTrue(HasFlag(Flag.EMPLOYED).test(updated))
        assertTrue(HasFlag(Flag.DEGREE).test(updated))
    }

    @Test
    fun `when applies then branch when condition matches`() {
        val state = fresh()
        val effect = When(
            condition = Not(HasFlag(Flag.EMPLOYED)),
            then = SetFlag(Flag.EMPLOYED),
        )

        val updated = effect.apply(state, context())

        assertTrue(HasFlag(Flag.EMPLOYED).test(updated))
    }

    @Test
    fun `when applies otherwise branch when condition does not match`() {
        val state = fresh()
        val effect = When(
            condition = HasFlag(Flag.EMPLOYED),
            then = SetFlag(Flag.DEGREE),
            otherwise = SetFlag(Flag.BAD_REP),
        )

        val updated = effect.apply(state, context())

        assertFalse(HasFlag(Flag.DEGREE).test(updated))
        assertTrue(HasFlag(Flag.BAD_REP).test(updated))
    }

    @Test
    fun `add counter changes counter condition`() {
        val state = fresh()
        val counter = CounterKey("test_counter")

        val updated = AddCounter(counter, 2).apply(state, context())

        assertTrue(
            com.znova.engine.rules.CounterAtLeast(counter, 2).test(updated),
        )
        assertFalse(
            com.znova.engine.rules.CounterAtLeast(counter, 3).test(updated),
        )
    }

    @Test
    fun `schedule in adds an event to monthly queue`() {
        val state = fresh()
        val event = Evt.MEET_FIXER

        val updated = ScheduleIn(event).apply(state, context())

        val scheduled = updated.monthly?.scheduled.orEmpty()

        assertEquals(1, scheduled.size)
        assertEquals(event, scheduled.single().event)
    }

    @Test
    fun `schedule in preserves event and delay`() {
        val state = fresh()
        val event = Evt.EXAM
        val delay = 3

        val updated = ScheduleIn(
            event = event,
            delayMonths = delay,
        ).apply(state, context())

        val scheduled = updated.monthly?.scheduled?.singleOrNull()

        assertNotNull(scheduled)
        assertEquals(event, scheduled.event)
        assertEquals(state.monthly!!.month + delay, scheduled.dueMonth)
    }

    @Test
    fun `schedule in does nothing without monthly state`() {
        val state = fresh().copy(monthly = null)

        val updated = ScheduleIn(Evt.EXAM).apply(state, context())

        assertEquals(state, updated)
    }

    @Test
    fun `life content has expected event cooldowns`() {
        assertEquals(12, content.events[Evt.MEET_FIXER]?.cooldownMonths)
        assertEquals(18, content.events[Evt.LAYOFF]?.cooldownMonths)
        assertEquals(8, content.events[Evt.QUARREL]?.cooldownMonths)
        assertEquals(10, content.events[Evt.IN_NEED]?.cooldownMonths)
        assertEquals(8, content.events[Evt.WINDFALL]?.cooldownMonths)
        assertEquals(6, content.events[Evt.INSPIRATION]?.cooldownMonths)
        assertEquals(12, content.events[Evt.HEADHUNTER]?.cooldownMonths)
    }

    @Test
    fun `life content limits gossip to one occurrence`() {
        val gossip = content.events[Evt.GOSSIP]

        assertNotNull(gossip)
        assertEquals(1, gossip.maxPerLife)
    }

    private val brokeTracker = TrackerId("broke")

    @Test
    fun `tracker condition accepts zero thresholds when tracker is absent`() {
        assertTrue(
            TrackerAtLeast(id = brokeTracker).test(fresh()),
        )
    }

    @Test
    fun `tracker condition rejects positive thresholds when tracker is absent`() {
        assertFalse(
            TrackerAtLeast(
                id = brokeTracker,
                best = 1,
            ).test(fresh()),
        )
    }

    @Test
    fun `life content validation has no errors`() {
        assertTrue(
            engine.validate().isEmpty(),
            engine.validate().joinToString(separator = "\n"),
        )
    }
}
