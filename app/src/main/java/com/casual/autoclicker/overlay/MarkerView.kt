package com.casual.autoclicker.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/** 中心十字表示精确点击位置，右上角标签表示顺序，编辑态用金色突出。 */
class MarkerView(context: Context) : View(context) {
    var number: Int = 1
        set(value) {
            field = value.coerceAtLeast(1)
            contentDescription = "第 $field 个点击点"
            invalidate()
        }
    var highlight: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val inkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(3f)
        val gap = dp(3f)
        if (highlight) {
            fill.color = 0x40FFD166
            canvas.drawCircle(cx, cy, radius, fill)
        }
        inkStroke.color = if (highlight) 0xFFFFBD38.toInt() else 0xFF111111.toInt()
        for (paint in arrayOf(whiteStroke, inkStroke)) {
            canvas.drawCircle(cx, cy, radius, paint)
            canvas.drawLine(cx, cy - gap, cx, cy - radius, paint)
            canvas.drawLine(cx, cy + gap, cx, cy + radius, paint)
            canvas.drawLine(cx - gap, cy, cx - radius, cy, paint)
            canvas.drawLine(cx + gap, cy, cx + radius, cy, paint)
        }
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, dp(2.6f), fill)
        fill.color = 0xFF111111.toInt()
        canvas.drawCircle(cx, cy, dp(1.4f), fill)

        // 标签位于右上方，不遮挡中心十字。根据位数缩小文字，支持两位及更多编号。
        val badgeX = width - dp(10f)
        val badgeY = dp(10f)
        val badgeRadius = dp(8.5f)
        fill.color = if (highlight) 0xFFFFBD38.toInt() else 0xFF111111.toInt()
        canvas.drawCircle(badgeX, badgeY, badgeRadius, fill)
        canvas.drawCircle(badgeX, badgeY, badgeRadius, whiteStroke)
        val text = number.toString()
        label.color = if (highlight) 0xFF111111.toInt() else Color.WHITE
        label.textSize = dp(12f)
        val availableWidth = dp(13f)
        val textWidth = label.measureText(text)
        if (textWidth > availableWidth) label.textSize *= availableWidth / textWidth
        val baseline = badgeY - (label.ascent() + label.descent()) / 2f
        canvas.drawText(text, badgeX, baseline, label)
    }
}
