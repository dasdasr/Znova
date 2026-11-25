package com.znova.original

import com.znova.engine.content.AxisUi
import com.znova.engine.content.FactFormat
import com.znova.engine.content.FactUi
import com.znova.engine.content.KindUi
import com.znova.engine.content.PackMapUi
import com.znova.engine.content.SheetText
import com.znova.engine.content.TagUi
import com.znova.engine.content.Tone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Keep UI metadata alongside world_data.json
object OriginalMapUi {
    private const val RESOURCE = "/original/world_ui.json"

    val value: PackMapUi by lazy {
        val stream =
            requireNotNull(OriginalMapUi::class.java.getResourceAsStream(RESOURCE)) {
                "Missing UI resource: $RESOURCE"
            }
        val text = stream.bufferedReader().use { it.readText() }
        parseMapUi(Json.parseToJsonElement(text).jsonObject)
    }

    private fun parseMapUi(root: JsonObject): PackMapUi {
        root.allowOnly("axes", "axisMax", "tags", "hiddenTags", "kinds", "text")

        return PackMapUi(
            axes =
                root.objects("axes").mapValues { (_, o) ->
                    o.allowOnly("label", "bad", "good")
                    AxisUi(o.string("label"), o.string("bad"), o.string("good"))
                },
            axisMax = root["axisMax"]?.jsonPrimitive?.int ?: PackMapUi().axisMax,
            tags =
                root.objects("tags").mapValues { (_, o) ->
                    o.allowOnly("label", "tone")
                    TagUi(
                        label = o.optional("label"),
                        tone = o.optional("tone")?.let(Tone::valueOf) ?: Tone.NEUTRAL,
                    )
                },
            hiddenTags =
                root["hiddenTags"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?.toSet()
                    .orEmpty(),
            kinds = root.objects("kinds").mapValues { (_, o) -> parseKind(o) },
            text = root["text"]?.let { parseText(it.jsonObject) } ?: SheetText(),
        )
    }

    private fun parseKind(o: JsonObject): KindUi {
        o.allowOnly(
            "label",
            "childrenTitle",
            "tagsFromChildren",
            "typeProp",
            "types",
            "codeProp",
            "subtitleProp",
            "facts",
        )
        val defaults = KindUi(label = o.string("label"))

        return defaults.copy(
            childrenTitle = o.optional("childrenTitle") ?: defaults.childrenTitle,
            tagsFromChildren = o["tagsFromChildren"]?.jsonPrimitive?.boolean ?: false,
            typeProp = o.optional("typeProp"),
            // Key order defines the display order.
            types =
                o["types"]
                    ?.jsonObject
                    ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                    .orEmpty(),
            codeProp = o.optional("codeProp") ?: defaults.codeProp,
            subtitleProp = o.optional("subtitleProp"),
            facts = o["facts"]?.jsonArray?.map { parseFact(it.jsonObject) }.orEmpty(),
        )
    }

    private fun parseFact(o: JsonObject): FactUi {
        o.allowOnly("prop", "label", "format", "refPrefix")
        return FactUi(
            prop = o.string("prop"),
            label = o.string("label"),
            format = o.optional("format")?.let(FactFormat::valueOf) ?: FactFormat.TEXT,
            refPrefix = o.optional("refPrefix").orEmpty(),
        )
    }

    private fun parseText(o: JsonObject): SheetText {
        o.allowOnly(
            "overview",
            "conditions",
            "best",
            "worst",
            "traits",
            "commonTraits",
            "localTraits",
            "facts",
            "positive",
            "negative",
            "neutral",
            "averageNote",
            "empty",
            "notFound",
        )
        val d = SheetText()

        fun get(
            key: String,
            default: String,
        ) = o.optional(key) ?: default

        return SheetText(
            overview = get("overview", d.overview),
            conditions = get("conditions", d.conditions),
            best = get("best", d.best),
            worst = get("worst", d.worst),
            traits = get("traits", d.traits),
            commonTraits = get("commonTraits", d.commonTraits),
            localTraits = get("localTraits", d.localTraits),
            facts = get("facts", d.facts),
            positive = get("positive", d.positive),
            negative = get("negative", d.negative),
            neutral = get("neutral", d.neutral),
            averageNote = get("averageNote", d.averageNote),
            empty = get("empty", d.empty),
            notFound = get("notFound", d.notFound),
        )
    }

    // Reject unknown keys instead of silently ignoring typos.
    private fun JsonObject.allowOnly(vararg allowed: String) {
        val unknown = keys - allowed.toSet()
        require(unknown.isEmpty()) { "Unknown keys in $RESOURCE: $unknown" }
    }

    private fun JsonObject.objects(key: String): Map<String, JsonObject> =
        this[key]?.jsonObject?.mapValues { (_, v) -> v.jsonObject }.orEmpty()

    private fun JsonObject.optional(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.string(key: String): String = optional(key) ?: error("Missing '$key' in $RESOURCE")
}
