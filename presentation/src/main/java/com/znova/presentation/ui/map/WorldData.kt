package com.znova.ui.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Lightweight profiles; geometry is stored separately.
data class CityProfile(
    val id: String,
    val name: String,
    val type: String,
    val countryId: String,
    val zoneIds: List<String>,
    val ownTags: List<String>,
    val tags: List<String>,
    val axes: Map<String, Int>,
    val originType: String?,
)

data class CountryProfile(
    val id: String,
    val name: String,
    val code: String,
    val government: String?,
    val currency: String?,
    val divisions: String?,
    val capitalCityId: String?,
    val tags: List<String>,
)

class WorldData(
    val cities: Map<String, CityProfile>,
    val countries: Map<String, CountryProfile>,
) {
    fun citiesOf(countryId: String) =
        cities.values.filter { it.countryId == countryId }
            .sortedWith(compareBy({ it.type != "capital" }, { it.type != "major_city" }, { it.name }))

    fun countryAxes(countryId: String): Map<String, Int> {
        val list = citiesOf(countryId)
        if (list.isEmpty()) return emptyMap()
        return list.flatMap { it.axes.entries }.groupBy({ it.key }, { it.value })
            .mapValues { (_, v) -> v.average().let { Math.round(it).toInt() } }
    }

    companion object {
        val EMPTY = WorldData(emptyMap(), emptyMap())

        fun load(context: Context): WorldData = runCatching {
            val root = JSONObject(
                context.assets.open("map/world_data.json").bufferedReader().use { it.readText() },
            )
            val countries = root.getJSONObject("countries").let { o ->
                o.keys().asSequence().associateWith { id ->
                    val c = o.getJSONObject(id)
                    CountryProfile(
                        id, c.getString("name"), c.getString("code"),
                        c.optString("government").ifEmpty { null },
                        c.optString("currency").ifEmpty { null },
                        c.optString("divisions").ifEmpty { null },
                        c.optString("capital_city").takeIf { it.isNotEmpty() && it != "null" },
                        c.getJSONArray("tags").strings(),
                    )
                }
            }
            val cities = root.getJSONObject("cities").let { o ->
                o.keys().asSequence().associateWith { id ->
                    val c = o.getJSONObject(id)
                    val ax = c.getJSONObject("axes")
                    CityProfile(
                        id, c.getString("name"), c.getString("type"), c.getString("country"),
                        c.getJSONArray("zones").strings(), c.getJSONArray("own_tags").strings(),
                        c.getJSONArray("tags").strings(),
                        ax.keys().asSequence().associateWith { ax.getInt(it) },
                        c.optString("origin_type").takeIf { it.isNotEmpty() && it != "null" },
                    )
                }
            }
            WorldData(cities, countries)
        }.getOrElse { EMPTY }

        private fun JSONArray.strings() = List(length()) { getString(it) }
    }
}
