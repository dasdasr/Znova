package com.znova.engine.content

import com.znova.engine.core.AchievementId
import com.znova.engine.core.ActivityId
import com.znova.engine.core.EventId
import com.znova.engine.core.FocusId
import com.znova.engine.core.LegacyId
import com.znova.engine.core.OriginId
import com.znova.engine.core.PlaceId
import com.znova.engine.core.TrackerId
import com.znova.engine.core.UnlockId
import com.znova.engine.rules.Effect
import com.znova.engine.state.World
import com.znova.engine.world.WorldSchema

class ContentPack internal constructor(
    val packId: String,
    val schema: WorldSchema,
    val events: Map<EventId, EventDef>,
    val activities: Map<ActivityId, ActivityDef>,
    val thoughts: Map<String, ThoughtDef>,
    val places: Map<PlaceId, PlaceDef>,
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
    fun pathOf(id: PlaceId): List<PlaceDef> =
        generateSequence(places[id]) { place ->
            place.parent?.let(places::get)
        }.take(32).toList()

    fun lawsOf(id: PlaceId) = pathOf(id).firstNotNullOfOrNull { it.laws }

    fun namesOf(id: PlaceId) = pathOf(id).firstNotNullOfOrNull { it.names }

    fun startYearsOf(id: PlaceId) = pathOf(id).firstNotNullOfOrNull { it.startYears }

    fun moveWorld(
        world: World,
        id: PlaceId,
    ): World {
        val path = pathOf(id)

        require(path.isNotEmpty()) {
            "Unknown place: ${id.id}"
        }

        return world.copy(
            placeId = id,
            placePath = path.map { it.id },
            placeTags = path.flatMap { it.tags }.toSet(),
            placeAxes =
                path.reversed().fold(emptyMap()) { acc, place ->
                    acc + place.axes
                },
            laws = lawsOf(id) ?: emptySet(),
        )
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        fun check(
            owner: String,
            effects: List<Effect>,
        ) {
            effects
                .flatMap { it.referencedEvents }
                .filter { it !in events }
                .forEach {
                    errors += "$owner references a non-existent event ${it.id}"
                }
        }

        fun unlockRef(
            owner: String,
            id: UnlockId?,
        ) {
            if (id != null && id !in unlocks) {
                errors += "$owner references an undeclared unlock ${id.id}"
            }
        }

        events.values.forEach { e ->
            check("event ${e.id.id}", e.onTrigger)
            unlockRef("event ${e.id.id}", e.requiresUnlock)

            e.choices.forEach {
                check("event ${e.id.id}/${it.id.id}", it.onSuccess + it.onFailure)
            }

            if (e.choices.isNotEmpty() &&
                e.choices.none { it.visibleWhen.isEmpty() && it.enabledWhen.isEmpty() }
            ) {
                errors += "event ${e.id.id}: no unconditional choice (player may get stuck)"
            }

            if (e.choices
                    .map { it.id }
                    .toSet()
                    .size != e.choices.size
            ) {
                errors += "event ${e.id.id}: duplicate choice IDs"
            }
        }

        activities.values.forEach {
            check("activity ${it.id.id}", it.onSuccess + it.onFailure)
            unlockRef("activity ${it.id.id}", it.requiresUnlock)
        }

        thoughts.values.forEach {
            check("thought ${it.id}", it.effects)
        }

        legacies.values.forEach {
            check("legacy ${it.id.id}", it.onStart)
            unlockRef("legacy ${it.id.id}", it.requiresUnlock)
        }

        places.values.forEach { p ->
            check("place ${p.id.id}", p.onStart)
            unlockRef("place ${p.id.id}", p.requiresUnlock)

            if (p.parent != null && p.parent !in places) {
                errors += "place ${p.id.id}: unknown parent ${p.parent.id}"
            }

            if (p.startable) {
                if (namesOf(p.id) == null) {
                    errors += "place ${p.id.id}: startable but has no name pool"
                }
                if (lawsOf(p.id) == null) {
                    errors += "place ${p.id.id}: startable but has no laws"
                }
            }
        }

        origins.values.forEach {
            check("origin ${it.id.id}", it.effects)
            unlockRef("origin ${it.id.id}", it.requiresUnlock)
        }

        foci.values.forEach {
            unlockRef("focus ${it.id.id}", it.requiresUnlock)
        }

        observerNotes.values.forEach {
            unlockRef("observer ${it.id}", it.requiresUnlock)
        }

        achievements.values.forEach { a ->
            a.unlocks.forEach {
                unlockRef("achievement ${a.id.id}", it)
            }
        }

        check("upkeep", upkeep)

        if (places.values.none { it.startable }) {
            errors += "No start places are registered"
        }

        if (origins.isEmpty()) {
            errors += "No origins are registered"
        }

        return errors
    }
}

fun interface ContentModule {
    fun register(builder: ContentBuilder)
}

class ContentBuilder(
    val packId: String,
    val schema: WorldSchema,
) {
    private val events = LinkedHashMap<EventId, EventDef>()
    private val activities = LinkedHashMap<ActivityId, ActivityDef>()
    private val thoughts = LinkedHashMap<String, ThoughtDef>()
    private val places = LinkedHashMap<PlaceId, PlaceDef>()
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
        require(put(key, value) == null) {
            "Duplicate $what: $key"
        }
    }

    fun event(e: EventDef) =
        apply {
            events.addUnique(e.id, e, "event")
        }

    fun activity(a: ActivityDef) =
        apply {
            activities.addUnique(a.id, a, "activity")
        }

    fun thought(t: ThoughtDef) =
        apply {
            thoughts.addUnique(t.id, t, "thought")
        }

    fun place(p: PlaceDef) =
        apply {
            places.addUnique(p.id, p, "place")
        }

    fun origin(o: OriginDef) =
        apply {
            origins.addUnique(o.id, o, "origin")
        }

    fun legacy(l: LegacyDef) =
        apply {
            legacies.addUnique(l.id, l, "legacy")
        }

    fun achievement(a: AchievementDef) =
        apply {
            achievements.addUnique(a.id, a, "achievement")
        }

    fun unlock(u: UnlockDef) =
        apply {
            unlocks.addUnique(u.id, u, "unlock")
        }

    fun tracker(t: TrackerDef) =
        apply {
            trackers.addUnique(t.id, t, "tracker")
        }

    fun focus(f: FocusDef) =
        apply {
            foci.addUnique(f.id, f, "focus")
        }

    fun observerNote(n: ObserverNoteDef) =
        apply {
            observerNotes.addUnique(n.id, n, "observer note")
        }

    fun title(t: TitleDef) =
        apply {
            titles += t
        }

    fun upkeep(e: Effect) =
        apply {
            upkeep += e
        }

    fun install(module: ContentModule) =
        apply {
            module.register(this)
        }

    fun build() =
        ContentPack(
            packId = packId,
            schema = schema,
            events = events,
            activities = activities,
            thoughts = thoughts,
            places = places,
            origins = origins,
            legacies = legacies,
            achievements = achievements,
            unlocks = unlocks,
            trackers = trackers,
            foci = foci,
            observerNotes = observerNotes,
            titles = titles.sortedByDescending { it.priority },
            upkeep = upkeep.toList(),
        )
}

fun contentPack(
    packId: String,
    schema: WorldSchema,
    vararg modules: ContentModule,
): ContentPack =
    ContentBuilder(packId, schema)
        .also { builder ->
            modules.forEach { builder.install(it) }
        }.build()
