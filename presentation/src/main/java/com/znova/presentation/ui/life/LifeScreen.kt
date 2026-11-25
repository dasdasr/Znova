package com.znova.presentation.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.znova.app.GameViewModel
import com.znova.app.GameViewModel.UiState
import com.znova.app.keyLabel
import com.znova.presentation.ui.CardRow
import com.znova.presentation.ui.ChapterPanel
import kotlin.collections.orEmpty

@Composable
internal fun LifeScreen(
    state: UiState,
    vm: GameViewModel,
) {
    val life = state.life ?: return
    val chapter = state.chapter ?: return
    val cards = life.chapterState?.cards.orEmpty()
    val character = life.character

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "${character.name}, ${character.ageYears} y, money ${character.money}",
            style = MaterialTheme.typography.titleMedium,
        )
        // Display the seed to make reported bugs reproducible.
        Text("chapter ${life.currentChapter}   seed ${life.seed}", style = MaterialTheme.typography.bodySmall)
        Text(
            character.stats.entries.joinToString("   ") { "${it.key.keyLabel()} ${it.value}" },
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cards) { CardRow(it) }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ChapterPanel(chapter, state.pickedActivities, vm)
    }
}
