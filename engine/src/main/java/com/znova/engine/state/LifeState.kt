package com.znova.engine.state

import com.znova.engine.core.CounterKey
import com.znova.engine.core.CountryId
import com.znova.engine.core.EventId
import com.znova.engine.core.FlagKey
import com.znova.engine.core.FocusId
import com.znova.engine.core.LawKey
import com.znova.engine.core.LegacyId
import com.znova.engine.core.NpcId
import com.znova.engine.core.OriginId
import com.znova.engine.core.RoleKey
import com.znova.engine.core.SkillKey
import com.znova.engine.core.StatKey
import com.znova.engine.core.TagKey
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.core.ViewKey
import com.znova.engine.meta.LifeSummary
import com.znova.engine.rules.CheckResult

private const val MONTHS_PER_YEAR = 12

enum class Tier { MAJOR, MEDIUM, MINOR }

enum class Phase { DECISIONS, ACTIVITIES, CLOSED }

data class Character(
    val name: String,
    val ageMonths: Int,
    val money: Long,
    val stats: Map<StatKey, Int>,
    val skills: Map<SkillKey, Int>,
    val views: Map<ViewKey, Int>,
    val tags: Map<TagKey, Int> = emptyMap(),
    val flags: Map<FlagKey, Int> = emptyMap(),
    val counters: Map<CounterKey, Int> = emptyMap(),
) {
    val ageYears: Int get() = ageMonths / 12

    fun stat(key: StatKey): Int = stats[key] ?: 0

    fun skill(key: SkillKey): Int = skills[key] ?: 0

    fun view(key: ViewKey): Int = views[key] ?: 0

    fun tagLevel(key: TagKey): Int = tags[key] ?: 0

    fun hasFlag(key: FlagKey): Boolean = key in flags

    fun counter(key: CounterKey): Int = counters[key] ?: 0
}

data class World(
    val countryId: CountryId,
    val startDate: WorldDate,
    val laws: Set<LawKey>,
)

data class WorldDate(
    val year: Int,
    val month: Int,
) {
    init {
        require(month in 1..MONTHS_PER_YEAR) {
            "Month must be between 1 and $MONTHS_PER_YEAR"
        }
    }
}

data class Npc(
    val id: NpcId,
    val name: String,
    val role: RoleKey,
    val attitude: Int = 0,
    val tags: Set<TagKey> = emptySet(),
    val alive: Boolean = true,
)

data class Scheduled(
    val event: EventId,
    val dueChapter: Int,
)

data class SeenRecord(
    val count: Int,
    val lastChapter: Int,
)

data class Card(
    val tier: Tier,
    val text: String,
    val source: String? = null,
    val voice: SkillKey? = null,
    val check: CheckResult? = null,
    val observer: Boolean = false,
)

data class ChronicleEntry(
    val chapter: Int,
    val ageYears: Int,
    val tier: Tier,
    val text: String,
)

data class ChapterState(
    val index: Int,
    val lengthMonths: Int,
    val phase: Phase,
    val cards: List<Card> = emptyList(),
    val pending: List<EventId> = emptyList(),
)

data class TrackerState(
    val current: Int = 0,
    val best: Int = 0,
    val total: Int = 0,
)

data class LifeEnding(
    val cause: String,
    val ageMonths: Int,
    val chapter: Int,
)

data class LifeState(
    val seed: Long,
    val rngDraws: Long = 0,
    val chapter: Int = 0,
    val character: Character,
    val world: World,
    val worldMonthsElapsed: Int = 0,
    val origin: OriginId? = null,
    val npcs: List<Npc> = emptyList(),
    val legacies: Set<LegacyId> = emptySet(),
    val unlocks: Set<UnlockId> = emptySet(),
    val focus: FocusId? = null,
    val trackers: Map<TrackerId, TrackerState> = emptyMap(),
    val seen: Map<String, SeenRecord> = emptyMap(),
    val scheduled: List<Scheduled> = emptyList(),
    val chronicle: List<ChronicleEntry> = emptyList(),
    val pastLives: List<LifeSummary> = emptyList(),
    val chapterState: ChapterState? = null,
    val ending: LifeEnding? = null,
) {
    val currentDate: WorldDate
        get() {
            val totalMonths = world.startDate.month - 1 + worldMonthsElapsed

            return WorldDate(
                year = world.startDate.year + totalMonths / 12,
                month = totalMonths % 12 + 1,
            )
        }

    val currentYear: Int
        get() = currentDate.year

    val currentMonth: Int
        get() = currentDate.month

    val currentChapter: Int
        get() = chapterState?.index ?: chapter

    fun seenCount(eventId: String): Int = seen[eventId]?.count ?: 0
}

internal inline fun LifeState.mapCharacter(f: (Character) -> Character): LifeState = copy(character = f(character))

internal fun LifeState.endLife(cause: String): LifeState =
    if (ending != null) {
        this
    } else {
        copy(
            ending =
                LifeEnding(
                    cause,
                    character.ageMonths,
                    currentChapter,
                ),
        )
    }
