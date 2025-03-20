// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.objectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.samsung.objectdetection.data.DetectionResult
import java.lang.Float.max
import java.lang.Float.min
import android.util.Log


class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {
    private var results: List<DetectionResult> = emptyList()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scale = 1F
    private var offset = 0F

    init {
        initPaints()
    }

    private fun initPaints() {
        with(textBackgroundPaint) {
            color = Color.BLACK
            style = Paint.Style.FILL
            textSize = 50f
        }

        with(textPaint) {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 50f
        }

        with(boxPaint) {
            color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
            strokeWidth = 8F
            style = Paint.Style.STROKE
        }
    }

    fun setResults(detectionResults: List<DetectionResult>) {
        results = detectionResults
        scale = min(width.toFloat(), height.toFloat())
        offset = (max(width.toFloat(), height.toFloat()) - min(width.toFloat(), height.toFloat())) / 2
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        results.forEach { result ->
            val boundingBox = result.requireBoundingBox()
            val scaledBoundingBox = getScaledCoordinates(boundingBox)

            canvas.drawRect(scaledBoundingBox, boxPaint)
            drawText(canvas, result, scaledBoundingBox)
        }
    }

    private fun getScaledCoordinates(boundingBox: RectF): RectF {
        val left = boundingBox.left * scale
        val top = boundingBox.top * scale + offset
        val right = boundingBox.right * scale
        val bottom = boundingBox.bottom * scale + offset

        return RectF(left, top, right, bottom)
    }

    private fun drawText(
        canvas: Canvas, result: DetectionResult, boundingBox: RectF
    ) {
        val left = boundingBox.left
        val top = boundingBox.top
        val drawableText = result.score.first + " " + String.format("%.2f", result.score.second)
        val textBounds = Rect()

        textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, textBounds)
        canvas.drawRect(
            left,
            top,
            left + textBounds.width() + 8,
            top + textBounds.height() + 8,
            textBackgroundPaint
        )
        canvas.drawText(drawableText, left, top + textBounds.height(), textPaint)
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        results = emptyList()
        invalidate()
        initPaints()
    }
}