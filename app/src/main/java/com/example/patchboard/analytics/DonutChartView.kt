package com.example.patchboard.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.patchboard.R
import kotlin.math.max
import kotlin.math.min

/** Ring chart with a big number and a caption in the middle. */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Slice(val value: Int, val color: Int)

    private var slices: List<Slice> = emptyList()
    private var centerValue = ""
    private var centerLabel = ""

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pb_on_surface)
        textSize = sp(24f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pb_on_surface_variant)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val trackColor = ContextCompat.getColor(context, R.color.pb_divider)
    private val oval = RectF()

    fun setData(slices: List<Slice>, centerValue: String, centerLabel: String) {
        this.slices = slices
        this.centerValue = centerValue
        this.centerLabel = centerLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val stroke = size * 0.17f
        val radius = (size - stroke) / 2f
        val cx = width / 2f
        val cy = height / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        ringPaint.strokeWidth = stroke

        val total = slices.sumOf { it.value }
        if (total <= 0) {
            ringPaint.color = trackColor
            canvas.drawArc(oval, 0f, 360f, false, ringPaint)
        } else {
            val visible = slices.count { it.value > 0 }
            val gap = if (visible > 1) 2f else 0f // small visual gap between segments
            var start = -90f
            for (slice in slices) {
                if (slice.value <= 0) continue
                val sweep = 360f * slice.value / total
                ringPaint.color = slice.color
                canvas.drawArc(oval, start + gap / 2f, max(sweep - gap, 0.5f), false, ringPaint)
                start += sweep
            }
        }

        // Centre text, vertically centred as a two-line block.
        val valueHeight = -valuePaint.ascent()
        val labelHeight = -labelPaint.ascent()
        val blockHeight = valueHeight + labelHeight + sp(2f)
        val topY = cy - blockHeight / 2f
        canvas.drawText(centerValue, cx, topY + valueHeight, valuePaint)
        canvas.drawText(centerLabel, cx, topY + valueHeight + sp(2f) + labelHeight, labelPaint)
    }
}
