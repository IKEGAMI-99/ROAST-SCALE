package jp.ikegami.roastscale.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Imports the CSV emitted by HealthExport. Derived columns (7-day average and
 * waist-to-height ratio) are recalculated by the app, never stored.
 * Validate every row before touching the database.
 */
object HealthImport {
    data class WeightRow(
        val measuredAt: Long,
        val weightKg: Double,
        val impedanceOhm: Int?,
        val composition: Composition?
    )
    data class WaistRow(val measuredAt: Long, val waistCm: Double)
    data class Data(val weights: List<WeightRow>, val waists: List<WaistRow>)

    private val datePattern = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} [+-]\d{4}""")
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.JAPAN).apply {
        isLenient = false
    }

    /** RFC 4180-style CSV with doubled quotes and CRLF support. */
    private fun rows(input: String): List<List<String>> {
        val output = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closedQuote = false
        var atStart = true
        var i = 0
        fun finishCell() {
            current += cell.toString()
            cell.setLength(0)
            atStart = true
            closedQuote = false
        }
        fun finishRow() {
            finishCell()
            if (!(current.size == 1 && current[0].isBlank())) output += current.toList()
            current.clear()
        }
        while (i < input.length) {
            val c = input[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                        closedQuote = true
                    }
                } else cell.append(c)
            } else {
                when (c) {
                    '"' -> {
                        require(atStart && !closedQuote) { "CSVの引用符が不正です" }
                        quoted = true
                        atStart = false
                    }
                    ',' -> finishCell()
                    '\n', '\r' -> {
                        finishRow()
                        if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    }
                    else -> {
                        require(!closedQuote) { "引用符の後に不正な文字があります" }
                        cell.append(c)
                        atStart = false
                    }
                }
            }
            i++
        }
        require(!quoted) { "CSVの引用符が閉じられていません" }
        if (current.isNotEmpty() || cell.isNotEmpty() || closedQuote) finishRow()
        return output
    }

    fun parse(csvText: String): Data {
        val records = rows(csvText.removePrefix("\uFEFF"))
        require(records.isNotEmpty()) { "CSVが空です" }
        require(records.size <= 50_001) { "CSVは最大50,000件までです" }
        val header = records.first()
        require(header.size == header.distinct().size) { "CSVに重複した列名があります" }
        val columns = header.withIndex().associate { it.value to it.index }
        require(listOf("kind", "date_local", "weight_kg", "waist_cm_manual").all {
            it in columns
        }) { "ROAST SCALEのCSV形式ではありません" }

        val weights = mutableListOf<WeightRow>()
        val waists = mutableListOf<WaistRow>()
        for ((index, fields) in records.drop(1).withIndex()) {
            val line = (index + 2).toString()
            require(fields.size == header.size) { "CSVの" + line + "行目は列数が一致しません" }
            fun field(name: String): String = columns[name]?.let { fields[it].trim() } ?: ""
            fun number(name: String): Double? {
                val raw = field(name)
                if (raw.isEmpty()) return null
                val value = raw.toDoubleOrNull()
                require(value != null && value.isFinite()) {
                    line + "行目の" + name + "が数値ではありません"
                }
                return value
            }
            val date = field("date_local")
            require(datePattern.matches(date)) { line + "行目の日時形式が違います" }
            val position = ParsePosition(0)
            val parsed = synchronized(dateFormatter) { dateFormatter.parse(date, position) }
            require(parsed != null && position.index == date.length) { line + "行目の日時が不正です" }
            val time = parsed.time

            when (field("kind")) {
                "weight" -> {
                    val weight = number("weight_kg")
                    require(weight != null && weight in 20.0..200.0) {
                        line + "行目の体重が範囲外です"
                    }
                    val impedanceValue = number("impedance_ohm")
                    require(impedanceValue == null || (
                        impedanceValue in 100.0..3000.0 &&
                        impedanceValue == impedanceValue.toInt().toDouble()
                    )) { line + "行目のインピーダンスが不正です" }
                    val bmi = number("bmi")
                    val fat = number("fat_pct_est")
                    val muscle = number("muscle_kg_est")
                    val water = number("water_pct_est")
                    val bone = number("bone_kg_est")
                    val bmr = number("bmr_kcal_est")
                    val protein = number("protein_pct_est")
                    val visceral = number("visceral_index_est")
                    require(bmi != null || listOf(fat, muscle, water, bone, bmr, protein, visceral)
                        .all { it == null }) { line + "行目はBMIがありません" }
                    val composition = bmi?.let {
                        Composition(it, fat, water, muscle, bone, protein, visceral, bmr)
                    }
                    weights += WeightRow(time, weight, impedanceValue?.toInt(), composition)
                }
                "waist" -> {
                    val waist = number("waist_cm_manual")
                    require(waist != null && waist in 30.0..200.0) {
                        line + "行目の腹囲が範囲外です"
                    }
                    waists += WaistRow(time, waist)
                }
                else -> throw IllegalArgumentException(line + "行目のkindはweightかwaistにしてください")
            }
        }
        return Data(weights, waists)
    }
}
