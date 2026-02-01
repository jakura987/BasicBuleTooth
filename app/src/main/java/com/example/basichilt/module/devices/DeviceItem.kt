package com.example.basichilt.module.devices

data class DeviceItem (
    val address: String,
    val name: String? = null,
    val rssi: Int = 0,
)