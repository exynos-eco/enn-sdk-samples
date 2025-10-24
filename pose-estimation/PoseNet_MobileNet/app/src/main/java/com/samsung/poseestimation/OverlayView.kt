// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
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

    private var scaleX = 1F;
    private var scaleY = 1F;


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

        scaleX = width.toFloat() / 257  // (257 is input image width size)
        scaleY = height.toFloat() / 513 // (513 is input image height size)

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        result?.let { human ->

            human.points.forEach {
                val x = it.coordinate.x * scaleX
                val y = it.coordinate.y * scaleY

                canvas.drawPoint(x, y, pointPaint)
            }
            human.edges.forEach {
                canvas.drawLine(
                    it.first.coordinate.x * scaleX,
                    it.first.coordinate.y * scaleY,
                    it.second.coordinate.x * scaleX,
                    it.second.coordinate.y * scaleY,
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