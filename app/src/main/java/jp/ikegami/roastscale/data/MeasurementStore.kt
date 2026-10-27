package jp.ikegami.roastscale.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Measurement(
    val id: Long,
    val measuredAt: Long,
    val weightKg: Double,
    val impedanceOhm: Int?,
    val composition: Composition?,
    val comment: String
)

class MeasurementStore(context: Context) : SQLiteOpenHelper(context, "roast-scale.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                measured_at INTEGER NOT NULL,
                weight_kg REAL NOT NULL,
                impedance_ohm INTEGER,
                bmi REAL, fat_percent REAL, water_percent REAL,
                muscle_kg REAL, bone_kg REAL, protein_percent REAL,
                visceral_index REAL, bmr_kcal REAL,
                comment TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_measurement_date ON measurements(measured_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun readings(limit: Int = 180): List<Measurement> {
        val out = mutableListOf<Measurement>()
        readableDatabase.rawQuery(
            "SELECT * FROM measurements ORDER BY measured_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                fun nullableDouble(name: String): Double? {
                    val i = c.getColumnIndexOrThrow(name)
                    return if (c.isNull(i)) null else c.getDouble(i)
                }
                val z = c.getColumnIndexOrThrow("impedance_ohm")
                val bmi = nullableDouble("bmi")
                out += Measurement(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getLong(c.getColumnIndexOrThrow("measured_at")),
                    c.getDouble(c.getColumnIndexOrThrow("weight_kg")),
                    if (c.isNull(z)) null else c.getInt(z),
                    bmi?.let {
                        Composition(it, nullableDouble("fat_percent"), nullableDouble("water_percent"),
                            nullableDouble("muscle_kg"), nullableDouble("bone_kg"),
                            nullableDouble("protein_percent"), nullableDouble("visceral_index"),
                            nullableDouble("bmr_kcal"))
                    },
                    c.getString(c.getColumnIndexOrThrow("comment")) ?: ""
                )
            }
        }
        return out
    }

    private fun values(reading: ScaleReading, composition: Composition?): ContentValues = ContentValues().apply {
        put("weight_kg", reading.weightKg)
        if (reading.impedanceOhm != null) put("impedance_ohm", reading.impedanceOhm) else putNull("impedance_ohm")
        fun putNullable(key: String, number: Double?) {
            if (number != null) put(key, number) else putNull(key)
        }
        putNullable("bmi", composition?.bmi)
        putNullable("fat_percent", composition?.fatPercent)
        putNullable("water_percent", composition?.waterPercent)
        putNullable("muscle_kg", composition?.muscleKg)
        putNullable("bone_kg", composition?.boneKg)
        putNullable("protein_percent", composition?.proteinPercent)
        putNullable("visceral_index", composition?.visceralIndex)
        putNullable("bmr_kcal", composition?.bmrKcal)
    }

    /** Returns null for duplicate advertisements. Upgrades weight-only entry when impedance arrives. */
    @Synchronized fun record(reading: ScaleReading, profile: Profile): Measurement? {
        val now = System.currentTimeMillis()
        val previous = readings(1).firstOrNull()
        val composition = BodyMetrics.estimate(reading, profile)
        if (previous != null && now - previous.measuredAt < 90_000 &&
            kotlin.math.abs(previous.weightKg - reading.weightKg) <= 0.11) {
            if (previous.impedanceOhm == null && reading.impedanceOhm != null) {
                writableDatabase.update("measurements", values(reading, composition), "id=?",
                    arrayOf(previous.id.toString()))
                return previous.copy(impedanceOhm = reading.impedanceOhm, composition = composition)
            }
            return null
        }
        val entry = values(reading, composition).apply { put("measured_at", now) }
        val id = writableDatabase.insertOrThrow("measurements", null, entry)
        return Measurement(id, now, reading.weightKg, reading.impedanceOhm, composition, "")
    }

    @Synchronized fun saveComment(id: Long, comment: String) {
        writableDatabase.update("measurements", ContentValues().apply { put("comment", comment) },
            "id=?", arrayOf(id.toString()))
    }
}

class UserSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun profile() = Profile(prefs.getInt("height", 0), prefs.getInt("age", 0), prefs.getString("sex", "male") ?: "male")
    fun saveProfile(profile: Profile) {
        prefs.edit().putInt("height", profile.heightCm).putInt("age", profile.age).putString("sex", profile.sex).apply()
    }
    fun roast() = prefs.getInt("roast", 3)
    fun saveRoast(level: Int) { prefs.edit().putInt("roast", level.coerceIn(1, 4)).apply() }
}
