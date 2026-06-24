package com.casual.autoclicker.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 点击循环（Step 4，已加入串行节流，修复系统弹窗时的卡死问题）。
 *
 * 通过 AccessibilityService.dispatchGesture() 在指定【屏幕绝对坐标】持续模拟单击。
 * - 无需 root；依赖 accessibility_service_config.xml 的 canPerformGestures="true"。
 *
 * 【关键：串行驱动，避免淹没系统】
 *   绝不固定按间隔狂发手势。每次只允许【一个】手势在飞，
 *   通过 GestureResultCallback 的 onCompleted / onCancelled 回调，
 *   等上一次手势真正结束后，再延迟 interval 发下一次。
 *   否则当系统安全弹窗（如「已授予无障碍权限」）出现、手势被持续取消时，
 *   旧的「定时狂发」会把 system_server 灌垮，导致整机输入卡死（连锁屏键都失灵）。
 *
 * @param service 无障碍服务实例，提供 dispatchGesture 能力。
 */
class ClickLoop(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "ClickLoop"
        // 单次点击的按下时长。取很小值，使点击频率主要由间隔决定。
        private const val TAP_DURATION_MS = 1L
        // 间隔下限兜底，避免极端情况下空转过密。
        private const val MIN_DELAY_MS = 10L
        // 被门控暂停时的轮询间隔：不点击，只是定期检查是否可以恢复。
        private const val PAUSE_POLL_MS = 200L
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var running = false
        private set

    private var pointProvider: (() -> Point)? = null
    private var intervalProvider: (() -> Long)? = null

    /**
     * 注入门控：返回 false 时【暂停】点击（不发任何手势，仅低频轮询等待恢复）。
     * 用于在前台变成系统弹窗 / 其它应用时停止注入，避免向安全窗口灌手势导致整机卡死。
     */
    private var canDispatch: (() -> Boolean)? = null

    /** 手势结果回调：无论完成或被取消，都在等待 interval 后发起下一次。 */
    private val gestureCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            scheduleNext()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            // 被取消通常意味着前台是系统弹窗 / 安全窗口；不重试得更快，仍按 interval 退避。
            scheduleNext()
        }
    }

    private val dispatchRunnable = Runnable { dispatchOnce() }

    /**
     * 开始循环点击。
     * @param point       每次点击前提供的目标屏幕绝对坐标
     * @param interval    每次点击后提供的等待间隔(ms)
     * @param canDispatch 注入门控：返回 false 时暂停点击（如前台为系统弹窗/其它应用）
     */
    fun start(point: () -> Point, interval: () -> Long, canDispatch: () -> Boolean) {
        if (running) return
        pointProvider = point
        intervalProvider = interval
        this.canDispatch = canDispatch
        running = true
        dispatchOnce() // 立即首次点击
        Log.d(TAG, "点击循环开始")
    }

    /** 立即停止循环。已在飞的手势其回调会因 running=false 而不再续发。 */
    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(dispatchRunnable)
        Log.d(TAG, "点击循环停止")
    }

    /** 在等待 interval 后调度下一次点击（仅当仍在运行）。 */
    private fun scheduleNext() {
        if (!running) return
        val interval = (intervalProvider?.invoke() ?: 100L).coerceAtLeast(MIN_DELAY_MS)
        handler.removeCallbacks(dispatchRunnable)
        handler.postDelayed(dispatchRunnable, interval)
    }

    /** 在被门控暂停时，低频轮询等待恢复（不发手势）。 */
    private fun schedulePausePoll() {
        if (!running) return
        handler.removeCallbacks(dispatchRunnable)
        handler.postDelayed(dispatchRunnable, PAUSE_POLL_MS)
    }

    /** 发起一次单击；成功则等回调，失败（被拒）则退避后重试。 */
    private fun dispatchOnce() {
        if (!running) return
        // 门控：前台不是目标应用（系统弹窗/切到别的 App）时暂停注入，避免整机卡死。
        if (canDispatch?.invoke() == false) {
            schedulePausePoll()
            return
        }
        val p = pointProvider?.invoke()
        if (p == null) {
            scheduleNext()
            return
        }
        // 按 Android 文档：单击仅需 moveTo 一个点 + 给定时长的 Stroke。
        val path = Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        // 传入 callback + handler：由回调驱动下一次，实现串行节流。
        val dispatched = service.dispatchGesture(gesture, gestureCallback, handler)
        if (!dispatched) {
            // 未被接受（如正有手势在处理 / 不可用），退避后再试，绝不空转狂发。
            Log.w(TAG, "dispatchGesture 返回 false，退避重试")
            scheduleNext()
        }
    }
}
