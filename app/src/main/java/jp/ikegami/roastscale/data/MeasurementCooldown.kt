package jp.ikegami.roastscale.data

import kotlin.math.abs

/** Avoid saving successive BLE advertisements as separate weigh-ins. */
internal object MeasurementCooldown {
    const val LOCK_MS = 30_000L
    private const val SAME_WEIGHT_WINDOW_MS = 90_000L
    private const val SAME_WEIGHT_TOLERANCE_KG = 0.11

    fun isDuplicate(
        previousAt: Long,
        previousWeightKg: Double,
        receivedAt: Long,
        receivedWeightKg: Double
    ): Boolean {
        val elapsed = receivedAt - previousAt
        if (elapsed < 0L) return false
        // Block every new record for 30 seconds, even if the scale fluctuates.
        if (elapsed < LOCK_MS) return true
        // Keep the existing protection against repeated advertisements of
        // the same reading after the initial lock expires.
        return elapsed < SAME_WEIGHT_WINDOW_MS &&
            abs(previousWeightKg - receivedWeightKg) <= SAME_WEIGHT_TOLERANCE_KG
    }
}
