package jp.ikegami.roastscale.data

import java.util.Locale

/**
 * Adult reference values, NOT a clinical diagnosis or automatic weight-loss target.
 * Referenced from Japanese MHLW BMI ranges and Tanita adult composition guides.
 * Cross-device BIA estimates are not interchangeable.
 */
data class ReferenceBand(
    val label: String,
    val min: Double? = null,
    val max: Double? = null,
    val applicable: Boolean = false
) {
    fun inRange(value: Double?): Boolean? {
        if (!applicable || value == null || min == null || max == null) return null
        return value >= min && value <= max
    }
}

object ReferenceGuide {
    private fun n(value: Double) = String.format(Locale.JAPAN, "%.1f", value)

    fun weight(profile: Profile): ReferenceBand {
        if (!profile.valid) return ReferenceBand("身長を設定すると参考体重を表示")
        val meters = profile.heightCm / 100.0
        val factor = meters * meters
        val min = 18.5 * factor
        val max = 24.9 * factor
        val target = 22.0 * factor
        return ReferenceBand("普通体重 " + n(min) + "〜" + n(max) +
            " kg  /  BMI22: " + n(target) + " kg", min, max, true)
    }

    fun bmi(): ReferenceBand = ReferenceBand("普通体重 18.5〜24.9  /  基準値22.0", 18.5, 24.9999, true)

    fun fat(profile: Profile): ReferenceBand {
        if (!profile.valid) return ReferenceBand("年齢・性別を設定すると参考範囲を表示")
        val female = profile.sex == "female"
        // Tanita adult reference ranges, age-band adjusted (not derived from this Xiaomi scale).
        val band = when {
            profile.age < 20 -> if (female) 21.0 to 31.0 else 14.0 to 24.0
            profile.age < 40 -> if (female) 21.0 to 33.0 else 8.0 to 19.0
            profile.age < 60 -> if (female) 23.0 to 34.0 else 11.0 to 21.0
            else -> if (female) 24.0 to 36.0 else 13.0 to 25.0
        }
        return ReferenceBand("成人の参考範囲 " + n(band.first) + "〜" + n(band.second) + " %", band.first, band.second, true)
    }

    fun water(profile: Profile): ReferenceBand {
        if (!profile.valid) return ReferenceBand("性別を設定すると参考範囲を表示")
        val low = if (profile.sex == "female") 45.0 else 55.0
        val high = if (profile.sex == "female") 60.0 else 65.0
        return ReferenceBand("一般参考 " + n(low) + "〜" + n(high) + " %", low, high, true)
    }

    fun muscle(): ReferenceBand =
        ReferenceBand("理想値は一律に設定不可・前回比で確認")

    fun bone(): ReferenceBand =
        ReferenceBand("機器依存の推定量・骨密度ではありません")

    fun protein(): ReferenceBand =
        ReferenceBand("推定式固有の値・一般的な理想値なし")

    fun visceral(): ReferenceBand =
        ReferenceBand("機器ごとに判定基準が異なります")

    fun bmr(): ReferenceBand =
        ReferenceBand("年齢・体格依存・高低だけでは判定不可")

    fun impedance(): ReferenceBand =
        ReferenceBand("電極・体水分などで変動・理想値なし")
}
