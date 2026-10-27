package jp.ikegami.roastscale.data

import org.junit.Assert.*
import org.junit.Test

class ScalePacketTest {
    @Test fun decodesStableWeightAndImpedance() {
        val bytes = byteArrayOf(
            0x02, 0x22, 0xEA.toByte(), 0x07, 0x0A, 0x1B, 0x10, 0x00,
            0x00, 0x30, 0x02, 0xC8.toByte(), 0x32
        )
        val result = ScalePacket.decode(bytes)
        assertNotNull(result)
        assertEquals(65.0, result!!.weightKg, 0.001)
        assertEquals(560, result.impedanceOhm)
    }

    @Test fun rejectsUnstableAndInvalid() {
        assertNull(ScalePacket.decode(byteArrayOf(0x02, 0x00) + ByteArray(11)))
        assertNull(ScalePacket.decode(ByteArray(12)))
    }

    @Test fun bodyMetricsNeedsProfileAndImpedance() {
        val r = ScaleReading(65.0, 560, true)
        assertNull(BodyMetrics.estimate(r, Profile()))
        val data = BodyMetrics.estimate(r, Profile(172, 35, "female"))
        assertNotNull(data)
        assertEquals(31.7, data!!.fatPercent!!, 0.5)
    }
}
