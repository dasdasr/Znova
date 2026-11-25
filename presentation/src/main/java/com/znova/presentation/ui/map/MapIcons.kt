package com.znova.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

private const val INK = 0xFF2B2118.toInt()
private const val PAPER = 0xFFF4E9CD.toInt()
private const val RED = 0xFFB23A30.toInt()
private const val SIZE = 48

private fun paint(
    color: Int,
    stroke: Float? = null,
) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    if (stroke != null) {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeJoin = Paint.Join.ROUND
    }
}

/**
 * Generates city marker bitmaps without external sprite assets.
 */
internal object MapIcons {
    const val CAPITAL = "city-capital"
    const val MAJOR = "city-major"
    const val MINOR = "city-minor"

    fun capital(): Bitmap =
        bitmap { c ->
            val path =
                Path().apply {
                    moveTo(24f, 3f)
                    lineTo(43f, 24f)
                    lineTo(24f, 45f)
                    lineTo(5f, 24f)
                    close()
                }

            c.drawPath(path, paint(PAPER))
            c.drawPath(path, paint(INK, 3.5f))

            c.drawCircle(24f, 24f, 6f, paint(RED))
            c.drawCircle(24f, 24f, 2f, paint(PAPER))
        }

    fun major(): Bitmap =
        bitmap { c ->
            c.drawCircle(24f, 24f, 16f, paint(INK))
            c.drawCircle(24f, 24f, 12f, paint(PAPER))
            c.drawCircle(24f, 24f, 5f, paint(RED))
        }

    fun minor(): Bitmap =
        bitmap { c ->
            c.drawCircle(24f, 24f, 8f, paint(PAPER))
            c.drawCircle(24f, 24f, 6f, paint(INK))
        }

    private fun bitmap(draw: (Canvas) -> Unit): Bitmap =
        Bitmap
            .createBitmap(
                SIZE,
                SIZE,
                Bitmap.Config.ARGB_8888,
            ).also {
                draw(Canvas(it))
            }
}
