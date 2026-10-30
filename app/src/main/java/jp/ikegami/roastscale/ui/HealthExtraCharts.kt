package jp.ikegami.roastscale.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/** Additional history graphs reuse the same axis and tap interaction as BIA graphs. */
@Composable
fun HealthExtraCharts(weights: List<Measurement>, waists: List<WaistEntry>, profile: Profile) {
    val weightSorted = weights.sortedBy { it.measuredAt }
    val rollingAverage = weightSorted.map { record ->
        HistoryPlotPoint(
            record.id,
            record.measuredAt,
            HealthTrends.weight7DayAverage(weights, record.measuredAt)
        )
    }
    SingleLineCard(
        title = "直近7日間平均体重",
        subtitle = "1暦日1測定の平均 · kg",
        points = rollingAverage,
        threshold = null,
        unit = "kg"
    )
    Spacer(Modifier.height(12.dp))
    SingleLineCard(
        title = "腹囲（手入力）",
        subtitle = "メジャーで測定した腹囲 · cm",
        points = waists.sortedBy { it.measuredAt }
            .map { HistoryPlotPoint(it.id, it.measuredAt, it.waistCm) },
        threshold = null,
        unit = "cm"
    )
    Spacer(Modifier.height(12.dp))
    SingleLineCard(
        title = "腹囲身長比（WHtR）",
        subtitle = "成人の一般目安：0.5未満",
        points = waists.sortedBy { it.measuredAt }.map {
            HistoryPlotPoint(
                it.id, it.measuredAt,
                HealthTrends.waistToHeight(it.waistCm, profile.heightCm)
            )
        },
        threshold = 0.5,
        unit = "",
        axisDigits = 3
    )
}

@Composable
private fun SingleLineCard(
    title: String,
    subtitle: String,
    points: List<HistoryPlotPoint>,
    threshold: Double?,
    unit: String,
    axisDigits: Int = 1
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
            val valid = points.filter { it.value?.isFinite() == true }
            if (valid.isEmpty()) {
                Text("測定データなし", color = Secondary, modifier = Modifier.padding(vertical = 32.dp))
                return@Column
            }
            val values = valid.mapNotNull { it.value }
            fun n(v: Double?): String = v?.let {
                String.format(Locale.JAPAN, "%." + axisDigits + "f", it)
            } ?: "—"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最新 " + n(valid.last().value) + if (unit.isNotBlank()) " " + unit else "",
                    color = Lime, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold)
                Text("最小 " + n(values.minOrNull()) + " / 最大 " + n(values.maxOrNull()),
                    color = Secondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            val lo = min(values.minOrNull() ?: 0.0, threshold ?: Double.POSITIVE_INFINITY)
            val hi = max(values.maxOrNull() ?: 1.0, threshold ?: Double.NEGATIVE_INFINITY)
            val pad = max(hi - lo, max(abs(hi) * 0.02, 0.01))
            val yMin = lo - pad * 0.2
            val yMax = hi + pad * 0.2
            val tMin = valid.first().measuredAt
            val tMax = valid.last().measuredAt
            var selectedId by remember(title) { mutableStateOf<Long?>(null) }

            InteractiveHistoryPlot(
                points = points,
                yMin = yMin,
                yMax = yMax,
                selectedId = selectedId,
                onSelect = { selectedId = it.id },
                height = 152.dp,
                threshold = threshold,
                axisDigits = axisDigits
            )
            valid.firstOrNull { it.id == selectedId }?.let { reading ->
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF253449)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
                                .format(Date(reading.measuredAt)),
                            color = Secondary, style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            title + ": " + n(reading.value) +
                                if (unit.isNotBlank()) " " + unit else "",
                            color = Lime, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            val date = SimpleDateFormat("MM/dd", Locale.JAPAN)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(date.format(Date(tMin)), color = Secondary,
                    style = MaterialTheme.typography.labelSmall)
                Text(if (valid.size == 1) "記録1件" else date.format(Date(tMax)),
                    color = Secondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
