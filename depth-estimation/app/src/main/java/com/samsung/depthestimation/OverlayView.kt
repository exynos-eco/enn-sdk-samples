// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.depthestimation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import java.lang.Float.min


class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {
    private var resultMask: Bitmap? = null

    fun setResults(alphaArray: IntArray, imageWidth: Int, imageHeight: Int) {
        val pixels = IntArray(alphaArray.size)

        alphaArray.forEachIndexed { index, value ->
            pixels[index] = Color.argb(220, 255 - value, value, 255)
        }

        val image = Bitmap.createBitmap(
            pixels, imageWidth, imageHeight, Bitmap.Config.ARGB_8888
        )

        resultMask = createScaledBitmap(image, imageWidth, imageHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        resultMask?.let {
            canvas.drawBitmap(it, 0F, (height - width) / 2F, null)
        }
    }

    private fun createScaledBitmap(image: Bitmap, imageWidth: Int, imageHeight: Int): Bitmap {
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val scaleWidth = (imageWidth * scale).toInt()
        val scaleHeight = (imageHeight * scale).toInt()

        return Bitmap.createScaledBitmap(image, scaleWidth, scaleHeight, false)
    }

    fun clear() {
        resultMask = null
        invalidate()
    }

}