package com.example.basichilt.module

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.basichilt.BuildConfig
import com.example.basichilt.R
import com.example.basichilt.module.ble.BleGattClient
import com.example.basichilt.module.ble.BleScanner
import com.example.basichilt.module.devices.DeviceAdapter
import com.example.basichilt.module.devices.DeviceItem

import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var bluetoothAdapter: BluetoothAdapter
    @Inject lateinit var bleScanner: BleScanner
    @Inject lateinit var gattClient: BleGattClient

    private lateinit var adapter: DeviceAdapter
    private val deviceMap = LinkedHashMap<String, DeviceItem>()

    private var filterKey: String = ""
    private var pendingAction: (() -> Unit)? = null

    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button

    private val requestPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val ok = res[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    res[Manifest.permission.BLUETOOTH_CONNECT] == true

            if (ok) {
                pendingAction?.invoke()
                pendingAction = null
            } else {
                pendingAction = null
                Timber.w("Permission denied: $res")
                // 权限拒绝时，把按钮文本恢复一下（避免 UI 假状态）
                btnScan.text = "开始扫描"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        tvStatus = findViewById(R.id.tv_ble_status)
        btnScan = findViewById(R.id.btn_scan)

        // RecyclerView
        val rv = findViewById<RecyclerView>(R.id.rv_devices)
        adapter = DeviceAdapter { item ->
            runBleAction {
                connectInternal(item.address)
            }
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // 监听连接状态
        gattClient.setListener(object : BleGattClient.Listener {
            override fun onState(state: BleGattClient.State) {
                tvStatus.text = when (state) {
                    is BleGattClient.State.Disconnected -> "状态：未连接"
                    is BleGattClient.State.Connecting -> "状态：连接中 ${state.address}"
                    is BleGattClient.State.Connected -> "状态：已连接 ${state.address}（发现服务中）"
                    is BleGattClient.State.ServicesDiscovered ->
                        "状态：服务发现完成 ${state.address}（NUS=${state.hasNus}）"
                    is BleGattClient.State.Failed -> "状态：失败 ${state.msg}"
                }
            }
        })

        // Buttons
        val btnClear = findViewById<Button>(R.id.btn_clear)

        btnScan.setOnClickListener {
            if (bleScanner.isScanning) {
                runBleAction {
                    stopScanInternal()
                    btnScan.text = "开始扫描"
                }
            } else {
                runBleAction {
                    startScanInternal()
                    btnScan.text = "停止扫描"
                }
            }
        }

        btnClear.setOnClickListener {
            deviceMap.clear()
            refreshList()

            runBleAction {
                writeTest01()
            }
        }



        // 搜索框过滤
        val etFilter = findViewById<TextInputEditText>(R.id.et_device_filter)
        etFilter.addTextChangedListener { s ->
            filterKey = s?.toString().orEmpty().trim()
            refreshList()
        }
    }


    @SuppressLint("MissingPermission")
    private fun writeTest01() {
        val ok = gattClient.writeNus(byteArrayOf(0x01))
        Timber.i("write 0x01 => $ok")
        tvStatus.text = "write 0x01 => $ok\n" + tvStatus.text
    }


    @SuppressLint("MissingPermission")
    private fun connectInternal(address: String) {
        if (bleScanner.isScanning) stopScanInternal()
        gattClient.connect(address)
    }

    private fun runBleAction(action: () -> Unit) {
        pendingAction = action

        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        val granted = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

        if (granted) {
            action()
            pendingAction = null
        } else {
            requestPerms.launch(perms)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanInternal() {
        if (bleScanner.isScanning) return

        bleScanner.startScan(
            onResult = { result ->
                val addr = result.device.address

                // 先取广播里的名字（不一定有）
                val nameFromAdv = result.scanRecord?.deviceName
                // 再兜底：device.name（需要 CONNECT 权限）
                val name = nameFromAdv ?: runCatching { result.device.name }.getOrNull()

                val item = DeviceItem(
                    address = addr,
                    name = name,
                    rssi = result.rssi
                )
                deviceMap[addr] = item
                refreshList()
            },
            onError = { msg ->
                Timber.e("scan error: $msg")
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        bleScanner.stopScan()
    }

    private fun refreshList() {
        val list = deviceMap.values.toList()
        val key = filterKey

        val filtered = if (key.isEmpty()) {
            list
        } else {
            list.filter {
                (it.name ?: "").contains(key, ignoreCase = true) ||
                        it.address.contains(key, ignoreCase = true)
            }
        }

        adapter.submitList(filtered)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { if (bleScanner.isScanning) stopScanInternal() }
        runCatching { gattClient.disconnect() }
        gattClient.setListener(null)
    }
}

