package com.arknights.recruitonly.analysis

import com.arknights.recruitonly.model.Operator

/**
 * 一个词条组合的计算结果。
 *
 * 公招的星级是「保底」逻辑：这组词条锁定的候选池是固定的，出货星级在池里按权重随机，
 * 高星概率极低。所以有价值的是池里的**最低**星级——保证至少几星；
 * 池里的最高星级只代表"运气好能出"。
 *
 * floorLevel   保底星级（池中最低星级）
 * floorOps     保底星级那几位干员（也就是最可能出的）
 * ceilingLevel 池中最高星级
 * avgLevel     池内平均星级，用来在同保底时排序
 * poolSize     候选人数
 */
data class ComboResult(
    val tags: List<String>,
    val floorLevel: Int,
    val floorOps: List<String>,
    val ceilingLevel: Int,
    val avgLevel: Double,
    val poolSize: Int,
) {
    val isSingleLevel: Boolean get() = floorLevel == ceilingLevel
    val isFloorUnique: Boolean get() = floorOps.size == 1

    /** 保底低于 3★：只有「支援机械」刷机器人时才会出现，且需要把时间调短。 */
    val needsShortTime: Boolean get() = floorLevel < 3
}

/**
 * 公招匹配内核：纯 Kotlin，无 Android 依赖，可直接单元测试。
 *
 * 规则对齐 MAA 的 `recruit_calc::get_all_combs`（src/MaaCore/Task/Miscellaneous/AutoRecruitTask.cpp）：
 *
 * 1. **最多只枚举 3 个词条的组合**。游戏里「职业需求（最多3项）」，勾 4、5 个在界面上根本做不到，
 *    枚举出来等于给不可执行的建议。
 * 2. **组合里没有「高级资深干员」时，把 6★ 从池里剔掉**。公招的 6★ 只能靠高资标签出，
 *    不剔会把上限算虚高（显示"运气好能出 6★"，其实永远出不来）。
 * 3. 排序：保底星级 ↓ → 上限 ↓ → 平均星级 ↓ → 词条数 ↑（MAA 同款优先级）。
 *
 * 4. **按 9:00:00 招募算**（与 MAA 一致）：1★/2★ 只有在很短的时间下才会出，
 *    所以保底 <3★ 时把它们从池里抬掉；整池都是 1★/2★ 时，只有「支援机械」的组合保留
 *    （那正是刷机器人的玩法），其余组合在 9 小时下什么都出不来，直接丢弃。
 */
object MatchEngine {

    const val MAX_TAGS = 5

    /** 游戏内一次最多勾 3 个词条。 */
    const val MAX_SELECT = 3

    private const val SENIOR_TAG = "高级资深干员"
    private const val ROBOT_TAG = "支援机械"

    fun solve(selected: List<String>, operators: List<Operator>): List<ComboResult> {
        val tags = selected.distinct().filter { it.isNotBlank() }.take(MAX_TAGS)
        if (tags.isEmpty() || operators.isEmpty()) return emptyList()

        val out = ArrayList<ComboResult>()
        val n = tags.size
        for (mask in 1 until (1 shl n)) {
            if (Integer.bitCount(mask) > MAX_SELECT) continue

            val subset = ArrayList<String>(MAX_SELECT)
            for (i in 0 until n) {
                if (mask and (1 shl i) != 0) subset.add(tags[i])
            }
            val hasSenior = SENIOR_TAG in subset

            var floor = Int.MAX_VALUE
            var ceiling = 0
            var sum = 0
            var poolSize = 0
            val floorOps = ArrayList<String>()

            var pool = operators.filter { op ->
                hasAll(op, subset) &&
                    // 没有高资标签，6★ 不可能出现
                    (hasSenior || op.rarity < 6)
            }
            if (pool.isEmpty()) continue

            // 9 小时招募下 1★/2★ 出不来，把它们从池子里抬掉
            if (pool.minOf { it.rarity } < 3) {
                val lifted = pool.filter { it.rarity >= 3 }
                pool = if (lifted.isNotEmpty()) {
                    lifted
                } else if (ROBOT_TAG in subset) {
                    pool          // 支援机械：整池就是 1★ 机器人，保留
                } else {
                    continue      // 纯 1★/2★ 且不是支援机械 → 9 小时下什么也不出
                }
            }

            for (op in pool) {
                poolSize++
                sum += op.rarity
                if (op.rarity > ceiling) ceiling = op.rarity
                when {
                    op.rarity < floor -> {
                        floor = op.rarity
                        floorOps.clear()
                        floorOps.add(op.name)
                    }
                    op.rarity == floor -> floorOps.add(op.name)
                }
            }
            if (poolSize == 0) continue

            out.add(
                ComboResult(
                    tags = subset,
                    floorLevel = floor,
                    floorOps = floorOps.sorted(),
                    ceilingLevel = ceiling,
                    avgLevel = sum.toDouble() / poolSize,
                    poolSize = poolSize,
                )
            )
        }

        out.sortWith(
            compareByDescending<ComboResult> { it.floorLevel }
                .thenByDescending { it.ceilingLevel }
                .thenByDescending { it.avgLevel }
                .thenBy { it.tags.size }
                .thenBy { it.tags.joinToString("") }
        )
        return out
    }

    fun best(selected: List<String>, operators: List<Operator>): ComboResult? =
        solve(selected, operators).firstOrNull()

    private fun hasAll(op: Operator, subset: List<String>): Boolean {
        for (t in subset) if (t !in op.tags) return false
        return true
    }
}
