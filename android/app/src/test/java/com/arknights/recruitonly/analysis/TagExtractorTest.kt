package com.arknights.recruitonly.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagExtractorTest {

    private val vocab = setOf(
        "高级资深干员", "资深干员", "新手",
        "近战位", "远程位",
        "先锋干员", "近卫干员", "重装干员", "狙击干员", "辅助干员",
        "输出", "生存", "防护", "治疗", "支援", "群攻", "减速",
        "女性干员", "男性干员",
    )

    private val extractor = TagExtractor(vocab)

    @Test
    fun `单个词条原样识别`() {
        assertEquals(listOf("输出"), extractor.segment("输出"))
    }

    @Test
    fun `最长匹配优先 不会把高级资深干员切成资深干员`() {
        assertEquals(listOf("高级资深干员"), extractor.segment("高级资深干员"))
        assertEquals(listOf("资深干员"), extractor.segment("资深干员"))
    }

    @Test
    fun `一行里挤了两个词条也能切开`() {
        assertEquals(listOf("输出", "近战位"), extractor.segment("输出 近战位"))
        assertEquals(listOf("近战位", "输出"), extractor.segment("近战位/输出"))
    }

    @Test
    fun `干被认成千时也能纠正`() {
        // 真机上 ML Kit 的实测错误：辅助干员 → 辅助千员
        assertEquals(listOf("辅助干员"), extractor.segment("辅助千员"))
        assertEquals(listOf("狙击干员"), extractor.segment("狙击千员"))
    }

    @Test
    fun `同长度差一个字的模糊匹配`() {
        assertEquals(listOf("辅助干员"), extractor.segment("辅优干员"))
    }

    @Test
    fun `模糊匹配有多个候选时宁可放弃`() {
        // 近卫干员 / 重装干员 都只差一个字，不能瞎猜
        assertTrue(extractor.segment("近装干员").isEmpty())
    }

    @Test
    fun `标点和数字被清掉`() {
        assertEquals(listOf("治疗", "支援"), extractor.segment("治疗、支援 3:50"))
    }

    @Test
    fun `非词条文本不会误判`() {
        assertTrue(extractor.segment("开始招募").isEmpty())
        assertTrue(extractor.segment("加急许可").isEmpty())
    }

    @Test
    fun `性别词条被忽略`() {
        assertTrue(extractor.segment("女性干员").isEmpty())
    }

    @Test
    fun `性别词条会被单独标记出来`() {
        val lines = listOf("输出 男性干员", "近战位")
        assertFalse(extractor.extract(lines).contains("男性干员"))
        assertEquals(listOf("男性干员"), extractor.detectUnusable(lines))
        assertTrue(extractor.extract(lines).containsAll(listOf("输出", "近战位")))
    }

    @Test
    fun `整屏抽取按出现顺序并去重`() {
        val lines = listOf(
            "公开招募",
            "输出 近战位",
            "输出 生存",
            "开始招募",
        )
        assertEquals(listOf("输出", "近战位", "生存"), extractor.extract(lines))
    }

    @Test
    fun `最多抽 5 个词条`() {
        val lines = listOf("输出", "生存", "近战位", "治疗", "支援", "防护", "群攻")
        assertEquals(5, extractor.extract(lines).size)
    }

    @Test
    fun `招募界面的锚点判定`() {
        assertTrue(RecruitAnchor.looksLikeRecruitScreen(listOf("公开招募", "开始招募")))
        assertFalse(RecruitAnchor.looksLikeRecruitScreen(listOf("作战", "代理指挥")))
    }
}
