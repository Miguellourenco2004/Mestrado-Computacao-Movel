package com.example.minequest.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DailyQuest(
    val id: String = "",
    val type: QuestType = QuestType.MINE_BLOCKS,
    val target: Int = 0,
    val reward: Int = 0,
    val description: String = "",
)