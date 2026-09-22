package com.casual.autoclicker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.casual.autoclicker.service.ClickerAccessibilityService

/**
 * 权限管理：只检查和引导无障碍服务。
 * 控制球使用 TYPE_ACCESSIBILITY_OVERLAY，不再申请普通悬浮窗权限。
 */
object PermissionManager {

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
}
