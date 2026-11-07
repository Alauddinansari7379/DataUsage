package dev.jahidhasanco.datausage.presentation.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular speed gauge rendered as a glass dome.
 *
 * The arc is a 270 degree sweep starting bottom-left. Depth comes from three
 * stacked passes: a recessed track, the lit progress arc, and a dome highlight
 * that sits over the top half of the dial.
 */
class LiquidGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val capGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val domePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#B3C7CEEA")
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#7AA7B0D4")
    }

    private val arcBounds = RectF()
    private val capMatrix = android.graphics.Matrix()
    private var capGlowShader: RadialGradient? = null
    private var capRadius = 0f

    private var thickness = dp(14f)
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private var valueText = "0"
    private var unitText = ""
    private var captionText = ""

    /**
     * Sets the dial position.
     *
     * @param fraction 0..1 position on the arc, clamped.
     * @param animate whether to ease into the new position.
     */
    fun setProgress(fraction: Float, animate: Boolean = true) {
        val target = fraction.coerceIn(0f, 1f)
        animator?.cancel()

        if (!animate || !isAttachedToWindow) {
            progress = target
            invalidate()
            return
        }

        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 520
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setReadout(value: String, unit: String, caption: String = captionText) {
        if (valueText == value && unitText == unit && captionText == caption) return
        valueText = value
        unitText = unit
        captionText = caption
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(190f).toInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val size = min(w, h).toFloat()
        thickness = size * 0.085f
        val pad = thickness / 2f + dp(8f)
        val left = (w - size) / 2f + pad
        val top = (h - size) / 2f + pad
        arcBounds.set(left, top, left + size - pad * 2, top + size - pad * 2)

        trackPaint.strokeWidth = thickness
        trackPaint.color = Color.parseColor("#33FFFFFF")

        progressPaint.strokeWidth = thickness
        // Rotated so the gradient's first stop lands at the arc's start angle.
        progressPaint.shader = SweepGradient(
            arcBounds.centerX(), arcBounds.centerY(),
            intArrayOf(
                Color.parseColor("#4DE8FF"),
                Color.parseColor("#A78BFA"),
                Color.parseColor("#FF7A8A"),
                Color.parseColor("#5EF2C0"),
                Color.parseColor("#4DE8FF")
            ),
            floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)
        ).apply {
            val rotation = android.graphics.Matrix()
            rotation.setRotate(START_ANGLE, arcBounds.centerX(), arcBounds.centerY())
            setLocalMatrix(rotation)
        }

        tickPaint.strokeWidth = dp(1.5f)
        tickPaint.color = Color.parseColor("#33FFFFFF")

        // Dome: a highlight anchored above the dial, so the face looks convex.
        domePaint.shader = RadialGradient(
            arcBounds.centerX(), arcBounds.top + arcBounds.height() * 0.1f,
            arcBounds.width() * 0.72f,
            intArrayOf(Color.parseColor("#3DFFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        innerPaint.shader = LinearGradient(
            arcBounds.left, arcBounds.top, arcBounds.right, arcBounds.bottom,
            intArrayOf(Color.parseColor("#1FFFFFFF"), Color.parseColor("#0AFFFFFF")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // Built once at the origin, then translated into place each frame.
        capRadius = thickness * 1.5f
        capGlowShader = RadialGradient(
            0f, 0f, capRadius,
            intArrayOf(Color.parseColor("#CCFFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        ).also { capGlowPaint.shader = it }

        valuePaint.textSize = size * 0.20f
        unitPaint.textSize = size * 0.082f
        captionPaint.textSize = size * 0.055f
        captionPaint.letterSpacing = 0.18f
    }

    override fun onDraw(canvas: Canvas) {
        if (arcBounds.isEmpty) return

        val cx = arcBounds.centerX()
        val cy = arcBounds.centerY()
        val radius = arcBounds.width() / 2f

        // Recessed inner face.
        canvas.drawCircle(cx, cy, radius - thickness * 0.75f, innerPaint)

        // Tick marks around the dial.
        for (i in 0..TICK_COUNT) {
            val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * i / TICK_COUNT).toDouble())
            val outer = radius + thickness * 0.82f
            val inner = outer - dp(if (i % 5 == 0) 8f else 4f)
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(), cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(), cy + (sin(angle) * outer).toFloat(),
                tickPaint
            )
        }

        canvas.drawArc(arcBounds, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        if (progress > 0f) {
            val sweep = SWEEP_ANGLE * progress
            canvas.drawArc(arcBounds, START_ANGLE, sweep, false, progressPaint)

            // Bright bead at the leading edge of the arc.
            val angle = Math.toRadians((START_ANGLE + sweep).toDouble())
            val px = cx + (cos(angle) * radius).toFloat()
            val py = cy + (sin(angle) * radius).toFloat()
            capGlowShader?.let { shader ->
                capMatrix.setTranslate(px, py)
                shader.setLocalMatrix(capMatrix)
                canvas.drawCircle(px, py, capRadius, capGlowPaint)
            }
        }

        canvas.drawCircle(cx, cy, radius - thickness * 0.75f, domePaint)

        // Readout, vertically centred as a block rather than on the baseline.
        val valueY = cy + valuePaint.textSize * 0.28f
        canvas.drawText(valueText, cx, valueY, valuePaint)
        canvas.drawText(unitText, cx, valueY + unitPaint.textSize * 1.45f, unitPaint)
        if (captionText.isNotEmpty()) {
            canvas.drawText(captionText, cx, arcBounds.top + radius * 0.34f, captionPaint)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_ANGLE = 270f
        const val TICK_COUNT = 40
    }
}
