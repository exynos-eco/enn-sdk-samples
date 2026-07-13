package com.samsung.imagetotext

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.samsung.imagetotext.data.DetectionResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(
    context: Context?,
    attrs: AttributeSet?
) : View(context, attrs) {

    private var results: List<DetectionResult> = emptyList()
    private var sourceImageWidth: Int = 0
    private var sourceImageHeight: Int = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        boxPaint.apply {
            color = ContextCompat.getColor(
                requireNotNull(context),
                R.color.bounding_box_color
            )
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

        textBackgroundPaint.apply {
            color = Color.argb(210, 0, 0, 0)
            style = Paint.Style.FILL
        }

        textPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 34f
        }
    }

    fun setImageSize(width: Int, height: Int) {
        sourceImageWidth = width
        sourceImageHeight = height
        invalidate()
    }

    fun setResults(detectionResults: List<DetectionResult>) {
        results = detectionResults
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (sourceImageWidth <= 0 || sourceImageHeight <= 0) {
            return
        }

        val displayedImageRect = calculateDisplayedImageRect()

        results.forEach { result ->
            val box = scaleToDisplayedImage(
                normalized = result.requireBoundingBox(),
                displayedImageRect = displayedImageRect
            )

            canvas.drawRect(box, boxPaint)
            drawLabel(
                canvas = canvas,
                text = result.score.first,
                box = box
            )
        }
    }

    private fun calculateDisplayedImageRect(): RectF {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imageWidth = sourceImageWidth.toFloat()
        val imageHeight = sourceImageHeight.toFloat()

        val scale = min(
            viewWidth / imageWidth,
            viewHeight / imageHeight
        )

        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale

        val offsetX = (viewWidth - displayedWidth) / 2f
        val offsetY = (viewHeight - displayedHeight) / 2f

        return RectF(
            offsetX,
            offsetY,
            offsetX + displayedWidth,
            offsetY + displayedHeight
        )
    }

    private fun scaleToDisplayedImage(
        normalized: RectF,
        displayedImageRect: RectF
    ): RectF {
        return RectF(
            displayedImageRect.left +
                    normalized.left * displayedImageRect.width(),
            displayedImageRect.top +
                    normalized.top * displayedImageRect.height(),
            displayedImageRect.left +
                    normalized.right * displayedImageRect.width(),
            displayedImageRect.top +
                    normalized.bottom * displayedImageRect.height()
        )
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        box: RectF
    ) {
        val label = text
        val padding = 8f
        val measuredWidth = textPaint.measureText(label)
        val fontMetrics = textPaint.fontMetrics
        val labelHeight = fontMetrics.bottom - fontMetrics.top
        val backgroundHeight = labelHeight + padding * 2f
        val backgroundWidth = measuredWidth + padding * 2f

        val maxLeft = max(0f, width.toFloat() - backgroundWidth)
        val left = box.left.coerceIn(0f, maxLeft)

        val top = if (box.top >= backgroundHeight) {
            box.top - backgroundHeight
        } else {
            box.top
        }.coerceIn(
            0f,
            max(0f, height.toFloat() - backgroundHeight)
        )

        val right = min(width.toFloat(), left + backgroundWidth)
        val bottom = min(height.toFloat(), top + backgroundHeight)

        canvas.drawRect(left, top, right, bottom, textBackgroundPaint)
        canvas.drawText(label, left + padding, bottom - padding - fontMetrics.bottom, textPaint)
    }

    fun clear() {
        results = emptyList()
        sourceImageWidth = 0
        sourceImageHeight = 0
        invalidate()
    }
}
