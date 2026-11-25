package com.znova.engine.sim

import com.znova.engine.content.ActivityDef
import com.znova.engine.content.ChoiceDef
import com.znova.engine.content.ContentPack
import com.znova.engine.content.EventDef
import com.znova.engine.content.isAvailable
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.core.Rng
import com.znova.engine.core.Skills
import com.znova.engine.core.Stats
import com.znova.engine.core.Views
import com.znova.engine.meta.ConcludeResult
import com.znova.engine.meta.LaboratoryView
import com.znova.engine.meta.LifeSetup
import com.znova.engine.meta.LifeSummary
import com.znova.engine.meta.MetaProfile
import com.znova.engine.rules.CheckMath
import com.znova.engine.rules.CheckResult
import com.znova.engine.rules.Effect
import com.znova.engine.rules.EffectContext
import com.znova.engine.rules.GameModifier
import com.znova.engine.rules.SkillCheck
import com.znova.engine.rules.TextRenderer
import com.znova.engine.state.Card
import com.znova.engine.state.ChapterState
import com.znova.engine.state.Character
import com.znova.engine.state.ChronicleEntry
import com.znova.engine.state.LifeState
import com.znova.engine.state.Phase
import com.znova.engine.state.SeenRecord
import com.znova.engine.state.Tier
import com.znova.engine.state.TrackerState
import com.znova.engine.state.World
import com.znova.engine.state.WorldDate
import com.znova.engine.state.endLife
import com.znova.engine.state.mapCharacter

/**
 * Deterministic, platform-independent simulation engine.
 * All randomness is derived from LifeState.seed and rngDraws.
 */
class GameEngine(
    val content: ContentPack,
    val config: EngineConfig = EngineConfig(),
) {
    fun startLife(
        setup: LifeSetup,
        meta: MetaProfile = MetaProfile(),
    ): LifeState {
        val rng = Rng(setup.seed)
        val countries = content.countries.values.filter { it.isAvailable(meta.unlocks) }
        val country =
            setup.country?.let { content.countries.getValue(it) }
                ?: rng.weighted(countries) { it.weight }
                ?: error("No available countries in ContentPack")

        val skills = Skills.ALL.associateWith { 0 }.toMutableMap()
        repeat(config.startSkillPoints) {
            val open = Skills.ALL.filter { skills.getValue(it) < 4 }
            val key = open[rng.nextInt(open.size)]
            skills[key] = skills.getValue(key) + 1
        }
        val views = Views.ALL.associateWith { rng.nextInt(5) - 2 }
        val years = country.startYears

        var state =
            LifeState(
                seed = setup.seed,
                character =
                    Character(
                        name = setup.name ?: country.names.pick(rng),
                        ageMonths = 0,
                        money = 0,
                        stats = Stats.ALL.associateWith { config.startStat },
                        skills = skills,
                        views = views,
                    ),
                world =
                    World(
                        countryId = country.id,
                        startDate =
                            setup.startDate ?: WorldDate(
                                year = years.first + rng.nextInt(years.last - years.first + 1),
                                month = 1,
                            ),
                        laws = country.laws,
                    ),
                unlocks = meta.unlocks,
                focus = setup.focus?.takeIf { content.foci[it]?.isAvailable(meta.unlocks) == true },
                pastLives = meta.archive.takeLast(config.maxPastLives),
            )

        val chosen =
            setup.legacies
                .mapNotNull { content.legacies[it] }
                .filter { it.isAvailable(meta.unlocks) }
                .distinct()
                .take(config.legacySlots)
        state = state.copy(legacies = chosen.map { it.id }.toSet())
        state = applyAll(state, chosen.flatMap { it.onStart } + country.onStart, rng)

        val requestedOrigin = setup.origin?.let { content.origins[it] }?.takeIf { it.isAvailable(meta.unlocks) }
        val origin =
            requestedOrigin ?: run {
                val candidates =
                    content.origins.values.filter { o ->
                        o.isAvailable(meta.unlocks) && o.conditions.all { it.test(state) }
                    }
                rng.weighted(candidates) { it.weight }
            }
        if (origin != null) {
            state = state.copy(origin = origin.id)
            state = applyAll(state, origin.effects, rng)
        }
        return state.copy(rngDraws = rng.draws)
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    fun openChapter(state: LifeState): LifeState {
        check(state.ending == null) { "Life has already ended." }
        check(state.chapterState == null || state.chapterState.phase == Phase.CLOSED) {
            "Previous chapter is not closed"
        }

        return step(state) { rng ->
            val index = state.chapter + 1
            val band = config.pacing.bandFor(state.character.ageYears)
            var s = state.copy(chapterState = ChapterState(index, band.lengthMonths, Phase.DECISIONS))
            val picked = mutableSetOf<EventId>()
            val groups = mutableSetOf<String>()

            // Scheduled events bypass the regular event budget.
            val (due, later) = s.scheduled.partition { it.dueChapter <= index }
            s = s.copy(scheduled = later)
            for (d in due) {
                if (s.ending != null) break
                val e = content.events[d.event] ?: continue
                if (e.conditions.all { it.test(s) }) {
                    s = trigger(s, e, rng)
                    picked += e.id
                    e.group?.let { groups += it }
                }
            }

            val mods = modifiersFor(s)
            for (tier in Tier.entries) {
                val n = band.budget.roll(tier, rng)
                for (i in 0 until n) {
                    if (s.ending != null) break
                    val pool =
                        content.events.values.filter { event ->
                            val matchesTier = event.tier == tier
                            val notPicked = event.id !in picked
                            val groupAvailable = event.group == null || event.group !in groups

                            matchesTier && notPicked && groupAvailable && isEligible(s, event, index)
                        }
                    val event = rng.weighted(pool) { weightOf(s, it, mods) } ?: continue
                    picked += event.id
                    event.group?.let { groups += it }
                    s = trigger(s, event, rng)
                }
            }

            if (s.ending == null && rng.chance(config.thoughtChance)) {
                val pool =
                    content.thoughts.values.filter { t ->
                        val rec = s.seen["thought:${t.id}"]
                        (rec == null || index - rec.lastChapter > t.cooldownChapters) && t.conditions.all { it.test(s) }
                    }
                rng.weighted(pool) { it.weight }?.let { t ->
                    s = markSeen(s, "thought:${t.id}")
                    s = applyAll(s, t.effects, rng)
                    s = addCard(s, Card(Tier.MINOR, render(t.text, s), t.id, t.skill))
                }
            }

            if (s.ending == null && content.observerNotes.isNotEmpty() && rng.chance(config.observerChance)) {
                val pool =
                    content.observerNotes.values.filter { n ->
                        val rec = s.seen["observer:${n.id}"]
                        val cooldownPassed = rec == null || index - rec.lastChapter > n.cooldownChapters

                        n.isAvailable(s.unlocks) &&
                            cooldownPassed &&
                            n.conditions.all { it.test(s) }
                    }
                rng.weighted(pool) { it.weight }?.let { n ->
                    s = markSeen(s, "observer:${n.id}")
                    s = addCard(s, Card(Tier.MINOR, render(n.text, s), n.id, observer = true))
                }
            }

            s = vitals(s)
            if (s.ending == null && s.chapterState!!.pending.isEmpty()) s = setPhase(s, Phase.ACTIVITIES)
            settle(s)
        }
    }

    fun choose(
        state: LifeState,
        eventId: EventId,
        choiceId: ChoiceId,
    ): LifeState {
        val cs = state.chapterState ?: error("No active chapter")
        check(cs.phase == Phase.DECISIONS) { "Not in the decisions phase" }
        require(eventId in cs.pending) { "Event ${eventId.id} is not pending" }
        val event = content.events.getValue(eventId)
        val choice = event.choices.firstOrNull { it.id == choiceId } ?: error("Choice ${choiceId.id} not found")
        require(isVisible(state, choice) && isEnabled(state, choice)) { "Choice is unavailable" }

        return step(state) { rng ->
            var s = state
            val mods = modifiersFor(s)
            var text = choice.resultText
            var effects = choice.onSuccess
            var result: CheckResult? = null
            choice.check?.let { c ->
                val r = CheckMath.resolve(c, s, mods.sumOf { it.checkBonus(s, c) }, rng)
                result = r
                if (!r.success) {
                    effects = choice.onFailure
                    text = choice.failText ?: choice.resultText
                }
            }
            val rendered = render(text, s)
            s = s.copy(chapterState = cs.copy(pending = cs.pending - eventId))
            s = applyAll(s, effects, rng)
            if (rendered.isNotBlank() || result != null) {
                s = addCard(s, Card(event.tier, rendered, eventId.id, null, result))
            }
            s = vitals(s)
            if (s.ending == null && s.chapterState!!.pending.isEmpty()) s = setPhase(s, Phase.ACTIVITIES)
            settle(s)
        }
    }

    fun commitActivities(
        state: LifeState,
        ids: List<ActivityId>,
    ): LifeState {
        val cs = state.chapterState ?: error("No active chapter")
        check(cs.phase == Phase.ACTIVITIES) { "Not in the activities phase" }
        require(ids.distinct().size == ids.size) { "Duplicate activities" }
        val defs = ids.map { content.activities[it] ?: error("Activity ${it.id} not found") }
        require(defs.sumOf { it.slotCost } <= slotsFor(state)) { "Not enough activity slots" }
        require(defs.all { isAvailable(state, it) }) { "Activity is unavailable" }
        val totalMoneyCost = defs.sumOf { it.moneyCost }
        require(
            totalMoneyCost <= state.character.money.coerceAtLeast(0) || defs.all { it.moneyCost == 0L },
        ) {
            "Not enough money"
        }

        return step(state) { rng ->
            var s = state
            val mods = modifiersFor(s)
            for (a in defs) {
                if (s.ending != null) break
                s = s.mapCharacter { it.copy(money = it.money - a.moneyCost) }
                s = markSeen(s, "activity:${a.id.id}")
                var effects = a.onSuccess
                var text = a.resultText
                var result: CheckResult? = null
                a.check?.let { c ->
                    val r = CheckMath.resolve(c, s, mods.sumOf { it.checkBonus(s, c) }, rng)
                    result = r
                    if (!r.success) {
                        effects = a.onFailure
                        text = a.failText ?: a.resultText
                    }
                }
                val rendered = render(text, s)
                s = applyAll(s, effects, rng)
                if (rendered.isNotBlank()) s = addCard(s, Card(a.resultTier, rendered, a.id.id, null, result))
            }
            finishChapter(s, rng)
        }
    }

    private fun finishChapter(
        state: LifeState,
        rng: Rng,
    ): LifeState {
        var s = state
        if (s.ending == null) {
            s = applyAll(s, content.upkeep, rng)
            val months = s.chapterState!!.lengthMonths
            s = s.mapCharacter { it.copy(ageMonths = it.ageMonths + months) }
            s = vitals(s)
            if (s.ending == null) s = updateTrackers(s)
            if (s.ending == null && rng.chance(config.mortality.deathChance(s, months))) s = s.endLife("natural")
        }
        return flush(s)
    }

    fun view(state: LifeState): ChapterView {
        val cs = state.chapterState ?: error("No active chapter")
        val mods = modifiersFor(state)

        val decisions =
            cs.pending.map { id ->
                val e = content.events.getValue(id)
                DecisionView(
                    eventId = id,
                    tier = e.tier,
                    text = render(e.text, state),
                    choices =
                        e.choices.filter { isVisible(state, it) }.map { c ->
                            ChoiceView(
                                c.id,
                                render(c.label, state),
                                isEnabled(state, c),
                                c.lockedHint,
                                preview(state, c.check, mods),
                            )
                        },
                )
            }
        val activities =
            if (cs.phase != Phase.ACTIVITIES) {
                emptyList()
            } else {
                content.activities.values
                    .filter {
                        it.isAvailable(state.unlocks) &&
                            it.conditions.all { c ->
                                c.test(state)
                            }
                    }.map { a ->
                        ActivityView(
                            a.id,
                            render(a.label, state),
                            a.category,
                            a.slotCost,
                            a.moneyCost,
                            enabled = isAvailable(state, a),
                            check = preview(state, a.check, mods),
                        )
                    }
            }
        return ChapterView(
            cs.index,
            state.character.ageYears,
            cs.lengthMonths,
            cs.phase,
            cs.cards,
            decisions,
            activities,
            slotsFor(state),
        )
    }

    fun summarize(state: LifeState): LifeSummary {
        val ending = checkNotNull(state.ending) { "Life has not ended" }
        val title = content.titles.firstOrNull { t -> t.conditions.all { it.test(state) } }?.text ?: config.defaultTitle
        val byImportance = compareBy<ChronicleEntry> { it.tier.ordinal }.thenBy { it.chapter }
        val highlights =
            state.chronicle
                .sortedWith(byImportance)
                .take(config.highlightsInSummary)
                .sortedBy { it.chapter }
        return LifeSummary(
            state.character.name,
            state.world.countryId,
            title,
            ending.ageMonths / 12,
            ending.cause,
            highlights,
            state.character.tags.keys,
            state.seed,
            flags = state.character.flags.keys,
            counters = state.character.counters,
        )
    }

    // Meta-progress persists between lives.
    fun conclude(
        meta: MetaProfile,
        state: LifeState,
    ): ConcludeResult {
        val summary = summarize(state)
        val earned = content.achievements.values.filter { it.id !in meta.achievements && it.condition.test(state) }
        val newUnlockIds = earned.flatMap { it.unlocks }.toSet() - meta.unlocks
        val earnedIds = earned.map { it.id }.toSet()
        val gaps =
            content.achievements.values
                .filter {
                    it.hidden && it.id !in meta.achievements && it.id !in earnedIds && it.hintWhen?.test(state) == true
                }.mapNotNull { it.hint }
        val updated =
            meta.copy(
                unlocks = meta.unlocks + newUnlockIds,
                achievements = meta.achievements + earnedIds,
                archive = meta.archive + summary,
            )
        return ConcludeResult(updated, summary, earned, newUnlockIds.mapNotNull { content.unlocks[it] }, gaps)
    }

    fun laboratory(meta: MetaProfile): LaboratoryView =
        LaboratoryView(
            countries = content.countries.values.filter { it.isAvailable(meta.unlocks) },
            origins = content.origins.values.filter { it.isAvailable(meta.unlocks) },
            legacies = content.legacies.values.filter { it.isAvailable(meta.unlocks) },
            foci = content.foci.values.filter { it.isAvailable(meta.unlocks) },
            legacySlots = config.legacySlots,
            openGoals = content.achievements.values.filter { !it.hidden && it.id !in meta.achievements },
            archive = meta.archive,
        )

    // Persist RNG progress so the simulation can be reproduced.
    private fun step(
        state: LifeState,
        block: (Rng) -> LifeState,
    ): LifeState {
        val rng = Rng(state.seed, state.rngDraws)
        return block(rng).copy(rngDraws = rng.draws)
    }

    private fun modifiersFor(state: LifeState): List<GameModifier> =
        state.legacies
            .mapNotNull { content.legacies[it] }
            .flatMap { it.gameModifiers } + content.countries[state.world.countryId]?.gameModifiers.orEmpty()

    private fun applyAll(
        state: LifeState,
        effects: List<Effect>,
        rng: Rng,
    ): LifeState {
        val ctx = EffectContext(rng, content, modifiersFor(state))
        var s = state
        for (e in effects) {
            if (s.ending != null) break
            s = e.apply(s, ctx)
        }
        return s
    }

    private fun render(
        text: String,
        state: LifeState,
    ) = TextRenderer.render(text, state, content)

    @Suppress("ReturnCount")
    private fun isEligible(
        s: LifeState,
        e: EventDef,
        index: Int,
    ): Boolean {
        if (!e.isAvailable(s.unlocks)) return false
        val rec = s.seen[e.id.id]
        if (rec != null && (index - rec.lastChapter <= e.cooldownChapters || rec.count >= e.maxPerLife)) return false
        if (!e.conditions.all { it.test(s) }) return false
        return e.choices.isEmpty() || e.choices.any { isVisible(s, it) && isEnabled(s, it) }
    }

    private fun weightOf(
        s: LifeState,
        e: EventDef,
        mods: List<GameModifier>,
    ): Double {
        var w = e.weight
        for (r in e.weightRules) if (r.condition.test(s)) w *= r.multiplier
        s.focus?.let { f -> content.foci[f]?.let { focus -> for (t in e.topics) w *= focus.topicWeights[t] ?: 1.0 } }
        for (m in mods) w = m.eventWeight(s, e, w)
        return w
    }

    private fun trigger(
        state: LifeState,
        e: EventDef,
        rng: Rng,
    ): LifeState {
        var s = markSeen(state, e.id.id)
        s = applyAll(s, e.onTrigger, rng)
        if (s.ending != null) return s
        return if (e.choices.isEmpty()) {
            addCard(s, Card(e.tier, render(e.text, s), e.id.id))
        } else {
            val cs = s.chapterState!!
            s.copy(chapterState = cs.copy(pending = cs.pending + e.id))
        }
    }

    private fun isVisible(
        s: LifeState,
        c: ChoiceDef,
    ): Boolean = modifiersFor(s).fold(c.visibleWhen.all { it.test(s) }) { acc, m -> m.choiceVisible(s, c, acc) }

    private fun isEnabled(
        s: LifeState,
        c: ChoiceDef,
    ): Boolean = modifiersFor(s).fold(c.enabledWhen.all { it.test(s) }) { acc, m -> m.choiceEnabled(s, c, acc) }

    private fun isAvailable(
        s: LifeState,
        a: ActivityDef,
    ) = a.isAvailable(s.unlocks) && a.conditions.all { it.test(s) } && s.character.money >= a.moneyCost

    private fun slotsFor(s: LifeState): Int {
        val base = config.pacing.bandFor(s.character.ageYears).slots
        return modifiersFor(s).fold(base) { acc, m -> m.activitySlots(s, acc) }
    }

    private fun preview(
        s: LifeState,
        check: SkillCheck?,
        mods: List<GameModifier>,
    ): CheckPreview? {
        check ?: return null
        val modifier =
            s.character.skill(check.skill) + CheckMath.bonusFor(check, s) + mods.sumOf { it.checkBonus(s, check) }
        return CheckPreview(check.skill, check.difficulty, CheckMath.chancePercent(modifier, check.difficulty))
    }

    private fun updateTrackers(s: LifeState): LifeState {
        if (content.trackers.isEmpty()) return s
        val updated =
            content.trackers.values.associate { t ->
                val prev = s.trackers[t.id] ?: TrackerState()
                val on = t.condition.test(s)
                val current = if (on) prev.current + 1 else 0
                t.id to TrackerState(current, maxOf(prev.best, current), prev.total + if (on) 1 else 0)
            }
        return s.copy(trackers = updated)
    }

    private fun markSeen(
        s: LifeState,
        key: String,
    ): LifeState {
        val rec = s.seen[key]
        return s.copy(seen = s.seen + (key to SeenRecord((rec?.count ?: 0) + 1, s.currentChapter)))
    }

    private fun addCard(
        s: LifeState,
        card: Card,
    ): LifeState {
        val cs = s.chapterState ?: return s
        return s.copy(chapterState = cs.copy(cards = cs.cards + card))
    }

    private fun setPhase(
        s: LifeState,
        phase: Phase,
    ): LifeState = s.copy(chapterState = s.chapterState?.copy(phase = phase))

    private fun vitals(s: LifeState): LifeState =
        if (s.ending == null && s.character.stat(Stats.BODY) <= 0) s.endLife("body_failure") else s

    private fun settle(s: LifeState): LifeState {
        val cs = s.chapterState ?: return s
        return if (s.ending != null && cs.phase != Phase.CLOSED) flush(s) else s
    }

    // Move chapter cards into the permanent chronicle.
    private fun flush(s: LifeState): LifeState {
        val cs = s.chapterState ?: return s
        val age = s.character.ageYears
        val entries = cs.cards.filter { it.text.isNotBlank() }.map { ChronicleEntry(cs.index, age, it.tier, it.text) }
        return s.copy(
            chapter = cs.index,
            chronicle = s.chronicle + entries,
            chapterState = cs.copy(phase = Phase.CLOSED, pending = emptyList()),
        )
    }
}
