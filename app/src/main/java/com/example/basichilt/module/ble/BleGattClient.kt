package com.example.basichilt.module.ble


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleGattClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adapter: BluetoothAdapter
) {

    sealed class State {
        data object Disconnected : State()
        data class Connecting(val address: String) : State()
        data class Connected(val address: String) : State()
        data class ServicesDiscovered(val address: String, val hasNus: Boolean) : State()
        data class Failed(val msg: String) : State()
    }

    interface Listener {
        fun onState(state: State)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: Listener? = null
    private var gatt: BluetoothGatt? = null
    private var currentAddress: String? = null

    // 预留：后面写 NUS 用
    private var nusWriteChar: BluetoothGattCharacteristic? = null

    fun setListener(l: Listener?) {
        listener = l
    }

    private fun postState(state: State) {
        mainHandler.post { listener?.onState(state) }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String) {
        disconnect()

        currentAddress = address
        postState(State.Connecting(address))

        val device = runCatching { adapter.getRemoteDevice(address) }
            .getOrNull()

        if (device == null) {
            postState(State.Failed("Invalid address: $address"))
            return
        }

        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (se: SecurityException) {
            Timber.e(se)
            postState(State.Failed("No BLUETOOTH_CONNECT permission"))
        } catch (t: Throwable) {
            Timber.e(t)
            postState(State.Failed("connectGatt error: ${t.message}"))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        val g = gatt ?: return
        Timber.i("BLE disconnect()")

        runCatching { g.disconnect() }
        runCatching { g.close() }   // ✅ 关键：close 才会释放资源

        gatt = null
        nusWriteChar = null
        currentAddress = null

        postState(State.Disconnected)
    }


    // （可选）后面你要写 NUS 时用
    //@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    fun writeNus(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = nusWriteChar ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                val status = g.writeCharacteristic(
                    ch,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
                status == BluetoothGatt.GATT_SUCCESS
            } else {
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ch.value = bytes
                g.writeCharacteristic(ch) // 旧 API 返回 Boolean
            }
        } catch (_: Throwable) {
            false
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val addr = currentAddress ?: gatt.device?.address ?: "unknown"

            // ✅ 对端主动断开（19）也会走到这里：把它当成“断开原因”
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val msg = when (status) {
                    19 -> "对端主动断开(19)"
                    8 -> "连接超时(8)"
                    133 -> "GATT_ERROR(133)"
                    else -> "GATT status=$status"
                }
                Timber.w("BLE disconnected: $msg, newState=$newState")
                postState(State.Failed(msg)) // 或者你想更“温柔”，也可以改成 Disconnected
                try { gatt.close() } catch (_: Throwable) {}
                this@BleGattClient.gatt = null
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    postState(State.Connected(addr))
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    postState(State.Disconnected)
                    try { gatt.close() } catch (_: Throwable) {}
                    this@BleGattClient.gatt = null
                }
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val addr = currentAddress ?: gatt.device?.address ?: "unknown"
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postState(State.Failed("discover failed: $status"))
                return
            }

            // 尝试找 NUS（找不到也不算失败）
            val nusService: BluetoothGattService? = gatt.getService(NusUuids.SERVICE)
            val writeChar: BluetoothGattCharacteristic? =
                nusService?.getCharacteristic(NusUuids.CHAR_WRITE)

            nusWriteChar = writeChar

            val hasNus = (nusService != null && writeChar != null)
            postState(State.ServicesDiscovered(addr, hasNus))
        }
    }
}

object NusUuids {
    val SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val CHAR_WRITE: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
}
