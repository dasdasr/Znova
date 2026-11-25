package com.znova.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.znova.app.ChapterUi
import com.znova.app.DecisionUi
import com.znova.app.GameViewModel
import com.znova.app.keyLabel
import com.znova.engine.core.ActivityId
import com.znova.engine.core.ChoiceId
import com.znova.engine.core.EventId
import com.znova.engine.state.Phase
import com.znova.engine.state.Tier
import com.znova.presentation.ui.laboratory.LaboratoryScreen
import com.znova.presentation.ui.life.LifeScreen
import com.znova.presentation.ui.summary.SummaryScreen
import com.znova.ui.map.MapScreen
import androidx.compose.foundation.lazy.grid.items as gridItems
import com.znova.engine.state.Card as GameCard

/** Bare Compose, no design: the goal is to play a full life by hand and see what is boring or broken. */
@Composable
fun ZnovaRoot(vm: GameViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.systemBarsPadding()) {
                when (state.screen) {
                    GameViewModel.Screen.LABORATORY ->
                        LaboratoryScreen(state, vm)

                    GameViewModel.Screen.LIFE ->
                        LifeScreen(state, vm)

                    GameViewModel.Screen.SUMMARY ->
                        SummaryScreen(state, vm)

                    GameViewModel.Screen.MAP -> {
                        MapScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = vm::closeMap,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardRow(card: GameCard) {
    val style =
        when (card.tier) {
            Tier.MAJOR -> MaterialTheme.typography.titleLarge
            Tier.MEDIUM -> MaterialTheme.typography.bodyLarge
            Tier.MINOR -> MaterialTheme.typography.bodySmall
        }
    val prefix =
        buildString {
            card.voice?.let { append("[${it.keyLabel()}] ") }
            if (card.observer) append("(observer) ")
        }
    val suffix = card.check?.let { " [check: $it]" }.orEmpty()
    Text(prefix + card.text + suffix, style = style)
}

@Composable
internal fun ChapterPanel(
    chapter: ChapterUi,
    picked: Set<ActivityId>,
    vm: GameViewModel,
) {
    when (chapter.phase) {
        Phase.DECISIONS -> chapter.decision?.let { DecisionPanel(it, vm::choose) }
        Phase.ACTIVITIES -> ActivitiesPanel(chapter, picked, vm::toggleActivity, vm::commit)
        Phase.CLOSED ->
            Button(onClick = vm::continueChapter, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
    }
}

@Composable
internal fun DecisionPanel(
    decision: DecisionUi,
    onChoose: (EventId, ChoiceId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(decision.text, style = MaterialTheme.typography.titleMedium)
        decision.choices.forEach { choice ->
            Button(
                onClick = { onChoose(decision.eventId, choice.id) },
                enabled = choice.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(choice.text) }
            val hint = choice.hint
            if (!choice.enabled && hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ActivitiesPanel(
    chapter: ChapterUi,
    picked: Set<ActivityId>,
    onToggle: (ActivityId) -> Unit,
    onCommit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Pick up to ${chapter.slots} activities (${picked.size}/${chapter.slots})",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.heightIn(max = 220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(chapter.activities, key = { it.id.id }) { activity ->
                FilterChip(
                    selected = activity.id in picked,
                    onClick = { onToggle(activity.id) },
                    enabled = activity.enabled,
                    label = { Text(activity.name) },
                )
            }
        }
        Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text("Commit chapter") }
    }
}

internal fun LazyListScope.section(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium) }
    items(lines) { Text("- $it") }
}
