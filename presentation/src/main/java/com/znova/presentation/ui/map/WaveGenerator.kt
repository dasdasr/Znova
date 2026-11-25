package com.znova.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.znova.presentation.R
import org.json.JSONArray
import org.maplibre.android.maps.Style
import java.util.Locale
import kotlin.random.Random

/**
 * Generates a dense wave grid. Symbol collision controls density at each zoom level,
 * while fixed screen-space icon sizes create the appearance of more waves when zooming in.
 */
internal object WaveGenerator {
    private const val STEP = 0.5
    private const val MARGIN = 0.15
    private const val VARIANTS = 3

    fun generate(box: GeoBox, land: PolygonIndex, lakes: PolygonIndex): String {
        val rnd = Random(42)
        val sb = StringBuilder("""{"type":"FeatureCollection","features":[""")
        var first = true
        var lat = box.south
        while (lat <= box.north) {
            var lng = box.west
            while (lng <= box.east) {
                val x = lng + (rnd.nextDouble() - 0.5) * STEP * 0.9
                val y = lat + (rnd.nextDouble() - 0.5) * STEP * 0.9
                if (isOpenWater(x, y, land, lakes)) {
                    if (!first) sb.append(',')
                    first = false
                    sb.append(
                        String.format(
                            Locale.US,
                            """{"type":"Feature","properties":{"v":%d,"s":%.2f,"r":%.3f},"geometry":{"type":"Point","coordinates":[%.4f,%.4f]}}""",
                            rnd.nextInt(VARIANTS), 0.85 + rnd.nextDouble() * 0.35, rnd.nextDouble(), x, y,
                        ),
                    )
                }
                lng += STEP
            }
            lat += STEP
        }
        return sb.append("]}").toString()
    }

    private fun isOpenWater(x: Double, y: Double, land: PolygonIndex, lakes: PolygonIndex): Boolean =
        !land.contains(x, y) && !lakes.contains(x, y) &&
            !land.contains(x + MARGIN, y) && !land.contains(x - MARGIN, y) &&
            !land.contains(x, y + MARGIN) && !land.contains(x, y - MARGIN)
}

// Supports Polygon and MultiPolygon geometries, including holes.
internal class PolygonIndex private constructor(private val polys: List<Poly>) {

    private class Poly(
        val rings: List<DoubleArray>, // Flat coordinate pairs; first ring is the exterior.
        val minX: Double, val minY: Double, val maxX: Double, val maxY: Double,
    )

    fun contains(x: Double, y: Double): Boolean {
        for (p in polys) {
            if (x < p.minX || x > p.maxX || y < p.minY || y > p.maxY) continue
            if (!inRing(p.rings[0], x, y)) continue
            var inHole = false
            for (i in 1 until p.rings.size) {
                if (inRing(p.rings[i], x, y)) { inHole = true; break }
            }
            if (!inHole) return true
        }
        return false
    }

    private fun inRing(r: DoubleArray, x: Double, y: Double): Boolean {
        var inside = false
        var j = r.size - 2
        var i = 0
        while (i < r.size) {
            val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
            i += 2
        }
        return inside
    }

    companion object {
        fun fromFeatures(features: JSONArray): PolygonIndex {
            val out = ArrayList<Poly>()
            for (i in 0 until features.length()) {
                val g = features.getJSONObject(i).optJSONObject("geometry") ?: continue
                val coords = g.optJSONArray("coordinates") ?: continue
                when (g.optString("type")) {
                    "Polygon" -> parse(coords)?.let { out.add(it) }
                    "MultiPolygon" -> for (k in 0 until coords.length()) parse(coords.getJSONArray(k))?.let { out.add(it) }
                }
            }
            return PolygonIndex(out)
        }

        private fun parse(rings: JSONArray): Poly? {
            if (rings.length() == 0) return null
            val parsed = ArrayList<DoubleArray>(rings.length())
            for (r in 0 until rings.length()) {
                val ring = rings.getJSONArray(r)
                val arr = DoubleArray(ring.length() * 2)
                for (i in 0 until ring.length()) {
                    val pt = ring.getJSONArray(i)
                    arr[2 * i] = pt.getDouble(0)
                    arr[2 * i + 1] = pt.getDouble(1)
                }
                parsed += arr
            }
            val outer = parsed[0]
            if (outer.size < 6) return null
            var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            var i = 0
            while (i < outer.size) {
                minX = minOf(minX, outer[i]); maxX = maxOf(maxX, outer[i])
                minY = minOf(minY, outer[i + 1]); maxY = maxOf(maxY, outer[i + 1])
                i += 2
            }
            return Poly(parsed, minX, minY, maxX, maxY)
        }
    }
}

/**
 * Loads custom wave icons, falling back to generated placeholders for missing resources.
 */
internal object WaveIcons {
    private const val VARIANTS = 3
    private const val ICON_WIDTH = 48
    private const val ICON_HEIGHT = 24

    fun install(style: Style, context: Context) {
        val resources = intArrayOf(
            R.drawable.wave_0,
            R.drawable.wave_1,
            R.drawable.wave_2,
        )

        for (i in 0 until VARIANTS) {
            val custom = runCatching {
                val drawable = context.getDrawable(resources[i])
                    ?: return@runCatching null

                Bitmap.createBitmap(
                    ICON_WIDTH,
                    ICON_HEIGHT,
                    Bitmap.Config.ARGB_8888,
                ).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, ICON_WIDTH, ICON_HEIGHT)
                    drawable.draw(canvas)
                }
            }.getOrNull()

            style.addImage("wave-$i", custom ?: placeholder(i))
        }
    }

    private fun placeholder(variant: Int): Bitmap {
        val bmp = Bitmap.createBitmap(48, 24, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x592B2118
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }
        fun wave(x0: Float, x1: Float, y: Float) {
            val path = Path()
            path.moveTo(x0, y)
            var x = x0
            var up = true
            while (x < x1) {
                val next = minOf(x + 6f, x1)
                path.quadTo(x + 3f, if (up) y - 4f else y + 4f, next, y)
                up = !up
                x = next
            }
            canvas.drawPath(path, paint)
        }
        when (variant) {
            0 -> wave(4f, 44f, 12f)
            1 -> { wave(6f, 34f, 8f); wave(14f, 42f, 17f) }
            else -> wave(10f, 38f, 12f)
        }
        return bmp
    }
}
