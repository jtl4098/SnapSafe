package com.example.snapsafe.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.snapsafe.core.MaskRegion
import com.example.snapsafe.core.MaskType

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val facePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val regions = mutableListOf<MaskRegion>()

    fun setRegions(newRegions: List<MaskRegion>) {
        regions.clear()
        regions.addAll(newRegions)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        regions.forEach { region ->
            val paint = when (region.type) {
                MaskType.FACE -> facePaint
                MaskType.TEXT -> textPaint
            }
            canvas.drawRect(region.rect, paint)
        }
    }
}

