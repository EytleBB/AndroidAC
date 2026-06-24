package com.casual.autoclicker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.casual.autoclicker.service.ClickerAccessibilityService

/**
 * 权限管理：集中处理「悬浮窗权限」与「无障碍服务」的状态检查与跳转引导。
 *
 * 本应用仅依赖两类权限：
 *  1. 悬浮窗权限 (SYSTEM_ALERT_WINDOW / canDrawOverlays)
 *  2. 无障碍服务已开启 (用户在系统设置里手动启用)
 */
object PermissionManager {

    /** 悬浮窗权限是否已授予。minSdk 24 下 canDrawOverlays 始终可用。 */
    fun canDrawOverlay(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** 跳转到「显示在其它应用上层」系统设置页。 */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 无障碍服务是否已启用。
     * 通过读取系统已启用服务列表 (ENABLED_ACCESSIBILITY_SERVICES) 判断，
     * 比依赖服务静态实例更可靠（服务实例可能尚未创建）。
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context.packageName,
            ClickerAccessibilityService::class.java.name
        )
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) {
                return true
            }
        }
        return false
    }

    /** 跳转到系统「无障碍」设置页，由用户手动开启本服务。 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 两类权限是否都已就绪。 */
    fun allReady(context: Context): Boolean {
        return canDrawOverlay(context) && isAccessibilityServiceEnabled(context)
    }
}
