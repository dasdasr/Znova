package com.znova.engine.life

import com.znova.engine.core.ChoiceId
import com.znova.engine.core.Rng
import com.znova.engine.meta.LifeSetup
import com.znova.engine.state.LifeState

enum class BotKind { PASSIVE, RANDOM, GREEDY }

data class BotRun(
    val kind: BotKind,
    val seed: Long,
    val months: Int,
    val ageYears: Int,
    val ended: Boolean,
    val reachedLimit: Boolean,
    val stops: Int,
    val stopReasons: Map<StopReason, Int>,
    val longestEmpty: Int,
    val started: Map<String, Int>,
    val finalState: LifeState,
)

class LifeBot(
    private val engine: LifeSimEngine,
    private val kind: BotKind,
    private val seed: Long,
) {
    private val rng = Rng(seed xor SALT)

    fun run(
        setup: LifeSetup,
        maxMonths: Int,
    ): BotRun {
        var s = engine.start(setup)
        var months = 0
        var stops = 0
        var empty = 0
        var longest = 0
        val reasons = HashMap<StopReason, Int>()
        while (s.ending == null && months < maxMonths) {
            s = act(s)
            if (s.ending != null) break
            val t = engine.tick(s)
            check(t.advanced) { "Tick did not advance (kind=$kind seed=$seed month=$months)" }
            s = t.state
            months++
            t.stop?.let { stop ->
                stops++
                stop.reasons.forEach { reasons.merge(it, 1, Int::plus) }
            }
            if (s.monthly!!.pursuits.isEmpty()) {
                empty++
                longest = maxOf(longest, empty)
            } else {
                empty = 0
            }
        }
        val started =
            s.seen.entries
                .filter { it.key.startsWith("activity:") }
                .associate { it.key.removePrefix("activity:") to it.value.count }
        return BotRun(
            kind = kind,
            seed = seed,
            months = months,
            ageYears = s.character.ageYears,
            ended = s.ending != null,
            reachedLimit = s.ending == null && months >= maxMonths,
            stops = stops,
            stopReasons = reasons,
            longestEmpty = longest,
            started = started,
            finalState = s,
        )
    }

    private fun act(start: LifeState): LifeState {
        var s = start
        var steps = 0
        var commands = 0
        while (s.ending == null) {
            check(steps++ < MAX_STEPS) { "Bot does not settle (kind=$kind seed=$seed)" }
            val view = engine.view(s)
            val decision = view.decision
            if (decision != null) {
                s = resolve(s, decision)
                continue
            }
            if (commands++ >= MAX_COMMANDS) break
            val cmd = nextCommand(view) ?: break
            when (val r = engine.execute(s, cmd)) {
                is CommandResult.Ok -> s = r.state
                is CommandResult.Rejected -> break
            }
        }
        return s
    }

    private fun resolve(
        s: LifeState,
        d: DecisionPrompt,
    ): LifeState {
        val enabled = d.choices.filter { it.enabled }
        check(enabled.isNotEmpty()) { "A decision without an available option: ${d.event?.id ?: d.kind}" }
        val pick =
            when (kind) {
                BotKind.PASSIVE -> enabled.first()
                BotKind.RANDOM -> enabled[rng.nextInt(enabled.size)]
                BotKind.GREEDY -> enabled.maxByOrNull { it.chance?.chancePercent ?: NEUTRAL_CHANCE }!!
            }
        val r = engine.execute(s, PlayerCommand.Resolve(d.uid, ChoiceId(pick.id)))
        check(r is CommandResult.Ok) { "Resolve was rejected: $r" }
        return (r as CommandResult.Ok).state
    }

    private fun nextCommand(view: LifeView): PlayerCommand? =
        when (kind) {
            BotKind.PASSIVE -> null
            BotKind.GREEDY -> greedy(view)
            BotKind.RANDOM -> random(view)
        }

    private fun options(view: LifeView) = (view.opportunities + view.initiatives).filter { it.available }

    private fun greedy(view: LifeView): PlayerCommand? {
        for (p in view.pursuits) {
            for (slot in p.openSlots) {
                val npc =
                    view.people.firstOrNull {
                        it.present && it.role == slot.role && it.attitude in slot.minAttitude..slot.maxAttitude &&
                            it.id !in p.participants.values
                    }
                if (npc != null) return PlayerCommand.Invite(p.uid, slot.slot, npc.id)
            }
        }
        if (view.slotsFree > 0) {
            val best =
                options(view)
                    .filter { it.slotCost <= view.slotsFree }
                    .maxByOrNull {
                        (it.chance?.chancePercent ?: NEUTRAL_CHANCE) + if (it.opportunity != null) BONUS else 0
                    }
            if (best != null) return PlayerCommand.Start(best.activity, best.opportunity)
        }
        val talk = view.people.firstOrNull { it.canInteract[InteractKind.TALK] == true }
        return talk?.let { PlayerCommand.Interact(it.id, InteractKind.TALK) }
    }

    private fun random(view: LifeView): PlayerCommand? {
        val options = options(view)
        return when (rng.nextInt(CHOICES)) {
            0 -> options.takeIf { it.isNotEmpty() }
                ?.let { o -> rng.pick(o).let { PlayerCommand.Start(it.activity, it.opportunity) } }

            1 -> view.pursuits.takeIf { it.isNotEmpty() }?.let { PlayerCommand.Drop(rng.pick(it).uid) }
            2 -> view.people.takeIf { it.isNotEmpty() }?.let {
                PlayerCommand.Interact(rng.pick(it).id, InteractKind.entries[rng.nextInt(InteractKind.entries.size)])
            }

            3 -> if (view.intent != null) PlayerCommand.DismissIntent else null
            else -> null
        }
    }

    private companion object {
        const val SALT = 0x5DEECE66DL
        const val MAX_STEPS = 60
        const val MAX_COMMANDS = 4
        const val NEUTRAL_CHANCE = 55
        const val BONUS = 20
        const val CHOICES = 6
    }
}

object BotReport {

    fun render(
        title: String,
        runs: List<BotRun>,
    ): String {
        if (runs.isEmpty()) return "No simulation results"

        val totalMonths = runs.sumOf { it.months }
        val safeMonths = totalMonths.coerceAtLeast(1)

        val ended = runs.filter { it.ended }
        val reachedLimit = runs.count { it.reachedLimit }

        val averageMonths = runs.map { it.months }.average()
        val averageAge = ended.takeIf { it.isNotEmpty() }
            ?.map { it.ageYears }
            ?.average()

        val totalStops = runs.sumOf { it.stops }

        val reasons = runs
            .flatMap { it.stopReasons.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.sum() }
            .entries
            .sortedByDescending { it.value }

        val activities = runs
            .flatMap { it.started.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.sum() }
            .entries
            .sortedByDescending { it.value }

        val averageEmpty = runs.map { it.longestEmpty }.average()

        val deathCauses = ended
            .mapNotNull { it.finalState.ending?.cause }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        return buildString {
            appendLine("== $title ==")
            appendLine("Runs: ${runs.size}")
            appendLine("Total simulated months: $totalMonths")
            appendLine("Average life duration: ${"%.1f".format(java.util.Locale.ROOT, averageMonths)} months")
            appendLine("Ended: ${ended.size}/${runs.size}")
            appendLine("Death causes: ${deathCauses.joinToString { "${it.key}=${it.value}" }}")
            appendLine("Reached maxMonths: $reachedLimit/${runs.size}")

            if (averageAge != null) {
                appendLine(
                    "Average ending age: ${"%.1f".format(java.util.Locale.ROOT, averageAge)} years"
                )
            }

            appendLine(
                "Stops per 10 years: ${
                    "%.1f".format(java.util.Locale.ROOT, totalStops * 120.0 / safeMonths)
                }"
            )

            appendLine("Stop reasons: ${reasons.joinToString { "${it.key}=${it.value}" }}")

            appendLine(
                "Average longest empty stretch: ${
                    "%.1f".format(java.util.Locale.ROOT, averageEmpty)
                } months"
            )

            appendLine("Activity frequency per 100 months:")

            activities.take(10).forEach { (activity, count) ->
                val frequency = count * 100.0 / safeMonths
                appendLine(
                    "  $activity: ${"%.2f".format(java.util.Locale.ROOT, frequency)}"
                )
            }

            appendLine("Individual runs:")

            runs.forEach { run ->
                val result = when {
                    run.ended -> "DEAD (${run.finalState.ending?.cause})"
                    run.reachedLimit -> "LIMIT"
                    else -> "STOPPED"
                }

                appendLine(
                    "  seed=${run.seed}: " +
                        "age=${run.ageYears}, " +
                        "months=${run.months}, " +
                        "result=$result, " +
                        "stops=${run.stops}, " +
                        "empty=${run.longestEmpty}"
                )
            }
        }
    }
}
