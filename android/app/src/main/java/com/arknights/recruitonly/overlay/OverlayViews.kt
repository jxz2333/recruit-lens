package com.arknights.recruitonly.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
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
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = false
    }

/** 悬浮球：一个可拖动的圆点，点一下触发识别。 */
internal fun buildBubble(ctx: Context): TextView =
    label(ctx, "招", 15f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        background = ctx.rounded(Palette.ACCENT, 24, 0x66FFFFFF)
        layoutParams = LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48))
    }

/** 结果面板：只读，不出现在截图里（识别时会被临时隐藏）。 */
internal class ResultPanel(ctx: Context) {

    private val context = ctx
    private val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val title = label(ctx, "公招推荐", 13f, Palette.MUTED, bold = true)

    val root: View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = ctx.rounded(Palette.CARD, 14, Palette.CARD_STROKE)
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = ctx.dp(8) })
    }

    fun showMessage(msg: String, detail: String? = null) {
        title.text = "公招推荐"
        body.removeAllViews()
        body.addView(label(context, msg, 12.5f, Palette.MUTED))
        if (!detail.isNullOrBlank()) {
            body.addView(label(context, detail, 10.5f, 0xFF6C7488.toInt()).apply {
                setPadding(0, context.dp(6), 0, 0)
            })
        }
    }

    fun render(
        tags: List<String>,
        results: List<ComboResult>,
        onRecruitScreen: Boolean,
        unusable: List<String> = emptyList(),
    ) {
        body.removeAllViews()
        title.text = if (tags.isEmpty()) "公招推荐" else "识别到 ${tags.size} 个词条"
        if (tags.isNotEmpty()) {
            body.addView(label(context, tags.joinToString("  "), 12f, Palette.TEXT))
        }
        if (unusable.isNotEmpty()) {
            body.addView(
                label(
                    context,
                    "含「${unusable.joinToString("、")}」，该词条未计入，结果可能偏乐观",
                    11f,
                    Palette.WARN,
                )
            )
        }
        if (!onRecruitScreen) {
            body.addView(label(context, "画面不像招募界面，结果仅供参考", 11f, Palette.WARN))
        }
        if (results.isEmpty()) {
            body.addView(label(context, "无匹配组合 → 随机 3★", 12.5f, Palette.MUTED))
            return
        }

        val best = results.first()
        val verdict = when {
            best.floorLevel >= 5 -> "可以上 · 保底 ${best.floorLevel}★"
            best.floorLevel == 4 -> "值得上 · 保底 4★"
            else -> "没有 4★ 保底，建议刷新词条"
        }
        body.addView(label(context, verdict, 12.5f, if (best.floorLevel >= 4) Palette.OK else Palette.WARN, bold = true).apply {
            setPadding(0, context.dp(6), 0, 0)
        })
        body.addView(bestBlock(best))
        body.addView(divider())
        results.drop(1).take(5).forEach { body.addView(comboRow(it)) }
        if (results.size > 6) {
            body.addView(label(context, "另有 ${results.size - 6} 个组合", 11f, Palette.MUTED))
        }
        body.addView(label(context, "保底 = 这组词条至少能出的星级", 10.5f, Palette.HINT).apply {
            setPadding(0, context.dp(8), 0, 0)
        })
    }

    private fun bestBlock(r: ComboResult): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, context.dp(6), 0, context.dp(6))
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(label(context, "保底 ", 12f, Palette.MUTED).apply {
            setPadding(0, context.dp(6), 0, 0)
        })
        head.addView(label(context, "${r.floorLevel}★", 24f, Palette.rarity(r.floorLevel), bold = true))
        addView(head)
        addView(label(context, r.tags.joinToString(" · "), 12.5f, Palette.TEXT).apply {
            setPadding(0, context.dp(2), 0, 0)
        })
        addView(label(context, "最低可出：${opsLine(r)}", 12f, Palette.MUTED).apply {
            setPadding(0, context.dp(4), 0, 0)
        })
        addView(label(context, meta(r), 11f, Palette.HINT).apply {
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
        return label(context, sb, 11.5f, Palette.MUTED).apply {
            setPadding(0, context.dp(6), 0, context.dp(2))
        }
    }

    /** 候选人数 + 上限，用大白话说清楚，不用"池"这种行话。 */
    private fun meta(r: ComboResult): String {
        val size = "候选 ${r.poolSize} 人"
        return when {
            r.needsShortTime -> "$size · 要把时间调短才会出"
            r.isSingleLevel -> "$size · 全是 ${r.floorLevel}★"
            else -> "$size · 运气好能出 ${r.ceilingLevel}★"
        }
    }

    private fun opsLine(r: ComboResult): String {
        if (r.floorOps.isEmpty()) return "无匹配"
        if (r.isFloorUnique) return "${r.floorOps[0]}（锁定）"
        val head = r.floorOps.take(6).joinToString("、")
        return if (r.floorOps.size > 6) "$head 等 ${r.floorOps.size} 名" else head
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Palette.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { topMargin = context.dp(8); bottomMargin = context.dp(2) }
    }
}
