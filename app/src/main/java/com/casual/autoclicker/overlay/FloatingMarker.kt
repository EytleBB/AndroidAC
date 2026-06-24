package com.casual.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

/**
 * 可拖动定位标记（Step 3）。十字准星的中心即为连点坐标。
 *
 * 【触摸穿透 —— 本步重点】
 *  - 默认（锁定态）：窗口带 FLAG_NOT_TOUCHABLE，使真实触摸与后续的模拟点击
 *    都能【穿透标记】落到下层目标 APP；否则点击会被标记窗口自身吞掉。
 *  - 进入「拖动定位模式」时：临时去掉 FLAG_NOT_TOUCHABLE，标记变为可触摸、可拖动。
 *  - 定位完成（退出该模式）：【立刻】恢复 FLAG_NOT_TOUCHABLE。
 *
 * 【坐标换算】
 *  - 使用 gravity = TOP|START，并加 FLAG_LAYOUT_IN_SCREEN，
 *    使 LayoutParams 的 x/y 即为屏幕左上角起算的【绝对像素】。
 *  - 点击坐标取十字中心：clickX = x + width/2, clickY = y + height/2。
 *    该绝对坐标可直接喂给 Step 4 的 dispatchGesture()。
 */
class FloatingMarker(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val markerView: MarkerView = MarkerView(context)

    // 注意：sizePx 必须在 layoutParams 之前初始化——
    // createLayoutParams() 会用到它，Kotlin 按声明顺序初始化属性，
    // 若放在后面，建窗时 sizePx 仍为 0，会得到 0×0 的不可见窗口。
    // 十字标记尺寸：原 56dp 的 0.5 倍
    private val sizePx = dp(28f)

    private val layoutParams: WindowManager.LayoutParams = createLayoutParams()

    private var added = false

    /** 是否处于「拖动定位模式」。 */
    var editing: Boolean = false
        private set

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** 锁定态的基础 flags：不抢焦点 + 屏幕绝对坐标 + 触摸穿透。 */
    private fun lockedFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    /** 编辑态：在锁定态基础上去掉 NOT_TOUCHABLE，使标记可拖。 */
    private fun editingFlags(): Int =
        lockedFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType(),
            lockedFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 默认放在屏幕中心附近
            val metrics = context.resources.displayMetrics
            x = (metrics.widthPixels - sizePx) / 2
            y = (metrics.heightPixels - sizePx) / 2
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        markerView.setOnTouchListener { _, event ->
            // 锁定态下窗口为 NOT_TOUCHABLE，此监听不会被触发；仅编辑态生效。
            when (event.action) {
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
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        layoutParams.x = clamp(initialX + dx.toInt(), maxX())
                        layoutParams.y = clamp(initialY + dy.toInt(), maxY())
                        if (added) windowManager.updateViewLayout(markerView, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
    }

    private fun clamp(value: Int, max: Int) = value.coerceIn(0, max.coerceAtLeast(0))
    private fun maxX(): Int = screenSize().x - sizePx
    private fun maxY(): Int = screenSize().y - sizePx

    private fun screenSize(): Point {
        val p = Point()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getSize(p)
        return p
    }

    /** 显示标记（锁定态、可穿透）。 */
    fun show() {
        if (added) return
        setupTouch()
        applyAppearance()
        windowManager.addView(markerView, layoutParams)
        added = true
    }

    /** 进入拖动定位模式：去掉 NOT_TOUCHABLE，使标记可拖。 */
    fun enterEditMode() {
        if (!added || editing) return
        editing = true
        layoutParams.flags = editingFlags()
        applyAppearance()
        windowManager.updateViewLayout(markerView, layoutParams)
    }

    /** 退出定位模式：立刻恢复 NOT_TOUCHABLE，使点击可穿透。 */
    fun exitEditMode() {
        if (!added || !editing) return
        editing = false
        layoutParams.flags = lockedFlags()
        applyAppearance()
        windowManager.updateViewLayout(markerView, layoutParams)
    }

    /** 在编辑态/锁定态间切换，返回切换后的编辑态。 */
    fun toggleEditMode(): Boolean {
        if (editing) exitEditMode() else enterEditMode()
        return editing
    }

    private fun applyAppearance() {
        markerView.highlight = editing
        markerView.alpha = if (editing) 1f else 0.9f
    }

    /**
     * 返回十字中心的屏幕绝对像素坐标，供 dispatchGesture 使用。
     * 因 gravity=TOP|START + FLAG_LAYOUT_IN_SCREEN，x/y 即绝对坐标。
     */
    fun getClickPoint(): Point =
        Point(layoutParams.x + sizePx / 2, layoutParams.y + sizePx / 2)

    /** 移除标记。 */
    fun destroy() {
        if (added) {
            windowManager.removeView(markerView)
            added = false
        }
    }
}
