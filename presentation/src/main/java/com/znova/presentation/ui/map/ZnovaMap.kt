package com.znova.ui.map

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.znova.presentation.ui.map.MapPalette
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.random.Random

internal val Ink = Color(0xFF2B2118)
internal val Paper = Color(0xFFF4E9CD)
internal val Red = Color(0xFFB23A30)

const val SHEET_FRACTION = 0.45f
private const val LAND_FILL = "land-fill"
private val CITY_LAYERS = arrayOf("cities-capital", "cities-major", "cities-minor")

sealed interface MapSelection {
    data class Country(val info: CountryInfo) : MapSelection
    data class City(val info: CityInfo) : MapSelection
}

// A city selection also highlights its country.
val MapSelection?.countryId: String?
    get() = when (this) {
        is MapSelection.Country -> info.id
        is MapSelection.City -> info.countryId
        null -> null
    }

@Stable
class ZnovaMapState {
    internal var map by mutableStateOf<MapLibreMap?>(null)

    var selection by mutableStateOf<MapSelection?>(null)
        private set

    var showAllCities by mutableStateOf(false)

    internal var focusRequest by mutableIntStateOf(0)
        private set

    fun zoomBy(delta: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta))
    }

    fun selectCountry(info: CountryInfo) {
        selection = MapSelection.Country(info)
        focusRequest++
    }

    fun selectCity(info: CityInfo) {
        selection = MapSelection.City(info)
        focusRequest++
    }

    fun clearSelection() {
        selection = null
    }
}

@Composable
fun ZnovaMap(
    data: MapData,
    palette: MapPalette,
    state: ZnovaMapState,
    modifier: Modifier = Modifier,
    paperTexture: ImageBitmap? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
            state.map = null
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            state.map = map
            map.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
            }
            map.setMaxZoomPreference(10.0)

            map.setStyle(
                Style.Builder().fromUri("asset://packs/original/map/style.json")
            ) { loaded ->
                loaded.addImage(MapIcons.CAPITAL, MapIcons.capital())
                loaded.addImage(MapIcons.MAJOR, MapIcons.major())
                loaded.addImage(MapIcons.MINOR, MapIcons.minor())
                WaveIcons.install(loaded, context)
                loaded.applyData(data)
                map.setLatLngBoundsForCameraTarget(data.worldBox.toLatLngBounds())
                style = loaded
            }

            map.addOnMapClickListener { latLng ->
                val p = map.projection.toScreenLocation(latLng)
                val r = 22f * density
                val city = map
                    .queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), *CITY_LAYERS)
                    .firstOrNull()?.getStringProperty("city_id")
                    ?.let { data.citiesById[it] }
                if (city != null) {
                    state.selectCity(city)
                } else {
                    val country = map.queryRenderedFeatures(p, LAND_FILL)
                        .firstOrNull()?.getStringProperty("country_id")
                        ?.let { data.countries[it] }
                    if (country != null) state.selectCountry(country) else state.clearSelection()
                }
                true
            }
        }
    }

    LaunchedEffect(style, mapSize) {
        val map = state.map ?: return@LaunchedEffect
        if (style == null || mapSize == IntSize.Zero) return@LaunchedEffect
        val minZoom = coverZoom(data.worldBox, mapSize, density)
        map.setMinZoomPreference(minZoom)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(data.worldBox.center, minZoom))
    }

    LaunchedEffect(style, state.selection, state.showAllCities) {
        val s = style ?: return@LaunchedEffect
        val countryId = state.selection.countryId
        val selected = Expression.eq(Expression.get("country_id"), Expression.literal(countryId ?: ""))
        listOf(
            "sel-fill",
            "sel-band",
            "sel-inner",
            "sel-line",
            "country-label",
        ).forEach { s.setFilter(it, selected) }

        val color = palette.color(countryId).toInt()

        s.getLayer("sel-fill")?.setProperties(
            PropertyFactory.fillColor(color),
            PropertyFactory.fillOpacity(0.2f),
        )
        s.getLayer("sel-band")?.setProperties(
            PropertyFactory.lineColor(color),
        )
        s.getLayer("sel-line")?.setProperties(
            PropertyFactory.lineColor(color),
        )

        val cityId = (state.selection as? MapSelection.City)?.info?.id ?: ""
        s.setFilter(
            "city-selected",
            Expression.eq(
                Expression.get("city_id"),
                Expression.literal(cityId)
            )
        )

        val scope: Expression? = when {
            state.showAllCities -> Expression.literal(true)
            countryId != null -> Expression.eq(Expression.get("country_id"), Expression.literal(countryId))
            else -> null
        }
        val typeFilters = mapOf(
            "cities-capital" to Expression.eq(Expression.get("type"), Expression.literal("capital")),
            "cities-major" to Expression.eq(Expression.get("type"), Expression.literal("major_city")),
            "cities-minor" to Expression.all(
                Expression.neq(Expression.get("type"), Expression.literal("capital")),
                Expression.neq(Expression.get("type"), Expression.literal("major_city")),
            ),
        )
        for ((layerId, typeFilter) in typeFilters) {
            if (scope == null) {
                s.getLayer(layerId)?.setProperties(PropertyFactory.visibility(Property.NONE))
            } else {
                s.setFilter(layerId, Expression.all(typeFilter, scope))
                s.getLayer(layerId)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
            }
        }
    }

    LaunchedEffect(state.focusRequest) {
        val map = state.map ?: return@LaunchedEffect
        if (mapSize == IntSize.Zero) return@LaunchedEffect
        val sheetPx = (mapSize.height * SHEET_FRACTION).roundToInt()
        when (val sel = state.selection) {
            is MapSelection.Country -> fitCountry(map, sel.info, data.worldBox, mapSize, density, sheetPx)
            is MapSelection.City -> flyToCity(map, sel.info, data.worldBox, mapSize, density, sheetPx)
            null -> Unit
        }
    }

    val texture = paperTexture ?: remember { noiseBitmap().asImageBitmap() }

    Box(
        modifier
            .background(Paper)
            .onSizeChanged { mapSize = it }) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = ShaderBrush(ImageShader(texture, TileMode.Repeated, TileMode.Repeated)),
                        alpha = 0.6f,
                    )
                },
        )
    }
}

private fun coverZoom(box: GeoBox, size: IntSize, density: Float): Double {
    val w = (box.east - box.west) / 360.0 * 512.0
    val h = (mercatorY(box.south) - mercatorY(box.north)) * 512.0
    return maxOf(log2(size.width / density / w), log2(size.height / density / h))
}

private fun fitCountry(
    map: MapLibreMap, country: CountryInfo, world: GeoBox,
    size: IntSize, density: Float, sheetPx: Int,
) {
    val pad = (28 * density).roundToInt()
    val cam = map.getCameraForLatLngBounds(
        country.box.toLatLngBounds(), intArrayOf(pad, pad, pad, pad + sheetPx),
    ) ?: return
    val zoom = cam.zoom.coerceAtMost(9.0)
    val center = clampCenter(cam.target ?: return, zoom, size, density, world)
    map.animateCamera(
        CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build()),
        700,
    )
}

private fun flyToCity(
    map: MapLibreMap, city: CityInfo, world: GeoBox,
    size: IntSize, density: Float, sheetPx: Int,
) {
    val zoom = maxOf(map.cameraPosition.zoom, 6.5)
    // Offset the city above the bottom sheet in a single camera animation.
    val worldPx = 512.0 * 2.0.pow(zoom)
    val y = mercatorY(city.position.latitude) + (sheetPx / density / 2.0) / worldPx
    val center = clampCenter(LatLng(latitudeOf(y), city.position.longitude), zoom, size, density, world)
    map.animateCamera(
        CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(center).zoom(zoom).build()),
        600,
    )
}

/** Keep the viewport inside world bounds while fitting the selected country. */
private fun clampCenter(c: LatLng, zoom: Double, size: IntSize, density: Float, box: GeoBox): LatLng {
    val worldPx = 512.0 * 2.0.pow(zoom)
    val halfLng = (size.width / density / 2.0) / worldPx * 360.0
    val halfY = (size.height / density / 2.0) / worldPx

    val minLng = box.west + halfLng
    val maxLng = box.east - halfLng
    val lng = if (minLng > maxLng) (box.west + box.east) / 2 else c.longitude.coerceIn(minLng, maxLng)

    val yTop = mercatorY(box.north)
    val yBottom = mercatorY(box.south)
    val minY = yTop + halfY
    val maxY = yBottom - halfY
    val y = if (minY > maxY) (yTop + yBottom) / 2 else mercatorY(c.latitude).coerceIn(minY, maxY)

    return LatLng(latitudeOf(y), lng)
}

private fun mercatorY(lat: Double): Double {
    val s = sin(Math.toRadians(lat.coerceIn(-85.0, 85.0)))
    return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
}

private fun latitudeOf(y: Double): Double = Math.toDegrees(atan(sinh(PI - 2 * PI * y)))

private fun Style.setJson(sourceId: String, json: String) {
    getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(json)
}

private fun countryLabelsJson(countries: Collection<CountryInfo>): String {
    val features = JSONArray()

    for (c in countries) {
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put(
                    "properties",
                    JSONObject()
                        .put("country_id", c.id)
                        .put("name", c.name.uppercase()),
                )
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "Point")
                        .put(
                            "coordinates",
                            JSONArray()
                                .put(c.box.center.longitude)
                                .put(c.box.center.latitude),
                        ),
                ),
        )
    }

    return JSONObject()
        .put("type", "FeatureCollection")
        .put("features", features)
        .toString()
}

private fun Style.applyData(d: MapData) {
    setJson("land", d.landJson)
    setJson("seas", d.seasJson)
    setJson("lakes", d.lakesJson)
    setJson("rivers", d.riversJson)
    setJson("cities", d.citiesJson)
    setJson("waves", d.wavesJson)

    setJson("graticule15", graticuleJson(15))
    setJson("graticule5", graticuleJson(5))

    setJson(
        "borders",
        d.bordersJson ?: """{"type":"FeatureCollection","features":[]}""",
    )

    setJson(
        "country-labels",
        countryLabelsJson(d.countries.values),
    )

    getLayer("borders-fallback")?.setProperties(
        PropertyFactory.visibility(
            if (d.bordersJson == null) Property.VISIBLE else Property.NONE,
        ),
    )
}

private fun Style.setFilter(layerId: String, filter: Expression) {
    when (val layer = getLayer(layerId)) {
        is FillLayer -> layer.setFilter(filter)
        is LineLayer -> layer.setFilter(filter)
        is CircleLayer -> layer.setFilter(filter)
        is SymbolLayer -> layer.setFilter(filter)
        else -> Unit
    }
}

private fun graticuleJson(step: Int): String {
    val sb = StringBuilder("""{"type":"FeatureCollection","features":[""")
    var first = true
    fun line(coords: String) {
        if (!first) sb.append(',')
        first = false
        sb.append("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":$coords}}""")
    }
    for (lon in -180..180 step step) line("[[$lon,-80],[$lon,80]]")
    for (lat in -75..75 step step) line("[[-180,$lat],[180,$lat]]")
    return sb.append("]}").toString()
}

/** Generates a repeating paper texture */
private fun noiseBitmap(size: Int = 128): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    repeat(size * size / 14) {
        bmp.setPixel(rnd.nextInt(size), rnd.nextInt(size), android.graphics.Color.argb(38, 43, 33, 24))
    }
    return bmp
}
