// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.lang.Float.min


class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {
    private var resultMask: Bitmap? = null
    private var scaleWidth: Int = 0
    private var scaleHeight: Int = 0

    fun setResults(pixels: IntArray, imageWidth: Int, imageHeight: Int) {
        val image = Bitmap.createBitmap(
            pixels, imageWidth, imageHeight, Bitmap.Config.ARGB_8888
        )

        resultMask = createScaledBitmap(image, imageWidth, imageHeight)
    }

    private fun createScaledBitmap(image: Bitmap, imageWidth: Int, imageHeight: Int): Bitmap {
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        scaleWidth = (imageWidth * scale).toInt()
        scaleHeight = (imageHeight * scale).toInt()

        return Bitmap.createScaledBitmap(image, scaleWidth, scaleHeight, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        resultMask?.let {
            canvas.drawBitmap(it, 0F, (height - scaleHeight) / 2F, null)
        }
    }

    fun clear() {
        resultMask = null
        invalidate()
    }
}