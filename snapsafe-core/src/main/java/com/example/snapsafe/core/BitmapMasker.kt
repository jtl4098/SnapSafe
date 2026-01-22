package com.example.snapsafe.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max

class BitmapMasker(
    private val config: MaskingConfig = MaskingConfig()
) {
    fun applyMasks(source: Bitmap, regions: List<MaskRegion>, debugOutline: Boolean = false): Bitmap {
        if (regions.isEmpty()) {
            return source
        }

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val textPaint = Paint().apply {
            color = config.textColor
            style = Paint.Style.FILL
        }
        val faceOutlinePaint = Paint().apply {
            color = Color.MAGENTA
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val textOutlinePaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        regions.forEach { region ->
            val clamped = clampRect(region.rect, output.width, output.height)
            when (region.type) {
                MaskType.TEXT -> {
                    if (!clamped.isEmpty) {
                        canvas.drawRect(clamped, textPaint)
                        if (debugOutline) {
                            canvas.drawRect(clamped, textOutlinePaint)
                        }
                    }
                }
                MaskType.FACE -> {
                    applyPixelation(canvas, output, region.rect, config.facePixelSize)
                    if (debugOutline && !clamped.isEmpty) {
                        canvas.drawRect(clamped, faceOutlinePaint)
                    }
                }
            }
        }

        return output
    }

    private fun applyPixelation(canvas: Canvas, bitmap: Bitmap, rect: Rect, pixelSize: Int) {
        val clamped = clampRect(rect, bitmap.width, bitmap.height)
        if (clamped.isEmpty) {
            return
        }

        val width = clamped.width()
        val height = clamped.height()
        val safePixelSize = max(1, pixelSize)
        val scaledWidth = max(1, width / safePixelSize)
        val scaledHeight = max(1, height / safePixelSize)

        val region = Bitmap.createBitmap(bitmap, clamped.left, clamped.top, width, height)
        val small = Bitmap.createScaledBitmap(region, scaledWidth, scaledHeight, false)
        val pixelated = Bitmap.createScaledBitmap(small, width, height, false)

        canvas.drawBitmap(pixelated, clamped.left.toFloat(), clamped.top.toFloat(), null)

        region.recycle()
        small.recycle()
        pixelated.recycle()
    }

    private fun clampRect(rect: Rect, width: Int, height: Int): Rect {
        val left = rect.left.coerceIn(0, width)
        val top = rect.top.coerceIn(0, height)
        val right = rect.right.coerceIn(0, width)
        val bottom = rect.bottom.coerceIn(0, height)

        return if (right <= left || bottom <= top) {
            Rect()
        } else {
            Rect(left, top, right, bottom)
        }
    }
}

