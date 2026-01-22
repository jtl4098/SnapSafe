package com.github.jtl4098.snapsafe

import android.content.Context
import android.net.Uri
import com.github.jtl4098.snapsafe.core.BitmapIO
import com.github.jtl4098.snapsafe.core.BitmapMasker
import com.github.jtl4098.snapsafe.core.MaskRegion
import com.github.jtl4098.snapsafe.core.MaskType
import com.github.jtl4098.snapsafe.core.MaskingConfig
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class SnapSafe private constructor(
    private val context: Context,
    private val options: MaskOptions,
    private val executor: Executor,
    private val shutdownExecutor: Boolean
) : Closeable {
    private val faceDetector: FaceDetector by lazy {
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(faceOptions)
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    constructor(context: Context, options: MaskOptions = MaskOptions()) :
        this(context, options, Executors.newSingleThreadExecutor(), true)

    constructor(context: Context, options: MaskOptions, executor: Executor) :
        this(context, options, executor, false)

    fun maskBitmap(
        bitmap: android.graphics.Bitmap,
        rotationDegrees: Int = 0,
        onSuccess: (MaskResult) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
        process(inputImage, bitmap, onSuccess, onError)
    }

    suspend fun maskBitmap(
        bitmap: android.graphics.Bitmap,
        rotationDegrees: Int = 0
    ): MaskResult = suspendCoroutine { continuation ->
        maskBitmap(
            bitmap,
            rotationDegrees,
            onSuccess = { continuation.resume(it) },
            onError = { continuation.resumeWithException(it) }
        )
    }

    fun maskFile(
        file: File,
        onSuccess: (MaskResult) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val inputImage = try {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        } catch (ex: Exception) {
            onError(ex)
            return
        }

        val bitmap = BitmapIO.decodeUprightBitmap(file)
        if (bitmap == null) {
            onError(IllegalStateException("Failed to decode bitmap"))
            return
        }

        process(inputImage, bitmap, onSuccess, onError)
    }

    suspend fun maskFile(file: File): MaskResult = suspendCoroutine { continuation ->
        maskFile(
            file,
            onSuccess = { continuation.resume(it) },
            onError = { continuation.resumeWithException(it) }
        )
    }

    fun maskUri(
        uri: Uri,
        onSuccess: (MaskResult) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val inputImage = try {
            InputImage.fromFilePath(context, uri)
        } catch (ex: Exception) {
            onError(ex)
            return
        }

        val bitmap = BitmapIO.decodeUprightBitmap(context, uri)
        if (bitmap == null) {
            onError(IllegalStateException("Failed to decode bitmap"))
            return
        }

        process(inputImage, bitmap, onSuccess, onError)
    }

    suspend fun maskUri(uri: Uri): MaskResult = suspendCoroutine { continuation ->
        maskUri(
            uri,
            onSuccess = { continuation.resume(it) },
            onError = { continuation.resumeWithException(it) }
        )
    }

    override fun close() {
        if (options.detectFaces) {
            faceDetector.close()
        }
        if (options.detectText) {
            textRecognizer.close()
        }
        if (shutdownExecutor) {
            (executor as? ExecutorService)?.shutdown()
        }
    }

    private fun process(
        inputImage: InputImage,
        bitmap: android.graphics.Bitmap,
        onSuccess: (MaskResult) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (!options.detectFaces && !options.detectText) {
            executor.execute {
                onSuccess(MaskResult(bitmap, 0, 0, emptyList()))
            }
            return
        }

        val masker = BitmapMasker(
            MaskingConfig(
                textColor = options.textMaskColor,
                facePixelSize = options.facePixelSize
            )
        )

        if (options.detectFaces && options.detectText) {
            val faceTask = faceDetector.process(inputImage)
            val textTask = textRecognizer.process(inputImage)

            Tasks.whenAllSuccess<Any>(faceTask, textTask)
                .addOnSuccessListener(executor) { results ->
                    val faces = (results.getOrNull(0) as? List<*>)?.filterIsInstance<Face>().orEmpty()
                    val text = results.getOrNull(1) as? Text
                    if (text == null) {
                        onError(IllegalStateException("Text detection failed"))
                        return@addOnSuccessListener
                    }
                    val regions = buildRegions(faces, text)
                    val masked = masker.applyMasks(bitmap, regions, options.debugOutline)
                    onSuccess(MaskResult(masked, faces.size, text.textBlocks.size, regions))
                }
                .addOnFailureListener(executor) { error ->
                    onError(error)
                }
            return
        }

        if (options.detectFaces) {
            faceDetector.process(inputImage)
                .addOnSuccessListener(executor) { faces ->
                    val regions = faces.map { face ->
                        MaskRegion(face.boundingBox, MaskType.FACE)
                    }
                    val masked = masker.applyMasks(bitmap, regions, options.debugOutline)
                    onSuccess(MaskResult(masked, faces.size, 0, regions))
                }
                .addOnFailureListener(executor) { error ->
                    onError(error)
                }
            return
        }

        textRecognizer.process(inputImage)
            .addOnSuccessListener(executor) { text ->
                val regions = text.textBlocks.mapNotNull { block ->
                    block.boundingBox?.let { box -> MaskRegion(box, MaskType.TEXT) }
                }
                val masked = masker.applyMasks(bitmap, regions, options.debugOutline)
                onSuccess(MaskResult(masked, 0, text.textBlocks.size, regions))
            }
            .addOnFailureListener(executor) { error ->
                onError(error)
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
}

