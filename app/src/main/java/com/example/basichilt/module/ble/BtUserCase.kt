package com.example.basichilt.module.ble

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button

import com.example.basichilt.module.ble.BtManager.Companion.REQ_ENABLE_BT
import com.example.basichilt.module.ble.BtManager.Companion.REQ_LOCATION
import timber.log.Timber

import javax.inject.Inject

class BtUseCase
@Inject constructor(
    private val btManager: BtManager,
    private val dialogHelper: DialogHelper,
) {

    /**
     * 确保蓝牙可用：
     * 1. 如果还没授权，弹运行时权限框；
     * 2. 如果蓝牙未开启，弹系统“打开蓝牙”框；
     * 3. 否则提示“蓝牙已就绪”，并在这里继续做扫描/连接等操作。
     */
    fun ensure(activity: Activity) {
        val hasPermission = btManager.hasPermission(activity)
        Timber.d("app location 权限：mychecker_hasPermission = %b", hasPermission)
        if (!hasPermission) {
            //showPermissionDialog(activity)
            btManager.requestPermission(activity, REQ_LOCATION)
            return
        }

        val isOpen = btManager.isBtOpen()
        Timber.d("蓝牙硬件开关 ：mychecker_btManager = %b", isOpen)
        if (!isOpen) {
            btManager.openBt(activity, REQ_ENABLE_BT)
        }


    }



    private fun connect() {
        // 真正的连接逻辑
    }


}
