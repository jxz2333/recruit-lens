package com.arknights.recruitonly.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 截帧器。
 *
 * 关键约束：Android 14 起，一次投屏授权只允许 createVirtualDisplay 一次，
 * 再建一次会直接让系统停掉整个投屏（日志里是 "Reusing token: invalid projection"）。
 * 所以这里改成「启动时建一次，之后一直复用同一个 ImageReader 取最新帧」。
 *
 * 用正方形表面是刻意的：横屏游戏和竖屏桌面共用一个 VirtualDisplay，
 * 镜像内容会等比缩放居中、两边留黑边，不会拉伸变形，也不需要在转屏时重建。
 */
class ScreenCapturer(private val context: Context) {

    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var side = 0

    val isAttached: Boolean get() = display != null

    /** 只调一次。返回 false 表示这枚 token 已经不能用了，得让用户重新授权。 */
    fun attach(projection: MediaProjection): Boolean {
        if (display != null) return true
        val s = SIDE
        val r = ImageReader.newInstance(s, s, PixelFormat.RGBA_8888, 2)
        return try {
            display = projection.createVirtualDisplay(
                "recruit-lens",
                s, s,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface,
                null,
                Handler(Looper.getMainLooper()),
            )
            reader = r
            side = s
            true
        } catch (e: Exception) {
            runCatching { r.close() }
            reader = null
            display = null
            false
        }
    }

    /** 取当前画面。第一次调用可能拿到空帧，会重试到超时。 */
    suspend fun grab(): Bitmap? = withContext(Dispatchers.Default) {
        val r = reader ?: return@withContext null
        val deadline = SystemClock.uptimeMillis() + 1500L
        var image: Image? = null
        while (SystemClock.uptimeMillis() < deadline) {
            image = runCatching { r.acquireLatestImage() }.getOrNull()
            if (image != null) break
            delay(30)
        }
        val img = image ?: return@withContext null
        try {
            toBitmap(img, side, side)
        } finally {
            runCatching { img.close() }
        }
    }

    fun release() {
        runCatching { display?.release() }
        runCatching { reader?.close() }
        display = null
        reader = null
    }

    private fun toBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            padded
        } else {
            val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
            padded.recycle()
            cropped
        }
    }

    companion object {
        /** 正方形边长。1600 在横屏下仍能给出比原生 1080 更宽的画面，够 OCR 认词条。 */
        private const val SIDE = 1600
    }
}
