// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageediting.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MaskDrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val completedPaths = mutableListOf<Path>()
    private var currentPath: Path? = null

    private val displayFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 255, 0, 0)
    }

    private val displayStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 50.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(150, 255, 0, 0)
    }

    private val maskFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (path in completedPaths) {
            canvas.drawPath(path, displayFillPaint)
            canvas.drawPath(path, displayStrokePaint)
        }

        currentPath?.let { path ->
            canvas.drawPath(path, displayStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                currentPath = Path().apply {
                    moveTo(event.x, event.y)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath?.lineTo(event.x, event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.let { path ->
                    path.lineTo(event.x, event.y)
                    path.close()
                    completedPaths.add(Path(path))
                }
                currentPath = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }

        return true
    }

    fun clear() {
        completedPaths.clear()
        currentPath = null
        visibility = VISIBLE
        invalidate()
    }

    fun hasMask(): Boolean {
        return completedPaths.isNotEmpty()
    }

    fun getMaskBitmap(targetWidth: Int, targetHeight: Int): Bitmap {
        val srcWidth = width.coerceAtLeast(1)
        val srcHeight = height.coerceAtLeast(1)

        val mask = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        canvas.drawColor(Color.BLACK)

        for (path in completedPaths) {
            canvas.drawPath(path, maskFillPaint)
        }

        return Bitmap.createScaledBitmap(mask, targetWidth, targetHeight, true)
    }
}
