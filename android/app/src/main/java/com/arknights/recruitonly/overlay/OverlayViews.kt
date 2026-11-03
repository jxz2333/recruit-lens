package com.arknights.recruitonly.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.arknights.recruitonly.analysis.ComboResult

internal fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

internal object Palette {
    val CARD = 0xF2161A21.toInt()
    val CARD_STROKE = 0xFF2F3646.toInt()
    val TEXT = 0xFFE7EBF2.toInt()
    val MUTED = 0xFF8B94A7.toInt()
    val ACCENT = 0xFF2B6EF6.toInt()
    val DIVIDER = 0xFF262E3B.toInt()
    val HINT = 0xFF6C7488.toInt()
    val WARN = 0xFFE0A33E.toInt()
    val OK = 0xFF6FCF8B.toInt()

    fun rarity(r: Int): Int = when {
        r >= 6 -> 0xFFFF8C26.toInt()
        r == 5 -> 0xFFFFD23F.toInt()
        r == 4 -> 0xFFC084FC.toInt()
        r == 3 -> 0xFF5B9BF8.toInt()
        else -> 0xFF9AA3B2.toInt()
    }
}

internal fun Context.rounded(
    color: Int,
    radiusDp: Int,
    stroke: Int? = null,
    strokeDp: Int = 1,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(radiusDp).toFloat()
    setColor(color)
    if (stroke != null) setStroke(dp(strokeDp), stroke)
}

internal fun label(ctx: Context, s: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
    }

/** 悬浮球：一个可拖动的圆点，点一下触发识别。 */
internal fun buildBubble(ctx: Context): TextView =
    label(ctx, "招", 15f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        background = ctx.rounded(Palette.ACCENT, 24, 0x66FFFFFF)
        layoutParams = LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48))
    }

/**
 * 面板容器：整块都能拖。
 *
 * 普通 LinearLayout 的问题在于触摸点落在子 View（那些文字）上时，
 * 子 View 先拿到事件，父容器的 OnTouchListener 根本不会被调用——
 * 表现就是"拖文字拖不动，只有拖四周留白才行"。
 * 这里用 onInterceptTouchEvent 把标题栏以下的触摸全部截过来自己处理。
 */
internal class DragPanel(ctx: Context) : LinearLayout(ctx) {

    /** 这条线以上留给子 View（A- / A+ / × 按钮），以下由面板自己拖动。 */
    var interactiveTop: Int = 0
    var onMove: ((dx: Float, dy: Float) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = ev.y > interactiveTop

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX
                lastY = ev.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - lastX
                val dy = ev.rawY - lastY
                if (dx != 0f || dy != 0f) {
                    onMove?.invoke(dx, dy)
                    lastX = ev.rawX
                    lastY = ev.rawY
                }
            }
        }
        return true
    }
}

/** 结果面板：可拖动、可缩放、识图时会被临时隐藏。 */
internal class ResultPanel(ctx: Context) {

    private val context = ctx
    private val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val title = label(ctx, "公招推荐", 13f, Palette.MUTED, bold = true)

    val root: DragPanel = DragPanel(ctx)
    var onClose: (() -> Unit)? = null
    var onScaleChange: ((Float) -> Unit)? = null

    /** 当前字号倍率，1.0 为基准。 */
    var scale = 1.0f
        private set

    private var lastContent: ((LinearLayout) -> Unit)? = null

    init {
        root.orientation = LinearLayout.VERTICAL
        root.background = ctx.rounded(Palette.CARD, 14, Palette.CARD_STROKE)
        root.setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(12))
        root.interactiveTop = ctx.dp(34)
        root.addView(buildHeader(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = ctx.dp(6) })
    }

    // ---------------- 头部：缩放 + 关闭 ----------------

    private fun buildHeader(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(chip("A-") { setScale(scale - 0.1f) })
        addView(chip("A+") { setScale(scale + 0.1f) })
        addView(chip("×") { onClose?.invoke() })
    }

    private fun chip(text: String, onClick: () -> Unit): TextView =
        label(context, text, 12f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
            background = context.rounded(0x1AFFFFFF, 7)
            setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = context.dp(6) }
        }

    fun setScale(next: Float) {
        val clamped = next.coerceIn(0.8f, 1.6f)
        if (clamped == scale) return
        scale = clamped
        rebuild()
        onScaleChange?.invoke(scale)
    }

    private fun sp(v: Float): Float = v * scale

    private fun rebuild() {
        body.removeAllViews()
        lastContent?.invoke(body)
    }

    // ---------------- 内容 ----------------

    fun showMessage(msg: String, detail: String? = null) {
        title.text = "公招推荐"
        lastContent = { b ->
            b.addView(label(context, msg, sp(12.5f), Palette.MUTED))
            if (!detail.isNullOrBlank()) {
                b.addView(label(context, detail, sp(10.5f), Palette.HINT).apply {
                    setPadding(0, context.dp(6), 0, 0)
                })
            }
        }
        rebuild()
    }

    fun render(
        tags: List<String>,
        results: List<ComboResult>,
        onRecruitScreen: Boolean,
        unusable: List<String> = emptyList(),
    ) {
        title.text = if (tags.isEmpty()) "公招推荐" else "识别到 ${tags.size} 个词条"
        lastContent = { b -> fill(b, tags, results, onRecruitScreen, unusable) }
        rebuild()
    }

    private fun fill(
        b: LinearLayout,
        tags: List<String>,
        results: List<ComboResult>,
        onRecruitScreen: Boolean,
        unusable: List<String>,
    ) {
        if (tags.isNotEmpty()) {
            b.addView(label(context, tags.joinToString("  "), sp(12f), Palette.TEXT))
        }
        if (unusable.isNotEmpty()) {
            b.addView(
                label(
                    context,
                    "含「${unusable.joinToString("、")}」，该词条未计入，结果可能偏乐观",
                    sp(11f),
                    Palette.WARN,
                )
            )
        }
        if (!onRecruitScreen) {
            b.addView(label(context, "画面不像招募界面，结果仅供参考", sp(11f), Palette.WARN))
        }
        if (results.isEmpty()) {
            b.addView(label(context, "无匹配组合 → 随机 3★", sp(12.5f), Palette.MUTED))
            return
        }

        val best = results.first()
        val verdict = when {
            best.floorLevel >= 5 -> "可以上 · 保底 ${best.floorLevel}★"
            best.floorLevel == 4 -> "值得上 · 保底 4★"
            else -> "没有 4★ 保底，建议刷新词条"
        }
        b.addView(
            label(
                context, verdict, sp(12.5f),
                if (best.floorLevel >= 4) Palette.OK else Palette.WARN, bold = true,
            ).apply { setPadding(0, context.dp(6), 0, 0) }
        )
        b.addView(bestBlock(best))
        b.addView(divider())
        results.drop(1).take(5).forEach { b.addView(comboRow(it)) }
        if (results.size > 6) {
            b.addView(label(context, "另有 ${results.size - 6} 个组合", sp(11f), Palette.MUTED))
        }
        b.addView(
            label(context, "保底 = 这组词条至少能出的星级", sp(10.5f), Palette.HINT)
                .apply { setPadding(0, context.dp(8), 0, 0) }
        )
    }

    private fun bestBlock(r: ComboResult): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, context.dp(6), 0, context.dp(6))
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(label(context, "保底 ", sp(12f), Palette.MUTED).apply {
            setPadding(0, context.dp(6), 0, 0)
        })
        head.addView(label(context, "${r.floorLevel}★", sp(24f), Palette.rarity(r.floorLevel), bold = true))
        addView(head)
        addView(label(context, r.tags.joinToString(" · "), sp(12.5f), Palette.TEXT).apply {
            setPadding(0, context.dp(2), 0, 0)
        })
        addView(label(context, "最低可出：${opsLine(r)}", sp(12f), Palette.MUTED).apply {
            setPadding(0, context.dp(4), 0, 0)
        })
        addView(label(context, meta(r), sp(11f), Palette.HINT).apply {
            setPadding(0, context.dp(2), 0, 0)
        })
    }

    private fun comboRow(r: ComboResult): View {
        val sb = SpannableStringBuilder()
        val star = "${r.floorLevel}★"
        sb.append(star)
        sb.setSpan(
            ForegroundColorSpan(Palette.rarity(r.floorLevel)),
            0, star.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        sb.setSpan(RelativeSizeSpan(1.12f), 0, star.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  ").append(r.tags.joinToString("·"))
        sb.append("\n").append(opsLine(r))
        return label(context, sb, sp(11.5f), Palette.MUTED).apply {
            setPadding(0, context.dp(6), 0, context.dp(2))
        }
    }

    private fun opsLine(r: ComboResult): String {
        if (r.floorOps.isEmpty()) return "无匹配"
        if (r.isFloorUnique) return "${r.floorOps[0]}（锁定）"
        val head = r.floorOps.take(6).joinToString("、")
        return if (r.floorOps.size > 6) "$head 等 ${r.floorOps.size} 名" else head
    }

    private fun meta(r: ComboResult): String {
        val size = "候选 ${r.poolSize} 人"
        return when {
            r.needsShortTime -> "$size · 要把时间调短才会出"
            r.isSingleLevel -> "$size · 全是 ${r.floorLevel}★"
            else -> "$size · 运气好能出 ${r.ceilingLevel}★"
        }
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Palette.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { topMargin = context.dp(8); bottomMargin = context.dp(2) }
    }
}
