package com.casual.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * 常驻悬浮控制球（Step 2：显示 + 自身拖动 + 点击切换）。
 *
 * 关键点：
 *  - 控制球必须【可触摸】（接收拖动 / 点击），因此【不】使用 FLAG_NOT_TOUCHABLE。
 *    （FLAG_NOT_TOUCHABLE 是 Step 3 定位标记专属，用于让模拟点击穿透。）
 *  - 直接从无障碍服务的 Context 创建，使用 TYPE_APPLICATION_OVERLAY。
 *  - 通过 touchSlop 区分「拖动」与「点击」：移动超过阈值视为拖动，否则视为点击。
 *
 * @param context  无障碍服务上下文
 * @param onToggle    点击控制球时回调，参数为切换后的运行状态（true=开始, false=停止）。
 *                    实际的点击循环将在 Step 4 接入此回调。
 * @param onLongPress 长按控制球时回调，用于进入/退出「拖动定位模式」（Step 3）。
 */
class FloatingControlBall(
    private val context: Context,
    private val onToggle: (running: Boolean) -> Unit,
    private val onLongPress: () -> Unit
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val ballView: TextView = createBallView()
    private val layoutParams: WindowManager.LayoutParams = createLayoutParams()

    private var added = false

    /** 当前是否处于「运行中」状态（由点击切换）。 */
    var running: Boolean = false
        private set

    // 拖动状态记录
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false

    // 长按检测
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressTriggered = true
            onLongPress()
        }
    }
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
    ).toInt()

    private fun createBallView(): TextView {
        // 控制球尺寸：原 56dp 的 0.8 倍
        val size = dp(45f)
        return TextView(context).apply {
            text = "▶"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundResource(com.casual.autoclicker.R.drawable.bg_control_ball)
            // 固定尺寸（WindowManager 会用 measure 后的尺寸，但这里给定 minWidth/Height 保证圆形）
            minWidth = size
            minHeight = size
            width = size
            height = size
        }
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // 不抢焦点；但保持可触摸（不加 NOT_TOUCHABLE）
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(120f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable) // 拖动则取消长按
                    }
                    if (dragging) {
                        layoutParams.x = clampX(initialX + dx.toInt())
                        layoutParams.y = clampY(initialY + dy.toInt())
                        if (added) windowManager.updateViewLayout(ballView, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!dragging && !longPressTriggered) {
                        // 既非拖动也非长按 -> 视为点击：切换运行状态
                        toggle()
                    }
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

    /** 将控制球限制在屏幕范围内（粗略，按当前测量宽高）。 */
    private fun clampX(x: Int): Int {
        val maxX = windowManager.defaultDisplay.let { d ->
            val p = android.graphics.Point()
            @Suppress("DEPRECATION") d.getSize(p)
            p.x - ballView.width
        }
        return x.coerceIn(0, maxX.coerceAtLeast(0))
    }

    private fun clampY(y: Int): Int {
        val maxY = windowManager.defaultDisplay.let { d ->
            val p = android.graphics.Point()
            @Suppress("DEPRECATION") d.getSize(p)
            p.y - ballView.height
        }
        return y.coerceIn(0, maxY.coerceAtLeast(0))
    }

    /** 切换开始/停止，并更新外观，回调通知外部。 */
    private fun toggle() {
        running = !running
        updateAppearance()
        onToggle(running)
    }

    private fun updateAppearance() {
        if (running) {
            // 运行中：白底黑字
            ballView.text = "■"
            ballView.setTextColor(0xFF111111.toInt())
            ballView.setBackgroundResource(com.casual.autoclicker.R.drawable.bg_control_ball_running)
        } else {
            // 待机：黑底白字
            ballView.text = "▶"
            ballView.setTextColor(0xFFFFFFFF.toInt())
            ballView.setBackgroundResource(com.casual.autoclicker.R.drawable.bg_control_ball)
        }
    }

    /** 显示控制球。 */
    fun show() {
        if (added) return
        setupTouch()
        updateAppearance()
        windowManager.addView(ballView, layoutParams)
        added = true
    }

    /** 移除控制球。 */
    fun destroy() {
        handler.removeCallbacks(longPressRunnable)
        if (added) {
            windowManager.removeView(ballView)
            added = false
        }
    }

    /** 供外部（如点击循环异常终止时）强制复位为停止态。 */
    fun forceStopState() {
        if (running) {
            running = false
            updateAppearance()
        }
    }
}
