package com.znova.engine.life

import com.znova.engine.core.ActivityId
import com.znova.engine.core.EventId
import com.znova.engine.core.NpcId
import com.znova.engine.core.RoleKey
import com.znova.engine.rules.Bindings
import com.znova.engine.rules.CheckResult
import com.znova.engine.state.ChronicleEntry
import com.znova.engine.state.LifeState
import com.znova.engine.state.Npc
import com.znova.engine.state.Tier

enum class Rarity { COMMON, UNCOMMON, RARE }

enum class StopReason { DECISION_NEEDED, SLOT_FREED, MAJOR_EVENT, RARE_EXPIRING, NEW_INTENT, DEATH }

enum class DecisionKind { EVENT, OVERFLOW, SACRIFICE }

/** Behavior when a bound NPC dies or leaves. */
enum class LossRule { END, CONTINUE }

enum class UnpaidPolicy { PAUSE, DROP, DEBT }

enum class MissedPolicy { CANCEL, POSTPONE }

enum class InteractKind { TALK, HELP, ASK }

object LifeRoles {
    val SELF = RoleKey("self")
    val TARGET = RoleKey("target")
}

/** Built-in memory types; content may define more. */
object MemoryTypes {
    const val MET = "met"
    const val INVITED = "invited"
    const val ABANDONED = "abandoned"
    const val SHARED_SUCCESS = "shared_success"
    const val SHARED_FAILURE = "shared_failure"
}

data class MemoryEntry(
    val npc: NpcId?,
    val type: String,
    val month: Int,
    val source: String,
    val weight: Int = 1,
)

data class FeedEntry(
    val month: Int,
    val tier: Tier,
    val text: String,
    val source: String,
    val kind: String = "note",
    val check: CheckResult? = null,
    val seq: Int = 0,
)

data class Stop(
    val month: Int,
    val reasons: List<StopReason>,
)

data class Pursuit(
    val uid: String,
    val activity: ActivityId,
    val slots: Int,
    val startMonth: Int,
    val progress: Int = 0,
    val participants: Bindings = Bindings.EMPTY,
    val invested: Long = 0,
)

data class Opportunity(
    val uid: String,
    val activity: ActivityId,
    val source: String,
    val rarity: Rarity,
    val expiresMonth: Int?,
    val participant: NpcId? = null,
    val slot: RoleKey? = null,
    val onLost: LossRule = LossRule.END,
    val warned: Boolean = false,
)

data class PendingDecision(
    val uid: String,
    val kind: DecisionKind,
    val eventId: EventId? = null,
    val bindings: Bindings = Bindings.EMPTY,
    val month: Int = 0,
    val offer: Opportunity? = null,
)

data class ScheduledEvent(
    val event: EventId,
    val dueMonth: Int,
    val bindings: Bindings,
    val policy: MissedPolicy,
    val postponeLeft: Int,
)

data class Intent(
    val activity: ActivityId,
    val opportunity: String?,
    val createdMonth: Int,
    val executeMonth: Int,
    val announced: Boolean = false,
)

data class MonthlyLimits(
    val opportunities: Int = 5,
    val feed: Int = 40,
    val memory: Int = 80,
)

/** Serializable state of the monthly cycle. */
data class MonthlyState(
    val month: Int = 0,
    val limits: MonthlyLimits = MonthlyLimits(),
    val pursuits: List<Pursuit> = emptyList(),
    val opportunities: List<Opportunity> = emptyList(),
    val pending: List<PendingDecision> = emptyList(),
    val feed: List<FeedEntry> = emptyList(),
    val memory: List<MemoryEntry> = emptyList(),
    val away: Set<NpcId> = emptySet(),
    val scheduled: List<ScheduledEvent> = emptyList(),
    val intent: Intent? = null,
    val cooldowns: Map<String, Int> = emptyMap(),
    val carried: Map<ActivityId, Int> = emptyMap(),
    val interactions: Map<String, Int> = emptyMap(),
    val lastStop: Stop? = null,
    val scene: Bindings = Bindings.EMPTY,
    val nextUid: Int = 1,
)

internal fun LifeState.mon(): MonthlyState = checkNotNull(monthly) { "This life does not use the monthly cycle" }

internal inline fun LifeState.mapMonthly(f: (MonthlyState) -> MonthlyState): LifeState {
    val m = monthly ?: return this
    return copy(monthly = f(m))
}

internal fun LifeState.scene(): Bindings = monthly?.scene ?: Bindings.EMPTY

internal fun LifeState.isPresent(npc: Npc): Boolean = npc.alive && npc.id !in (monthly?.away ?: emptySet())

internal fun LifeState.isPresentId(id: NpcId): Boolean = npcs.firstOrNull { it.id == id }?.let { isPresent(it) } == true

internal fun LifeState.withFeed(entry: FeedEntry): LifeState {
    val m = mon()
    val stamped = entry.copy(seq = m.nextUid)
    val s = copy(monthly = m.copy(feed = (m.feed + stamped).takeLast(m.limits.feed), nextUid = m.nextUid + 1))
    return if (entry.tier != Tier.MINOR && entry.text.isNotBlank()) {
        s.copy(chronicle = s.chronicle + ChronicleEntry(entry.month, s.character.ageYears, entry.tier, entry.text))
    } else {
        s
    }
}

internal fun LifeState.remember(entry: MemoryEntry): LifeState =
    mapMonthly { it.copy(memory = (it.memory + entry).takeLast(it.limits.memory)) }

internal fun LifeState.allocate(prefix: String): Pair<LifeState, String> {
    val m = mon()
    return copy(monthly = m.copy(nextUid = m.nextUid + 1)) to "$prefix${m.nextUid}"
}

internal fun LifeState.memoryWeight(
    npc: NpcId?,
    type: String?,
    withinMonths: Int?,
): Int {
    val m = monthly ?: return 0
    return m.memory
        .filter { e ->
            (npc == null || e.npc == npc) &&
                (type == null || e.type == type) &&
                (withinMonths == null || m.month - e.month <= withinMonths)
        }.sumOf { it.weight }
}

internal fun LifeState.offer(
    opp: Opportunity,
    text: String,
): LifeState {
    val m = mon()
    return when {
        m.opportunities.size < m.limits.opportunities ->
            mapMonthly { it.copy(opportunities = it.opportunities + opp) }
                .withFeed(FeedEntry(m.month, Tier.MINOR, text, opp.activity.id, "opportunity"))
        opp.rarity == Rarity.RARE -> {
            val (s, uid) = allocate("d")
            s.mapMonthly {
                it.copy(
                    pending = it.pending +
                        PendingDecision(uid, DecisionKind.OVERFLOW, month = it.month, offer = opp),
                )
            }
        }
        else -> this
    }
}
