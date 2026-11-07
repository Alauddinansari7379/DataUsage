package dev.jahidhasanco.datausage.presentation.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.alauddin.datausagen.R
import kotlin.math.min
import kotlin.random.Random

/**
 * A frosted-glass panel.
 *
 * Android has no public API for blurring what sits *behind* a view inside an
 * activity window, so the frosted look is built up instead of sampled: a
 * translucent body gradient, a fine grain layer that scatters the light, a
 * specular sheen across the top, and a rim that is bright along the top edge and
 * fades toward the bottom. That asymmetric rim is what reads as thickness.
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bounds = RectF()
    private val innerBounds = RectF()

    private var cornerRadius = dp(28f)
    private var rimWidth = dp(1f)
    private var bodyAlpha = 0.16f
    private var glowColor = Color.TRANSPARENT
    private var drawGrain = true

    init {
        setWillNotDraw(false)

        context.obtainStyledAttributes(attrs, R.styleable.GlassCardView).use { a ->
            cornerRadius =
                a.getDimension(R.styleable.GlassCardView_glassCornerRadius, cornerRadius)
            bodyAlpha = a.getFloat(R.styleable.GlassCardView_glassAlpha, bodyAlpha)
            glowColor = a.getColor(R.styleable.GlassCardView_glassGlow, Color.TRANSPARENT)
            drawGrain = a.getBoolean(R.styleable.GlassCardView_glassGrain, true)
        }

        rimPaint.strokeWidth = rimWidth
        grainPaint.shader = BitmapShader(GRAIN, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainPaint.alpha = 14

        // Real elevation shadow, clipped to the same rounded silhouette as the body.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = true
        if (elevation == 0f) elevation = dp(6f)
    }

    /** Tint the panel with a coloured light source bleeding up from the bottom. */
    fun setGlow(color: Int) {
        if (glowColor == color) return
        glowColor = color
        rebuildShaders(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders(w, h)
    }

    private fun rebuildShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        val inset = rimWidth / 2f
        innerBounds.set(inset, inset, w - inset, h - inset)

        val top = withAlpha(Color.WHITE, bodyAlpha * 1.45f)
        val bottom = withAlpha(Color.WHITE, bodyAlpha * 0.55f)
        bodyPaint.shader = LinearGradient(
            0f, 0f, w * 0.35f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP
        )

        // Sheen sits in the upper third only, so the panel looks lit from above.
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.45f,
            intArrayOf(withAlpha(Color.WHITE, 0.30f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        rimPaint.shader = LinearGradient(
            0f, 0f, w * 0.2f, h.toFloat(),
            intArrayOf(
                withAlpha(Color.WHITE, 0.60f),
                withAlpha(Color.WHITE, 0.10f),
                withAlpha(Color.WHITE, 0.22f)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        glowPaint.shader = if (glowColor == Color.TRANSPARENT) null else RadialGradient(
            w * 0.5f, h.toFloat(), min(w, h) * 0.9f,
            intArrayOf(withAlpha(glowColor, 0.34f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (bounds.isEmpty) return

        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, bodyPaint)
        if (glowPaint.shader != null) {
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, glowPaint)
        }
        if (drawGrain) {
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, grainPaint)
        }
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, sheenPaint)
        canvas.drawRoundRect(innerBounds, cornerRadius, cornerRadius, rimPaint)

        super.onDraw(canvas)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        /**
         * A single 64x64 monochrome noise tile, shared by every card. Frosted
         * glass scatters light; a flat translucent fill reads as plastic without it.
         */
        val GRAIN: Bitmap = run {
            val size = 64
            val random = Random(20240501)
            val pixels = IntArray(size * size) {
                val level = random.nextInt(96, 256)
                Color.argb(255, level, level, level)
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }

        fun withAlpha(color: Int, fraction: Float): Int = Color.argb(
            (255 * fraction).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}

private inline fun <R> android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> R): R {
    try {
        return block(this)
    } finally {
        recycle()
    }
}
