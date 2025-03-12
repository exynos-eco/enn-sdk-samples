// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.samsung.poseestimation.data.Human
import java.lang.Float.max
import java.lang.Float.min
import kotlin.let
import kotlin.with


class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {
    private var result: Human? = null
    private val pointPaint = Paint()
    private val edgePaint = Paint()

    private var scale = 1F
    private var offset = 0F

    init {
        initPaints()
    }

    private fun initPaints() {
        with(pointPaint) {
            color = ContextCompat.getColor(context!!, R.color.pose_color)
            strokeWidth = 12f
            style = Paint.Style.FILL
        }

        with(edgePaint) {
            color = ContextCompat.getColor(context!!, R.color.pose_color)
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
    }

    fun setResults(human: Human) {
        result = human
        scale = min(width.toFloat(), height.toFloat()) / 257
        offset = (max(width.toFloat(), height.toFloat()) - min(width.toFloat(), height.toFloat())) / 2 + 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        result?.let { human ->
            human.points.forEach {
                canvas.drawPoint(
                    it.coordinate.x * scale, it.coordinate.y * scale + offset, pointPaint
                )
            }
            human.edges.forEach {
                canvas.drawLine(
                    it.first.coordinate.x * scale,
                    it.first.coordinate.y * scale + offset,
                    it.second.coordinate.x * scale,
                    it.second.coordinate.y * scale + offset,
                    edgePaint
                )
            }
        }
    }

    fun clear() {
        result = null
        scale = 0F
        offset = 0F
        pointPaint.reset()
        edgePaint.reset()
        invalidate()
        initPaints()
    }
}