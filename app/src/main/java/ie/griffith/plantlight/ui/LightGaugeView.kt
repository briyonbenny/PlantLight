package ie.griffith.plantlight.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightClassifier
import ie.griffith.plantlight.data.LightLevel
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.SettingsRepository
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom view: a horseshoe gauge that shows the live (averaged) light level.
 * The three coloured segments map to Low, Medium and Bright.
 */
class LightGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = ContextCompat.getColor(context, R.color.forest_dark)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_muted)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_muted)
    }

    private val colorLow = ContextCompat.getColor(context, R.color.level_low)
    private val colorMedium = ContextCompat.getColor(context, R.color.level_medium)
    private val colorBright = ContextCompat.getColor(context, R.color.level_bright)
    private val colorTrack = ContextCompat.getColor(context, R.color.gauge_track)
    private val colorNeedle = ContextCompat.getColor(context, R.color.forest_dark)

    private val arc = RectF()
    private var displayLux = 0f
    private var level: LightLevel = LightLevel.LOW
    private var hasSensor: Boolean = true
    private var unit: LightUnit = LightUnit.LUX
    private var animator: ValueAnimator? = null

    fun setReading(lux: Float, newLevel: LightLevel, sensorPresent: Boolean, newUnit: LightUnit) {
        hasSensor = sensorPresent
        level = newLevel
        unit = newUnit
        animator?.cancel()
        val start = displayLux
        animator = ValueAnimator.ofFloat(start, lux).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                displayLux = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun showUnavailable() {
        hasSensor = false
        displayLux = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (width * 0.72f).toInt().coerceAtLeast(
            (220 * resources.displayMetrics.density).toInt()
        )
        setMeasuredDimension(width, desired)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = width * 0.055f
        trackPaint.strokeWidth = stroke
        segmentPaint.strokeWidth = stroke * 0.92f
        needlePaint.strokeWidth = stroke * 0.28f
        needlePaint.color = colorNeedle
        hubPaint.color = colorNeedle

        val pad = stroke
        val size = min(width.toFloat(), height * 1.25f) - pad * 2
        val cx = width / 2f
        val cy = height * 0.58f
        val radius = size / 2f
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background track, then the three classification segments.
        trackPaint.color = colorTrack
        canvas.drawArc(arc, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        val lowSweep = SWEEP_ANGLE * (LightClassifier.LOW_MAX_LUX / LightClassifier.GAUGE_MAX_LUX)
        val midSweep = SWEEP_ANGLE * (
            (LightClassifier.MEDIUM_MAX_LUX - LightClassifier.LOW_MAX_LUX) /
                LightClassifier.GAUGE_MAX_LUX
        )
        val brightSweep = SWEEP_ANGLE - lowSweep - midSweep
        segmentPaint.color = colorLow
        canvas.drawArc(arc, START_ANGLE, lowSweep, false, segmentPaint)
        segmentPaint.color = colorMedium
        canvas.drawArc(arc, START_ANGLE + lowSweep, midSweep, false, segmentPaint)
        segmentPaint.color = colorBright
        canvas.drawArc(arc, START_ANGLE + lowSweep + midSweep, brightSweep, false, segmentPaint)

        valuePaint.textSize = radius * 0.28f
        labelPaint.textSize = radius * 0.11f
        captionPaint.textSize = radius * 0.10f

        if (hasSensor) {
            val clamped = displayLux.coerceIn(0f, LightClassifier.GAUGE_MAX_LUX)
            val fraction = clamped / LightClassifier.GAUGE_MAX_LUX
            val angleDeg = START_ANGLE + SWEEP_ANGLE * fraction
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val inner = radius - stroke * 1.6f
            val outer = radius + stroke * 0.15f
            val x1 = cx + inner * cos(angleRad).toFloat()
            val y1 = cy + inner * sin(angleRad).toFloat()
            val x2 = cx + outer * cos(angleRad).toFloat()
            val y2 = cy + outer * sin(angleRad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, needlePaint)
            canvas.drawCircle(cx, cy, stroke * 0.35f, hubPaint)

            canvas.drawText(
                SettingsRepository.format(displayLux, unit),
                cx,
                cy - radius * 0.08f,
                valuePaint
            )
            canvas.drawText(
                "${level.label} light",
                cx,
                cy + radius * 0.14f,
                labelPaint
            )
            canvas.drawText(
                "averaged over 3 seconds",
                cx,
                cy + radius * 0.28f,
                captionPaint
            )
        } else {
            canvas.drawText("No sensor", cx, cy - radius * 0.04f, valuePaint)
            canvas.drawText(
                "Choose a light level below",
                cx,
                cy + radius * 0.18f,
                captionPaint
            )
        }

        // Tick labels along the arc
        drawTickLabel(canvas, cx, cy, radius + stroke * 1.15f, START_ANGLE, "Low")
        drawTickLabel(
            canvas, cx, cy, radius + stroke * 1.15f,
            START_ANGLE + SWEEP_ANGLE / 2f, "Medium"
        )
        drawTickLabel(canvas, cx, cy, radius + stroke * 1.15f, START_ANGLE + SWEEP_ANGLE, "Bright")
    }

    private fun drawTickLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        angleDeg: Float,
        text: String
    ) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val x = cx + r * cos(rad).toFloat()
        val y = cy + r * sin(rad).toFloat() + captionPaint.textSize / 3f
        canvas.drawText(text, x, y, captionPaint)
    }

    companion object {
        // Horseshoe opening at the bottom: 135 degrees through to 45 degrees (270 sweep).
        private const val START_ANGLE = 135f
        private const val SWEEP_ANGLE = 270f
    }
}
