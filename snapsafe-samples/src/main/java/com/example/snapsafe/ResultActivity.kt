package com.example.snapsafe

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.snapsafe.core.BitmapDownsampler
import java.io.File

class ResultActivity : AppCompatActivity() {
    private var outputFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val backButton = findViewById<Button>(R.id.back_button)
        val imageView = findViewById<ImageView>(R.id.result_image)
        val statusView = findViewById<TextView>(R.id.result_status)

        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }

        val faceCount = intent.getIntExtra(EXTRA_FACE_COUNT, 0)
        val textCount = intent.getIntExtra(EXTRA_TEXT_COUNT, 0)
        statusView.text = if (faceCount == 0 && textCount == 0) {
            "No masks detected"
        } else {
            "Faces: $faceCount  Text: $textCount"
        }

        outputFile = File(path)
        val bitmap = decodeForDisplay(outputFile!!)
        if (bitmap == null) {
            finish()
            return
        }

        imageView.setImageBitmap(bitmap)
        backButton.setOnClickListener { finish() }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            outputFile?.delete()
        }
    }

    private fun decodeForDisplay(file: File): Bitmap? {
        val metrics = resources.displayMetrics
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        options.inSampleSize = BitmapDownsampler.calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            metrics.widthPixels,
            metrics.heightPixels
        )
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    companion object {
        private const val EXTRA_IMAGE_PATH = "extra_image_path"
        private const val EXTRA_FACE_COUNT = "extra_face_count"
        private const val EXTRA_TEXT_COUNT = "extra_text_count"

        fun launch(context: Context, file: File, faceCount: Int, textCount: Int) {
            val intent = Intent(context, ResultActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_PATH, file.absolutePath)
                putExtra(EXTRA_FACE_COUNT, faceCount)
                putExtra(EXTRA_TEXT_COUNT, textCount)
            }
            context.startActivity(intent)
        }
    }
}

