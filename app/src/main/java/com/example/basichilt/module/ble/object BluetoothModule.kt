package com.example.basichilt.module.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 从系统里拿到 BluetoothManager
 * 再从 BluetoothManager 拿到 BluetoothAdapter
 * 把它们注册给 Hilt 管理成 单例（@Singleton）
 * 以后你在 BleScanner / BleGattClient / MainActivity 里直接
 * @Inject lateinit var bluetoothAdapter: BluetoothAdapter 就能用
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context
    ): BluetoothManager {
        return context.getSystemService(BluetoothManager::class.java)
    }

    @Provides
    @Singleton
    fun provideBluetoothAdapter(
        manager: BluetoothManager
    ): BluetoothAdapter {
        // 注意：某些没有蓝牙的设备可能为 null，但你是 BLE demo 设备一般没问题
        return manager.adapter
    }
}