package com.aibox.kotlin.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 13（API 33）+ `POST_NOTIFICATIONS` 运行时权限辅助。
 *
 * 通知权限用于展示「生成中 / 已完成」保活通知。API < 33 无需运行时申请。
 *
 * 接线说明（Activity，本仓库已在 `MainActivity` 落地）：
 * ```
 * private val launcher = registerForActivityResult(
 *     ActivityResultContracts.RequestPermission(),
 * ) { granted -> /* granted=false 时仍可正常生成，仅不显示通知 */ }
 *
 * if (NotificationPermission.shouldRequest(this)) {
 *     launcher.launch(NotificationPermission.PERMISSION)
 * }
 * ```
 */
object NotificationPermission {

    /**
     * 权限名常量。`Manifest.permission.POST_NOTIFICATIONS` 是编译期内联的字符串常量，
     * 在 API < 33 上引用安全（仅字符串，不触发类加载）。
     */
    const val PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

    /** 是否需要在运行时申请通知权限（仅 API 33+ 且当前未授权时为 true）。 */
    fun shouldRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(context, PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
    }
}
