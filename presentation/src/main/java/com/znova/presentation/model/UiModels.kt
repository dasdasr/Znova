package com.znova.app

import com.znova.engine.content.*
import com.znova.engine.core.*
import com.znova.engine.meta.*
import com.znova.engine.sim.*
import com.znova.engine.state.LifeState
import com.znova.engine.state.Phase

// Maps engine models to UI models. Keep engine-specific fields isolated here.

data class LegacyOption(val id: LegacyId, val label: String)

data class ChoiceUi(val id: ChoiceId, val text: String, val enabled: Boolean, val hint: String?)

data class DecisionUi(val eventId: EventId, val text: String, val choices: List<ChoiceUi>)

data class ActivityUi(val id: ActivityId, val name: String, val enabled: Boolean)

data class ChapterUi(
    val phase: Phase,
    val decision: DecisionUi?,
    val activities: List<ActivityUi>,
    val slots: Int,
) {
    companion object {
        /** Chapter is finished: the player reads the result cards and presses Continue. */
        fun closed() = ChapterUi(Phase.CLOSED, null, emptyList(), 0)
    }
}

data class SummaryUi(
    val title: String,
    val cause: String,
    val ageYears: Int,
    val chronicle: List<String>,
    val achievements: List<String>,
    val unlocks: List<String>,
    val gaps: List<String>,
)

internal fun ChapterView.toUi(): ChapterUi = ChapterUi(
    phase = phase,
    // Only the first decision is presented at a time.
    decision = decisions.firstOrNull()?.let { d ->
        DecisionUi(
            eventId = d.eventId,
            text = d.text,
            choices = d.choices.map {
                ChoiceUi(
                    id = it.id,
                    text = it.label,
                    enabled = it.enabled,
                    hint = it.lockedHint,
                )
            }
        )
    },
    activities = activities.map {
        ActivityUi(
            id = it.id,
            name = it.label,
            enabled = it.enabled,
        )
    },
    slots = slots,
)

internal fun LaboratoryView.toLegacyOptions(): List<LegacyOption> =
    legacies.map { LegacyOption(it.id, it.id.id) }

internal fun ConcludeResult.toUi(life: LifeState): SummaryUi {
    val ending = checkNotNull(life.ending) { "conclude() called for a life that has not ended" }
    return SummaryUi(
        title = summary.title,
        cause = ending.cause,
        ageYears = ending.ageMonths / 12,
        chronicle = life.chronicle.map { "${it.ageYears}: ${it.text}" },
        achievements = newAchievements.map { it.toString() },
        unlocks = newUnlocks.map { it.toString() },
        gaps = archiveGaps.map { it.toString() },
    )
}

/** Normalizes key.toString() into a readable identifier */
internal fun Any.keyLabel(): String =
    toString().substringAfter('(', toString()).substringAfter('=').removeSuffix(")")
