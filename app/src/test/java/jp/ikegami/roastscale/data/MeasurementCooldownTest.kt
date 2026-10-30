package jp.ikegami.roastscale.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCooldownTest {
    @Test fun blocksAllWeightsWithinThirtySeconds() {
        assertTrue(MeasurementCooldown.isDuplicate(1_000L, 74.0, 1_001L, 74.0))
        assertTrue(MeasurementCooldown.isDuplicate(1_000L, 74.0, 30_999L, 76.5))
    }

    @Test fun permitsNewDifferentReadingAfterThirtySeconds() {
        assertFalse(MeasurementCooldown.isDuplicate(1_000L, 74.0, 31_000L, 74.3))
    }

    @Test fun stillBlocksSameWeightForNinetySeconds() {
        assertTrue(MeasurementCooldown.isDuplicate(1_000L, 74.0, 60_000L, 74.05))
        assertFalse(MeasurementCooldown.isDuplicate(1_000L, 74.0, 91_000L, 74.0))
    }

    @Test fun ignoresFutureDatedHistory() {
        assertFalse(MeasurementCooldown.isDuplicate(50_000L, 74.0, 1_000L, 74.0))
    }
}
