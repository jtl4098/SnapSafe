package com.github.jtl4098.imageredact

import android.graphics.Bitmap
import com.github.jtl4098.imageredact.core.MaskRegion


data class MaskResult(
    val bitmap: Bitmap,
    val faceCount: Int,
    val textCount: Int,
    val regions: List<MaskRegion>
)


