package com.example.basichilt.module.ble

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

//通用dialog
@Singleton
class DialogHelper
@Inject constructor(
    @ApplicationContext private val appCtx: Context
) {
    fun showConfirmDialog(
        activity: Activity,
        title: String,
        message: String,
        confirmText: String = "确定",
        cancelText: String = "取消",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmText) { _, _ -> onConfirm() }
            .setNegativeButton(cancelText, null)
            .show()
    }
}
