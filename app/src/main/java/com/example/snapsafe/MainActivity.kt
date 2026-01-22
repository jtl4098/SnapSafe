package com.example.snapsafe

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.snapsafe.core.BitmapMasker
import com.example.snapsafe.core.BitmapIO
import com.example.snapsafe.core.MaskRegion
import com.example.snapsafe.core.MaskType
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var loadButton: Button
    private lateinit var captureButton: Button
    private lateinit var statusText: TextView
    private var imageCapture: ImageCapture? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val masker = BitmapMasker()
    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            statusText.text = "Camera permission required"
        }
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

        previewView = findViewById(R.id.preview_view)
        loadButton = findViewById(R.id.load_button)
        captureButton = findViewById(R.id.capture_button)
        statusText = findViewById(R.id.status_text)

        loadButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        captureButton.setOnClickListener {
            if (imageCapture == null) {
                Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            } else {
                capturePhoto()
            }
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector.close()
        textRecognizer.close()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    statusText.text = "Preview ready"
                } catch (ex: Exception) {
                    statusText.text = "Camera bind failed"
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        updateStatus("Capturing...", false)

        val photoFile = File.createTempFile("SnapSafe_capture_", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    updateStatus("Processing...", false)
                    processImageFile(photoFile, deleteInputAfter = false)
                }

                override fun onError(exception: ImageCaptureException) {
                    updateStatus("Capture failed", true)
                }
            }
        )
    }

    private fun processPickedImage(uri: Uri) {
        cameraExecutor.execute {
            val copied = copyUriToCache(uri)
            if (copied == null) {
                updateStatus("Load failed", true)
                return@execute
            }
            processImageFile(copied, deleteInputAfter = true)
        }
    }

    private fun processImageFile(photoFile: File, deleteInputAfter: Boolean) {
        val inputImage = try {
            InputImage.fromFilePath(this, Uri.fromFile(photoFile))
        } catch (ex: IOException) {
            updateStatus("Image load failed", true)
            if (deleteInputAfter) {
                photoFile.delete()
            }
            return
        }

        val faceTask = faceDetector.process(inputImage)
        val textTask = textRecognizer.process(inputImage)

        Tasks.whenAllSuccess<Any>(faceTask, textTask)
            .addOnSuccessListener(cameraExecutor) { results ->
                val faces = (results.getOrNull(0) as? List<*>)?.filterIsInstance<Face>().orEmpty()
                val text = results.getOrNull(1) as? Text
                if (text == null) {
                    updateStatus("Text decode failed", true)
                    if (deleteInputAfter) {
                        photoFile.delete()
                    }
                    return@addOnSuccessListener
                }
                val regions = buildRegions(faces, text)

                val sourceBitmap = BitmapIO.decodeUprightBitmap(photoFile)
                if (sourceBitmap == null) {
                    updateStatus("Decode failed", true)
                    if (deleteInputAfter) {
                        photoFile.delete()
                    }
                    return@addOnSuccessListener
                }

                val masked = masker.applyMasks(sourceBitmap, regions, debugOutline = BuildConfig.DEBUG)
                val outputFile = File.createTempFile("SnapSafe_masked_", ".jpg", cacheDir)
                val saved = BitmapIO.saveJpeg(masked, outputFile)

                if (!saved) {
                    updateStatus("Save failed", true)
                    if (deleteInputAfter) {
                        photoFile.delete()
                    }
                    return@addOnSuccessListener
                }

                val faceCount = faces.size
                val textCount = text.textBlocks.size
                runOnUiThread {
                    statusText.text = "Faces: $faceCount, Text: $textCount"
                    captureButton.isEnabled = true
                    loadButton.isEnabled = true
                    ResultActivity.launch(this, outputFile, faceCount, textCount)
                }

                if (deleteInputAfter) {
                    photoFile.delete()
                }
            }
            .addOnFailureListener(cameraExecutor) {
                updateStatus("Detection failed", true)
                if (deleteInputAfter) {
                    photoFile.delete()
                }
            }
    }

    private fun buildRegions(faces: List<Face>, text: Text): List<MaskRegion> {
        val regions = mutableListOf<MaskRegion>()
        faces.forEach { face ->
            regions.add(MaskRegion(face.boundingBox, MaskType.FACE))
        }
        text.textBlocks.forEach { block ->
            val box = block.boundingBox ?: return@forEach
            regions.add(MaskRegion(box, MaskType.TEXT))
        }
        return regions
    }

    private fun updateStatus(message: String, enableActions: Boolean) {
        runOnUiThread {
            statusText.text = message
            captureButton.isEnabled = enableActions
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


