package jp.ikegami.roastscale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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

/**
 * Passive BLE listener for Xiaomi XMTZC02HM/MIBFS.
 * The app intentionally never pairs or opens a GATT connection.
 */
class ScaleScanner(
    private val context: Context,
    private val onReading: (ScaleReading) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val uuid = ParcelUuid(UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb"))
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var pending: ScaleReading? = null
    private var pendingTask: Runnable? = null
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val frame = result.scanRecord?.getServiceData(uuid) ?: return
            val reading = ScalePacket.decode(frame) ?: return
            // A valid impedance packet is the final and most complete reading.
            if (reading.impedanceOhm != null) {
                pendingTask?.let(handler::removeCallbacks)
                pendingTask = null
                pending = null
                onReading(reading)
            } else {
                pending = reading
                pendingTask?.let(handler::removeCallbacks)
                val task = Runnable {
                    pending?.let(onReading)
                    pending = null
                    pendingTask = null
                }
                pendingTask = task
                handler.postDelayed(task, 7_000)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            running = false
            onStatus("Bluetoothスキャン失敗（コード: " + errorCode + "）")
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter?.isEnabled != true) {
            onStatus("BluetoothをONにしてください")
            return false
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            onStatus("BLEスキャナーを利用できません")
            return false
        }
        return try {
            if (!running) {
                scanner.startScan(
                    null, // Some hardware doesn't include UUID in the scan filter fields.
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    callback
                )
                running = true
                onStatus("測定待機中。体組成計に裸足で乗ってください")
            }
            true
        } catch (e: SecurityException) {
            onStatus("Bluetoothの権限がありません")
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
        pendingTask?.let(handler::removeCallbacks)
        pendingTask = null
        pending = null
        onStatus("測定停止")
    }
}
