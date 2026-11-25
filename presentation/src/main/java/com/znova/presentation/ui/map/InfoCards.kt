package com.znova.presentation.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znova.ui.map.CityInfo
import com.znova.ui.map.CountryInfo
import com.znova.ui.map.Ink
import com.znova.ui.map.MapSelection
import com.znova.ui.map.Paper
import com.znova.ui.map.Red
import com.znova.ui.map.StampButton
import com.znova.ui.map.WorldData
import kotlin.math.abs

private val Mono = FontFamily.Monospace

private val AXIS = mapOf(
    "industry" to Triple(
        "INDUSTRY",
        "Getting and spending, we lay waste our powers.",
        "Some are born great, some achieve greatness."
    ),
    "farming" to Triple(
        "FARMING",
        "Little we see in Nature that is ours.",
        "A host of golden daffodils."
    ),
    "trade" to Triple(
        "TRADE",
        "Neither a borrower nor a lender be.",
        "Everybody has won, and all must have prizes."
    ),
    "nature" to Triple(
        "NATURE",
        "The world is too much with us.",
        "The woods are lovely, dark and deep."
    ),
    "air" to Triple(
        "AIR",
        "The air bites shrewdly; it is very cold.",
        "Fluttering and dancing in the breeze."
    ),
    "water" to Triple(
        "WATER",
        "Water, water, everywhere, nor any drop to drink.",
        "Clear rills that for themselves a cooling covert make."
    ),
    "soil" to Triple(
        "SOIL",
        "A sterile promontory.",
        "A flowery band to bind us to the earth."
    ),
    "health" to Triple(
        "HEALTH",
        "The slings and arrows of outrageous fortune.",
        "Full of sweet dreams, and health, and quiet breathing."
    ),
    "care" to Triple(
        "MEDICINE",
        "The miserable have no other medicine, but only hope.",
        "I am a free human being with an independent will."
    ),
    "safety" to Triple(
        "SAFETY",
        "I have supped full with horrors.",
        "No net ensnares me."
    ),
    "stability" to Triple(
        "ORDER",
        "Tomorrow, and tomorrow, and tomorrow.",
        "Though this be madness, yet there is method in't."
    ),
    "access" to Triple(
        "ROADS",
        "Then it doesn't matter which way you go.",
        "That depends a good deal on where you want to get to."
    ),
)

private val TYPE_LABEL = mapOf("capital" to "CAPITAL", "major_city" to "MAJOR CITY", "city" to "CITY")

@Composable
internal fun InfoSheet(
    selection: MapSelection,
    world: WorldData,
    onPickCity: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Paper)
            .border(2.dp, Ink)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(12.dp),
    ) {
        when (selection) {
            is MapSelection.Country ->
                CountryCard(selection.info, world, onPickCity, onClose, Modifier.weight(1f))

            is MapSelection.City ->
                CityCard(selection.info, world, onClose, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Header(kicker: String, title: String, sub: String?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(kicker, color = Red, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            Text(title.uppercase(), color = Ink, fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = Ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = Mono)
        }
        StampButton("X", onClose)
    }
    Spacer(Modifier.height(8.dp))
    Divider()
}

@Composable
private fun Divider() = Box(
    Modifier
        .fillMaxWidth()
        .height(2.dp)
        .background(Ink)
)

@Composable
private fun CountryCard(
    info: CountryInfo,
    world: WorldData,
    onPickCity: (String) -> Unit,
    onClose: () -> Unit,
    contentModifier: Modifier,
) {
    val c = world.countries[info.id]
    val cities = world.citiesOf(info.id)
    val capital = c?.capitalCityId?.let { world.cities[it] }
    Header("COUNTRY  ·  ${c?.code ?: ""}", info.name, c?.government, onClose)
    Column(
        contentModifier
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FactRow("CAPITAL", capital?.name ?: "none yet")
        FactRow("CURRENCY", c?.currency ?: "?")
        FactRow("CITIES", cities.size.toString())
        c?.divisions?.let { FactRow("DIVISIONS", it.substringAfter(": ")) }

        Label("CITIES")
        FlowChips(
            cities.map { it.name + if (it.type == "capital") " ★" else "" },
            onClick = { i -> onPickCity(cities[i].id) })

        val axes = world.countryAxes(info.id)
        if (axes.isNotEmpty()) {
            Label("LIFE HERE, ON AVERAGE"); AxisBlock(axes, max = 4)
        }
    }
}

@Composable
private fun CityCard(
    info: CityInfo,
    world: WorldData,
    onClose: () -> Unit,
    contentModifier: Modifier,
) {
    val p = world.cities[info.id]
    val country = world.countries[info.countryId]
    Header(
        (TYPE_LABEL[info.type.name.lowercase()] ?: "CITY") + "  ·  ${info.id}",
        info.name, country?.name?.let { "in $it" }, onClose,
    )
    Column(
        contentModifier
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (p == null) {
            Text("No world data for this city yet.", color = Ink, fontFamily = Mono, fontSize = 12.sp); return@Column
        }
        val axes = p.axes.filter { it.value != 0 }
        axes.maxByOrNull { abs(it.value) }?.let { (k, v) ->
            AXIS[k]?.let {
                Text(
                    "“" + (if (v < 0) it.second else it.third) + "”",
                    color = Red,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
        p.originType?.let { FactRow("ORIGIN", it) }
        if (axes.isNotEmpty()) {
            Label("CONDITIONS"); AxisBlock(axes, max = 6)
        }
        val shown = (p.ownTags + p.tags).distinct().filter { it !in setOf("capital", "major_city", "city") }
        if (shown.isNotEmpty()) {
            Label("REPUTATION"); FlowChips(shown.take(10).map { it.replace('_', ' ') })
        }
    }
}

@Composable
private fun Label(t: String) =
    Text(t, color = Red, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 10.sp)

@Composable
private fun FactRow(k: String, v: String) = Row {
    Text(k, color = Ink.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.width(96.dp))
    Text(v, color = Ink, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

@Composable
private fun AxisBlock(axes: Map<String, Int>, max: Int) {
    axes.entries.sortedByDescending { abs(it.value) }.take(max).forEach { (k, v) ->
        val label = AXIS[k]?.first ?: k.uppercase()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ink, fontFamily = Mono, fontSize = 10.sp, modifier = Modifier.width(80.dp))
            val filled = (v + 5).coerceIn(0, 10)
            val color = if (v < 0) Red else Ink
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(10) { i ->
                    Box(
                        Modifier
                            .size(width = 12.dp, height = 10.dp)
                            .background(if (i < filled) color else Color.Transparent)
                            .border(1.dp, Ink)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, onClick: ((Int) -> Unit)? = null) {
    items.withIndex().chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (i, t) ->
                Text(
                    t.uppercase(), color = Ink, fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .drawBehind { drawRect(Ink, topLeft = Offset(0f, 3.dp.toPx())) }
                        .background(Paper)
                        .border(1.5.dp, Ink)
                        .let { m -> if (onClick != null) m.clickable { onClick(i) } else m }
                        .padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
    }
}
