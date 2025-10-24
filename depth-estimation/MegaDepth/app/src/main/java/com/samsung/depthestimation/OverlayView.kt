package com.samsung.depthestimation

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {

    private var depthMask: Bitmap? = null
    private val paint = Paint().apply {
        alpha = OVERLAY_ALPHA
        isFilterBitmap = true
    }

    /**
     * result: 0~255 그레이스케일 값 배열 (H*W)
     * imageWidth, imageHeight: 모델 출력 크기
     */
    fun setResults(depthArray: IntArray, imageWidth: Int, imageHeight: Int) {
        val pixels = IntArray(depthArray.size)
        for (i in depthArray.indices) {
            val v = depthArray[i].coerceIn(0, 255)
            // A|R|G|B = v (흑백)
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val rawBitmap = Bitmap.createBitmap(pixels, imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        depthMask = createScaledBitmap(rawBitmap, imageWidth, imageHeight)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        depthMask?.let { bmp ->
            val left = (width - bmp.width) / 2f
            val top = (height - bmp.height) / 2f
            canvas.drawBitmap(bmp, left, top, paint)
        }
    }

    private fun createScaledBitmap(image: Bitmap, imageWidth: Int, imageHeight: Int): Bitmap {
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val scaleWidth = (imageWidth * scale).toInt()
        val scaleHeight = (imageHeight * scale).toInt()
        return Bitmap.createScaledBitmap(image, scaleWidth, scaleHeight, true)
    }

    fun clear() {
        depthMask = null
        invalidate()
    }

    companion object {
        private const val OVERLAY_ALPHA = 255 // 🔹 흑백을 뚜렷하게 보기 위해 255로
    }
}