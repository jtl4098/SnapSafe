package com.example.snapsafe

import android.graphics.Color


data class MaskOptions(
    val detectFaces: Boolean = true,
    val detectText: Boolean = true,
    val textMaskColor: Int = Color.BLACK,
    val facePixelSize: Int = 24,
    val debugOutline: Boolean = false
)
