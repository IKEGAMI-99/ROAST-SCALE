package jp.ikegami.roastscale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.MetricCatalog
import jp.ikegami.roastscale.data.MetricDefinition
import jp.ikegami.roastscale.data.Profile
import jp.ikegami.roastscale.data.ReferenceBand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Green = Color(0xFFB8FF6A)
private val Pink = Color(0xFFF2A1BF)
private val Amber = Color(0xFFFFBF70)
private val Silver = Color(0xFF9EACC1)
private val PanelBackground = Color(0xFF171E2C)

private fun number(value: Double?): String =
    if (value == null || !value.isFinite()) "—"
    else String.format(Locale.JAPAN, "%.1f", value)

private data class GaugeScale(
    val start: Double,
    val end: Double,
    val bandStart: Double?,
    val bandEnd: Double?
) {
    fun relative(v: Double): Float = ((v - start) / (end - start)).toFloat().coerceIn(0f, 1f)
}

private fun gaugeScale(reference: ReferenceBand, history: List<Double>, value: Double?): GaugeScale {
    val low = reference.min
    val high = reference.max
    if (reference.applicable && low != null && high != null && high > low) {
        val margin = (high - low) * 0.65
        return GaugeScale(max(0.0, low - margin), high + margin, low, high)
    }
    val observations = history.filter(Double::isFinite) + listOfNotNull(value?.takeIf(Double::isFinite))
    val observedMin = observations.minOrNull() ?: 0.0
    val observedMax = observations.maxOrNull() ?: 1.0
    val span = max(observedMax - observedMin, max(abs(observedMax) * 0.08, 1.0))
    return GaugeScale(max(0.0, observedMin - span * 0.5),
        observedMax + span * 0.5, null, null)
}

@Composable
fun MetricMeter(
    metric: MetricDefinition,
    current: Measurement?,
    history: List<Measurement>,
    profile: Profile,
    showTitle: Boolean = true,
    compact: Boolean = false
) {
    val value = current?.let(metric.value)
    val reference = metric.reference(profile)
    val observations = history.mapNotNull(metric.value)
    val scale = gaugeScale(reference, observations, value)
    val assessment = when {
        value == null -> "未測定"
        !reference.applicable || reference.min == null || reference.max == null -> "共通基準なし"
        value < reference.min -> "低め"
        value > reference.max -> "高め"
        else -> "参考範囲内"
    }
    val stateColor = when (assessment) {
        "参考範囲内" -> Green
        "低め" -> Amber
        "高め" -> Pink
        else -> Silver
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (compact) {
            Text(metric.label, color = Silver,
                style = MaterialTheme.typography.labelMedium,
                minLines = 2, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(number(value) + if (value != null && metric.unit.isNotBlank()) " " + metric.unit else "",
                color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(assessment, color = stateColor, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        } else if (showTitle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metric.label, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = Silver)
                Text(assessment, color = stateColor, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(3.dp))
            Text(number(value) + if (value != null && metric.unit.isNotBlank()) " " + metric.unit else "",
                color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(assessment, color = stateColor, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(if (compact) 3.dp else 9.dp))
        Canvas(Modifier.fillMaxWidth().height(if (compact) 16.dp else 24.dp)) {
            val cy = size.height / 2f
            val thickness = (if (compact) 5.dp else 9.dp).toPx()
            val corner = CornerRadius(thickness / 2f)
            drawRoundRect(color = Color(0xFF344254),
                topLeft = Offset(0f, cy - thickness / 2f),
                size = Size(size.width, thickness), cornerRadius = corner)
            if (scale.bandStart != null && scale.bandEnd != null) {
                val a = scale.relative(scale.bandStart) * size.width
                val b = scale.relative(scale.bandEnd) * size.width
                drawRoundRect(color = Green.copy(alpha = 0.85f),
                    topLeft = Offset(a, cy - thickness / 2f),
                    size = Size((b - a).coerceAtLeast(1f), thickness),
                    cornerRadius = corner)
            } else if (value != null) {
                // This is a position within the observed trend, not an "ideal".
                drawRoundRect(color = Color(0xFF516D8E),
                    topLeft = Offset(0f, cy - thickness / 2f),
                    size = Size((scale.relative(value) * size.width).coerceAtLeast(1f), thickness),
                    cornerRadius = corner)
            }
            if (value != null && value.isFinite()) {
                val x = scale.relative(value) * size.width
                drawCircle(Color(0xFF0B0F1A),
                    radius = (if (compact) 6.dp else 10.dp).toPx(),
                    center = Offset(x, cy))
                drawCircle(stateColor, radius = (if (compact) 4.dp else 7.dp).toPx(),
                    center = Offset(x, cy))
                drawCircle(Color.White, radius = (if (compact) 1.3.dp else 2.4.dp).toPx(),
                    center = Offset(x, cy))
            }
        }

        if (compact) {
            val shortReference =
                if (reference.applicable && reference.min != null && reference.max != null) {
                    "目安 " + number(reference.min) + "〜" + number(reference.max) +
                        if (metric.unit.isNotBlank()) " " + metric.unit else ""
                } else "適正値なし・履歴比較"
            Spacer(Modifier.height(3.dp))
            Text(shortReference, color = Silver,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        } else {
            if (reference.applicable && reference.min != null && reference.max != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("低め", color = Amber, style = MaterialTheme.typography.labelSmall)
                    Text("適正目安", color = Green, style = MaterialTheme.typography.labelSmall)
                    Text("高め", color = Pink, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("履歴内での相対位置", color = Silver,
                        style = MaterialTheme.typography.labelSmall)
                    Text("適正判定なし", color = Silver,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("目安: " + reference.label, color = Silver,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun HistoryCharts(history: List<Measurement>, profile: Profile) {
    if (history.isEmpty()) {
        Text("測定後、全項目の折れ線グラフが表示されます。",
            color = Silver, style = MaterialTheme.typography.bodyMedium)
        return
    }
    var selected by rememberSaveable { mutableIntStateOf(30) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to "直近7件", 30 to "直近30件", 0 to "全件").forEach { (limit, label) ->
            FilterChip(
                selected = selected == limit,
                onClick = { selected = limit },
                label = { Text(label) }
            )
        }
    }
    val points = if (selected == 0) history.reversed() else history.take(selected).reversed()
    MetricCatalog.all.forEach { metric ->
        val reference = metric.reference(profile)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PanelBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                val last = points.lastOrNull()?.let(metric.value)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(metric.label, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text(number(last) + if (last != null && metric.unit.isNotBlank()) " " + metric.unit else "",
                        color = Green, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                LineGraph(points, metric, reference)
                Spacer(Modifier.height(6.dp))
                Text(if (reference.applicable) "緑の帯は参考範囲です。" else "一律の適正範囲はありません。推移をご確認ください。",
                    color = Silver, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LineGraph(
    rows: List<Measurement>,
    metric: MetricDefinition,
    reference: ReferenceBand
) {
    val series = rows.map { it.measuredAt to metric.value(it)?.takeIf(Double::isFinite) }
    val values = series.mapNotNull { it.second }
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
            Text("データなし", color = Silver)
        }
        return
    }
    val bandMin = if (reference.applicable) reference.min else null
    val bandMax = if (reference.applicable) reference.max else null
    val low = min(values.minOrNull() ?: 0.0, bandMin ?: Double.POSITIVE_INFINITY)
    val high = max(values.maxOrNull() ?: 0.0, bandMax ?: Double.NEGATIVE_INFINITY)
    val delta = (high - low).takeIf { it > 0.00001 } ?: max(abs(high) * 0.05, 1.0)
    val yMin = low - delta * 0.12
    val yMax = high + delta * 0.12
    val xMin = series.first().first
    val xMax = series.last().first
    val firstDate = SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(xMin))
    val lastDate = SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(xMax))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("最大 " + number(values.maxOrNull()) + " " + metric.unit,
            color = Silver, style = MaterialTheme.typography.labelSmall)
        Text("最小 " + number(values.minOrNull()) + " " + metric.unit,
            color = Silver, style = MaterialTheme.typography.labelSmall)
    }
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().height(152.dp)) {
        val plotLeft = 5.dp.toPx()
        val plotRight = size.width - 5.dp.toPx()
        val plotTop = 9.dp.toPx()
        val plotBottom = size.height - 9.dp.toPx()
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        fun xOf(timestamp: Long): Float = if (xMax == xMin) {
            (plotLeft + plotRight) / 2f
        } else {
            (plotLeft + ((timestamp - xMin).toDouble() /
                (xMax - xMin).toDouble()).toFloat() * plotWidth)
        }
        fun yOf(v: Double): Float = plotBottom -
            ((v - yMin) / (yMax - yMin)).toFloat() * plotHeight

        if (bandMin != null && bandMax != null) {
            val upper = yOf(bandMax).coerceIn(plotTop, plotBottom)
            val lower = yOf(bandMin).coerceIn(plotTop, plotBottom)
            if (lower > upper) {
                drawRect(Green.copy(alpha = 0.09f),
                    topLeft = Offset(plotLeft, upper), size = Size(plotWidth, lower - upper))
            }
        }
        repeat(5) { index ->
            val y = plotTop + plotHeight * index / 4f
            drawLine(color = Color(0xFF354254), start = Offset(plotLeft, y),
                end = Offset(plotRight, y), strokeWidth = 1.dp.toPx())
        }
        var previous: Offset? = null
        // Missing BIA values create a genuine gap rather than an invented reading.
        series.forEach { (timestamp, value) ->
            if (value == null) {
                previous = null
            } else {
                val point = Offset(xOf(timestamp), yOf(value))
                previous?.let {
                    drawLine(Green, it, point, strokeWidth = 2.8.dp.toPx())
                }
                drawCircle(Green, radius = 3.3.dp.toPx(), center = point)
                previous = point
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(firstDate, color = Silver, style = MaterialTheme.typography.labelSmall)
        Text(if (values.size == 1) "1件のため点のみ表示" else lastDate,
            color = Silver, style = MaterialTheme.typography.labelSmall)
    }
}
