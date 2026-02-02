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
    private lateinit var tvSendLog: TextView

    //循环发送
    private lateinit var btnSendLoop: Button
    private val loopHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var loopRunning = false
    private var loopRunnable: Runnable? = null
    // 循环发送间隔（ms），先固定 500ms
    private val loopPeriodMs = 500L


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
        tvSendLog = findViewById(R.id.tv_send_log)



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

                // ✅ 断开/失败时自动停止循环
                if (state is BleGattClient.State.Disconnected || state is BleGattClient.State.Failed) {
                    stopLoopSend("连接断开/失败")
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
            runBleAction {
                stopLoopSend("手动断开")     // ✅ 加这一行
                gattClient.disconnect()
                tvStatus.text = "状态：已断开"
            }
        }


        // 搜索框过滤
        val etFilter = findViewById<TextInputEditText>(R.id.et_device_filter)
        etFilter.addTextChangedListener { s ->
            filterKey = s?.toString().orEmpty().trim()
            refreshList()
        }

        //发送
        val etHex = findViewById<TextInputEditText>(R.id.et_hex)
        val btnSend = findViewById<Button>(R.id.btn_send)

        btnSend.setOnClickListener {
            runBleAction {
                // ✅ 手动发送前：先停掉自动循环
                if (loopRunning) stopLoopSend("手动发送前停止循环")

                val hex = etHex.text?.toString().orEmpty()
                val bytes = hexToBytesOrNull(hex)
                if (bytes == null) {
                    appendSendLog("HEX格式错误：$hex")
                    return@runBleAction
                }
                val ok = gattClient.writeNus(bytes)
                appendSendLog("send(${bytes.size}B) $hex => $ok")
                Timber.i("send $hex => $ok")
            }
        }

        //循环发送
        btnSendLoop = findViewById(R.id.btn_send_loop)
        btnSendLoop.setOnClickListener {
            if (loopRunning) {
                stopLoopSend("用户停止")
            } else {
                runBleAction {
                    val hex = etHex.text?.toString().orEmpty()
                    val bytes = hexToBytesOrNull(hex)
                    if (bytes == null) {
                        appendSendLog("HEX格式错误：$hex")
                        return@runBleAction
                    }
                    startLoopSend(bytes, hex)
                }
            }
        }

        //清空日志
        findViewById<Button>(R.id.btn_clear_log).setOnClickListener {
            tvSendLog.text = "发送日志：\n"
        }

    }


    @SuppressLint("MissingPermission")
    private fun writeTest01() {
        val ok = gattClient.writeNus(byteArrayOf(0x01))
        Timber.i("write 0x01 => $ok")
        tvStatus.text = "write 0x01 => $ok\n" + tvStatus.text
    }

    @SuppressLint("MissingPermission")
    private fun startLoopSend(bytes: ByteArray, hexText: String) {
        if (loopRunning) return
        loopRunning = true
        btnSendLoop.text = "停止循环"
        appendSendLog("开始循环发送：$hexText  interval=${loopPeriodMs}ms")

        loopRunnable = object : Runnable {
            override fun run() {
                // 保险：循环中如果权限被收回，就停掉（一般不会发生）
                val granted = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    stopLoopSend("CONNECT权限缺失")
                    return
                }

                val ok = gattClient.writeNus(bytes)
                appendSendLog("loop send(${bytes.size}B) => $ok")

                // 如果写失败（比如已断开/特征为空），就停止循环，避免一直刷屏
                if (!ok) {
                    stopLoopSend("写失败/可能已断开")
                    return
                }

                loopHandler.postDelayed(this, loopPeriodMs)
            }
        }

        loopHandler.postDelayed(loopRunnable!!, loopPeriodMs)
    }

    private fun stopLoopSend(reason: String) {
        loopRunnable?.let { loopHandler.removeCallbacks(it) }
        loopRunnable = null
        if (loopRunning) {
            loopRunning = false
            btnSendLoop.text = "循环发送"
            appendSendLog("停止循环：$reason")
        }
    }




    private fun appendSendLog(s: String) {
        tvSendLog.append(s + "\n")
        // 尽量滚到末尾
        tvSendLog.post {
            val layout = tvSendLog.layout ?: return@post
            val scroll = layout.getLineTop(tvSendLog.lineCount) - tvSendLog.height
            tvSendLog.scrollTo(0, scroll.coerceAtLeast(0))
        }
    }



    private fun hexToBytesOrNull(input: String): ByteArray? {
        // 支持： "01", "010203", "01 02 03", "0x01 0x02"
        val cleaned = input
            .trim()
            .replace("0x", "", ignoreCase = true)
            .replace("\\s+".toRegex(), "")
            .uppercase()

        if (cleaned.isEmpty()) return byteArrayOf() // 允许发空（你不想允许就 return null）
        if (cleaned.length % 2 != 0) return null

        return try {
            ByteArray(cleaned.length / 2) { i ->
                val hi = Character.digit(cleaned[i * 2], 16)
                val lo = Character.digit(cleaned[i * 2 + 1], 16)
                if (hi == -1 || lo == -1) throw IllegalArgumentException("bad hex")
                ((hi shl 4) or lo).toByte()
            }
        } catch (_: Throwable) {
            null
        }
    }



    @SuppressLint("MissingPermission")
    private fun connectInternal(address: String) {
        if (bleScanner.isScanning) {
            stopScanInternal()
            btnScan.text = "开始扫描"   // ✅ 加这一行
        }
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
        stopLoopSend("activity destory")
        runCatching { if (bleScanner.isScanning) stopScanInternal() }
        runCatching { gattClient.disconnect() }
        gattClient.setListener(null)
    }
}

