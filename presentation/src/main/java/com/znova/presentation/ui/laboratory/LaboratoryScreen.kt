package com.znova.presentation.ui.laboratory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.znova.app.GameViewModel

@Composable
internal fun LaboratoryScreen(
    state: GameViewModel.UiState,
    vm: GameViewModel,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Laboratory", style = MaterialTheme.typography.headlineMedium)
        Text("Archive: ${state.meta.archive.size} lives, ${state.meta.unlocks.size} unlocks")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Seed: ${state.seed}", Modifier.weight(1f))
            OutlinedButton(onClick = vm::rerollSeed) { Text("Reroll") }
        }
        Text("Legacies (up to ${GameViewModel.MAX_LEGACIES})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.legacies, key = { it.id.id }) { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleLegacy(option.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = option.id in state.selectedLegacies, onCheckedChange = null)
                    Text(option.label, Modifier.padding(start = 12.dp))
                }
            }
        }
        OutlinedButton(
            onClick = vm::openMap,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open map")
        }
        Button(onClick = vm::startLife, modifier = Modifier.fillMaxWidth()) { Text("Start life") }
    }
}
