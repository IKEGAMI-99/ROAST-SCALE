package jp.ikegami.roastscale.data

/**
 * Every persisted measurement exposed in the dashboard and as its own line chart.
 * The same descriptor is used by both UIs to avoid mismatched metrics.
 */
data class MetricDefinition(
    val key: String,
    val label: String,
    val unit: String,
    val value: (Measurement) -> Double?,
    val reference: (Profile) -> ReferenceBand
)

object MetricCatalog {
    val all: List<MetricDefinition> = listOf(
        MetricDefinition("weight", "体重", "kg", { it.weightKg }, { ReferenceGuide.weight(it) }),
        MetricDefinition("bmi", "BMI", "", { it.composition?.bmi }, { ReferenceGuide.bmi() }),
        MetricDefinition("fat", "体脂肪率（推定）", "%", { it.composition?.fatPercent }, { ReferenceGuide.fat(it) }),
        MetricDefinition("muscle", "筋肉量（推定）", "kg", { it.composition?.muscleKg }, { ReferenceGuide.muscle() }),
        MetricDefinition("water", "体水分率（推定）", "%", { it.composition?.waterPercent }, { ReferenceGuide.water(it) }),
        MetricDefinition("bone", "骨量（推定）", "kg", { it.composition?.boneKg }, { ReferenceGuide.bone() }),
        MetricDefinition("bmr", "基礎代謝（推定）", "kcal", { it.composition?.bmrKcal }, { ReferenceGuide.bmr() }),
        MetricDefinition("protein", "タンパク質率（推定）", "%", { it.composition?.proteinPercent }, { ReferenceGuide.protein() }),
        MetricDefinition("visceral", "内臓脂肪指数（推定）", "", { it.composition?.visceralIndex }, { ReferenceGuide.visceral() }),
        MetricDefinition("impedance", "インピーダンス（実測）", "Ω", { it.impedanceOhm?.toDouble() }, { ReferenceGuide.impedance() })
    )
}
