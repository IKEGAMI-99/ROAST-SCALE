package jp.ikegami.roastscale.data

/**
 * XMTZC02HM's 0x181B service-data advertisement (13 bytes).
 * Byte 1 flags: 0x20 stable, 0x02 impedance available, 0x80 load removed.
 * The scale repeats packets and may transmit impedance after stable weight.
 */
data class ScaleReading(val weightKg: Double, val impedanceOhm: Int?, val stabilized: Boolean)

object ScalePacket {
    fun decode(data: ByteArray): ScaleReading? {
        if (data.size != 13) return null
        val header = data[0].toInt() and 0xff
        val flags = data[1].toInt() and 0xff
        if (header and 0x80 != 0 || flags and 0x80 != 0) return null
        if (flags and 0x20 == 0) return null
        // Only the kg scale mode is supported; do not silently mislabel lb or catty.
        if (header and 0x10 != 0) return null
        val rawWeight = (data[11].toInt() and 0xff) or ((data[12].toInt() and 0xff) shl 8)
        val weight = rawWeight / 200.0
        if (weight !in 10.0..200.0) return null
        val rawImpedance = (data[9].toInt() and 0xff) or ((data[10].toInt() and 0xff) shl 8)
        val impedance = rawImpedance.takeIf {
            flags and 0x02 != 0 && it in 100..3000
        }
        return ScaleReading(weight, impedance, true)
    }
}
