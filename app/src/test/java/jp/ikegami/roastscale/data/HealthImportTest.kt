package jp.ikegami.roastscale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthImportTest {
    private val header = listOf(
        "kind", "date_local", "weight_kg", "weight_7d_mean_kg",
        "bmi", "fat_pct_est", "muscle_kg_est", "water_pct_est",
        "bone_kg_est", "bmr_kcal_est", "protein_pct_est",
        "visceral_index_est", "impedance_ohm", "waist_cm_manual",
        "waist_to_height_ratio"
    ).joinToString(",") { "\"" + it + "\"" }

    private fun row(vararg values: String) =
        values.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }

    @Test fun parsesWeightAndWaistFromExportFormatWithBomAndCrLf() {
        val weight = row("weight", "2026-10-28 12:34:56 +0900", "74.000",
            "74.000", "25.909", "22.500", "54.100", "56.200", "2.900",
            "1640.000", "17.000", "9.000", "520", "", "")
        val waist = row("waist", "2026-10-29 12:00:00 +0900", "", "",
            "", "", "", "", "", "", "", "", "", "94.000", "0.556")
        val imported = HealthImport.parse("\uFEFF" + header + "\r\n" +
            weight + "\r\n" + waist + "\r\n")

        assertEquals(1, imported.weights.size)
        assertEquals(1, imported.waists.size)
        assertEquals(74.0, imported.weights.single().weightKg, 0.0001)
        assertEquals(520, imported.weights.single().impedanceOhm)
        assertEquals(22.5, imported.weights.single().composition!!.fatPercent!!, 0.0001)
        assertEquals(94.0, imported.waists.single().waistCm, 0.0001)
        assertTrue(imported.waists.single().measuredAt > imported.weights.single().measuredAt)
    }

    @Test fun acceptsWeightWithoutImpedanceOrEstimatedMetrics() {
        val weight = row("weight", "2026-10-28 12:34:56 +0900", "74.000",
            "", "", "", "", "", "", "", "", "", "", "", "")
        val parsed = HealthImport.parse(header + "\n" + weight + "\n")
        assertNull(parsed.weights.single().composition)
        assertNull(parsed.weights.single().impedanceOhm)
    }

    @Test fun refusesInvalidDateAndDoesNotSilentlyImport() {
        val row = row("waist", "2026-02-30 12:34:56 +0900", "", "",
            "", "", "", "", "", "", "", "", "", "94.000", "")
        val exception = runCatching { HealthImport.parse(header + "\n" + row) }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
    }

    @Test fun refusesUnknownSchema() {
        val exception = runCatching {
            HealthImport.parse("\"foo\",\"bar\"\n\"x\",\"y\"\n")
        }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
    }
}
