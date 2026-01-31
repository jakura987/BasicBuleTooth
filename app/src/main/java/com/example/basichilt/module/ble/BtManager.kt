package com.example.basichilt.module.ble

import android.app.Activity

interface BtManager {
    companion object {
        const val REQ_LOCATION    = 2001 // 用于 requestPermissions 的回调区分
        const val REQ_ENABLE_BT   = 2002 // 用于 startActivityForResult 的回调区分
    }

    //蓝牙权限(检查App 是否获得了运行时「定位（扫描蓝牙需要）权限)
    fun hasPermission(activity: Activity): Boolean

    //系统蓝牙硬件是否打开 (读设备上蓝牙模块的开关状态)
    fun isBtOpen(): Boolean

    //请求缺失的权限（会弹系统对话框）
    fun requestPermission(activity: Activity, requestCode: Int)

    //通过系统 Intent 请求打开蓝牙（会弹系统对话框）
    fun openBt(activity: Activity, requestCode: Int)

}
