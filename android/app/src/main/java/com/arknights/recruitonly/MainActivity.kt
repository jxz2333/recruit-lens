package com.arknights.recruitonly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arknights.recruitonly.data.RecruitRepository
import com.arknights.recruitonly.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var overlayState: TextView
    private lateinit var notificationState: TextView
    private lateinit var dataState: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                OverlayService.start(this, result.resultCode, data)
                // 直接把 App 藏到后台，把游戏让回前台
                moveTaskToBack(true)
            } else {
                toast("已取消截屏授权")
            }
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast("没有通知权限也能用，只是看不到前台服务通知")
            refreshState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    // ---------------- 界面 ----------------

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(36), dp(22), dp(36))
            setBackgroundColor(BG)
        }

        root.addView(title("公招透镜", 22f, TEXT, bold = true))
        root.addView(title("只读识别 · 不点击、不操作游戏", 12.5f, MUTED).apply {
            setPadding(0, dp(6), 0, dp(22))
        })

        overlayState = statusLine()
        notificationState = statusLine()
        dataState = statusLine()
        root.addView(overlayState)
        root.addView(notificationState)
        root.addView(dataState)

        root.addView(button("① 授予悬浮窗权限") { openOverlaySettings() })
        root.addView(button("② 授权截屏并开始") { startCapture() })
        root.addView(button("停止识别") { OverlayService.stop(this) })

        root.addView(title("怎么用", 13f, TEXT, bold = true).apply {
            setPadding(0, dp(26), 0, dp(8))
        })
        root.addView(
            title(
                "1. 先在这里点②，系统弹窗里选「整个屏幕」，然后允许。\n" +
                    "2. 回到游戏，进到公招界面。\n" +
                    "3. 点屏幕右侧的悬浮球，面板会列出这 5 个词条的所有组合，" +
                    "按保底星级从高到低排，第一名就是推荐。\n" +
                    "4. 再点一次悬浮球刷新；长按悬浮球收起面板。",
                12.5f, MUTED,
            )
        )
        root.addView(title("说明", 13f, TEXT, bold = true).apply {
            setPadding(0, dp(22), 0, dp(8))
        })
        root.addView(
            title(
                "· 授权只管本次会话：停掉服务或系统中断投屏后，要重新点②授权。\n" +
                    "· 识别时自己的悬浮窗会自动隐藏 100ms，避免把自己的面板读进去。\n" +
                    "· 词条靠 OCR + 词表约束抽取，不依赖坐标，换分辨率不用重新标定。",
                12f, MUTED,
            )
        )

        return ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(root)
        }
    }

    private fun statusLine() = title("", 12.5f, MUTED).apply {
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun refreshState() {
        val overlayOk = Settings.canDrawOverlays(this)
        overlayState.text = (if (overlayOk) "● 悬浮窗权限：已授予" else "○ 悬浮窗权限：未授予")
        overlayState.setTextColor(if (overlayOk) OK else WARN)

        val notifyOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        notificationState.text = if (notifyOk) "● 通知权限：已授予" else "○ 通知权限：未授予"
        notificationState.setTextColor(if (notifyOk) OK else WARN)

        val repo = RecruitRepository(this)
        dataState.text = "● 数据：${repo.dataVersion()}（PRTS 池 · 已交叉校验）"
        dataState.setTextColor(MUTED)
    }

    // ---------------- 行为 ----------------

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            toast("先给悬浮窗权限")
            openOverlaySettings()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- 小工具 ----------------

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
            .toInt()

    private fun title(s: CharSequence, sp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
        }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(TEXT)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(CARD)
            setStroke(dp(1), CARD_STROKE)
        }
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(18) }
    }

    companion object {
        private val BG = 0xFF0E1116.toInt()
        private val CARD = 0xFF1B2330.toInt()
        private val CARD_STROKE = 0xFF2E3949.toInt()
        private val TEXT = 0xFFE7EBF2.toInt()
        private val MUTED = 0xFF8B94A7.toInt()
        private val OK = 0xFF6FCF8B.toInt()
        private val WARN = 0xFFE0A33E.toInt()
    }
}
