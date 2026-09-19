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
 * 保持原版黑白配色、描边和定位高亮；仅多个点击点时补充小号顺序编号。
 */
class MarkerView(context: Context) : View(context) {

    var number: Int = 1
        set(value) {
            field = value
            contentDescription = "第 $value 个点击点"
            invalidate()
        }
    var showNumber: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

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

        if (showNumber) {
            // 小号编号位于右上象限；不放大原有准星，不遮挡中心坐标。
            numberPaint.textSize = dp(7f)
            val text = number.toString()
            val available = dp(9f)
            if (numberPaint.measureText(text) > available) {
                numberPaint.textSize *= available / numberPaint.measureText(text)
            }
            val labelX = cx + dp(6f)
            val labelY = cy - dp(5f)
            numberPaint.style = Paint.Style.STROKE
            numberPaint.strokeWidth = dp(2f)
            numberPaint.color = Color.WHITE
            canvas.drawText(text, labelX, labelY, numberPaint)
            numberPaint.style = Paint.Style.FILL
            numberPaint.color = Color.parseColor("#111111")
            canvas.drawText(text, labelX, labelY, numberPaint)
        }
    }
}
