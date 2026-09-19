package com.casual.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.view.doOnPreDraw
import kotlin.math.abs

/** 编号点击点。编辑时可拖动；锁定时真实触摸与模拟点击都穿透窗口。 */
class FloatingMarker(
    private val context: Context,
    number: Int = 1,
    initialPoint: Point? = null,
    private val onPositionChanged: () -> Unit = {}
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 28f, context.resources.displayMetrics
    ).toInt()
    private val markerView = MarkerView(context).apply { this.number = number }
    private val layoutParams = createLayoutParams(initialPoint)
    private var added = false
    var editing: Boolean = false
        private set
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun lockedFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun createLayoutParams(initialPoint: Point?): WindowManager.LayoutParams {
        val screen = OverlayWindow.screenSize(windowManager)
        val point = initialPoint ?: Point(screen.x / 2, screen.y / 2)
        return WindowManager.LayoutParams(
            sizePx, sizePx, OverlayWindow.type(context), lockedFlags(), PixelFormat.TRANSLUCENT
        ).apply {
            OverlayWindow.useScreenCoordinates(this)
            x = (point.x - sizePx / 2).coerceIn(0, (screen.x - sizePx).coerceAtLeast(0))
            y = (point.y - sizePx / 2).coerceIn(0, (screen.y - sizePx).coerceAtLeast(0))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        markerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        clampPosition()
                        if (added) windowManager.updateViewLayout(markerView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) onPositionChanged()
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPosition() {
        val screen = OverlayWindow.screenSize(windowManager)
        layoutParams.x = layoutParams.x.coerceIn(0, (screen.x - sizePx).coerceAtLeast(0))
        layoutParams.y = layoutParams.y.coerceIn(0, (screen.y - sizePx).coerceAtLeast(0))
    }

    fun ensureOnScreen() {
        val oldX = layoutParams.x
        val oldY = layoutParams.y
        clampPosition()
        if (added) windowManager.updateViewLayout(markerView, layoutParams)
        if (oldX != layoutParams.x || oldY != layoutParams.y) onPositionChanged()
    }

    fun show() {
        if (added) return
        setupTouch()
        clampPosition()
        applyAppearance()
        windowManager.addView(markerView, layoutParams)
        added = true
    }

    fun enterEditMode() {
        if (editing) return
        editing = true
        layoutParams.flags = lockedFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        applyAppearance()
        if (added) windowManager.updateViewLayout(markerView, layoutParams)
    }

    fun exitEditMode() {
        if (!editing) return
        editing = false
        layoutParams.flags = lockedFlags()
        applyAppearance()
        if (added) windowManager.updateViewLayout(markerView, layoutParams)
    }

    fun toggleEditMode(): Boolean {
        if (editing) exitEditMode() else enterEditMode()
        return editing
    }

    fun setNumber(number: Int, showNumber: Boolean) {
        markerView.number = number
        markerView.showNumber = showNumber
    }

    private fun applyAppearance() {
        markerView.highlight = editing
        markerView.alpha = if (editing) 1f else 0.9f
    }

    /** 中心十字坐标以全屏左上角为原点，与 dispatchGesture 使用相同坐标系。 */
    fun getClickPoint(): Point = Point(layoutParams.x + sizePx / 2, layoutParams.y + sizePx / 2)

    /** 等待锁定窗口的重布局完成，避免首次手势被尚未更新的可触摸窗口截获。 */
    fun whenReadyForClick(onReady: () -> Unit) {
        if (!added) {
            onReady()
            return
        }
        markerView.doOnPreDraw { markerView.post { onReady() } }
        markerView.invalidate()
    }

    fun destroy() {
        if (added) {
            windowManager.removeView(markerView)
            added = false
        }
    }
}
