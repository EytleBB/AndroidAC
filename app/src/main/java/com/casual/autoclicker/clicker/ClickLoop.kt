package com.casual.autoclicker.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper

/** 按编号顺序循环点击；每个手势结束并等待间隔后，才会发出下一次点击。 */
class ClickLoop(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val runner = SequentialClickRunner<Point>(
        schedule = { task, delay -> handler.postDelayed(task, delay) },
        cancel = { task -> handler.removeCallbacks(task) },
        dispatch = { point, onResult -> dispatchTap(point, onResult) }
    )

    val running: Boolean
        get() = runner.running

    /**
     * 每次开始时保存点击点快照，并从 1 号开始；运行中的定位修改应先停止循环。
     * canDispatch 为 false 时只轮询，不向系统弹窗或其他应用发出手势。
     */
    fun start(points: () -> List<Point>, interval: () -> Long, canDispatch: () -> Boolean) {
        if (running) return
        runner.start(points().map { Point(it.x, it.y) }, interval, canDispatch)
    }

    /** 停止待执行的任务；已发出手势的迟到回调不会影响下一轮启动。 */
    fun stop() = runner.stop()

    private fun dispatchTap(point: Point, onResult: (Boolean) -> Unit): Boolean {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        // 每次派发拥有独立回调，使停止/重启后的旧回调无法推进新的循环。
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onResult(true)

            override fun onCancelled(gestureDescription: GestureDescription?) = onResult(false)
        }
        return service.dispatchGesture(gesture, callback, handler)
    }
}
