package jp.ikegami.roastscale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.ikegami.roastscale.ai.GemmaCoach
import jp.ikegami.roastscale.ai.ModelDownload
import jp.ikegami.roastscale.ble.BleDiagnostics
import jp.ikegami.roastscale.ble.ScaleScanner
import jp.ikegami.roastscale.data.Measurement
import jp.ikegami.roastscale.data.MeasurementStore
import jp.ikegami.roastscale.data.Profile
import jp.ikegami.roastscale.data.ScaleReading
import jp.ikegami.roastscale.data.UserSettings
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
    val roastLevel: Int = 3,
    val measurements: List<Measurement> = emptyList(),
    val scanning: Boolean = false,
    val bleDiagnostics: BleDiagnostics = BleDiagnostics(),
    val status: String = "測定待機",
    val aiStatus: String = "",
    val modelReady: Boolean = false,
    val modelDownloading: Boolean = false,
    val modelProgress: Int = 0,
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
    private val coach = GemmaCoach(app)
    private val scanner = ScaleScanner(
        app,
        ::onReading,
        { status ->
            _state.update { it.copy(status = status, scanning = !status.startsWith("スキャンエラー") &&
                !status.startsWith("BluetoothがOFF") && !status.startsWith("測定停止")) }
        },
        { diagnostic -> _state.update { it.copy(bleDiagnostics = diagnostic) } }
    )
    private val _state = MutableStateFlow(
        RoastUiState(profile = settings.profile(), roastLevel = settings.roast(),
            modelReady = ModelDownload.isReady(app))
    )
    val state = _state.asStateFlow()

    init {
        refresh()
        checkUpdate()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { db.readings() }
            _state.update { it.copy(measurements = list) }
        }
    }

    fun startScan() {
        // BLE reception should work even before a body-composition profile is configured.
        val started = scanner.start()
        _state.update { it.copy(scanning = started) }
    }

    fun stopScan() {
        scanner.stop()
        _state.update { it.copy(scanning = false) }
    }

    private fun onReading(reading: ScaleReading) {
        viewModelScope.launch {
            val currentProfile = _state.value.profile
            val record = withContext(Dispatchers.IO) { db.record(reading, currentProfile) } ?: return@launch
            val history = withContext(Dispatchers.IO) { db.readings() }
            _state.update { it.copy(measurements = history, status = "測定を保存しました") }
            if (!ModelDownload.isReady(getApplication())) {
                _state.update { it.copy(aiStatus = "AIモデル未設定。設定画面でE4Bを取得してください") }
                return@launch
            }
            _state.update { it.copy(aiStatus = "Gemma 4がデータを査定中…") }
            try {
                val reply = coach.comment(record, history, _state.value.roastLevel)
                withContext(Dispatchers.IO) { db.saveComment(record.id, reply) }
                val refreshed = withContext(Dispatchers.IO) { db.readings() }
                _state.update { it.copy(aiStatus = "", measurements = refreshed) }
            } catch (e: Exception) {
                _state.update { it.copy(aiStatus = "AI生成失敗: " + e.message) }
            }
        }
    }

    fun saveProfile(profile: Profile) {
        if (!profile.valid) {
            _state.update { it.copy(message = "身長120〜210cm、年齢18〜99歳を入力してください") }
            return
        }
        settings.saveProfile(profile)
        _state.update { it.copy(profile = profile, message = "プロフィールを保存しました。過去の推定値は再計算されません") }
    }

    fun setRoast(level: Int) {
        settings.saveRoast(level)
        _state.update { it.copy(roastLevel = level) }
    }

    fun downloadModel() {
        if (_state.value.modelDownloading || _state.value.modelReady) return
        viewModelScope.launch {
            _state.update { it.copy(modelDownloading = true, modelProgress = 0, message = null) }
            try {
                ModelDownload.download(getApplication()) { progress ->
                    _state.update { it.copy(modelProgress = progress) }
                }
                _state.update { it.copy(modelReady = true, message = "Gemma 4 E4Bを準備しました") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "モデル取得失敗: " + e.message) }
            } finally {
                _state.update { it.copy(modelDownloading = false) }
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
            } catch (_: Exception) {
                // Offline measurement must work without GitHub connectivity.
            } finally {
                _state.update { it.copy(updateChecking = false) }
            }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.release ?: return
        if (_state.value.updateDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(updateDownloading = true, updateProgress = 0) }
            try {
                val file = ReleaseUpdater.download(getApplication(), release) { progress ->
                    _state.update { it.copy(updateProgress = progress) }
                }
                _state.update { it.copy(apkPath = file.absolutePath, message = "APKの検証が完了しました") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "更新失敗: " + e.message) }
            } finally {
                _state.update { it.copy(updateDownloading = false) }
            }
        }
    }

    fun installUpdate() {
        val file = _state.value.apkPath?.let(::File) ?: return
        try {
            ReleaseUpdater.install(getApplication(), file)
        } catch (e: Exception) {
            _state.update { it.copy(message = "インストーラーの起動に失敗: " + e.message) }
        }
    }

    fun reportPermissionDenied() {
        _state.update { it.copy(message = "近くのデバイス（Bluetooth）権限を許可してください") }
    }
    fun clearMessage() { _state.update { it.copy(message = null) } }
    override fun onCleared() {
        scanner.stop()
        db.close()
        super.onCleared()
    }
}
