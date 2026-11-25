package com.znova.engine.content

/** Pack-specific map presentation metadata. */
data class PackMapUi(
    val axes: Map<String, AxisUi> = emptyMap(),
    val axisMax: Int = 5,
    val tags: Map<String, TagUi> = emptyMap(),
    val hiddenTags: Set<String> = emptySet(),
    /** Keys match PlaceDef.kind values. */
    val kinds: Map<String, KindUi> = emptyMap(),
    val text: SheetText = SheetText(),
)

data class KindUi(
    val label: String,
    val childrenTitle: String = "Places",
    /** Derive tags and traits from child places. */
    val tagsFromChildren: Boolean = false,
    val typeProp: String? = null,
    /** Entry order determines display order. */
    val types: Map<String, String> = emptyMap(),
    val codeProp: String? = "code",
    val subtitleProp: String? = null,
    val facts: List<FactUi> = emptyList(),
)

data class AxisUi(
    val label: String,
    val bad: String,
    val good: String,
)

enum class Tone { GOOD, BAD, NEUTRAL }

data class TagUi(
    val label: String? = null,
    val tone: Tone = Tone.NEUTRAL,
)

enum class FactFormat { TEXT, HUMANIZE, AFTER_COLON, PLACE_REF }

data class FactUi(
    val prop: String,
    val label: String,
    val format: FactFormat = FactFormat.TEXT,
    /** Prefix used to resolve a PLACE_REF value into a PlaceId. */
    val refPrefix: String = "",
)

data class SheetText(
    val overview: String = "Overview",
    val conditions: String = "Living",
    val best: String = "Strength",
    val worst: String = "Trouble",
    val traits: String = "Known for",
    val commonTraits: String = "Character",
    val localTraits: String = "Only in some places",
    val facts: String = "Records",
    val positive: String = "In your favor",
    val negative: String = "Against you",
    val neutral: String = "Unremarkable",
    val averageNote: String = "Average across places inside. A single one may be very different.",
    val empty: String = "No records.",
    val notFound: String = "File not found",
)
