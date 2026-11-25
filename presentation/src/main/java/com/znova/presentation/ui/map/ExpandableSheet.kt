package com.znova.presentation.ui.map

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.znova.ui.map.SHEET_FRACTION
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val EXPANDED = 0.94f
private const val FLING = 600f

/**
 * Two snap points: collapsed and expanded.
 * Upward scroll expands the sheet before scrolling its content.
 */
@Composable
internal fun ExpandableSheet(
    modifier: Modifier = Modifier,
    content: @Composable (sheet: Modifier, handle: Modifier, toggle: () -> Unit) -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        var fraction by remember { mutableFloatStateOf(SHEET_FRACTION) }
        val scope = rememberCoroutineScope()

        // Consume the vertical delta and return the distance moved by the sheet.
        fun drag(dy: Float): Float {
            val old = fraction
            fraction = (old - dy / heightPx).coerceIn(SHEET_FRACTION, EXPANDED)
            return (old - fraction) * heightPx
        }

        suspend fun animateTo(target: Float) {
            animate(fraction, target) { value, _ -> fraction = value }
        }

        suspend fun settle(velocity: Float) {
            val middle = (SHEET_FRACTION + EXPANDED) / 2
            val expand = if (abs(velocity) > FLING) velocity < 0 else fraction > middle
            animateTo(if (expand) EXPANDED else SHEET_FRACTION)
        }

        val connection = remember(heightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (source == NestedScrollSource.UserInput && available.y < 0) {
                        Offset(0f, drag(available.y))
                    } else {
                        Offset.Zero
                    }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset =
                    if (source == NestedScrollSource.UserInput && available.y > 0) {
                        Offset(0f, drag(available.y))
                    } else {
                        Offset.Zero
                    }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (fraction <= SHEET_FRACTION || fraction >= EXPANDED) return Velocity.Zero
                    settle(available.y)
                    return available
                }
            }
        }

        val dragState = rememberDraggableState { drag(it) }

        content(
            Modifier
                .fillMaxWidth()
                .height(maxHeight * fraction)
                .nestedScroll(connection),
            Modifier.draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { settle(it) },
            ),
        ) {
            val middle = (SHEET_FRACTION + EXPANDED) / 2
            scope.launch { animateTo(if (fraction > middle) SHEET_FRACTION else EXPANDED) }
        }
    }
}
