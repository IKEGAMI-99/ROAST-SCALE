package jp.ikegami.roastscale.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** One history sample, with an actual persisted record ID for accurate tap selection. */
data class HistoryPlotPoint(
    val id: Long,
    val measuredAt: Long,
    val value: Double?
)

private val Lime = Color(0xFFB8FF6A)
private val Grid = Color(0xFF354254)
private val AxisLabel = Color(0xFFABB7C8)

private fun xOf(
    timestamp: Long, first: Long, last: Long,
    left: Float, right: Float
): Float = if (first == last) (left + right) / 2f
else left + ((timestamp - first).toDouble() / (last - first).toDouble()).toFloat() * (right - left)

private fun yOf(value: Double, low: Double, high: Double, top: Float, bottom: Float): Float =
    bottom - ((value - low) / (high - low)).toFloat() * (bottom - top)

/**
 * Shared touch-enabled history chart. Y values use the same scale and positions
 * as the plotted samples. A tap selects only a nearby *actual* measurement dot.
 */
@Composable
fun InteractiveHistoryPlot(
    points: List<HistoryPlotPoint>,
    yMin: Double,
    yMax: Double,
    selectedId: Long?,
    onSelect: (HistoryPlotPoint) -> Unit,
    height: Dp = 152.dp,
    bandMin: Double? = null,
    bandMax: Double? = null,
    threshold: Double? = null,
    axisDigits: Int = 1
) {
    val usable = points.filter { it.value?.isFinite() == true }
    if (usable.isEmpty() || yMax <= yMin) return
    val first = points.first().measuredAt
    val last = points.last().measuredAt
    val labelFormat = "%." + axisDigits.coerceIn(0, 3) + "f"

    Canvas(
        Modifier.fillMaxWidth().height(height).pointerInput(points, yMin, yMax) {
            detectTapGestures { tap ->
                val left = 58.dp.toPx()
                val right = size.width.toFloat() - 8.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = size.height.toFloat() - 10.dp.toPx()
                if (right <= left || bottom <= top) return@detectTapGestures
                val nearest = usable.minByOrNull { point ->
                    val px = xOf(point.measuredAt, first, last, left, right)
                    val py = yOf(point.value!!, yMin, yMax, top, bottom)
                    val dx = px - tap.x
                    val dy = py - tap.y
                    dx * dx + dy * dy
                } ?: return@detectTapGestures
                val dx = xOf(nearest.measuredAt, first, last, left, right) - tap.x
                val dy = yOf(nearest.value!!, yMin, yMax, top, bottom) - tap.y
                val hitRadius = 24.dp.toPx()
                if (dx * dx + dy * dy <= hitRadius * hitRadius) onSelect(nearest)
            }
        }
    ) {
        val left = 58.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas
        val width = right - left
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(171, 183, 200)
            textSize = 10.sp.toPx()
            textAlign = Paint.Align.RIGHT
        }

        if (bandMin != null && bandMax != null) {
            val upper = yOf(bandMax, yMin, yMax, top, bottom).coerceIn(top, bottom)
            val lower = yOf(bandMin, yMin, yMax, top, bottom).coerceIn(top, bottom)
            if (lower > upper) {
                drawRect(Lime.copy(alpha = 0.09f),
                    topLeft = Offset(left, upper), size = Size(width, lower - upper))
            }
        }

        for (i in 0..4) {
            val fraction = i / 4.0
            val y = top + (bottom - top) * fraction.toFloat()
            val value = yMax - (yMax - yMin) * fraction
            drawLine(Grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.JAPAN, labelFormat, value),
                left - 7.dp.toPx(), y + labelPaint.textSize * 0.35f, labelPaint
            )
        }

        threshold?.let { value ->
            val y = yOf(value, yMin, yMax, top, bottom)
            if (y in top..bottom) {
                drawLine(Color(0xFFE7B16A), Offset(left, y), Offset(right, y),
                    strokeWidth = 1.5.dp.toPx())
            }
        }

        val highlighted = usable.firstOrNull { it.id == selectedId }
        highlighted?.let { point ->
            val x = xOf(point.measuredAt, first, last, left, right)
            drawLine(Lime.copy(alpha = 0.35f), Offset(x, top), Offset(x, bottom),
                strokeWidth = 1.dp.toPx())
        }

        var previous: Offset? = null
        for (point in points) {
            val value = point.value?.takeIf(Double::isFinite)
            if (value == null) {
                previous = null
                continue
            }
            val center = Offset(
                xOf(point.measuredAt, first, last, left, right),
                yOf(value, yMin, yMax, top, bottom)
            )
            previous?.let { drawLine(Lime, it, center, strokeWidth = 2.8.dp.toPx()) }
            val isSelected = point.id == selectedId
            if (isSelected) {
                drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
                drawCircle(Lime, radius = 4.5.dp.toPx(), center = center)
            } else {
                drawCircle(Lime, radius = 3.5.dp.toPx(), center = center)
            }
            previous = center
        }
    }
}
