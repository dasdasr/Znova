package com.znova.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znova.presentation.ui.map.InfoSheet

@Composable
fun MapScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var data by remember { mutableStateOf<MapData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        runCatching { MapDataLoader.load(context) }
            .onSuccess { data = it }
            .onFailure { error = it.stackTraceToString() }
    }

    Box(modifier.fillMaxSize()) {
        when {
            data != null -> MapContent(data!!, onBack)
            error != null -> Text(error.orEmpty())
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun MapContent(data: MapData, onBack: () -> Unit) {
    val state = remember { ZnovaMapState() }
    var lastSelection by remember { mutableStateOf<MapSelection?>(null) }
    LaunchedEffect(state.selection) { state.selection?.let { lastSelection = it } }

    Box(Modifier.fillMaxSize().border(2.dp, Ink)) {
        ZnovaMap(data, state, Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StampButton("<", onBack)
            StampButton("+", { state.zoomBy(1.0) })
            StampButton("-", { state.zoomBy(-1.0) })
        }
        StampButton(
            "CITIES", { state.showAllCities = !state.showAllCities },
            active = state.showAllCities,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )

        AnimatedVisibility(
            visible = state.selection != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            lastSelection?.let {
                InfoSheet(
                    it,
                    data.world,
                    { id -> data.citiesById[id]?.let(state::selectCity) },
                    state::clearSelection,
                    Modifier.fillMaxWidth().fillMaxHeight(SHEET_FRACTION)
                )
            }
        }
    }
}

@Composable
internal fun StampButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    Box(
        modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .drawBehind { drawRect(Ink, topLeft = Offset(0f, 4.dp.toPx())) }
            .background(if (active) Ink else Paper)
            .border(2.dp, Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Paper else Ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
