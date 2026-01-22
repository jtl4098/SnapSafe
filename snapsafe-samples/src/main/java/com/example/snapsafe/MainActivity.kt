package com.example.snapsafe

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.snapsafe.core.BitmapIO
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var loadButton: Button
    private lateinit var statusText: TextView

    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val snapSafe by lazy {
        SnapSafe(this, MaskOptions(debugOutline = BuildConfig.DEBUG), workerExecutor)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        updateStatus("Loading image...", false)
        processPickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadButton = findViewById(R.id.load_button)
        statusText = findViewById(R.id.status_text)

        loadButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        snapSafe.close()
        workerExecutor.shutdown()
    }

    private fun processPickedImage(uri: Uri) {
        workerExecutor.execute {
            val copied = copyUriToCache(uri)
            if (copied == null) {
                updateStatus("Load failed", true)
                return@execute
            }
            processImageFile(copied, deleteInputAfter = true)
        }
    }

    private fun processImageFile(photoFile: File, deleteInputAfter: Boolean) {
        snapSafe.maskFile(
            photoFile,
            onSuccess = { result ->
                val outputFile = File.createTempFile("SnapSafe_masked_", ".jpg", cacheDir)
                val saved = BitmapIO.saveJpeg(result.bitmap, outputFile)
                if (!saved) {
                    updateStatus("Save failed", true)
                    if (deleteInputAfter) {
                        photoFile.delete()
                    }
                    return@maskFile
                }

                runOnUiThread {
                    statusText.text = "Faces: ${result.faceCount}, Text: ${result.textCount}"
                    loadButton.isEnabled = true
                    ResultActivity.launch(this, outputFile, result.faceCount, result.textCount)
                }

                if (deleteInputAfter) {
                    photoFile.delete()
                }
            },
            onError = {
                updateStatus("Detection failed", true)
                if (deleteInputAfter) {
                    photoFile.delete()
                }
            }
        )
    }

    private fun updateStatus(message: String, enableActions: Boolean) {
        runOnUiThread {
            statusText.text = message
            loadButton.isEnabled = enableActions
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("SnapSafe_import_", ".jpg", cacheDir)
        try {
            FileOutputStream(tempFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
        } catch (ex: IOException) {
            tempFile.delete()
            return null
        }
        return tempFile
    }
}
