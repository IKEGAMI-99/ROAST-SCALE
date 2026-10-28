package jp.ikegami.roastscale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.ikegami.roastscale.data.HealthTrends
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.Profile
import jp.ikegami.roastscale.data.WaistEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Lime = Color(0xFFB8FF6A)
private val Secondary = Color(0xFFABB7C8)

/** Three extra history charts complementing the existing ten BIA charts. */
@Composable
fun HealthExtraCharts(weights: List<Measurement>, waists: List<WaistEntry>, profile: Profile) {
    val weightSorted = weights.sortedBy { it.measuredAt }
    val rollingAverage = weightSorted.map { m ->
        m.measuredAt to HealthTrends.weight7DayAverage(weights, m.measuredAt)
    }
    SingleLineCard(
        title = "直近7日間平均体重",
        subtitle = "1暦日1測定の平均 · kg",
        points = rollingAverage,
        threshold = null
    )
    Spacer(Modifier.height(12.dp))
    SingleLineCard(
        title = "腹囲（手入力）",
        subtitle = "メジャーで測定した腹囲 · cm",
        points = waists.sortedBy { it.measuredAt }.map { it.measuredAt to it.waistCm },
        threshold = null
    )
    Spacer(Modifier.height(12.dp))
    SingleLineCard(
        title = "腹囲身長比（WHtR）",
        subtitle = "成人の一般目安：0.5未満",
        points = waists.sortedBy { it.measuredAt }.map {
            it.measuredAt to HealthTrends.waistToHeight(it.waistCm,profile.heightCm)
        },
        threshold = 0.5
    )
}

@Composable
private fun SingleLineCard(
    title: String,
    subtitle: String,
    points: List<Pair<Long, Double?>>,
    threshold: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171E2C))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Secondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            val valid = points.filter { it.second?.isFinite() == true }
            if (valid.isEmpty()) {
                Text("測定データなし", color = Secondary, modifier = Modifier.padding(vertical = 32.dp))
                return@Column
            }
            val values = valid.mapNotNull { it.second }
            fun n(v: Double?) = v?.let { String.format(Locale.JAPAN,"%.2f",it) } ?: "—"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最新 " + n(valid.last().second), color = Lime,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("最小 " + n(values.minOrNull()) + " / 最大 " + n(values.maxOrNull()),
                    color = Secondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            val lo = min(values.minOrNull() ?: 0.0, threshold ?: Double.POSITIVE_INFINITY)
            val hi = max(values.maxOrNull() ?: 1.0, threshold ?: Double.NEGATIVE_INFINITY)
            val pad = max(hi - lo, max(abs(hi) * 0.02, 0.01))
            val yMin = lo - pad * 0.2
            val yMax = hi + pad * 0.2
            val tMin = valid.first().first
            val tMax = valid.last().first

            Canvas(Modifier.fillMaxWidth().height(125.dp)) {
                val left = 8.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                fun x(t: Long) = if (tMax == tMin) (left + right) / 2f
                    else left + ((t - tMin).toDouble() / (tMax - tMin).toDouble()).toFloat() * (right - left)
                fun y(v: Double) = bottom - ((v - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
                repeat(4) { i ->
                    val row = top + (bottom - top) * i / 3f
                    drawLine(Color(0xFF354254), Offset(left,row), Offset(right,row),
                        strokeWidth = 1.dp.toPx())
                }
                threshold?.let {
                    val yThreshold = y(it)
                    if (yThreshold in top..bottom) {
                        drawLine(Color(0xFFE7B16A), Offset(left,yThreshold),Offset(right,yThreshold),
                            strokeWidth = 1.5.dp.toPx())
                    }
                }
                var last: Offset? = null
                valid.forEach { (time,value) ->
                    if (value != null) {
                        val p = Offset(x(time), y(value))
                        last?.let { drawLine(Lime,it,p,strokeWidth=2.5.dp.toPx()) }
                        drawCircle(Lime,radius=3.5.dp.toPx(),center=p)
                        last=p
                    }
                }
            }
            val date = SimpleDateFormat("MM/dd",Locale.JAPAN)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(date.format(Date(tMin)),color=Secondary,style=MaterialTheme.typography.labelSmall)
                Text(if(valid.size==1) "記録1件" else date.format(Date(tMax)),
                    color=Secondary,style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
