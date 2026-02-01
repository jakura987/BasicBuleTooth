package com.example.basichilt.module.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * startScan(onResult)：开始扫描，回调每个 ScanResult
 * stopScan()：停止扫描
 * isScanning：是否正在扫描
 */
@Singleton
class BleScanner @Inject constructor(
    private val adapter: BluetoothAdapter
) {

    private var scanCallback: ScanCallback? = null

    val isScanning: Boolean
        get() = scanCallback != null

    /**
     * 开始 BLE 扫描
     *
     * 注意：Android 12+ 必须先拿到 BLUETOOTH_SCAN 运行时权限，否则会 SecurityException
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(
        onResult: (ScanResult) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (scanCallback != null) return // 已经在扫了

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onError("bluetoothLeScanner is null (is Bluetooth supported/enabled?)")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(onResult)
            }

            override fun onScanFailed(errorCode: Int) {
                val msg = "Scan failed: errorCode=$errorCode"
                Timber.e(msg)
                onError(msg)
            }
        }

        scanCallback = cb

        // null filters = 扫所有
        scanner.startScan(null, settings, cb)
        Timber.i("BLE scan started")
    }

    /**
     * 停止扫描
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        val cb = scanCallback ?: return
        val scanner = adapter.bluetoothLeScanner ?: run {
            scanCallback = null
            return
        }
        scanner.stopScan(cb)
        scanCallback = null
        Timber.i("BLE scan stopped")
    }
}