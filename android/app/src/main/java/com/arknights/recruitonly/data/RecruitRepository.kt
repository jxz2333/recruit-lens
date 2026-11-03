package com.arknights.recruitonly.data

import android.content.Context
import com.arknights.recruitonly.model.Operator
import com.arknights.recruitonly.model.RecruitData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RecruitRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private var cache: RecruitData? = null

    fun data(): RecruitData {
        cache?.let { return it }
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return json.decodeFromString<RecruitData>(text).also { cache = it }
    }

    fun operators(): List<Operator> = data().operators

    /** OCR 词表。性别词条数据源里没有干员属性，先排除，避免算出空池。 */
    fun tagVocabulary(): Set<String> = data().tags.keys - SEX_TAGS

    fun dataVersion(): String =
        "${data().operators.size} 名干员 / ${data().tags.size} 词条"

    companion object {
        private const val ASSET_NAME = "recruitment.json"
        private val SEX_TAGS = setOf("女性干员", "男性干员")
    }
}
