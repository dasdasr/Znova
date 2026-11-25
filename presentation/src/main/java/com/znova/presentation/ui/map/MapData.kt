package com.znova.ui.map

import android.content.Context
import android.util.Log
import com.znova.presentation.ui.map.MapAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

private const val TAG = "MapData"
private const val EMPTY_FC = """{"type":"FeatureCollection","features":[]}"""

enum class CityType {
    CAPITAL, MAJOR_CITY, CITY, OTHER;

    companion object {
        fun fromRaw(raw: String?): CityType = when (raw?.lowercase()) {
            "capital" -> CAPITAL
            "major_city" -> MAJOR_CITY
            "city" -> CITY
            else -> OTHER
        }
    }
}

data class GeoBox(val north: Double, val south: Double, val east: Double, val west: Double) {
    val center: LatLng get() = LatLng((north + south) / 2, (east + west) / 2)

    fun union(o: GeoBox) = GeoBox(
        maxOf(north, o.north), minOf(south, o.south), maxOf(east, o.east), minOf(west, o.west),
    )

    fun expand(deg: Double) = GeoBox(north + deg, south - deg, east + deg, west - deg)

    fun toLatLngBounds(): LatLngBounds =
        LatLngBounds.Builder().include(LatLng(south, west)).include(LatLng(north, east)).build()
}

data class CountryInfo(val id: String, val name: String, val box: GeoBox)

data class CityInfo(
    val id: String,
    val name: String,
    val countryId: String,
    val type: CityType,
    val position: LatLng,
)

class MapData(
    /** Countries and minor territories combined into a single land source. */
    val landJson: String,

    val seasJson: String,
    val lakesJson: String,
    val riversJson: String,
    val citiesJson: String,

    val wavesJson: String,
    val bordersJson: String?,
    val countries: Map<String, CountryInfo>,
    val cities: List<CityInfo>,
    val worldBox: GeoBox,
) {
    val citiesById: Map<String, CityInfo> = cities.associateBy { it.id }
}

object MapDataLoader {
    suspend fun load(
        context: Context,
        assets: MapAssets,
    ): MapData = withContext(Dispatchers.IO) {
        build(context, assets)
    }

    private suspend fun build(
        context: Context,
        assets: MapAssets,
    ): MapData = withContext(Dispatchers.IO) {
        fun read(name: String): String? = runCatching {
            context.assets
                .open("${assets.dir}/$name.geojson")
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()

        val countriesJson = read("countries")
            ?: error("assets/${assets.dir}/countries.geojson is required")
        val minorJson = read("minor_territories") ?: EMPTY_FC
        val seasJson = read("seas") ?: EMPTY_FC
        val lakesJson = read("lakes") ?: EMPTY_FC
        val riversJson = read("rivers") ?: EMPTY_FC
        val citiesJson = read("cities") ?: EMPTY_FC
        val bordersJson = read("borders")
        if (bordersJson == null) Log.w(TAG, "borders.geojson missing: run tools/build-map.ps1")

        val countryFeatures = JSONObject(countriesJson).getJSONArray("features")
        val minorFeatures = JSONObject(minorJson).getJSONArray("features")

        val countries = LinkedHashMap<String, CountryInfo>()
        var world: GeoBox? = null
        for (i in 0 until countryFeatures.length()) {
            val f = countryFeatures.getJSONObject(i)
            val props = f.optJSONObject("properties") ?: continue
            val id = props.optString("country_id", "")
            if (id.isEmpty()) {
                Log.w(TAG, "country feature #$i has no country_id, skipped")
                continue
            }
            val box = boxOf(f.optJSONObject("geometry")) ?: continue
            countries[id] = CountryInfo(id, props.optString("name", id), box)
            world = world?.union(box) ?: box
        }

        // Minor territories also extend the explorable world bounds.
        for (i in 0 until minorFeatures.length()) {
            boxOf(minorFeatures.getJSONObject(i).optJSONObject("geometry"))?.let { b ->
                world = world?.union(b) ?: b
            }
        }

        // Merge minor territories into the land source, preserving countries first.
        val merged = JSONArray()
        for (i in 0 until countryFeatures.length()) merged.put(countryFeatures.get(i))
        for (i in 0 until minorFeatures.length()) {
            val f = minorFeatures.getJSONObject(i)
            if (boxOf(f.optJSONObject("geometry")) == null) continue // empty geometry in the export
            val props = f.optJSONObject("properties") ?: JSONObject().also { f.put("properties", it) }
            props.put("minor", true)
            merged.put(f)
        }
        val landJson = JSONObject().put("type", "FeatureCollection").put("features", merged).toString()

        // Generate waves on open water, excluding land and lakes.
        val wavesJson = WaveGenerator.generate(
            box = world ?: assets.worldBox,
            land = PolygonIndex.fromFeatures(merged),
            lakes = PolygonIndex.fromFeatures(JSONObject(lakesJson).getJSONArray("features")),
        )

        // Resolve QGIS country names to IDs for city-country linking.
        val nameToId = countries.values.associate { it.name.lowercase() to it.id }
        val cities = ArrayList<CityInfo>()
        val cityRoot = JSONObject(citiesJson)
        val cityFeatures = cityRoot.getJSONArray("features")
        for (i in 0 until cityFeatures.length()) {
            val f = cityFeatures.getJSONObject(i)
            val props = f.optJSONObject("properties") ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val id = props.optString(
                "city_id",
                props.optString("city_code", "")
            )
            if (id.isEmpty() || coords.length() < 2) continue
            val countryId = props.optString("country_id", "")

            if (countryId.isEmpty()) {
                Log.w(TAG, "city $id has no country_id, skipped")
                continue
            }

            if (countryId !in countries) {
                Log.w(TAG, "city $id has unknown country_id '$countryId', skipped")
                continue
            }
            props.put("country_id", countryId)
            cities += CityInfo(
                id = id,
                name = props.optString("name", id),
                countryId = countryId,
                type = CityType.fromRaw(props.optString("type", "")),
                position = LatLng(coords.getDouble(1), coords.getDouble(0)),
            )
        }

        MapData(
            landJson = landJson,
            seasJson = seasJson,
            lakesJson = lakesJson,
            riversJson = riversJson,
            citiesJson = cityRoot.toString(),
            wavesJson = wavesJson,
            bordersJson = bordersJson,
            countries = countries,
            cities = cities,
            worldBox = assets.worldBox,
        )
    }

    private fun boxOf(geometry: JSONObject?): GeoBox? {
        val coords = geometry?.optJSONArray("coordinates") ?: return null
        var n = -90.0;
        var s = 90.0;
        var e = -180.0;
        var w = 180.0
        var any = false
        fun walk(node: JSONArray) {
            if (node.length() >= 2 && node.optJSONArray(0) == null) {
                val lng = node.getDouble(0);
                val lat = node.getDouble(1)
                n = maxOf(n, lat); s = minOf(s, lat); e = maxOf(e, lng); w = minOf(w, lng)
                any = true
                return
            }
            for (i in 0 until node.length()) walk(node.getJSONArray(i))
        }
        walk(coords)
        return if (any) GeoBox(n, s, e, w) else null
    }
}
