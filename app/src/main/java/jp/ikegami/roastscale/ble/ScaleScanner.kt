package jp.ikegami.roastscale.ble

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import jp.ikegami.roastscale.data.ScalePacket
import jp.ikegami.roastscale.data.ScaleReading
import java.util.UUID

data class BleDiagnostics(
    val packets: Int = 0,
    val scalePackets: Int = 0,
    val validPackets: Int = 0,
    val lastDevice: String = "",
    val lastFrame: String = "",
    val lastResult: String = "スキャンを開始してください",
    val nearbyDevices: List<String> = emptyList(),
    val scanPermission: Boolean = false,
    val connectPermission: Boolean = false,
    val fineLocationPermission: Boolean = false,
    val locationEnabled: Boolean = false,
    val secondsSinceStart: Int = 0
)

/**
 * Original Mi Body Composition Scale XMTZC02HM (usually advertised as MIBCS).
 * Passive, unfiltered foreground BLE scan with diagnostics.
 */
class ScaleScanner(
    private val context: Context,
    private val onReading: (ScaleReading) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onDiagnostic: (BleDiagnostics) -> Unit
) {
    private val uuid = ParcelUuid(UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb"))
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var pending: ScaleReading? = null
    private var pendingTask: Runnable? = null
    private var packetCount = 0
    private var scaleCount = 0
    private var validCount = 0
    private var lastEmitAt = 0L
    private var startedAt = 0L
    private var lastDevice = ""
    private var lastFrame = ""
    private var lastResult = ""
    private val nearby = LinkedHashMap<String, String>()

    /** Refreshes the zero-packet diagnosis even when Android never calls onScanResult. */
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val elapsed = ((android.os.SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
            if (packetCount == 0 && elapsed >= 12) {
                lastResult = "12秒以上、BLE受信ゼロ。位置情報・権限・Bluetoothを確認"
                onStatus("BLE受信ゼロ。位置情報ON・正確な位置情報の許可を確認してください")
            } else if (packetCount > 0 && scaleCount == 0 && elapsed >= 12) {
                lastResult = "周辺機器は受信中。Xiaomi体組成計の広告は未検出"
            }
            emitDiagnostic(force = true)
            handler.postDelayed(this, 3_000L)
        }
    }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun locationOn(): Boolean {
        val location = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled
        else location.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun refreshEnvironment() { emitDiagnostic(force = true) }


    private fun hex(data: ByteArray): String = data.joinToString(" ") {
        "%02X".format(it.toInt() and 0xff)
    }

    /** Fall back to raw AD type 0x16 (16-bit UUID 0x181B) if Android omits serviceData. */
    private fun extractRawServiceData(raw: ByteArray?): ByteArray? {
        if (raw == null) return null
        var i = 0
        while (i < raw.size) {
            val length = raw[i].toInt() and 0xff
            if (length == 0) break
            val next = i + 1 + length
            if (next > raw.size) break
            if (length >= 3 && (raw[i + 1].toInt() and 0xff) == 0x16 &&
                (raw[i + 2].toInt() and 0xff) == 0x1b &&
                (raw[i + 3].toInt() and 0xff) == 0x18) {
                return raw.copyOfRange(i + 4, next)
            }
            i = next
        }
        return null
    }

    private fun emitDiagnostic(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastEmitAt < 650L) return
        lastEmitAt = now
        onDiagnostic(
            BleDiagnostics(
                packets = packetCount,
                scalePackets = scaleCount,
                validPackets = validCount,
                lastDevice = lastDevice,
                lastFrame = lastFrame,
                lastResult = lastResult,
                nearbyDevices = nearby.values.toList().takeLast(6),
                scanPermission = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_SCAN),
                connectPermission = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT),
                fineLocationPermission = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                locationEnabled = locationOn(),
                secondsSinceStart = if (running) ((now - startedAt) / 1000L).toInt().coerceAtLeast(0) else 0
            )
        )
    }

    private fun handle(result: ScanResult) {
        if (!running) return
        packetCount++
        val record = result.scanRecord
        val name = record?.deviceName?.takeIf { it.isNotBlank() } ?: "名前なし"
        val deviceId = try { result.device.address } catch (_: SecurityException) { name }
        nearby[deviceId] = name + " · RSSI " + result.rssi + " dBm"
        if (nearby.size > 10) nearby.remove(nearby.keys.first())
        val frame = record?.getServiceData(uuid) ?: extractRawServiceData(record?.bytes)
        if (frame == null) {
            emitDiagnostic()
            return
        }
        scaleCount++
        lastDevice = name + " · RSSI " + result.rssi + " dBm"
        lastFrame = hex(frame)
        val parsed = ScalePacket.decode(frame)
        lastResult = if (parsed == null) ScalePacket.explain(frame)
            else "受信OK: " + parsed.weightKg + " kg / " +
                (parsed.impedanceOhm?.toString() ?: "抵抗未取得") + " Ω"
        if (parsed != null) {
            validCount++
            if (parsed.impedanceOhm != null) {
                pendingTask?.let(handler::removeCallbacks)
                pendingTask = null
                pending = null
                onReading(parsed)
            } else {
                pending = parsed
                pendingTask?.let(handler::removeCallbacks)
                val task = Runnable {
                    pending?.let(onReading)
                    pending = null
                    pendingTask = null
                }
                pendingTask = task
                handler.postDelayed(task, 5_000L)
            }
        }
        emitDiagnostic(force = true)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { handle(result) }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result -> handler.post { handle(result) } }
        }
        override fun onScanFailed(errorCode: Int) {
            handler.post {
                running = false
                handler.removeCallbacks(ticker)
                onStatus("スキャンエラー " + errorCode + "（Bluetoothを切り替えて再試行）")
                lastResult = "Android BLEスキャンエラー " + errorCode
                emitDiagnostic(force = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!locationOn()) {
            onStatus("端末の位置情報がOFFです。ONにしてください")
            refreshEnvironment()
            return false
        }
        if ((Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_SCAN)) ||
            !granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT))) {
            onStatus("BLEまたは正確な位置情報の権限が不足しています")
            refreshEnvironment()
            return false
        }
        return try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter?.isEnabled != true) {
                onStatus("BluetoothがOFFです。端末のBluetoothをONにしてください")
                refreshEnvironment()
                return false
            }
            val scanner = adapter.bluetoothLeScanner ?: run {
                onStatus("BLEスキャナーが利用できません")
                refreshEnvironment()
                return false
            }
            packetCount = 0
            scaleCount = 0
            validCount = 0
            nearby.clear()
            lastDevice = ""
            lastFrame = ""
            lastResult = "BLE広告を待機中"
            // A null filter maximizes diagnostic visibility of all nearby BLE ads.
            // Note: Android pauses unfiltered scans while the screen is off.
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0L).build(),
                callback
            )
            running = true
            startedAt = android.os.SystemClock.elapsedRealtime()
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 3_000L)
            onStatus("スキャン中。体組成計に乗り、表示が安定するまで待ってください")
            emitDiagnostic(force = true)
            true
        } catch (_: SecurityException) {
            onStatus("Bluetooth権限が拒否されています")
            false
        } catch (e: Exception) {
            onStatus("スキャン開始失敗: " + (e.message ?: "unknown"))
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (running) {
            try {
                context.getSystemService(BluetoothManager::class.java)?.adapter
                    ?.bluetoothLeScanner?.stopScan(callback)
            } catch (_: Exception) { }
        }
        running = false
        handler.removeCallbacks(ticker)
        pendingTask?.let(handler::removeCallbacks)
        pendingTask = null
        pending = null
        onStatus("測定停止")
        emitDiagnostic(force = true)
    }
}
