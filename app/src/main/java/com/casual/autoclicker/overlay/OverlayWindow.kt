package com.casual.autoclicker.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

/** 所有浮窗共享全屏坐标系，避免状态栏高度和 RTL 布局造成点击偏移。 */
internal object OverlayWindow {
    @Suppress("DEPRECATION")
    fun type(context: Context): Int = when {
        // 无障碍浮窗是可信窗口。Android 12+ 不会因多个重叠点的透明度而拦截穿透点击。
        context is AccessibilityService -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else -> WindowManager.LayoutParams.TYPE_PHONE
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
