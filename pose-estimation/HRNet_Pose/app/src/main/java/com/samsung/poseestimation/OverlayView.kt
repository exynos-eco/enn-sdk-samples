package com.samsung.poseestimation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {

    private var keypoints: List<Triple<Int?, Int?, Float>>? = null

    private val pointPaint = Paint()
    private val edgePaint = Paint()

    private val skeleton = listOf(
        0 to 1, 1 to 3, 0 to 2, 2 to 4,
        5 to 6, 5 to 7, 7 to 9, 6 to 8, 8 to 10,
        5 to 11, 6 to 12, 11 to 12,
        11 to 13, 13 to 15, 12 to 14, 14 to 16
    )

    init {
        initPaints()
    }

    private fun initPaints() {
        with(pointPaint) {
            color = ContextCompat.getColor(context!!, R.color.pose_color)
            strokeWidth = 10f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        with(edgePaint) {
            color = ContextCompat.getColor(context!!, R.color.pose_color)
            strokeWidth = 5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
    }

    fun setKeypoints(points: List<Triple<Int?, Int?, Float>>) {
        keypoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kp = keypoints ?: return
        if (kp.isEmpty()) return

        val inputW = 192f
        val inputH = 256f
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val scaleX = viewW / inputW
        val scaleY = viewH / inputH
        val scale = min(scaleX, scaleY)

        val offsetX = (viewW - inputW * scale) / 2f
        val offsetY = (viewH - inputH * scale) / 2f


        skeleton.forEach { (a, b) ->
            if (a < kp.size && b < kp.size) {
                val (xa, ya, ca) = kp[a]
                val (xb, yb, cb) = kp[b]
                if (xa != null && ya != null && xb != null && yb != null && ca > 0.3f && cb > 0.3f) {
                    canvas.drawLine(
                        xa * scale + offsetX,
                        ya * scale + offsetY,
                        xb * scale + offsetX,
                        yb * scale + offsetY,
                        edgePaint
                    )
                }
            }
        }

        kp.forEach { (x, y, conf) ->
            if (x != null && y != null && conf > 0.3f) {
                canvas.drawCircle(x * scale + offsetX, y * scale + offsetY, 6f, pointPaint)
            }
        }
    }


    fun clear() {
        keypoints = null
        invalidate()
    }
}
