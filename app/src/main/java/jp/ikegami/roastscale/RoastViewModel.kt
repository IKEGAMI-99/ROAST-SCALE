package jp.ikegami.roastscale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.ikegami.roastscale.ble.BleDiagnostics
import jp.ikegami.roastscale.ble.ScaleScanner
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.MeasurementStore
import jp.ikegami.roastscale.data.Profile
import jp.ikegami.roastscale.data.ScaleReading
import jp.ikegami.roastscale.data.UserSettings
import jp.ikegami.roastscale.data.WaistEntry
import jp.ikegami.roastscale.share.HealthExport
import jp.ikegami.roastscale.update.AvailableRelease
import jp.ikegami.roastscale.update.ReleaseUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RoastUiState(
    val profile: Profile = Profile(),
    val measurements: List<Measurement> = emptyList(),
    val waists: List<WaistEntry> = emptyList(),
    val scanning: Boolean = false,
    val bleDiagnostics: BleDiagnostics = BleDiagnostics(),
    val status: String = "測定待機",
    val updateChecking: Boolean = false,
    val release: AvailableRelease? = null,
    val updateDownloading: Boolean = false,
    val updateProgress: Int = 0,
    val apkPath: String? = null,
    val message: String? = null
)

class RoastViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MeasurementStore(app)
    private val settings = UserSettings(app)
    private val _state = MutableStateFlow(RoastUiState(profile = settings.profile()))
    val state = _state.asStateFlow()

    private val scanner = ScaleScanner(
        app,
        ::onReading,
        { status ->
            _state.update { it.copy(status = status, scanning =
                !status.startsWith("スキャンエラー") &&
                !status.startsWith("BluetoothがOFF") &&
                !status.startsWith("測定停止") &&
                !status.startsWith("BLE受信ゼロ")) }
        },
        { diagnostic -> _state.update { it.copy(bleDiagnostics = diagnostic) } }
    )

    init {
        scanner.refreshEnvironment()
        refresh()
        checkUpdate()
    }

    fun refresh() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { db.readings() to db.waists() }
            _state.update { it.copy(measurements = data.first, waists = data.second) }
        }
    }

    fun startScan() {
        val started = scanner.start()
        _state.update { it.copy(scanning = started) }
    }
    fun stopScan() {
        scanner.stop()
        _state.update { it.copy(scanning = false) }
    }

    private fun onReading(reading: ScaleReading) {
        viewModelScope.launch {
            val record = withContext(Dispatchers.IO) { db.record(reading, _state.value.profile) }
            if (record != null) {
                val history = withContext(Dispatchers.IO) { db.readings() }
                _state.update { it.copy(measurements = history, status = "測定を保存しました") }
            }
        }
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { db.deleteMeasurement(id) }
            val fresh = withContext(Dispatchers.IO) { db.readings() }
            _state.update { it.copy(measurements = fresh,
                message = if (deleted) "測定記録を削除しました" else "記録が見つかりませんでした") }
        }
    }

    fun addWaist(value: Double?) {
        if (value == null || value !in 30.0..200.0) {
            _state.update { it.copy(message = "腹囲は30〜200cmの数値を入力してください") }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { db.addWaist(value) }
            val updated = withContext(Dispatchers.IO) { db.waists() }
            _state.update { it.copy(waists = updated,
                message = if (ok) "腹囲を記録しました" else "腹囲の保存に失敗しました") }
        }
    }

    fun deleteWaist(id: Long) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { db.deleteWaist(id) }
            val updated = withContext(Dispatchers.IO) { db.waists() }
            _state.update { it.copy(waists = updated,
                message = if (deleted) "腹囲記録を削除しました" else "記録が見つかりませんでした") }
        }
    }

    fun saveProfile(profile: Profile) {
        if (!profile.valid) {
            _state.update { it.copy(message = "身長120〜210cm、年齢18〜99歳を入力してください") }
            return
        }
        settings.saveProfile(profile)
        _state.update { it.copy(profile = profile, message = "プロフィールを保存しました") }
    }

    fun shareChatGPT() {
        viewModelScope.launch {
            try {
                // Read latest complete database state, not a truncated screen snapshot.
                val (measurements, waists) = withContext(Dispatchers.IO) { db.readings() to db.waists() }
                HealthExport.share(getApplication(), measurements, waists, _state.value.profile, true)
                _state.update { it.copy(message = "相談文と全履歴CSVをコピーしました。ChatGPTで貼り付けできます") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "共有に失敗: " + (e.message ?: "不明なエラー")) }
            }
        }
    }

    fun shareCsv() {
        viewModelScope.launch {
            try {
                val (measurements, waists) = withContext(Dispatchers.IO) { db.readings() to db.waists() }
                HealthExport.share(getApplication(), measurements, waists, _state.value.profile, false)
            } catch (e: Exception) {
                _state.update { it.copy(message = "CSV共有に失敗: " + (e.message ?: "不明なエラー")) }
            }
        }
    }

    fun checkUpdate() {
        if (_state.value.updateChecking) return
        viewModelScope.launch {
            _state.update { it.copy(updateChecking = true) }
            try {
                val available = ReleaseUpdater.check()
                _state.update { it.copy(release = available) }
            } catch (_: Exception) { }
            finally { _state.update { it.copy(updateChecking = false) } }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.release ?: return
        if (_state.value.updateDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(updateDownloading = true, updateProgress = 0) }
            try {
                val file = ReleaseUpdater.download(getApplication(), release) {
                    progress -> _state.update { it.copy(updateProgress = progress) }
                }
                _state.update { it.copy(apkPath = file.absolutePath,
                    message = "更新APKを検証しました") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "更新失敗: " + e.message) }
            } finally {
                _state.update { it.copy(updateDownloading = false) }
            }
        }
    }

    fun installUpdate() {
        val file = _state.value.apkPath?.let(::File) ?: return
        try { ReleaseUpdater.install(getApplication(), file) }
        catch (e: Exception) { _state.update { it.copy(message = "インストーラー失敗: " + e.message) } }
    }

    fun reportLocationDisabled() { _state.update { it.copy(message = "端末の位置情報をONにしてください") } }
    fun reportPermissionDenied() { _state.update { it.copy(message = "Bluetoothと正確な位置情報を許可してください") } }
    fun clearMessage() { _state.update { it.copy(message = null) } }
    override fun onCleared() {
        scanner.stop()
        db.close()
        super.onCleared()
    }
}
