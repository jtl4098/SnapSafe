package com.example.snapsafe

import android.graphics.Bitmap
import com.example.snapsafe.core.MaskRegion


data class MaskResult(
    val bitmap: Bitmap,
    val faceCount: Int,
    val textCount: Int,
    val regions: List<MaskRegion>
)
