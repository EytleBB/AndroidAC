package com.casual.autoclicker.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * 自绘定位标记视图（替代矢量 drawable，确保在任何设备上都能稳定渲染）。
 *
 * 绘制内容：外环 + 十字准星（中间留缺口）+ 中心圆点，
 * 红色主体下垫白色描边以保证在任意背景上都清晰可见。
 * 进入定位模式时叠加黄色高亮（半透明填充 + 黄环）。
 */
class MarkerView(context: Context) : View(context) {

    /** 是否处于定位（可拖）模式，影响高亮外观。 */
    var highlight: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private fun dp(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    // 黑白配色：黑色主体下垫白色描边，保证在深/浅任意背景上都清晰。
    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val inkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val centerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        style = Paint.Style.FILL
    }
    private val highlightFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000")
        style = Paint.Style.FILL
    }
    private val highlightRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(2f)
        val gap = dp(3f)        // 十字中心缺口
        val arm = radius        // 臂长到外环
        val dotR = dp(2f)

        // 定位模式高亮背景
        if (highlight) {
            canvas.drawCircle(cx, cy, radius, highlightFill)
            canvas.drawCircle(cx, cy, radius, highlightRing)
        }

        // 外环（先白后黑）
        canvas.drawCircle(cx, cy, radius, whiteStroke)
        canvas.drawCircle(cx, cy, radius, inkStroke)

        // 十字四臂（先白底后黑，留中心缺口）
        for (paint in arrayOf(whiteStroke, inkStroke)) {
            canvas.drawLine(cx, cy - gap, cx, cy - arm, paint) // 上
            canvas.drawLine(cx, cy + gap, cx, cy + arm, paint) // 下
            canvas.drawLine(cx - gap, cy, cx - arm, cy, paint) // 左
            canvas.drawLine(cx + gap, cy, cx + arm, cy, paint) // 右
        }

        // 中心点（白描边 + 黑填充）
        canvas.drawCircle(cx, cy, dotR + dp(1f), whiteStroke)
        canvas.drawCircle(cx, cy, dotR, centerFill)
    }
}
