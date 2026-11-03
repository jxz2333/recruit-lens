package com.arknights.recruitonly.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.util.Log
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.arknights.recruitonly.BuildConfig
import com.arknights.recruitonly.MainActivity
import com.arknights.recruitonly.R
import com.arknights.recruitonly.analysis.MatchEngine
import com.arknights.recruitonly.analysis.RecruitAnchor
import com.arknights.recruitonly.analysis.TagExtractor
import com.arknights.recruitonly.capture.ScreenCapturer
import com.arknights.recruitonly.data.RecruitRepository
import com.arknights.recruitonly.ocr.OcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * 前台服务：持有 MediaProjection、悬浮球与结果面板。
 *
 * Android 14+ 的硬性顺序：先 startForeground(type=mediaProjection)，再 getMediaProjection，
 * 并且在 createVirtualDisplay 之前必须注册 MediaProjection.Callback——顺序错了会直接抛安全异常。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: RecruitRepository
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturer by lazy { ScreenCapturer(this) }
    private val ocr by lazy { OcrEngine() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var projection: MediaProjection? = null
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: ResultPanel? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelVisible = false
    private var extractor: TagExtractor? = null
    private var busy = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // 用户在系统弹窗里点了停止，或被系统回收
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        repository = RecruitRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REANCHOR -> {
                // 通知栏里的「归位」，球被挤出屏幕时用来救场
                reanchorBubble()
                return START_STICKY
            }
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                startForegroundCompat()
                if (data == null || code != Activity.RESULT_OK) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                beginProjection(code, data)
                return START_NOT_STICKY
            }
            else -> {
                // 进程被杀后系统重启的实例拿不到投屏 token，起来也没用
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "识别服务", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val anchorIntent = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_REANCHOR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_notify)
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("公招透镜运行中")
            .setContentText("回到游戏，点悬浮球识别词条")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(icon, "归位", anchorIntent).build())
            .addAction(Notification.Action.Builder(icon, "停止", stopIntent).build())
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun beginProjection(resultCode: Int, data: Intent) {
        if (projection != null) return
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = manager.getMediaProjection(resultCode, data)
        if (p == null) {
            stopSelf()
            return
        }
        // 必须在 createVirtualDisplay 之前注册
        p.registerCallback(projectionCallback, mainHandler)
        projection = p
        // 整个会话只能建这一次虚拟屏，建不起来就说明 token 已作废，只能重新授权
        if (!capturer.attach(p)) {
            stopSelf()
            return
        }
        extractor = TagExtractor(repository.tagVocabulary())
        addBubble()
    }

    // ---------------- 悬浮球 ----------------

    private fun addBubble() {
        if (bubble != null) return
        val size = dp(50)
        val view = buildBubble(this)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 基准用「右边 + 垂直居中」，而不是左上角的绝对像素：
            // 转屏时系统会按新屏幕重新摆，不会像绝对坐标那样掉到屏幕外
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        bubbleParams = params
        placeBubble(params)
        attachTouch(view, params, onTap = { recognize() }, onLongPress = { hidePanel() })
        windowManager.addView(view, params)
        bubble = view
    }

    /** 贴右边、垂直中心偏上一点。x 是距右边缘的距离，y 是相对垂直中心的偏移。 */
    private fun placeBubble(params: WindowManager.LayoutParams) {
        val bounds = windowManager.currentWindowMetrics.bounds
        params.x = dp(6)
        params.y = -(bounds.height() * 0.15f).toInt()
    }

    /**
     * 转屏后重新贴边。
     *
     * 公招界面是横屏、启动时的桌面是竖屏：竖屏按 1080×2400 算出来的 y=1200，
     * 到横屏（高 1080）就掉到屏幕外面去了——球还在，但看不见也点不到。
     */
    private fun reanchorBubble() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        placeBubble(params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** 只保证球在屏幕内，不改用户拖到的位置。 */
    private fun clampBubbleIntoBounds() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val size = dp(50)
        // x：距右边缘的距离，0 表示贴右；上限是能挪到最左边
        val maxX = (bounds.width() - size).coerceAtLeast(0)
        // y：相对垂直中心的偏移，上下各留半个屏高减球半径
        val maxYOffset = ((bounds.height() - size) / 2).coerceAtLeast(0)
        val nx = params.x.coerceIn(0, maxX)
        val ny = params.y.coerceIn(-maxYOffset, maxYOffset)
        if (nx != params.x || ny != params.y) {
            params.x = nx
            params.y = ny
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 面板位置是按旧屏幕算的，直接收起，下次点悬浮球按新屏幕重建
        hidePanel()
        reanchorBubble()
    }

    private fun attachTouch(
        view: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit,
        onLongPress: () -> Unit,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var longPressed = false

        val longPressRunnable = Runnable {
            if (!moved) {
                longPressed = true
                onLongPress()
            }
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressed = false
                    mainHandler.postDelayed(longPressRunnable, 550)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    // x 是距右边的距离：手指往右拖，x 要变小
                    params.x = startX - dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressed) onTap()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    // ---------------- 面板 ----------------

    private fun showPanel() {
        val p = panel ?: ResultPanel(this).also { panel = it }
        if (panelVisible) return
        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(140)
        }
        panelParams = params
        // 面板也能拖：它经常盖住词条区，让人自己挪开
        attachTouch(p.root, params, onTap = {}, onLongPress = {})
        windowManager.addView(p.root, params)
        panelVisible = true
    }

    private fun hidePanel() {
        if (!panelVisible) return
        panel?.root?.let { runCatching { windowManager.removeView(it) } }
        panelParams = null
        panelVisible = false
    }

    // ---------------- 识别 ----------------

    private fun recognize() {
        if (busy) return
        if (projection == null) return
        val ex = extractor ?: return
        busy = true

        clampBubbleIntoBounds()
        showPanel()
        panel?.showMessage("识别中…")

        scope.launch {
            try {
                val bitmap = withOverlayHidden { capturer.grab() }
                if (bitmap == null) {
                    panel?.showMessage("截图失败，请再点一次悬浮球")
                    return@launch
                }
                // 切后台被系统冻结、或 ML Kit 回调迟迟不回来时，别让 busy 卡死
                val lines = try {
                    withTimeoutOrNull(OCR_TIMEOUT_MS) { ocr.recognize(bitmap) }
                        ?: run {
                            bitmap.recycle()
                            panel?.showMessage("识别超时了，再点一次悬浮球")
                            return@launch
                        }
                } catch (e: Exception) {
                    bitmap.recycle()
                    throw e
                }

                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "OCR ${lines.size} 行: ${lines.joinToString(" | ")}")
                    dumpForDebug(bitmap)
                }
                bitmap.recycle()
                val onRecruit = RecruitAnchor.looksLikeRecruitScreen(lines)
                val tags = ex.extract(lines)
                Log.i(TAG, "识别到词条: ${tags.joinToString("、")} | 性别词条: ${ex.detectUnusable(lines)}")
                if (tags.isEmpty()) {
                    val hint = lines.take(6).joinToString(" / ").ifBlank { "（OCR 没读到任何文字）" }
                    panel?.showMessage(
                        if (onRecruit) "看到招募界面了，但没读到词条" else "没找到招募面板，请停在公招界面再点一次",
                        "OCR 看到：$hint",
                    )
                    return@launch
                }
                panel?.render(
                    tags,
                    MatchEngine.solve(tags, repository.operators()),
                    onRecruit,
                    ex.detectUnusable(lines),
                )
            } catch (e: Exception) {
                panel?.showMessage("识别出错：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /** Debug 包把 OCR 的输入图存一份，方便 adb pull 出来看它到底看到了什么。 */
    private fun dumpForDebug(bitmap: android.graphics.Bitmap) {
        runCatching {
            java.io.File(cacheDir, "last_capture.png").outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /** 截图前把自己的悬浮窗藏掉，否则会把自己的面板读进 OCR 结果。 */
    private suspend fun <T> withOverlayHidden(block: suspend () -> T): T {
        bubble?.visibility = View.INVISIBLE
        val p = panel
        p?.root?.visibility = View.INVISIBLE
        delay(100)
        try {
            return block()
        } finally {
            bubble?.visibility = View.VISIBLE
            p?.root?.visibility = View.VISIBLE
        }
    }

    // ---------------- 生命周期 ----------------

    override fun onDestroy() {
        hidePanel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        bubbleParams = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        capturer.release()
        runCatching { ocr.close() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.arknights.recruitonly.START"
        const val ACTION_STOP = "com.arknights.recruitonly.STOP"
        const val ACTION_REANCHOR = "com.arknights.recruitonly.REANCHOR"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "recruit_lens"
        private const val TAG = "RecruitLens"
        private const val OCR_TIMEOUT_MS = 8000L
        private const val NOTIF_ID = 1001

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
