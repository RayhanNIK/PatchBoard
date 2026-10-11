package com.example.patchboard.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.patchboard.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * Grouped bar chart: for each time slice, one bar for reports filed and one for repairs completed.
 * Drawn by hand so the project needs no chart library.
 */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var filed = IntArray(0)
    private var repaired = IntArray(0)
    private var labels: List<String> = emptyList()

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val filedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_filed)
    }
    private val repairedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_repaired)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pb_divider)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pb_on_surface_variant)
        textSize = sp(10f)
    }
    private val path = Path()
    private val rect = RectF()

    fun setData(buckets: List<TrendBucket>, labels: List<String>) {
        filed = IntArray(buckets.size) { buckets[it].filed }
        repaired = IntArray(buckets.size) { buckets[it].repaired }
        this.labels = labels
        invalidate()
    }

    /** Top of the y axis: an even number, so the middle gridline is a whole number too. */
    private fun niceMax(v: Int): Int = when {
        v <= 4 -> 4
        v <= 10 -> if (v % 2 == 0) v else v + 1
        else -> ((v + 9) / 10) * 10
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = filed.size
        val left = dp(30f)
        val right = dp(4f)
        val top = dp(8f)
        val bottom = dp(24f)
        val chartW = width - left - right
        val chartH = height - top - bottom
        if (n == 0 || chartW <= 0f || chartH <= 0f) return

        val maxValue = niceMax(max(filed.maxOrNull() ?: 0, repaired.maxOrNull() ?: 0))
        val baseY = top + chartH
        fun yOf(value: Float) = baseY - value / maxValue * chartH

        // Gridlines + y labels at 0, half, max.
        textPaint.textAlign = Paint.Align.RIGHT
        for (tick in intArrayOf(0, maxValue / 2, maxValue)) {
            val y = yOf(tick.toFloat())
            canvas.drawLine(left, y, left + chartW, y, gridPaint)
            canvas.drawText(tick.toString(), left - dp(6f), y + textPaint.textSize * 0.35f, textPaint)
        }

        // Bars.
        val groupW = chartW / n
        val barW = (groupW * 0.32f).coerceAtMost(dp(14f))
        val gap = dp(3f)
        val radius = dp(3f)
        for (i in 0 until n) {
            val cx = left + groupW * (i + 0.5f)
            drawBar(canvas, cx - gap / 2 - barW, cx - gap / 2, baseY, yOf(filed[i].toFloat()), radius, filed[i], filedPaint)
            drawBar(canvas, cx + gap / 2, cx + gap / 2 + barW, baseY, yOf(repaired[i].toFloat()), radius, repaired[i], repairedPaint)
        }

        // X labels, thinned out so they never overlap.
        textPaint.textAlign = Paint.Align.CENTER
        val step = ceil(n / 6.0).toInt().coerceAtLeast(1)
        for (i in 0 until n step step) {
            val label = labels.getOrNull(i) ?: continue
            canvas.drawText(label, left + groupW * (i + 0.5f), height - dp(6f), textPaint)
        }
    }

    private fun drawBar(
        canvas: Canvas, x1: Float, x2: Float, baseY: Float, topY: Float,
        radius: Float, value: Int, paint: Paint,
    ) {
        if (value <= 0) return
        val y = minOf(topY, baseY - dp(2f)) // keep tiny values visible
        rect.set(x1, y, x2, baseY)
        path.reset()
        // Rounded top corners only (top-left, top-right), square at the baseline.
        path.addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        canvas.drawPath(path, paint)
    }
}
