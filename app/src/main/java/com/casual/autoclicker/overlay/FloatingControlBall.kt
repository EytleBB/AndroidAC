package com.casual.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** 可拖动工具栏：开始/停止、添加点、删除最后一点、编辑/完成。 */
class FloatingControlBall(
    private val context: Context,
    private val onToggle: (running: Boolean) -> Unit,
    private val onLongPress: () -> Unit,
    private val onAdd: () -> Unit = {},
    private val onRemove: () -> Unit = {}
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val buttonSize = dp(45f)
    private val gap = dp(3f)
    private val toolbarWidth = buttonSize + gap * 2
    private val toolbarHeight = buttonSize * 4 + gap * 5
    private val playButton = createButton("▶", "开始按编号循环点击")
    private val addButton = createButton("+", "添加点击点")
    private val removeButton = createButton("−", "删除最后一个点击点")
    private val editButton = createButton("编辑", "编辑点击点位置").apply { textSize = 12f }
    private val toolbar = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(gap, gap, gap, gap)
        background = GradientDrawable().apply {
            setColor(0xDD252525.toInt())
            cornerRadius = toolbarWidth / 2f
            setStroke(dp(1f), 0xFFEEEEEE.toInt())
        }
        listOf(playButton, addButton, removeButton, editButton).forEachIndexed { index, button ->
            addView(button, LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                if (index > 0) topMargin = gap
            })
        }
    }
    private val layoutParams = WindowManager.LayoutParams(
        toolbarWidth,
        toolbarHeight,
        OverlayWindow.type(context),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        OverlayWindow.useScreenCoordinates(this)
        x = dp(16f)
        y = dp(120f)
    }

    private var added = false
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
        playButton.setOnClickListener { toggle() }
        playButton.setOnLongClickListener {
            onLongPress()
            true
        }
        addButton.setOnClickListener { onAdd() }
        removeButton.setOnClickListener { if (pointCount > 1) onRemove() }
        editButton.setOnClickListener { onLongPress() }
        setupTouch()
        updateAppearance()
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()

    private fun createButton(label: String, description: String): TextView =
        TextView(context).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundResource(com.casual.autoclicker.R.drawable.bg_control_ball)
            isClickable = true
            isFocusable = true
            contentDescription = description
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tooltipText = description
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        playButton.setOnTouchListener { view, event ->
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
        playButton.text = if (running) "■" else "▶"
        playButton.setTextColor(if (running) 0xFF111111.toInt() else Color.WHITE)
        playButton.setBackgroundResource(
            if (running) com.casual.autoclicker.R.drawable.bg_control_ball_running
            else com.casual.autoclicker.R.drawable.bg_control_ball
        )
        playButton.contentDescription = if (running) "停止点击" else "开始按编号循环点击"
        editButton.text = if (editing) "完成" else "编辑"
        editButton.contentDescription = if (editing) "完成定位并锁定点击点" else "编辑点击点位置"
        editButton.setTextColor(if (editing) 0xFFFFD166.toInt() else Color.WHITE)
        removeButton.isEnabled = pointCount > 1
        removeButton.alpha = if (pointCount > 1) 1f else 0.3f
        addButton.contentDescription = "添加第 ${pointCount + 1} 个点击点"
        removeButton.contentDescription = "删除第 $pointCount 个点击点"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(playButton, addButton, removeButton, editButton).forEach {
                it.tooltipText = it.contentDescription
            }
        }
    }

    fun setEditing(editing: Boolean) {
        this.editing = editing
        updateAppearance()
    }

    fun setPointCount(count: Int) {
        pointCount = count.coerceAtLeast(1)
        updateAppearance()
    }

    fun ensureOnScreen() {
        val screen = OverlayWindow.screenSize(windowManager)
        layoutParams.x = layoutParams.x.coerceIn(0, (screen.x - toolbarWidth).coerceAtLeast(0))
        layoutParams.y = layoutParams.y.coerceIn(0, (screen.y - toolbarHeight).coerceAtLeast(0))
        if (added) windowManager.updateViewLayout(toolbar, layoutParams)
    }

    fun show() {
        if (added) return
        ensureOnScreen()
        windowManager.addView(toolbar, layoutParams)
        added = true
    }

    fun destroy() {
        handler.removeCallbacks(longPressRunnable)
        if (added) {
            windowManager.removeView(toolbar)
            added = false
        }
    }

    fun forceStopState() {
        running = false
        updateAppearance()
    }
}
