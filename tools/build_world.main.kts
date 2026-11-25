@file:DependsOn("org.json:json:20240303")

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// kotlin tools/build_world.main.kts <qgis geojson dir> <assets/map>
val src = File(args[0]); val out = File(args[1]).also { it.mkdirs() }
val AXES = "industry farming trade nature air water soil health care safety stability access".split(" ")
val TYPES = mapOf("capital" to "capital", "major" to "major_city", "major_city" to "major_city", "city" to "city")
val warn = mutableListOf<String>()

fun json(f: File) = JSONObject(f.readText())
fun features(name: String) = json(File(src, "$name.geojson")).getJSONArray("features").let { a -> List(a.length()) { a.getJSONObject(it) } }
fun JSONArray.strs() = List(length()) { getString(it) }
fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

class Shape(g: JSONObject) {
    val polys: List<List<List<DoubleArray>>> = g.getJSONArray("coordinates").let { mp ->
        List(mp.length()) { p -> mp.getJSONArray(p).let { rs -> List(rs.length()) { r ->
            rs.getJSONArray(r).let { pts -> List(pts.length()) { i -> pts.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1)) } } } } } }
    }
    private fun inRing(r: List<DoubleArray>, x: Double, y: Double): Boolean {
        var c = false; var j = r.size - 1
        for (i in r.indices) {
            if ((r[i][1] > y) != (r[j][1] > y) && x < (r[j][0] - r[i][0]) * (y - r[i][1]) / (r[j][1] - r[i][1]) + r[i][0]) c = !c
            j = i
        }
        return c
    }
    fun contains(x: Double, y: Double) = polys.any { p -> inRing(p[0], x, y) && p.drop(1).none { inRing(it, x, y) } }
}

val kinds = json(File("zone_kinds.json")).let { k -> k.keys().asSequence().filter { !it.startsWith("_") }.associateWith { k.getJSONObject(it) } }

val cFeats = features("countries")
class Country(val id: String, val p: JSONObject, val shape: Shape, var capital: String? = null, var tags: Set<String> = emptySet())
val countries = cFeats.map { Country(it.getJSONObject("properties").getString("country_id"), it.getJSONObject("properties"), Shape(it.getJSONObject("geometry"))) }
val codeToId = countries.associate { it.p.getString("code") to it.id }

class Zone(val id: String, val name: String, val kind: String, val shape: Shape)
val seen = HashMap<String, Int>()
val zones = features("zones").mapNotNull { f ->
    val p = f.getJSONObject("properties")
    val kind = listOf(p.optString("kind"), p.optString("tags"), p.optString("category")).firstOrNull { it in kinds }
    if (kind == null) { warn += "zone '${p.optString("name")}': unknown kind, skipped"; return@mapNotNull null }
    val base = "z_" + slug(p.getString("name")); val n = seen.merge(base, 1, Int::plus)!!
    Zone(if (n == 1) base else "${base}_$n", p.getString("name"), kind, Shape(f.getJSONObject("geometry")))
}

class City(val code: String, val name: String, val type: String, val country: String, val zones: List<Zone>,
           val own: List<String>, val tags: Set<String>, val axes: Map<String, Int>, val props: JSONObject, val geom: JSONObject)
val cities = features("cities").mapNotNull { f ->
    val p = f.getJSONObject("properties"); val code = p.getString("city_code"); val name = p.getString("name")
    val xy = f.getJSONObject("geometry").getJSONArray("coordinates"); val x = xy.getDouble(0); val y = xy.getDouble(1)
    val byPrefix = codeToId[code.take(2)]
    val spatial = countries.firstOrNull { it.shape.contains(x, y) }?.id
    if (spatial != null && byPrefix != null && spatial != byPrefix) warn += "$code $name: point in '$spatial', code says '$byPrefix'"
    if (spatial == null) warn += "$code $name: outside every country, using code prefix -> $byPrefix"
    val country = spatial ?: byPrefix ?: run { warn += "$code $name: NO COUNTRY"; return@mapNotNull null }
    val type = TYPES[p.optString("type").lowercase()] ?: "city"
    val zs = zones.filter { it.shape.contains(x, y) }.sortedBy { it.id }
    if (zs.isEmpty()) warn += "$code $name: in no zone"
    val own = p.optString("tags").takeIf { it != "null" }?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val axes = AXES.associateWith { a -> zs.sumOf { kinds[it.kind]!!.getJSONObject("mods").optInt(a, 0) }.coerceIn(-5, 5) }
    val tags = (own + type + zs.flatMap { kinds[it.kind]!!.getJSONArray("tags").strs() }).toSet()
    City(code, name, type, country, zs, own, tags, axes, p, f.getJSONObject("geometry"))
}
countries.forEach { c ->
    c.capital = cities.firstOrNull { it.country == c.id && it.type == "capital" }?.code
    if (c.capital == null) warn += "country ${c.id}: no city of type Capital"
    c.tags = cities.filter { it.country == c.id }.flatMap { it.tags }.toSortedSet()
}

fun str(v: String?) = v?.takeIf { it != "null" } ?: JSONObject.NULL
val world = JSONObject().put("version", 1).put("axes", JSONArray(AXES))
world.put("countries", JSONObject().also { o -> countries.forEach { c -> o.put(c.id, JSONObject()
    .put("name", c.p.getString("name")).put("code", c.p.getString("code")).put("government", str(c.p.optString("government")))
    .put("currency", str(c.p.optString("currency"))).put("divisions", str(c.p.optString("divisions")))
    .put("capital_city", c.capital ?: JSONObject.NULL).put("tags", JSONArray(c.tags))) } })
world.put("zones", JSONObject().also { o -> zones.forEach { z -> val k = kinds[z.kind]!!
    o.put(z.id, JSONObject().put("name", z.name).put("category", k.getString("category")).put("kind", z.kind).put("tags", k.getJSONArray("tags"))) } })
world.put("cities", JSONObject().also { o -> cities.forEach { c -> o.put(c.code, JSONObject()
    .put("name", c.name).put("type", c.type).put("country", c.country).put("zones", JSONArray(c.zones.map { it.id }))
    .put("own_tags", JSONArray(c.own)).put("tags", JSONArray(c.tags.sorted())).put("axes", JSONObject(c.axes))
    .put("origin_type", str(c.props.optString("origin_type"))).put("historical_name", str(c.props.optString("historical_name")))) } })
File(out, "world_data.json").writeText(world.toString(1))

File(out, "cities.geojson").writeText(JSONObject().put("type", "FeatureCollection").put("features", JSONArray(cities.map { c ->
    JSONObject().put("type", "Feature").put("geometry", c.geom).put("properties",
        JSONObject().put("city_id", c.code).put("name", c.name).put("type", c.type).put("country_id", c.country)) })).toString())

cFeats.forEach { f -> val id = f.getJSONObject("properties").getString("country_id")
    f.getJSONObject("properties").put("capital_city_id", countries.first { it.id == id }.capital ?: JSONObject.NULL) }
File(out, "countries.geojson").writeText(JSONObject().put("type", "FeatureCollection").put("features", JSONArray(cFeats)).toString())

println("countries ${countries.size}, zones ${zones.size}, cities ${cities.size}")
println(if (warn.isEmpty()) "QA: clean" else "QA:\n" + warn.joinToString("\n") { "  - $it" })
