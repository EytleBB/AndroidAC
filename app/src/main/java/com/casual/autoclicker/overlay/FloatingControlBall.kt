package com.casual.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/** 保留原来的圆形控制球；长按定位时才展开两个独立的 ＋ / － 圆形按钮。 */
class FloatingControlBall(
    private val context: Context,
    private val onToggle: (running: Boolean) -> Unit,
    private val onLongPress: () -> Unit,
    private val onAdd: () -> Unit = {},
    private val onRemove: () -> Unit = {}
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    // 与原版一致：45dp 圆形按钮、16sp 图标、原有黑白背景。
    private val buttonSize = dp(45f)
    private val buttonStep = buttonSize + dp(6f)
    private val ballView = createButton("▶", "开始按编号循环点击")
    private val addButton = createButton("+", "添加点击点")
    private val removeButton = createButton("−", "删除最后一个点击点")
    private val layoutParams = createLayoutParams().apply {
        x = dp(16f)
        y = dp(120f)
    }
    private val addParams = createLayoutParams()
    private val removeParams = createLayoutParams()
    private var added = false
    private var addShown = false
    private var removeShown = false
    var running: Boolean = false
        private set
    private var editing = false
    private var pointCount = 1
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false
    private var longPressTriggered = false
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressRunnable = Runnable {
        if (!dragging && added) {
            longPressTriggered = true
            onLongPress()
        }
    }

    init {
        ballView.setOnClickListener { toggle() }
        ballView.setOnLongClickListener {
            onLongPress()
            true
        }
        addButton.setOnClickListener { if (editing) onAdd() }
        removeButton.setOnClickListener { if (editing && pointCount > 1) onRemove() }
        setupTouch()
        updateAppearance()
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()

    private fun createButton(label: String, description: String): TextView =
        TextView(context).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(com.casual.autoclicker.R.drawable.bg_control_ball)
            minWidth = buttonSize
            minHeight = buttonSize
            width = buttonSize
            height = buttonSize
            isClickable = true
            contentDescription = description
        }

    // 控制球沿用原版的窗口布局。加减按钮使用相同坐标系，展开时不移动主球。
    private fun createLayoutParams() = WindowManager.LayoutParams(
        buttonSize,
        buttonSize,
        OverlayWindow.type(context),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        ballView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        ensureOnScreen()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!dragging && !longPressTriggered) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggle() {
        running = !running
        updateAppearance()
        onToggle(running)
    }

    private fun updateAppearance() {
        ballView.text = if (running) "■" else "▶"
        ballView.setTextColor(if (running) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
        ballView.setBackgroundResource(
            if (running) com.casual.autoclicker.R.drawable.bg_control_ball_running
            else com.casual.autoclicker.R.drawable.bg_control_ball
        )
        ballView.contentDescription = if (running) "停止点击" else "开始按编号循环点击"
        removeButton.isEnabled = pointCount > 1
        removeButton.alpha = if (pointCount > 1) 1f else 0.3f
        addButton.contentDescription = "添加第 ${pointCount + 1} 个点击点"
        removeButton.contentDescription = "删除第 $pointCount 个点击点"
    }

    fun setEditing(editing: Boolean) {
        this.editing = editing
        updateExpandedButtons()
    }

    fun setPointCount(count: Int) {
        pointCount = count.coerceAtLeast(1)
        updateAppearance()
    }

    @Suppress("DEPRECATION")
    private fun screenSize() = Point().also { windowManager.defaultDisplay.getSize(it) }

    /** 默认向下延伸；靠近屏幕边缘时换方向，不挤动原来的主球。 */
    private fun positionExpandedButtons() {
        val screen = screenSize()
        val maxX = (screen.x - buttonSize).coerceAtLeast(0)
        val maxY = (screen.y - buttonSize).coerceAtLeast(0)
        val x = layoutParams.x
        val y = layoutParams.y
        fun fits(p: Point) = p.x in 0..maxX && p.y in 0..maxY
        val directions = listOf(Point(0, 1), Point(0, -1), Point(1, 0), Point(-1, 0))
        val pairs = directions.map { d ->
            Point(x + d.x * buttonStep, y + d.y * buttonStep) to
                Point(x + d.x * buttonStep * 2, y + d.y * buttonStep * 2)
        } + directions.take(3).map { d ->
            Point(x + d.x * buttonStep, y + d.y * buttonStep) to
                Point(x - d.x * buttonStep, y - d.y * buttonStep)
        }
        val positions = pairs.firstOrNull { fits(it.first) && fits(it.second) } ?: pairs.first()
        addParams.x = positions.first.x.coerceIn(0, maxX)
        addParams.y = positions.first.y.coerceIn(0, maxY)
        removeParams.x = positions.second.x.coerceIn(0, maxX)
        removeParams.y = positions.second.y.coerceIn(0, maxY)
    }

    private fun updateExpandedButtons() {
        if (!added || !editing) {
            hideExpandedButtons()
            return
        }
        positionExpandedButtons()
        if (addShown) windowManager.updateViewLayout(addButton, addParams)
        else {
            windowManager.addView(addButton, addParams)
            addShown = true
        }
        if (removeShown) windowManager.updateViewLayout(removeButton, removeParams)
        else {
            windowManager.addView(removeButton, removeParams)
            removeShown = true
        }
    }

    private fun hideExpandedButtons() {
        if (addShown) {
            windowManager.removeView(addButton)
            addShown = false
        }
        if (removeShown) {
            windowManager.removeView(removeButton)
            removeShown = false
        }
    }

    fun ensureOnScreen() {
        val screen = screenSize()
        layoutParams.x = layoutParams.x.coerceIn(0, (screen.x - buttonSize).coerceAtLeast(0))
        layoutParams.y = layoutParams.y.coerceIn(0, (screen.y - buttonSize).coerceAtLeast(0))
        if (added) windowManager.updateViewLayout(ballView, layoutParams)
        updateExpandedButtons()
    }

    fun show() {
        if (added) return
        ensureOnScreen()
        windowManager.addView(ballView, layoutParams)
        added = true
        updateExpandedButtons()
    }

    fun destroy() {
        handler.removeCallbacks(longPressRunnable)
        hideExpandedButtons()
        if (added) {
            windowManager.removeView(ballView)
            added = false
        }
    }

    fun forceStopState() {
        running = false
        updateAppearance()
    }
}
