package ie.griffith.plantlight.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightClassifier

/**
 * Milestone 2 custom view: a simple live line graph of the light readings.
 *
 * - Keeps the last [maxPoints] readings (one is added about every half second).
 * - The background has three coloured bands for Low, Medium and Bright,
 *   so you can see at a glance which zone the light is in.
 * - The Y axis grows automatically if the light goes above 2,000 lux.
 * - The Y axis uses a log scale, so low indoor readings (10-200 lux) are
 *   still clearly visible instead of hugging the bottom of the graph.
 */
class LiveLightGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxPoints = 120
    private val points = ArrayDeque<Float>()

    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.forest_dark)
    }
    private val bandPaint = Paint().apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        color = ContextCompat.getColor(context, R.color.text_muted)
    }
    private val path = Path()

    /** Adds one reading (in lux) and redraws the graph. */
    fun addPoint(lux: Float) {
        points.addLast(lux)
        while (points.size > maxPoints) points.removeFirst()
        invalidate()
    }

    fun clear() {
        points.clear()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize((180 * density).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Top of the graph: 2,000 lux, or higher if a reading goes above it.
        val topLux = maxOf(LightClassifier.GAUGE_MAX_LUX, (points.maxOrNull() ?: 0f) * 1.1f)
        // Log scale: 0 lux at the bottom, topLux at the top.
        val logTop = kotlin.math.log10(topLux + 1f)
        fun yFor(lux: Float): Float =
            h - (kotlin.math.log10(lux.coerceAtLeast(0f) + 1f) / logTop) * h

        // Coloured bands: Low (bottom), Medium, Bright (top).
        val lowTop = yFor(LightClassifier.LOW_MAX_LUX)
        val mediumTop = yFor(LightClassifier.MEDIUM_MAX_LUX)
        drawBand(canvas, lowTop, h, R.color.level_low)
        drawBand(canvas, mediumTop, lowTop, R.color.level_medium)
        drawBand(canvas, 0f, mediumTop, R.color.level_bright)
        canvas.drawText("Bright", 6 * density, 14 * density, textPaint)
        canvas.drawText("Medium", 6 * density, mediumTop + 14 * density, textPaint)
        canvas.drawText("Low", 6 * density, h - 6 * density, textPaint)

        if (points.size < 2) return

        // The line: oldest reading on the left, newest on the right.
        val stepX = w / (maxPoints - 1)
        val startX = w - stepX * (points.size - 1)
        path.reset()
        points.forEachIndexed { index, lux ->
            val x = startX + index * stepX
            val y = yFor(lux)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawBand(canvas: Canvas, top: Float, bottom: Float, colorRes: Int) {
        bandPaint.color = ContextCompat.getColor(context, colorRes)
        bandPaint.alpha = 45
        canvas.drawRect(0f, top, width.toFloat(), bottom, bandPaint)
    }
}
