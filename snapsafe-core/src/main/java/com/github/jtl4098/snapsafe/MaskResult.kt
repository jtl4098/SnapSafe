package com.github.jtl4098.snapsafe

import android.graphics.Bitmap
import com.github.jtl4098.snapsafe.core.MaskRegion


data class MaskResult(
    val bitmap: Bitmap,
    val faceCount: Int,
    val textCount: Int,
    val regions: List<MaskRegion>
)

