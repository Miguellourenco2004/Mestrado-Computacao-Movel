package com.example.minequest.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserQuestProgress(
    val questDetails: DailyQuest? = null,
    val currentProgress: Int = 0,
    val isCompleted: Boolean = false,
    val assignedDate: Long = 0L
)