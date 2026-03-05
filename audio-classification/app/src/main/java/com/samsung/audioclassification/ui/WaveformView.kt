package com.samsung.audioclassification.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.samsung.audioclassification.R

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.purple_700)
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // Ring buffer: stores the most recent displayBufferSize samples
    private val displayBufferSize = 4800  // 300ms worth of audio at 16kHz
    private val displayBuffer = FloatArray(displayBufferSize)
    private var writePointer = 0
    private var filledCount = 0

    fun updateSamples(newSamples: FloatArray) {
        for (sample in newSamples) {
            displayBuffer[writePointer] = sample
            writePointer = (writePointer + 1) % displayBufferSize
        }
        filledCount = (filledCount + newSamples.size).coerceAtMost(displayBufferSize)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (filledCount == 0) return

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // Read samples in chronological order from the ring buffer
        val totalSamples = filledCount
        val step = (totalSamples / width).toInt().coerceAtLeast(1)

        var lastX = 0f
        var lastY = centerY

        for (i in 0 until totalSamples step step) {
            // Calculate actual index in the ring buffer (oldest to newest)
            val bufIndex = (writePointer - filledCount + i + displayBufferSize) % displayBufferSize
            val x = (i.toFloat() / totalSamples) * width
            val y = centerY - (displayBuffer[bufIndex] * centerY * 0.8f)

            if (i > 0) {
                canvas.drawLine(lastX, lastY, x, y, paint)
            }
            lastX = x
            lastY = y
        }
    }
}
