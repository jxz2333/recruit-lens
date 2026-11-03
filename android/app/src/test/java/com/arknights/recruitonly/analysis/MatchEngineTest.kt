package com.arknights.recruitonly.analysis

import com.arknights.recruitonly.model.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchEngineTest {

    private val ops = listOf(
        Operator("1", "银灰", 6, listOf("输出", "支援", "近战位", "高级资深干员", "近卫干员")),
        Operator("2", "帕拉斯", 6, listOf("输出", "支援", "近战位", "高级资深干员", "近卫干员")),
        Operator("3", "临光", 5, listOf("防护", "治疗", "近战位", "资深干员", "重装干员")),
        Operator("4", "玫兰莎", 3, listOf("输出", "生存", "近战位", "近卫干员")),
        Operator("5", "克洛丝", 3, listOf("输出", "远程位", "狙击干员")),
    )

    @Test
    fun `空选择返回空列表`() {
        assertTrue(MatchEngine.solve(emptyList(), ops).isEmpty())
    }

    @Test
    fun `高级资深干员保底 6 星`() {
        val best = MatchEngine.best(listOf("高级资深干员"), ops)!!
        assertEquals(6, best.floorLevel)
        assertEquals(6, best.ceilingLevel)
        assertEquals(2, best.poolSize)
        assertEquals(listOf("帕拉斯", "银灰"), best.floorOps)
    }

    @Test
    fun `没有高资标签时六星不算进池子`() {
        // 银灰、帕拉斯是 6★，公招不勾高级资深干员永远出不来
        val best = MatchEngine.best(listOf("近战位"), ops)!!
        assertEquals(3, best.floorLevel)
        assertEquals(5, best.ceilingLevel)
        assertEquals(2, best.poolSize) // 只剩临光(5★) + 玫兰莎(3★)
        assertTrue(best.floorOps.none { it == "银灰" })
    }

    @Test
    fun `带高资时六星才回到池里`() {
        // 只勾「高级资深干员」时池子就是那两位 6★
        val senior = MatchEngine.best(listOf("高级资深干员"), ops)!!
        assertEquals(6, senior.ceilingLevel)
        assertEquals(2, senior.poolSize)
        // 勾上高资 + 近战位，池子仍然有 6★ 可选
        val both = MatchEngine.solve(listOf("高级资深干员", "近战位"), ops)
            .first { it.tags.containsAll(listOf("高级资深干员", "近战位")) }
        assertEquals(6, both.ceilingLevel)
        assertEquals(2, both.poolSize)
    }

    @Test
    fun `宽词条的保底是最低星级而不是最高`() {
        val best = MatchEngine.best(listOf("输出"), ops)!!
        assertEquals(3, best.floorLevel)
        assertEquals(3, best.ceilingLevel)
        assertEquals(listOf("克洛丝", "玫兰莎"), best.floorOps)
        assertEquals(2, best.poolSize)
    }

    @Test
    fun `窄词条把池子压到单星级`() {
        val best = MatchEngine.best(listOf("防护", "治疗", "重装干员"), ops)!!
        assertEquals(5, best.floorLevel)
        assertEquals(5, best.ceilingLevel)
        assertTrue(best.isSingleLevel)
        assertTrue(best.isFloorUnique)
        assertEquals("临光", best.floorOps.single())
    }

    @Test
    fun `推荐优先保底星级高的组合`() {
        val results = MatchEngine.solve(listOf("高级资深干员", "输出", "近战位"), ops)
        assertEquals(6, results.first().floorLevel)
        val floors = results.map { it.floorLevel }
        assertEquals(floors.sortedDescending(), floors)
    }

    @Test
    fun `最多只枚举 3 个词条的组合`() {
        // 游戏里「职业需求（最多3项）」，4、5 个词条的组合玩家根本选不了
        val five = listOf("输出", "生存", "近战位", "近卫干员", "支援")
        val results = MatchEngine.solve(five, ops)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.tags.size <= MatchEngine.MAX_SELECT })
        // C(5,1)+C(5,2)+C(5,3) = 25 是上限
        assertTrue(results.size <= 25)
    }

    @Test
    fun `无匹配的子集不会产生结果`() {
        val results = MatchEngine.solve(listOf("高级资深干员", "远程位"), ops)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.poolSize > 0 })
        assertTrue(results.none { it.tags.containsAll(listOf("高级资深干员", "远程位")) })
    }

    @Test
    fun `支援机械保留 1 星并提示调短时间`() {
        val withRobot = ops + Operator("7", "Castle-3", 1, listOf("支援机械", "支援", "近战位", "近卫干员"))
        val best = MatchEngine.best(listOf("支援机械"), withRobot)!!
        assertEquals(1, best.floorLevel)
        assertEquals(1, best.ceilingLevel)
        assertTrue(best.needsShortTime)
        assertEquals("Castle-3", best.floorOps.single())
    }

    @Test
    fun `九小时下新手这种纯二星组合直接丢弃`() {
        val withRookie = ops + Operator("6", "夜刀", 2, listOf("新手", "近战位", "先锋干员"))
        assertTrue(MatchEngine.solve(listOf("新手"), withRookie).isEmpty())
    }

    @Test
    fun `池里混进低星时按三星算保底`() {
        val withRobot = ops + Operator("7", "Castle-3", 1, listOf("支援机械", "支援", "近战位", "近卫干员"))
        val best = MatchEngine.best(listOf("近战位"), withRobot)!!
        assertEquals(3, best.floorLevel)                       // 1★ 被抬掉
        assertTrue(best.floorOps.none { it == "Castle-3" })
    }

    @Test
    fun `同保底星级时上限高的排前面`() {
        val results = MatchEngine.solve(listOf("高级资深干员", "输出", "近战位"), ops)
        val top = results.first().floorLevel
        val sameFloor = results.filter { it.floorLevel == top }
        val ceilings = sameFloor.map { it.ceilingLevel }
        assertEquals(ceilings.sortedDescending(), ceilings)
    }
}
