package com.znova.app

import androidx.lifecycle.ViewModel
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.core.LegacyId
import com.znova.engine.meta.LifeSetup
import com.znova.engine.meta.MetaProfile
import com.znova.engine.sim.GameEngine
import com.znova.engine.state.LifeState
import com.znova.engine.state.Phase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.random.Random

/**
 * Coordinates the game loop between the laboratory, life, and summary screens.
 * Delegates game logic to [GameEngine] and keeps UI state in memory.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val engine: GameEngine,
) : ViewModel() {

    enum class Screen {
        LABORATORY,
        LIFE,
        SUMMARY,
        MAP,
    }

    data class UiState(
        val screen: Screen = Screen.LABORATORY,
        val meta: MetaProfile = MetaProfile(),
        val legacies: List<LegacyOption> = emptyList(),
        val selectedLegacies: Set<LegacyId> = emptySet(),
        val seed: Long = 0L,
        val life: LifeState? = null,
        val chapter: ChapterUi? = null,
        val pickedActivities: Set<ActivityId> = emptySet(),
        val summary: SummaryUi? = null,
    )

    private val _state = MutableStateFlow(laboratoryState(MetaProfile()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun toggleLegacy(id: LegacyId) = _state.update { s ->
        val next = when {
            id in s.selectedLegacies -> s.selectedLegacies - id
            s.selectedLegacies.size < MAX_LEGACIES -> s.selectedLegacies + id
            else -> s.selectedLegacies
        }
        s.copy(selectedLegacies = next)
    }

    fun rerollSeed() = _state.update { it.copy(seed = Random.nextLong()) }

    fun startLife() {
        val s = _state.value
        val setup = LifeSetup(s.seed, legacies = s.selectedLegacies.toList())
        advance(engine.startLife(setup, s.meta))
    }

    fun choose(eventId: EventId, choiceId: ChoiceId) {
        val life = _state.value.life ?: return
        advance(engine.choose(life, eventId, choiceId))
    }

    fun toggleActivity(id: ActivityId) = _state.update { s ->
        val slots = s.chapter?.slots ?: 0
        val next = when {
            id in s.pickedActivities -> s.pickedActivities - id
            s.pickedActivities.size < slots -> s.pickedActivities + id
            else -> s.pickedActivities
        }
        s.copy(pickedActivities = next)
    }

    fun commit() {
        val s = _state.value
        val life = s.life ?: return
        advance(engine.commitActivities(life, s.pickedActivities.toList()))
    }

    fun continueChapter() {
        val life = _state.value.life ?: return
        advance(engine.openChapter(life))
    }

    fun backToLaboratory() = _state.update { laboratoryState(it.meta) }


    fun openMap() {
        _state.update { it.copy(screen = Screen.MAP) }
    }

    fun closeMap() {
        _state.update {
            it.copy(
                screen = when {
                    it.summary != null -> Screen.SUMMARY
                    it.life != null -> Screen.LIFE
                    else -> Screen.LABORATORY
                },
            )
        }
    }

    /** Routes the result of an engine operation to the appropriate screen. */
    private fun advance(start: LifeState) {
        var life = start
        if (life.ending == null && life.chapterState == null) life = engine.openChapter(life)
        if (life.ending != null) finish(life) else show(life)
    }

    private fun show(life: LifeState) {
        val phase = life.chapterState?.phase ?: Phase.CLOSED
        val chapter = if (phase == Phase.CLOSED) ChapterUi.closed() else engine.view(life).toUi()
        _state.update {
            it.copy(screen = Screen.LIFE, life = life, chapter = chapter, pickedActivities = emptySet())
        }
    }

    private fun finish(life: LifeState) {
        val result = engine.conclude(_state.value.meta, life)
        _state.update {
            it.copy(screen = Screen.SUMMARY, meta = result.meta, life = life, summary = result.toUi(life))
        }
    }

    private fun laboratoryState(meta: MetaProfile) = UiState(
        screen = Screen.LABORATORY,
        meta = meta,
        legacies = engine.laboratory(meta).toLegacyOptions(),
        seed = Random.nextLong(),
    )

    companion object {
        const val MAX_LEGACIES = 3
    }
}
