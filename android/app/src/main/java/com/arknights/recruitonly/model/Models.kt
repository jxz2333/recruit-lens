package com.arknights.recruitonly.model

import kotlinx.serialization.Serializable

@Serializable
data class Operator(
    val id: String = "",
    val name: String,
    val rarity: Int,
    val tags: List<String> = emptyList(),
)

/** 对应 MAA 的 resource/recruitment.json */
@Serializable
data class RecruitData(
    val operators: List<Operator> = emptyList(),
    val tags: Map<String, String> = emptyMap(),
)
