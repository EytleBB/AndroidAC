package com.casual.autoclicker.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

/** 所有浮窗共享全屏坐标系，避免状态栏高度和 RTL 布局造成点击偏移。 */
internal object OverlayWindow {
    fun type(context: Context): Int {
        require(context is AccessibilityService) { "悬浮窗只能由无障碍服务创建" }
        // 专用可信窗口无需 SYSTEM_ALERT_WINDOW，Android 12+ 也不会拦截重叠点的穿透点击。
        return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }

    @Suppress("DEPRECATION")
    fun screenSize(windowManager: WindowManager): Point = Point().also {
        windowManager.defaultDisplay.getRealSize(it)
    }

    fun useScreenCoordinates(params: WindowManager.LayoutParams) {
        params.gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
