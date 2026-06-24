package com.casual.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.casual.autoclicker.SettingsRepository
import com.casual.autoclicker.clicker.ClickLoop
import com.casual.autoclicker.overlay.FloatingControlBall
import com.casual.autoclicker.overlay.FloatingMarker

/**
 * 核心无障碍服务（Step 1 骨架）。
 *
 * 设计要点：
 *  - 本服务是整个应用的运行时载体：后续的悬浮控制球、定位标记、点击循环
 *    都将直接从「本服务的 Context」创建悬浮窗（TYPE_APPLICATION_OVERLAY）。
 *  - 无障碍服务由系统托管、常驻运行，不会被随意杀死，因此无需额外的前台服务保活。
 *  - dispatchGesture() 依赖 accessibility_service_config.xml 中的
 *    canPerformGestures="true"，模拟点击逻辑将在 Step 4 接入。
 *
 * 后续步骤将在此服务中：
 *  Step 2 -> 创建/管理 悬浮控制球
 *  Step 3 -> 创建/管理 定位标记（NOT_TOUCHABLE 穿透切换）
 *  Step 4 -> 通过 dispatchGesture 执行点击循环
 */
class ClickerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickerA11yService"

        /**
         * 全局唯一实例引用。供悬浮窗 UI / 点击逻辑获取服务 Context 与 dispatchGesture。
         * 服务销毁时置空，使用前需判空。
         */
        @Volatile
        var instance: ClickerAccessibilityService? = null
            private set

        /** 服务当前是否处于已连接（运行）状态。 */
        val isRunning: Boolean
            get() = instance != null
    }

    /** 悬浮控制球（Step 2）。 */
    private var controlBall: FloatingControlBall? = null

    /** 定位标记（Step 3）。 */
    private var marker: FloatingMarker? = null

    /** 点击循环（Step 4）。 */
    private var clickLoop: ClickLoop? = null

    /** 当前前台应用包名（来自 TYPE_WINDOW_STATE_CHANGED 事件）。 */
    @Volatile
    private var foregroundPackage: String? = null

    /** 开始连点时锁定的目标应用包名；前台偏离它即暂停注入。 */
    @Volatile
    private var targetPackage: String? = null

    /** 系统在服务成功绑定并连接后回调。此处完成实例登记并拉起悬浮窗。 */
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
        showOverlays()
    }

    /** 创建并显示悬浮控制球 + 定位标记（需已授予悬浮窗权限）。 */
    private fun showOverlays() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "未授予悬浮窗权限，跳过悬浮窗创建")
            return
        }
        if (clickLoop == null) {
            clickLoop = ClickLoop(this)
        }
        if (marker == null) {
            marker = FloatingMarker(this).also { it.show() }
        }
        if (controlBall == null) {
            controlBall = FloatingControlBall(
                context = this,
                onToggle = { running -> if (running) startClicking() else stopClicking() },
                onLongPress = { toggleLocateMode() }
            ).also { it.show() }
        }
    }

    /** 开始连点：确保标记已锁定(可穿透)，锁定当前前台为目标，再持续点击。 */
    private fun startClicking() {
        val m = marker ?: return
        // 若仍处于定位模式，先退出以恢复穿透，避免点击落到标记自身。
        if (m.editing) m.exitEditMode()
        // 锁定目标应用：之后只要前台偏离它（弹出系统弹窗/切到别的 App）就暂停注入。
        targetPackage = foregroundPackage
        clickLoop?.start(
            point = { m.getClickPoint() },
            interval = { SettingsRepository.getIntervalMs(this).toLong() },
            canDispatch = { foregroundPackage == targetPackage }
        )
    }

    /** 停止连点。 */
    private fun stopClicking() {
        clickLoop?.stop()
    }

    /** 长按控制球：进入/退出「拖动定位模式」。退出时立刻恢复标记的穿透。 */
    private fun toggleLocateMode() {
        val m = marker ?: return
        // 进入定位模式前先停止连点，并复位控制球外观，避免边拖边点。
        if (!m.editing) {
            stopClicking()
            controlBall?.forceStopState()
        }
        val editing = m.toggleEditMode()
        val tip = if (editing) "定位模式：拖动十字到目标位置，再长按控制球完成"
        else "已锁定，点击将穿透到下层应用（中心点：${m.getClickPoint().x}, ${m.getClickPoint().y}）"
        Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "定位模式 editing=$editing, point=${m.getClickPoint()}")
    }

    /**
     * 仅用于跟踪前台应用包名（订阅了 typeWindowStateChanged）。
     * 注意：Toast 走 typeNotificationStateChanged、不会触发此回调；
     * 我们的非聚焦悬浮窗也不会改变 WINDOW_STATE，故不会污染前台判断。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { foregroundPackage = it }
        }
    }

    /** 系统中断服务（如设置变更）时回调。 */
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    /** 服务解绑时清理实例引用，并（后续）移除所有悬浮窗。 */
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "无障碍服务解绑")
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /** 统一清理：移除悬浮窗并清空实例引用。 */
    private fun teardown() {
        clickLoop?.stop()
        clickLoop = null
        controlBall?.destroy()
        controlBall = null
        marker?.destroy()
        marker = null
        instance = null
    }
}
