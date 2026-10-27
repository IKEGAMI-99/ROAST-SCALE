package jp.ikegami.roastscale.data

import kotlin.math.max
import kotlin.math.min

data class Profile(val heightCm: Int = 0, val age: Int = 0, val sex: String = "male") {
    val valid: Boolean get() = heightCm in 120..210 && age in 18..99 && sex in listOf("male", "female")
}
data class Composition(
    val bmi: Double,
    val fatPercent: Double?,
    val waterPercent: Double?,
    val muscleKg: Double?,
    val boneKg: Double?,
    val proteinPercent: Double?,
    val visceralIndex: Double?,
    val bmrKcal: Double?
)

/** Reverse-engineered 2017 Mi Fit estimates. NOT clinical measurements. */
object BodyMetrics {
    private fun clamp(v: Double, low: Double, high: Double) = max(low, min(high, v))
    fun estimate(reading: ScaleReading, profile: Profile): Composition? {
        if (!profile.valid || reading.weightKg !in 20.0..200.0) return null
        val w = reading.weightKg
        val h = profile.heightCm.toDouble()
        val age = profile.age.toDouble()
        val bmi = w / ((h / 100) * (h / 100))
        val z = reading.impedanceOhm?.toDouble()
        if (z == null || z !in 100.0..3000.0) {
            return Composition(bmi, null, null, null, null, null, null, null)
        }
        val female = profile.sex == "female"
        val lbm = (h * 9.058 / 100) * (h / 100) + w * 0.32 + 12.226 - z * 0.0068 - age * 0.0542
        val constant = if (female) (if (age <= 49) 9.25 else 7.25) else 0.8
        val coefficient = when {
            !female && w < 61 -> 0.98
            female && w > 60 -> 0.96 * (if (h > 160) 1.03 else 1.0)
            female && w < 50 -> 1.02 * (if (h > 160) 1.03 else 1.0)
            else -> 1.0
        }
        var fat = (1.0 - (lbm - constant) * coefficient / w) * 100
        if (fat > 63) fat = 75.0
        fat = clamp(fat, 5.0, 75.0)
        val waterBase = (100 - fat) * 0.7
        val water = clamp(waterBase * (if (waterBase <= 50) 1.02 else 0.98), 35.0, 75.0)
        var bone = -((if (female) 0.245691014 else 0.18016894) - lbm * 0.05158)
        bone = clamp(bone + if (bone > 2.2) 0.1 else -0.1, 0.5, 8.0)
        val muscle = clamp(w - fat * w / 100 - bone, 10.0, 120.0)
        val protein = clamp(muscle / w * 100 - water, 5.0, 32.0)
        val visceral = if (female) {
            if (w > (13 - h * 0.5) * -1) {
                val sub = h * 1.45 + (h * 0.1158) * h - 120
                (w * 500 / sub - 6) + age * 0.07
            } else {
                val sub = 0.691 + h * -0.0024 + h * -0.0024
                (((h * 0.027) - (sub * w)) * -1) + age * 0.07 - age
            }
        } else {
            if (h < w * 1.6) {
                val sub = ((h * 0.4) - h * (h * 0.0826)) * -1
                ((w * 305) / (sub + 48)) - 2.9 + age * 0.15
            } else {
                val sub = 0.765 + h * -0.0015
                (((h * 0.143) - w * sub) * -1) + age * 0.15 - 5
            }
        }
        val bmr = if (female) 864.6 + w * 10.2036 - h * 0.39336 - age * 6.204
            else 877.8 + w * 14.916 - h * 0.726 - age * 8.976
        return Composition(bmi, fat, water, muscle, bone, protein, clamp(visceral, 1.0, 50.0), clamp(bmr, 500.0, 10000.0))
    }
}
