package jp.ikegami.roastscale.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import jp.ikegami.roastscale.data.Measurement
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors

/** A single thread owns the engine and all conversations. */
class GemmaCoach(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: Engine? = null
    private var cpuOnly = false

    private fun openEngine(useGpu: Boolean): Engine {
        val config = EngineConfig(
            modelPath = ModelDownload.file(context).absolutePath,
            backend = if (useGpu) Backend.GPU() else Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
        val candidate = Engine(config)
        candidate.initialize()
        return candidate
    }

    private fun createEngine(): Engine {
        engine?.let { return it }
        return try {
            openEngine(!cpuOnly).also { engine = it }
        } catch (first: Exception) {
            cpuOnly = true
            openEngine(false).also { engine = it }
        }
    }

    private fun generate(prompt: String, instruction: String): String {
        val conversation = createEngine().createConversation(
            ConversationConfig(systemInstruction = Contents.of(instruction))
        )
        return try {
            conversation.sendMessage(prompt).toString().trim()
        } finally {
            conversation.close()
        }
    }

    suspend fun comment(current: Measurement, history: List<Measurement>, level: Int): String =
        withContext(worker) {
            require(ModelDownload.isReady(context)) { "E4Bモデルをダウンロードしてください" }
            val previous = history.firstOrNull { it.id != current.id }
            val trailing = history.take(7).map { it.weightKg }
            val mean = trailing.average().takeIf { !it.isNaN() }
            fun f(v: Double?): String =
                if (v == null) "未取得" else String.format(Locale.US, "%.1f", v)
            val profileData = listOf(
                "現在の体重: " + f(current.weightKg) + "kg",
                "BMI: " + f(current.composition?.bmi),
                "推定体脂肪率: " + f(current.composition?.fatPercent) + "%",
                "推定筋肉量: " + f(current.composition?.muscleKg) + "kg",
                "直前の体重: " + f(previous?.weightKg) + "kg",
                "直近最大7件の平均: " + f(mean) + "kg",
                "履歴数: " + history.size,
                "※体組成は家庭用BIAの推定値であり、医学的診断ではない。"
            ).joinToString("\n")
            val tone = when (level) {
                1 -> "優しいが率直"
                2 -> "ちょっと辛口で小気味よい"
                3 -> "皮肉たっぷりの毒舌"
                else -> "鋭いブラックユーモアで容赦なくツッコむ"
            }
            val instruction = listOf(
                "あなたは日本語の毒舌フィットネス・コーチ。語調は" + tone + "。",
                "120字以内のコメントと、実行可能なアドバイスを1つ返してください。",
                "体型や人格を侮辱せず、行動・数字の読み間違い・言い訳を皮肉る。",
                "体重の日々の変動を脂肪の増減と断定しない。",
                "ない数値を捏造しない。体脂肪率は推定値として扱う。",
                "絶食・極端な制限・過度な運動などの危険な助言を禁止。",
                "計測データのみに言及し、診断をしない。"
            ).joinToString("\n")
            try {
                generate(profileData, instruction)
            } catch (first: Exception) {
                if (cpuOnly) throw first
                engine?.close()
                engine = null
                cpuOnly = true
                generate(profileData, instruction)
            }
        }
}
