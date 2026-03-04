package com.samsung.gazeestimation.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

data class GazeOverlayItem(
    val bbox: RectF,        // image coords
    val origin: PointF,     // image coords (ex: eyes center)
    val pitchDeg: Float,    // degrees (model output)
    val yawDeg: Float,      // degrees (model output)
    val rollDeg: Float
)

class GazeOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val USE_PYTHON_DRAW_GAZE = true
    private val ORIGIN_USE_BBOX_CENTER = false

    private val FLIP_PITCH_DEG = false
    private val FLIP_YAW_DEG = false

    // arrow length scale
    private val ARROW_LEN_SCALE = 1.5f
    private val BOX_STROKE = 6f
    private val ARROW_STROKE = 8f
    private val HEAD_LEN = 22f
    private val HEAD_ANGLE_DEG = 150.0
    private val MAX_ARROW_LEN_PX = 280f

    private var imageW = 0
    private var imageH = 0
    private var items: List<GazeOverlayItem> = emptyList()

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = BOX_STROKE
        isAntiAlias = true
        color = Color.GREEN
    }

    private val arrowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = ARROW_STROKE
        isAntiAlias = true
        color = Color.RED
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.RED
    }

    fun setImageSourceInfo(width: Int, height: Int) {
        imageW = width
        imageH = height
        invalidate()
    }

    fun setResults(results: List<GazeOverlayItem>) {
        items = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageW <= 0 || imageH <= 0) return
        if (items.isEmpty()) return

        val scale = minOf(width.toFloat() / imageW, height.toFloat() / imageH)
        val dx = (width - imageW * scale) / 2f
        val dy = (height - imageH * scale) / 2f

        fun mapRect(r: RectF): RectF = RectF(
            dx + r.left * scale,
            dy + r.top * scale,
            dx + r.right * scale,
            dy + r.bottom * scale
        )

        fun mapPoint(p: PointF): PointF = PointF(dx + p.x * scale, dy + p.y * scale)

        for (it in items) {
            val r = mapRect(it.bbox)
            canvas.drawRect(r, boxPaint)

            val (ox, oy) = if (ORIGIN_USE_BBOX_CENTER) {
                val cx = (r.left + r.right) * 0.5f
                val cy = (r.top + r.bottom) * 0.5f
                cx to cy
            } else {
                val o = mapPoint(it.origin)
                o.x to o.y
            }

            // degrees -> radians
            val pitchDeg = if (FLIP_PITCH_DEG) -it.pitchDeg else it.pitchDeg
            val yawDeg = if (FLIP_YAW_DEG) -it.yawDeg else it.yawDeg

            val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
            val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()

            var len = r.width() * ARROW_LEN_SCALE
            if (len > MAX_ARROW_LEN_PX) len = MAX_ARROW_LEN_PX

            val (ex, ey) = if (USE_PYTHON_DRAW_GAZE) {
                val ddx = (-len * sin(pitch) * cos(yaw))
                val ddy = (-len * sin(yaw))
                (ox + ddx) to (oy + ddy)
            } else {
                val vx = (-sin(yaw) * cos(pitch))
                val vy = (-sin(pitch))
                val norm = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(1e-6f)
                val nx = vx / norm
                val ny = vy / norm
                (ox + nx * len) to (oy + ny * len)
            }

            canvas.drawLine(ox, oy, ex, ey, arrowPaint)
            drawArrowHead(canvas, ox, oy, ex, ey)
        }
    }

    private fun drawArrowHead(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float) {
        val angle = Math.atan2((ey - sy).toDouble(), (ex - sx).toDouble()).toFloat()
        val a1 = angle + Math.toRadians(HEAD_ANGLE_DEG).toFloat()
        val a2 = angle - Math.toRadians(HEAD_ANGLE_DEG).toFloat()

        val x1 = ex + HEAD_LEN * cos(a1)
        val y1 = ey + HEAD_LEN * sin(a1)
        val x2 = ex + HEAD_LEN * cos(a2)
        val y2 = ey + HEAD_LEN * sin(a2)

        val path = Path().apply {
            moveTo(ex, ey)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        canvas.drawPath(path, fillPaint)
    }
}