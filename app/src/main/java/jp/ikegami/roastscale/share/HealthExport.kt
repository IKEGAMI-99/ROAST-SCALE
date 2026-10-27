package jp.ikegami.roastscale.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import jp.ikegami.roastscale.data.HealthTrends
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.Profile
import jp.ikegami.roastscale.data.WaistEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An explicit ACTION_SEND workflow; no measurement or profile data is transmitted
 * until the user selects a share target. A clipboard copy is also provided.
 */
object HealthExport {
    private val columns = listOf("kind", "date_local", "weight_kg", "weight_7d_mean_kg",
        "bmi", "fat_pct_est", "muscle_kg_est", "water_pct_est", "bone_kg_est",
        "bmr_kcal_est", "protein_pct_est", "visceral_index_est", "impedance_ohm",
        "waist_cm_manual", "waist_to_height_ratio")

    private fun n(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.3f", it) } ?: ""

    private fun row(cells: List<String>): String =
        cells.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }

    fun csv(measurements: List<Measurement>, waists: List<WaistEntry>, profile: Profile): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.JAPAN)
        val result = mutableListOf<Pair<Long, List<String>>>()
        measurements.forEach { m ->
            result += m.measuredAt to listOf(
                "weight", fmt.format(Date(m.measuredAt)), n(m.weightKg),
                n(HealthTrends.weight7DayAverage(measurements,m.measuredAt)),
                n(m.composition?.bmi), n(m.composition?.fatPercent),
                n(m.composition?.muscleKg), n(m.composition?.waterPercent),
                n(m.composition?.boneKg), n(m.composition?.bmrKcal),
                n(m.composition?.proteinPercent), n(m.composition?.visceralIndex),
                m.impedanceOhm?.toString() ?: "", "", ""
            )
        }
        waists.forEach { w ->
            result += w.measuredAt to listOf(
                "waist", fmt.format(Date(w.measuredAt)), "", "", "", "", "", "", "",
                "", "", "", "", n(w.waistCm),
                n(HealthTrends.waistToHeight(w.waistCm,profile.heightCm))
            )
        }
        return (listOf(row(columns)) + result.sortedBy { it.first }.map { row(it.second) })
            .joinToString("\n",postfix="\n")
    }

    fun prompt(profile: Profile, weights: List<Measurement>, waists: List<WaistEntry>): String {
        val latest = waists.maxByOrNull { it.measuredAt }
        return listOf(
            "あなたは健康的なダイエットを支援するコーチです。",
            "添付のROAST SCALE測定CSVを分析し、日本語で具体的な改善案を教えてください。",
            "身長: " + (profile.heightCm.takeIf { it in 120..210 }?.toString() ?: "不明") + " cm",
            "年齢: " + (profile.age.takeIf { it in 18..99 }?.toString() ?: "不明"),
            "性別区分（推定式用）: " + if (profile.sex == "female") "女性" else "男性",
            "直近7日平均体重: " + n(HealthTrends.weight7DayAverage(weights)).ifBlank { "記録なし" } + " kg",
            "最近の腹囲: " + n(latest?.waistCm).ifBlank { "未記録" } + " cm",
            "腹囲身長比: " + n(latest?.let {
                HealthTrends.waistToHeight(it.waistCm,profile.heightCm)
            }).ifBlank { "未記録" },
            "体脂肪率などの推定値は臨床測定ではありません。",
            "1日の体重変動を脂肪増減と断定しないでください。",
            "データ不足ならその旨を伝え、推測を事実のように扱わないでください。",
            "まず経過の要約、次に改善点3つ、最後に今週の現実的な行動計画を提示してください。",
            "極端な食事制限・過度な運動は勧めないでください。",
            "CSVではkind=weightが体組成計の測定、kind=waistが手入力の腹囲です。"
        ).joinToString("\n")
    }

    fun exportFile(context: Context, csv: String): File {
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(folder,"roast-scale-history.csv")
        file.outputStream().use {
            it.write(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()))
            it.write(csv.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun share(context: Context, weights: List<Measurement>, waists: List<WaistEntry>,
              profile: Profile, advice: Boolean) {
        val csv = csv(weights,waists,profile)
        val file = exportFile(context,csv)
        val uri = FileProvider.getUriForFile(
            context,context.packageName + ".fileprovider",file)
        val instruction = prompt(profile,weights,waists)
        val fullText = instruction + "\n\n--- CSV（全測定記録）---\n" + csv
        if (advice) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("ChatGPT相談文＋CSV",fullText))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (advice) "text/plain" else "text/csv"
            putExtra(Intent.EXTRA_STREAM,uri)
            if (advice) putExtra(Intent.EXTRA_TEXT,fullText)
            clipData = ClipData.newUri(context.contentResolver,"測定履歴CSV",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent,
            if (advice) "ChatGPTを選択（相談文＋CSVはコピー済み）" else "測定履歴CSVを共有")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
