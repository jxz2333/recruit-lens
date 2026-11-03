package com.arknights.recruitonly.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 中文 OCR。ML Kit 的中文模型随 APK 打包，离线可用，不依赖 Google Play 服务。
 *
 * 换成 PaddleOCR(ncnn) 也可以，但那样要带 .so + 模型，包体会大 10MB 以上；
 * 公招只需要认 31 个固定词条，ML Kit 够用。
 */
class OcrEngine {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognize(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val lines = ArrayList<String>()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        val t = line.text.trim()
                        if (t.isNotEmpty()) lines.add(t)
                    }
                }
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    fun close() {
        recognizer.close()
    }
}
