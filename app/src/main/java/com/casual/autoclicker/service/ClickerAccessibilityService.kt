package com.casual.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.casual.autoclicker.R
import com.casual.autoclicker.SettingsRepository
import com.casual.autoclicker.clicker.ClickLoop
import com.casual.autoclicker.overlay.FloatingControlBall
import com.casual.autoclicker.overlay.FloatingMarker

/** 管理编号点击点、悬浮控制栏及串行点击循环。所有 UI 操作均在主线程执行。 */
class ClickerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickerA11yService"

        @Volatile
        var instance: ClickerAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private var controlBall: FloatingControlBall? = null
    private val markers = mutableListOf<FloatingMarker>()
    private var clickLoop: ClickLoop? = null
    private var editing = false
    private var foregroundPackage: String? = null
    private var targetPackage: String? = null
    private var startGeneration = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ensureOverlays()
    }

    /** 也由主页面调用，支持先开无障碍、后授予悬浮窗权限的顺序。 */
    fun ensureOverlays() {
        if (!Settings.canDrawOverlays(this) || controlBall != null) return
        try {
            clickLoop = ClickLoop(this)
            val saved = SettingsRepository.getClickPoints(this)
            if (saved.isEmpty()) {
                createMarker(null)
            } else {
                saved.forEach { createMarker(it) }
            }
            controlBall = FloatingControlBall(
                context = this,
                onToggle = { running -> if (running) startClicking() else stopClicking() },
                onLongPress = { toggleLocateMode() },
                onAdd = { addPoint() },
                onRemove = { removeLastPoint() }
            ).also {
                it.setPointCount(markers.size)
                it.show()
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "创建悬浮窗失败", error)
            removeOverlays()
            toast(R.string.overlay_failed)
        }
    }

    private fun createMarker(point: Point?) {
        val marker = FloatingMarker(
            context = this,
            number = markers.size + 1,
            initialPoint = point,
            onPositionChanged = { savePoints() }
        )
        marker.show()
        markers.add(marker)
        if (editing) marker.enterEditMode()
    }

    private fun addPoint() {
        if (markers.size >= SettingsRepository.MAX_POINTS) {
            Toast.makeText(this, getString(R.string.point_limit, SettingsRepository.MAX_POINTS), Toast.LENGTH_SHORT).show()
            return
        }
        stopClicking()
        setEditing(true)
        try {
            createMarker(nextPointPosition())
            controlBall?.setPointCount(markers.size)
            savePoints()
            Toast.makeText(this, getString(R.string.point_added, markers.size), Toast.LENGTH_SHORT).show()
        } catch (error: RuntimeException) {
            Log.e(TAG, "添加点击点失败", error)
            toast(R.string.overlay_failed)
        }
    }

    /** 从屏幕中心向外寻找空闲网格，避免新按钮一开始就盖住已有按钮。 */
    private fun nextPointPosition(): Point {
        val screen = Point()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(screen)
        val spacing = (56 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val margin = spacing / 2
        val existing = markers.map { it.getClickPoint() }
        val candidates = mutableListOf<Point>()
        for (y in margin..maxOf(margin, screen.y - margin) step spacing) {
            for (x in margin..maxOf(margin, screen.x - margin) step spacing) {
                candidates.add(Point(x, y))
            }
        }
        return candidates.sortedBy {
            val dx = (it.x - screen.x / 2).toDouble()
            val dy = (it.y - screen.y / 2).toDouble()
            dx * dx + dy * dy
        }.firstOrNull { candidate ->
            existing.none { point ->
                val dx = (point.x - candidate.x).toDouble()
                val dy = (point.y - candidate.y).toDouble()
                dx * dx + dy * dy < spacing.toDouble() * spacing
            }
        } ?: Point(screen.x / 2, screen.y / 2)
    }

    private fun removeLastPoint() {
        if (markers.size <= 1) return
        stopClicking()
        setEditing(true)
        markers.removeAt(markers.lastIndex).destroy()
        controlBall?.setPointCount(markers.size)
        savePoints()
    }

    /** 每次启动都从 1 号开始，启动前锁定全部标记使点击可穿透。 */
    private fun startClicking() {
        if (markers.isEmpty() || foregroundPackage == null) {
            controlBall?.forceStopState()
            toast(R.string.open_target_first)
            return
        }
        setEditing(false)
        targetPackage = foregroundPackage
        val generation = ++startGeneration
        var remaining = markers.size
        // updateViewLayout 在下一帧才提交穿透标志；等全部窗口完成布局后再发第一下。
        markers.forEach { marker ->
            marker.whenReadyForClick {
                if (generation == startGeneration) {
                    remaining--
                    if (remaining == 0) {
                        clickLoop?.start(
                            points = { markers.map { it.getClickPoint() } },
                            interval = { SettingsRepository.getIntervalMs(this).toLong() },
                            canDispatch = { targetPackage != null && foregroundPackage == targetPackage }
                        )
                    }
                }
            }
        }
    }

    private fun stopClicking() {
        startGeneration++
        clickLoop?.stop()
        controlBall?.forceStopState()
        targetPackage = null
    }

    private fun toggleLocateMode() {
        stopClicking()
        setEditing(!editing)
        toast(if (editing) R.string.locate_started else R.string.locate_finished)
    }

    private fun setEditing(value: Boolean) {
        editing = value
        markers.forEach { if (value) it.enterEditMode() else it.exitEditMode() }
        controlBall?.setEditing(value)
        if (!value) savePoints()
    }

    private fun savePoints() {
        if (markers.isNotEmpty()) SettingsRepository.setClickPoints(this, markers.map { it.getClickPoint() })
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** 切换应用或进入系统弹窗时暂停注入，返回原应用后继续当前编号。 */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { foregroundPackage = it }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (markers.isEmpty()) return
        // 屏幕方向改变后让用户重新确认位置，不继续向旧坐标发送手势。
        stopClicking()
        markers.forEach { it.ensureOnScreen() }
        controlBall?.ensureOnScreen()
        setEditing(true)
        savePoints()
        toast(R.string.screen_changed)
    }

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun removeOverlays() {
        stopClicking()
        clickLoop = null
        controlBall?.destroy()
        controlBall = null
        markers.forEach { it.destroy() }
        markers.clear()
        editing = false
    }

    private fun teardown() {
        removeOverlays()
        if (instance === this) instance = null
    }
}
