package com.znova.presentation.ui.summary

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.znova.app.GameViewModel
import com.znova.presentation.ui.section
import kotlinx.coroutines.launch


@Composable
internal fun SummaryScreen(state: GameViewModel.UiState, vm: GameViewModel) {
    val summary = state.summary ?: return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val history = buildString {
        appendLine(summary.title)
        appendLine("Died at ${summary.ageYears}")
        appendLine("Cause: ${summary.cause}")
        appendLine()

        appendLine("ACHIEVEMENTS")
        if (summary.achievements.isEmpty()) appendLine("None")
        else summary.achievements.forEach { appendLine("- $it") }

        appendLine()
        appendLine("UNLOCKS")
        if (summary.unlocks.isEmpty()) appendLine("None")
        else summary.unlocks.forEach { appendLine("- $it") }

        appendLine()
        appendLine("ARCHIVE GAPS")
        if (summary.gaps.isEmpty()) appendLine("None")
        else summary.gaps.forEach { appendLine("- $it") }

        appendLine()
        appendLine("LIFE CHRONICLE")
        summary.chronicle.forEachIndexed { index, entry ->
            appendLine("${index + 1}. $entry")
        }
    }.trim()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                summary.title,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            Text("Died at ${summary.ageYears}: ${summary.cause}")
        }

        section("New achievements", summary.achievements)
        section("New unlocks", summary.unlocks)
        section("Archive gaps", summary.gaps)

        item {
            Text(
                "Chronicle",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(summary.chronicle) {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Button(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("Life history", history))
                    )
                }
            }) {
                Text("Copy life history")
            }
        }

        item {
            Button(
                onClick = vm::backToLaboratory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to laboratory")
            }
        }
    }
}
