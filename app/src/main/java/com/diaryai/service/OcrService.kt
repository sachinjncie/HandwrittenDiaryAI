package com.diaryai.service

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val fullText: String,
    val confidence: Float,
    val blocks: List<OcrBlock>
)

data class OcrBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: android.graphics.Rect?
)

@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(imagePath: String): OcrResult = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromFilePath(context, android.net.Uri.fromFile(File(imagePath)))
        } catch (e: Exception) {
            cont.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        OcrBlock(
                            text = line.text,
                            confidence = line.confidence ?: 0.8f,
                            boundingBox = line.boundingBox
                        )
                    }
                }
                val avgConfidence = if (blocks.isEmpty()) 0f
                else blocks.map { it.confidence }.average().toFloat()

                cont.resume(OcrResult(
                    fullText = visionText.text,
                    confidence = avgConfidence,
                    blocks = blocks
                ))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun close() = recognizer.close()
}
