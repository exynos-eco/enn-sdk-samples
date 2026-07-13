// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoenhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(
    context: Context?,
    attrs: AttributeSet?
) : View(context, attrs) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var resultBitmap: Bitmap? = null

    fun setResult(bitmap: Bitmap) {
        resultBitmap = bitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = resultBitmap ?: return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val scale = minOf(
            viewWidth / bitmap.width.toFloat(),
            viewHeight / bitmap.height.toFloat()
        )

        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (viewWidth - drawWidth) / 2.0F
        val top = (viewHeight - drawHeight) / 2.0F

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            bitmapPaint
        )
    }

    fun clear() {
        resultBitmap = null
        invalidate()
    }
}
