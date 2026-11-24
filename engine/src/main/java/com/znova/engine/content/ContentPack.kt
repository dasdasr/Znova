package com.znova.engine.content

import com.znova.engine.core.AchievementId
import com.znova.engine.core.ActivityId
import com.znova.engine.core.CountryId
import com.znova.engine.core.EventId
import com.znova.engine.core.FocusId
import com.znova.engine.core.LegacyId
import com.znova.engine.core.OriginId
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.rules.Effect

class ContentPack internal constructor(
    val events: Map<EventId, EventDef>,
    val activities: Map<ActivityId, ActivityDef>,
    val thoughts: Map<String, ThoughtDef>,
    val countries: Map<CountryId, CountryDef>,
    val origins: Map<OriginId, OriginDef>,
    val legacies: Map<LegacyId, LegacyDef>,
    val achievements: Map<AchievementId, AchievementDef>,
    val unlocks: Map<UnlockId, UnlockDef>,
    val trackers: Map<TrackerId, TrackerDef>,
    val foci: Map<FocusId, FocusDef>,
    val observerNotes: Map<String, ObserverNoteDef>,
    val titles: List<TitleDef>,
    val upkeep: List<Effect>,
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        fun check(
            owner: String,
            effects: List<Effect>,
        ) {
            effects
                .flatMap { it.referencedEvents }
                .filter { it !in events }
                .forEach { errors += "$owner references a non-existent event ${it.id}" }
        }

        fun unlockRef(
            owner: String,
            id: UnlockId?,
        ) {
            if (id != null && id !in unlocks) errors += "$owner references an undeclared unlock ${id.id}"
        }
        events.values.forEach { e ->
            check("event ${e.id.id}", e.onTrigger)
            unlockRef("event ${e.id.id}", e.requiresUnlock)
            e.choices.forEach { check("event ${e.id.id}/${it.id.id}", it.onSuccess + it.onFailure) }
            if (e.choices.isNotEmpty() && e.choices.none { it.visibleWhen.isEmpty() && it.enabledWhen.isEmpty() }) {
                errors += "event ${e.id.id}: no unconditional choice (player may get stuck)"
            }
            if (e.choices
                    .map { it.id }
                    .toSet()
                    .size !=
                e.choices.size
            ) {
                errors += "event ${e.id.id}: duplicate choice IDs"
            }
        }
        activities.values.forEach {
            check("activity ${it.id.id}", it.onSuccess + it.onFailure)
            unlockRef("activity ${it.id.id}", it.requiresUnlock)
        }
        thoughts.values.forEach { check("thought ${it.id}", it.effects) }
        legacies.values.forEach {
            check("legacy ${it.id.id}", it.onStart)
            unlockRef("legacy ${it.id.id}", it.requiresUnlock)
        }
        countries.values.forEach {
            check("country ${it.id.id}", it.onStart)
            unlockRef("country ${it.id.id}", it.requiresUnlock)
        }
        origins.values.forEach {
            check("origin ${it.id.id}", it.effects)
            unlockRef("origin ${it.id.id}", it.requiresUnlock)
        }
        foci.values.forEach { unlockRef("focus ${it.id.id}", it.requiresUnlock) }
        observerNotes.values.forEach { unlockRef("observer ${it.id}", it.requiresUnlock) }
        achievements.values.forEach { a -> a.unlocks.forEach { unlockRef("achievement ${a.id.id}", it) } }
        check("upkeep", upkeep)
        if (countries.isEmpty()) errors += "No countries are registered"
        if (origins.isEmpty()) errors += "No origins are registered"
        return errors
    }
}

fun interface ContentModule {
    fun register(builder: ContentBuilder)
}

class ContentBuilder {
    private val events = LinkedHashMap<EventId, EventDef>()
    private val activities = LinkedHashMap<ActivityId, ActivityDef>()
    private val thoughts = LinkedHashMap<String, ThoughtDef>()
    private val countries = LinkedHashMap<CountryId, CountryDef>()
    private val origins = LinkedHashMap<OriginId, OriginDef>()
    private val legacies = LinkedHashMap<LegacyId, LegacyDef>()
    private val achievements = LinkedHashMap<AchievementId, AchievementDef>()
    private val unlocks = LinkedHashMap<UnlockId, UnlockDef>()
    private val trackers = LinkedHashMap<TrackerId, TrackerDef>()
    private val foci = LinkedHashMap<FocusId, FocusDef>()
    private val observerNotes = LinkedHashMap<String, ObserverNoteDef>()
    private val titles = mutableListOf<TitleDef>()
    private val upkeep = mutableListOf<Effect>()

    private fun <K, V> MutableMap<K, V>.addUnique(
        key: K,
        value: V,
        what: String,
    ) {
        require(put(key, value) == null) { "Duplicate $what: $key" }
    }

    fun event(e: EventDef) = apply { events.addUnique(e.id, e, "event") }

    fun activity(a: ActivityDef) = apply { activities.addUnique(a.id, a, "activity") }

    fun thought(t: ThoughtDef) = apply { thoughts.addUnique(t.id, t, "thought") }

    fun country(c: CountryDef) = apply { countries.addUnique(c.id, c, "country") }

    fun origin(o: OriginDef) = apply { origins.addUnique(o.id, o, "origin") }

    fun legacy(l: LegacyDef) = apply { legacies.addUnique(l.id, l, "legacy") }

    fun achievement(a: AchievementDef) = apply { achievements.addUnique(a.id, a, "achievement") }

    fun unlock(u: UnlockDef) = apply { unlocks.addUnique(u.id, u, "unlock") }

    fun tracker(t: TrackerDef) = apply { trackers.addUnique(t.id, t, "tracker") }

    fun focus(f: FocusDef) = apply { foci.addUnique(f.id, f, "focus") }

    fun observerNote(n: ObserverNoteDef) = apply { observerNotes.addUnique(n.id, n, "observer note") }

    fun title(t: TitleDef) = apply { titles += t }

    fun upkeep(e: Effect) = apply { upkeep += e }

    fun install(module: ContentModule) = apply { module.register(this) }

    fun build() =
        ContentPack(
            events,
            activities,
            thoughts,
            countries,
            origins,
            legacies,
            achievements,
            unlocks,
            trackers,
            foci,
            observerNotes,
            titles.sortedByDescending { it.priority },
            upkeep.toList(),
        )
}

fun contentPack(vararg modules: ContentModule): ContentPack =
    ContentBuilder().also { b -> modules.forEach { b.install(it) } }.build()
