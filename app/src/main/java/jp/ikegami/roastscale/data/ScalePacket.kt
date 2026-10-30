package jp.ikegami.roastscale.data

/** XMTZC02HM / MIBCS: UUID 0x181B, 13-byte LE service data. */
data class ScaleReading(val weightKg: Double, val impedanceOhm: Int?, val stabilized: Boolean)

object ScalePacket {
    /** Weight shown during an in-progress BLE measurement, before stabilization. */
    fun previewWeight(data: ByteArray): Double? {
        if (data.size != 13 || (data[0].toInt() and 0xff) != 0x02) return null
        val raw = (data[11].toInt() and 0xff) or ((data[12].toInt() and 0xff) shl 8)
        return (raw / 200.0).takeIf { it in 10.0..200.0 }
    }

    fun decode(data: ByteArray): ScaleReading? {
        if (data.size != 13) return null
        val unit = data[0].toInt() and 0xff
        val flags = data[1].toInt() and 0xff
        // On the original Mi Body Composition Scale 0x02 denotes kg units.
        if (unit != 0x02) return null
        if (flags and 0x20 == 0) return null // Not yet stabilized.
        // IMPORTANT: 0x80 is "load removed". A FINAL VALID measurement
        // can have flags 0xA6 (stable + impedance + load removed).
        val rawWeight = (data[11].toInt() and 0xff) or ((data[12].toInt() and 0xff) shl 8)
        val weight = rawWeight / 200.0
        if (weight !in 10.0..200.0) return null
        val rawImpedance = (data[9].toInt() and 0xff) or ((data[10].toInt() and 0xff) shl 8)
        val impedance = rawImpedance.takeIf {
            flags and 0x02 != 0 && it in 100..3000
        }
        return ScaleReading(weight, impedance, true)
    }

    fun explain(data: ByteArray): String {
        if (data.size != 13) return "不明なデータ長 " + data.size + " (期待13)"
        val unit = data[0].toInt() and 0xff
        val flags = data[1].toInt() and 0xff
        val rawWeight = (data[11].toInt() and 0xff) or ((data[12].toInt() and 0xff) shl 8)
        return when {
            unit != 0x02 -> "未対応の重量単位 0x" + unit.toString(16)
            flags and 0x20 == 0 -> "計量中（未安定） " + (rawWeight / 200.0) + " kg"
            rawWeight / 200.0 !in 10.0..200.0 -> "体重値が範囲外"
            else -> "有効な測定パケット"
        }
    }
}
