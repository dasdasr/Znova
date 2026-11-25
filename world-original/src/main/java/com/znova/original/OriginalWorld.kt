package com.znova.original

import com.znova.engine.content.ContentBuilder
import com.znova.engine.content.ContentModule
import com.znova.engine.content.NamePool
import com.znova.engine.content.PlaceDef
import com.znova.engine.core.PlaceId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OriginalWorld : ContentModule {
    private const val RESOURCE = "/original/world_data.json"

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override fun register(builder: ContentBuilder) {
        val root = loadWorldData()

        val countries = root.getValue("countries").jsonObject
        val cities = root.getValue("cities").jsonObject

        val names =
            NamePool(
                first =
                    listOf(
                        "Marek",
                        "Tomas",
                        "Viktor",
                        "Andrej",
                        "Milan",
                        "Elena",
                        "Mira",
                        "Katarina",
                        "Nadia",
                        "Vera",
                        "Jakub",
                        "Roman",
                        "Daria",
                        "Marta",
                        "Anton",
                    ),
                last =
                    listOf(
                        "Novak",
                        "Kovalen",
                        "Markov",
                        "Vesnik",
                        "Radek",
                        "Morava",
                        "Belov",
                        "Dravin",
                        "Ostrov",
                        "Kremen",
                        "Varen",
                        "Zorin",
                        "Lazek",
                        "Beran",
                        "Tavik",
                    ),
            )

        countries.forEach { (countryKey, value) ->
            val country = value.jsonObject

            val placeId = PlaceId("country:$countryKey")

            builder.place(
                PlaceDef(
                    id = placeId,
                    kind = "country",
                    name = country.requiredString("name"),
                    startable = true,
                    laws = emptySet(),
                    names = names,
                    tags = country.stringSet("tags"),
                    props =
                        mapOf(
                            "code" to country.requiredString("code"),
                            "government" to country.requiredString("government"),
                            "currency" to country.requiredString("currency"),
                            "divisions" to country.requiredString("divisions"),
                            "capitalCity" to country.optionalString("capital_city").orEmpty(),
                        ),
                ),
            )
        }

        cities.forEach { (cityKey, value) ->
            val city = value.jsonObject
            val countryKey = city.requiredString("country")

            require(countryKey in countries) {
                "City '$cityKey' references unknown country '$countryKey'"
            }

            val cityType = city.requiredString("type")
            val cityTags = city.stringSet("tags") + city.stringSet("own_tags")

            builder.place(
                PlaceDef(
                    id = PlaceId("city:$cityKey"),
                    kind = "city",
                    name = city.requiredString("name"),
                    parent = PlaceId("country:$countryKey"),
                    startable = false,
                    tags = cityTags,
                    axes = city.intMap("axes"),
                    props =
                        mapOf(
                            "code" to cityKey,
                            "cityType" to cityType,
                            "zones" to city.stringList("zones").joinToString(","),
                            "originType" to city.optionalString("origin_type").orEmpty(),
                            "historicalName" to city.optionalString("historical_name").orEmpty(),
                        ),
                ),
            )
        }
    }

    private fun loadWorldData(): JsonObject {
        val stream =
            requireNotNull(
                OriginalWorld::class.java.getResourceAsStream(RESOURCE),
            ) {
                "Missing world resource: $RESOURCE"
            }

        return stream.bufferedReader().use { reader ->
            json.parseToJsonElement(reader.readText()).jsonObject
        }
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.content ?: error("Missing required string '$key'")

    private fun JsonObject.optionalString(key: String): String? =
        this[key]
            ?.takeUnless { it == JsonNull }
            ?.jsonPrimitive
            ?.content

    private fun JsonObject.stringSet(key: String): Set<String> =
        this[key]
            ?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.toSet()
            .orEmpty()

    private fun JsonObject.stringList(key: String): List<String> =
        this[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    private fun JsonObject.intMap(key: String): Map<String, Int> =
        this[key]?.jsonObject?.mapValues { (_, value) -> value.jsonPrimitive.int }.orEmpty()
}
