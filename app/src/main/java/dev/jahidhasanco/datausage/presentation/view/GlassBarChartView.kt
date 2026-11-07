package dev.jahidhasanco.datausage.presentation.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Hourly/daily usage as a row of glass columns.
 *
 * Each bar is a rounded gradient column with a vertical highlight down its left
 * face and a bright cap, which gives the row an extruded look rather than a flat
 * fill. Bars grow from the baseline on data change.
 */
class GlassBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF")
    }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#7AA7B0D4")
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#7AA7B0D4")
    }

    private val barRect = RectF()
    private var bars: List<ChartBar> = emptyList()
    private var minSlots = 0
    private var peak = 0L
    private var growth = 1f
    private var animator: ValueAnimator? = null

    private var cornerRadius = dp(6f)
    private var labelHeight = dp(18f)

    /** Shown when there is nothing to plot; set from a string resource. */
    var emptyText: String = ""
        set(value) {
            field = value
            if (bars.isEmpty()) invalidate()
        }

    init {
        labelPaint.textSize = dp(9f)
        emptyPaint.textSize = dp(12f)
    }

    /**
     * @param minSlots lower bound on the number of columns the width is divided
     *   into. An hourly chart passes 24 so the morning's bars stay hour-width
     *   rather than stretching across the whole card.
     */
    fun setBars(newBars: List<ChartBar>, minSlots: Int = 0, animate: Boolean = true) {
        bars = newBars
        this.minSlots = minSlots
        peak = newBars.maxOfOrNull { it.value } ?: 0L
        animator?.cancel()

        if (!animate || !isAttachedToWindow) {
            growth = 1f
            invalidate()
            return
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                growth = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(132f).toInt()
        setMeasuredDimension(
            resolveSize(dp(240f).toInt(), widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        barPaint.shader = LinearGradient(
            0f, 0f, 0f, h - labelHeight,
            intArrayOf(
                Color.parseColor("#F24DE8FF"),
                Color.parseColor("#D9A78BFA"),
                Color.parseColor("#8C6C5CE7")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        facePaint.shader = LinearGradient(
            0f, 0f, 0f, h - labelHeight,
            intArrayOf(Color.parseColor("#4DFFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val chartHeight = height - labelHeight
        if (chartHeight <= 0f) return

        if (bars.isEmpty()) {
            if (emptyText.isNotEmpty()) {
                canvas.drawText(emptyText, width / 2f, height / 2f, emptyPaint)
            }
            return
        }

        val slots = maxOf(bars.size, minSlots)
        val slot = width.toFloat() / slots
        val barWidth = (slot * 0.56f).coerceIn(dp(2f), dp(22f))
        val gap = (slot - barWidth) / 2f
        // A flat row of zero-height bars is unreadable, so give every column a
        // visible stub even when nothing was recorded in that window.
        val minHeight = dp(3f)

        canvas.drawRect(0f, chartHeight - dp(1f), width.toFloat(), chartHeight, baselinePaint)

        // Label every Nth column so they never overlap on narrow screens.
        val labelStride = maxOf(1, (slots / (width / dp(46f)).toInt().coerceAtLeast(1)))

        for ((index, bar) in bars.withIndex()) {
            val left = index * slot + gap
            val right = left + barWidth

            val ratio = if (peak > 0L) bar.value.toDouble() / peak else 0.0
            val filled = (chartHeight * ratio * growth).toFloat()
                .coerceAtLeast(if (bar.value > 0L) minHeight else minHeight * 0.5f)

            val top = chartHeight - filled
            barRect.set(left, top, right, chartHeight)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, facePaint)

            // Bright cap on the top face.
            if (filled > cornerRadius * 2) {
                barRect.set(left, top, right, top + cornerRadius)
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, capPaint)
            }

            if (index % labelStride == 0) {
                // Labels are centre-aligned, so the first and last would spill
                // past the card edge; keep them inside the drawable width.
                val half = labelPaint.measureText(bar.label) / 2f
                val x = (left + barWidth / 2f).coerceIn(half, width - half)
                canvas.drawText(bar.label, x, height - dp(4f), labelPaint)
            }
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
