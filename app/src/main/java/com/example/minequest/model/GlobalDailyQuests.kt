package com.example.minequest.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class GlobalDailyQuests(
    val assignedDate: Long = 0L,
    val activeQuests: Map<String, DailyQuest> = emptyMap()
)