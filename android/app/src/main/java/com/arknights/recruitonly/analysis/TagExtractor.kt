package com.arknights.recruitonly.analysis

/**
 * 把 OCR 出来的整行文本切成词条。
 *
 * 公招词条只有 31 种，OCR 常把相邻两个胶囊读成一行（"输出 近战位"），
 * 所以用"最长匹配 + 词表约束"做切分，比通用分词稳，也比正则好维护。
 *
 * 另外真机实测 ML Kit 会把「干」认成「千」（"辅助千员"），
 * 所以加了两层容错：常见的「千员」直接归一，以及同长度只差一个字的模糊匹配
 * （只在候选唯一时才认，避免瞎猜）。
 */
class TagExtractor(
    vocabulary: Collection<String>,
    private val unusable: Set<String> = setOf("女性干员", "男性干员"),
) {

    private val vocabAll: Set<String> = vocabulary.toSet()

    /** 能参与计算的词条。性别词条暂时没有干员属性数据，先剔除。 */
    private val vocab: Set<String> = vocabAll - unusable

    private val usableDict = Dict(vocab)
    private val unusableDict = Dict(unusable)

    /** 返回该行里能匹配到的词条，按出现顺序。 */
    fun segment(line: String): List<String> = segmentWith(line, usableDict)

    /**
     * 从整屏 OCR 结果里抽取词条。
     * 不依赖坐标：直接拿词表过一遍，非词条文本会被自然丢掉。
     */
    fun extract(lines: List<String>, maxTags: Int = 5): List<String> {
        val picked = LinkedHashSet<String>()
        for (line in lines) {
            for (t in segment(line)) {
                if (picked.size >= maxTags) return picked.toList()
                picked.add(t)
            }
        }
        return picked.toList()
    }

    /**
     * 画面上出现、但暂时算不了的词条（性别）。
     * 用于在结果里明确提示"这两个词条没计入"，而不是悄悄给出偏乐观的推荐。
     */
    fun detectUnusable(lines: List<String>): List<String> {
        val found = LinkedHashSet<String>()
        for (line in lines) found.addAll(segmentWith(line, unusableDict))
        return found.toList()
    }

    private fun segmentWith(line: String, dict: Dict): List<String> {
        val s = normalize(line)
        if (s.isEmpty() || dict.words.isEmpty()) return emptyList()
        val out = ArrayList<String>(2)
        var i = 0
        while (i < s.length) {
            var matched: String? = null
            var matchedLen = 0
            val maxTake = minOf(dict.maxLen, s.length - i)
            for (len in maxTake downTo 1) {
                val hit = dict.lookup(s, i, len)
                if (hit != null) {
                    matched = hit
                    matchedLen = len
                    break
                }
            }
            if (matched != null) {
                out.add(matched)
                i += matchedLen
            } else {
                i++
            }
        }
        return out
    }

    /** 只保留汉字，去掉 OCR 常见的空格、标点、数字，并归一最常见的错字。 */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) if (c in '\u4e00'..'\u9fff') sb.append(c)
        return sb.toString().replace("千员", "干员")
    }

    /** 按长度分桶的词表，支持"精确命中"和"同长度差一字"的容错。 */
    private class Dict(val words: Set<String>) {
        val maxLen: Int = words.maxOfOrNull { it.length } ?: 1
        private val byLen: Map<Int, List<String>> = words.groupBy { it.length }

        fun lookup(s: String, start: Int, len: Int): String? {
            if (start + len > s.length) return null
            val sub = s.substring(start, start + len)
            if (sub in words) return sub

            // 同长度、只差一个字符 → 认；有多个候选就放弃，宁可漏也不猜错
            val candidates = byLen[len] ?: return null
            var only: String? = null
            for (w in candidates) {
                if (!nearOne(sub, w)) continue
                if (only != null) return null
                only = w
            }
            return only
        }

        private fun nearOne(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) {
                if (a[i] != b[i]) {
                    diff++
                    if (diff > 1) return false
                }
            }
            return diff == 1
        }
    }
}

/** 判断当前画面是不是公招面板。 */
object RecruitAnchor {

    private val KEYWORDS = listOf(
        // 公招列表页
        "公开招募", "开始招募干员", "停止招募", "招募说明",
        // 单个招募位（词条页）
        "招募时限", "职业需求", "可获得的干员", "招募预算",
        // 通用
        "招聘合同", "加急许可",
    )

    fun looksLikeRecruitScreen(lines: List<String>): Boolean {
        for (raw in lines) {
            val line = raw.filter { it in '\u4e00'..'\u9fff' }.replace("千员", "干员")
            if (line.isEmpty()) continue
            for (k in KEYWORDS) if (line.contains(k)) return true
        }
        return false
    }
}
