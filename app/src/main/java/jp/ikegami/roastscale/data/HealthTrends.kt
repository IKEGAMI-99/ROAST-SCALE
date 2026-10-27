package jp.ikegami.roastscale.data

import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WaistEntry(val id: Long, val measuredAt: Long, val waistCm: Double)

object HealthTrends {
    fun waistToHeight(waist: Double, height: Int): Double? =
        if (height in 120..210 && waist in 30.0..200.0) waist / height else null

    /** Most recent reading of each local calendar date in last seven dates. */
    fun weight7DayAverage(rows: List<Measurement>, now: Long = System.currentTimeMillis()): Double? {
        val begin = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -6)
        }.timeInMillis
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val daily = LinkedHashMap<String, Double>()
        rows.asSequence().filter { it.measuredAt in begin..now }
            .sortedByDescending { it.measuredAt }
            .forEach {
                val day = formatter.format(Date(it.measuredAt))
                if (!daily.containsKey(day)) daily[day] = it.weightKg
            }
        return daily.values.takeIf { it.isNotEmpty() }?.average()
    }
}
