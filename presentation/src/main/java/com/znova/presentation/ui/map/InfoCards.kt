package com.znova.presentation.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znova.engine.content.AxisUi
import com.znova.engine.content.ContentPack
import com.znova.engine.content.FactFormat
import com.znova.engine.content.KindUi
import com.znova.engine.content.PackMapUi
import com.znova.engine.content.PlaceDef
import com.znova.engine.content.Tone
import com.znova.engine.core.PlaceId
import kotlin.math.abs
import kotlin.math.roundToInt

private val LocalMapUi = staticCompositionLocalOf { PackMapUi() }

private val mapUi: PackMapUi
    @Composable get() = LocalMapUi.current

private enum class Tab { OVERVIEW, CONDITIONS, CHILDREN }

private data class Dossier(
    val kicker: String,
    val title: String,
    val subtitle: String?,
    val facts: List<Pair<String, String>>,
    val axes: Map<String, Int>,
    val averaged: Boolean,
    val traits: List<String>,
    val localTraits: List<String>,
    val rolledUp: Boolean,
    val children: List<PlaceDef>,
    val childrenTitle: String,
)

@Composable
internal fun InfoSheet(
    placeId: PlaceId,
    content: ContentPack,
    packTheme: PackTheme,
    packMapUi: PackMapUi,
    onPickPlace: (PlaceId) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalTheme provides packTheme, LocalMapUi provides packMapUi) {
        ExpandableSheet(modifier) { sheet, handle, toggle ->
            val shape = RoundedCornerShape(topStart = packTheme.sheetCorner, topEnd = packTheme.sheetCorner)

            Column(
                sheet
                    .clip(shape)
                    .background(packTheme.paper)
                    .border(1.5.dp, packTheme.ink, shape)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                DragHandle(handle, toggle)

                val place = content.places[placeId]

                if (place == null) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label("${packMapUi.text.notFound}: ${placeId.id}", packTheme.bad, Modifier.weight(1f))
                        CloseButton(onClose)
                    }
                } else {
                    // Reset tab and scroll state when switching places.
                    key(placeId) {
                        val dossier = remember(content, packMapUi) { buildDossier(place, content, packMapUi) }
                        DossierView(dossier, handle, onPickPlace, onClose)
                    }
                }
            }
        }
    }
}

private fun buildDossier(
    place: PlaceDef,
    content: ContentPack,
    ui: PackMapUi,
): Dossier {
    val kind = kindUi(place, ui)
    val children =
        content.places.values
            .filter { it.parent == place.id }
            .sortedWith(compareBy<PlaceDef> { typeRank(it, ui) }.thenBy { it.name })

    val rolledUp = kind.tagsFromChildren && children.isNotEmpty()
    var traits = (place.tags - ui.hiddenTags).sorted()
    var localTraits = emptyList<String>()

    if (rolledUp) {
        // A trait is shared when at least half the children have it, with a minimum of two.
        val counts = children.flatMap { it.tags - ui.hiddenTags }.groupingBy { it }.eachCount()
        traits =
            counts
                .filter { (_, n) -> children.size == 1 || (n >= 2 && n * 2 >= children.size) }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(6)
                .map { it.key }
        localTraits = (counts.keys - traits.toSet()).sorted()
    }

    // Use local axes when available; otherwise derive averages from children.
    val averaged = place.axes.isEmpty() && children.any { it.axes.isNotEmpty() }
    val axes =
        if (!averaged) {
            place.axes
        } else {
            children.flatMap { it.axes.keys }.distinct().associateWith { axis ->
                children.mapNotNull { it.axes[axis] }.average().roundToInt()
            }
        }

    val facts =
        kind.facts.mapNotNull { fact ->
            val raw = place.props[fact.prop]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value =
                when (fact.format) {
                    FactFormat.TEXT -> raw
                    FactFormat.HUMANIZE -> humanize(raw)
                    FactFormat.AFTER_COLON -> raw.substringAfter(": ")
                    FactFormat.PLACE_REF -> content.places[PlaceId(fact.refPrefix + raw)]?.name
                }
            value?.let { fact.label to it }
        }

    return Dossier(
        kicker = typeLabel(place, ui),
        title = place.name,
        subtitle =
            kind.subtitleProp?.let { place.props[it] }
                ?: place.parent?.let { content.places[it]?.name },
        facts = facts,
        axes = axes,
        averaged = averaged,
        traits = traits,
        localTraits = localTraits,
        rolledUp = rolledUp,
        children = children,
        childrenTitle = kind.childrenTitle,
    )
}

private fun kindUi(
    place: PlaceDef,
    ui: PackMapUi,
) = ui.kinds[place.kind] ?: KindUi(label = humanize(place.kind))

private fun typeRank(
    place: PlaceDef,
    ui: PackMapUi,
): Int {
    val kind = kindUi(place, ui)
    val type = kind.typeProp?.let { place.props[it] }
    val rank = kind.types.keys.indexOf(type)
    return if (rank < 0) kind.types.size else rank
}

private fun typeLabel(
    place: PlaceDef,
    ui: PackMapUi,
): String {
    val kind = kindUi(place, ui)
    return kind.typeProp?.let { place.props[it] }?.let { kind.types[it] } ?: kind.label
}

private fun axisUi(
    key: String,
    ui: PackMapUi,
) = ui.axes[key] ?: AxisUi(humanize(key), "Poor ${key.replace('_', ' ')}", "Strong ${key.replace('_', ' ')}")

private fun tagLabel(
    tag: String,
    ui: PackMapUi,
) = ui.tags[tag]?.label ?: humanize(tag)

private fun humanize(value: String) = value.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun ColumnScope.DossierView(
    d: Dossier,
    handle: Modifier,
    onPickPlace: (PlaceId) -> Unit,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    val tabs = Tab.entries.filter { it != Tab.CHILDREN || d.children.isNotEmpty() }

    Header(d, handle, onClose)

    val text = mapUi.text
    DossierTabs(
        titles =
            tabs.map {
                when (it) {
                    Tab.OVERVIEW -> text.overview
                    Tab.CONDITIONS -> text.conditions
                    Tab.CHILDREN -> d.childrenTitle
                }
            },
        selected = tabs.indexOf(tab),
        onSelect = { tab = tabs[it] },
    )

    key(tab) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                Tab.OVERVIEW -> Overview(d)
                Tab.CONDITIONS -> Conditions(d)
                Tab.CHILDREN ->
                    d.children.forEach { child ->
                        PlaceRow(child) { onPickPlace(child.id) }
                    }
            }
        }
    }
}

@Composable
private fun Header(
    d: Dossier,
    handle: Modifier,
    onClose: () -> Unit,
) {
    val skin = theme
    Row(
        handle.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                d.title,
                color = skin.ink,
                fontFamily = skin.titleFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Body(
                listOfNotNull(d.kicker, d.subtitle).joinToString(" · "),
                skin.muted,
                size = 13.sp,
            )
        }
        CloseButton(onClose)
    }
}

@Composable
private fun Overview(d: Dossier) {
    val skin = theme
    val ui = mapUi
    val text = ui.text
    val ranked = d.axes.entries.filter { it.value != 0 }
    val best = ranked.filter { it.value > 0 }.maxByOrNull { it.value }
    val worst = ranked.filter { it.value < 0 }.minByOrNull { it.value }

    if (best != null || worst != null) {
        DossierCard {
            best?.let { VerdictRow(text.best, axisUi(it.key, ui).good, theme.good) }
            if (best != null && worst != null) DossierDivider()
            worst?.let { VerdictRow(text.worst, axisUi(it.key, ui).bad, theme.bad) }
        }
    }

    if (d.traits.isNotEmpty()) {
        Section(if (d.rolledUp) text.commonTraits else text.traits) {
            Chips(d.traits)
        }
    }

    if (d.localTraits.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        Column {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
                contentAlignment = Alignment.CenterStart,
            ) {
                Label("${if (expanded) "−" else "+"} ${text.localTraits} (${d.localTraits.size})", skin.accent)
            }
            if (expanded) Chips(d.localTraits)
        }
    }

    if (d.facts.isNotEmpty()) {
        Section(text.facts) {
            DossierCard {
                d.facts.forEach { (name, value) ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Label(name, skin.muted, Modifier.width(96.dp))
                        Body(value, skin.ink, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Conditions(d: Dossier) {
    val skin = theme
    val ui = mapUi
    val text = ui.text
    if (d.averaged) Body(text.averageNote, skin.muted, italic = true)

    val sorted = d.axes.entries.sortedByDescending { it.value }
    val positive = sorted.filter { it.value > 0 }
    val negative = sorted.filter { it.value < 0 }.reversed()
    val neutral = sorted.filter { it.value == 0 }

    listOf(text.positive to positive, text.negative to negative)
        .filter { it.second.isNotEmpty() }
        .forEach { (title, items) ->
            Section(title) { DossierCard { items.forEach { AxisRow(it.key, it.value) } } }
        }

    if (neutral.isNotEmpty()) {
        Section(text.neutral) {
            Body(neutral.joinToString(" · ") { axisUi(it.key, ui).label }, skin.muted)
        }
    }

    if (sorted.isEmpty()) Body(text.empty, skin.muted)
}

@Composable
private fun AxisRow(
    key: String,
    value: Int,
) {
    val skin = theme
    val info = axisUi(key, mapUi)
    val max = mapUi.axisMax
    val color = if (value > 0) skin.good else skin.bad

    Column(
        Modifier.padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(info.label, skin.ink, Modifier.weight(1f))
            Row(
                Modifier.width(92.dp).semantics { contentDescription = "$value of $max" },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(max) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < abs(value)) color else skin.ink.copy(alpha = 0.12f)),
                    )
                }
            }
        }
        Body(if (value > 0) info.good else info.bad, skin.muted, size = 13.sp)
    }
}

@Composable
private fun PlaceRow(
    place: PlaceDef,
    onClick: () -> Unit,
) {
    val skin = theme
    val ui = mapUi
    val strongest =
        place.axes.entries
            .filter { it.value != 0 }
            .maxByOrNull { abs(it.value) }

    DossierCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(typeLabel(place, ui), skin.accent)
                Text(
                    place.name,
                    color = skin.ink,
                    fontFamily = skin.titleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                strongest?.let {
                    val info = axisUi(it.key, ui)
                    Body(
                        if (it.value > 0) info.good else info.bad,
                        if (it.value > 0) skin.good else skin.bad,
                        size = 13.sp,
                    )
                }
            }
            Text("→", color = skin.ink, fontSize = 20.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(tags: List<String>) {
    val ui = mapUi
    val skin = theme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            val color =
                when (ui.tags[tag]?.tone) {
                    Tone.GOOD -> skin.good
                    Tone.BAD -> skin.bad
                    else -> skin.ink
                }
            DossierChip(tagLabel(tag, ui), color)
        }
    }
}
