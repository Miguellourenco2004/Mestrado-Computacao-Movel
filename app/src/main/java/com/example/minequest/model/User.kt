package com.example.minequest.model

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class User(
    val username: String? = null,
    val email: String? = null,
    val pontosXP: Int? = 0,
    val profileImage: String? = null,
    val pickaxeIndex: Int? = 0,
    val inventory: Map<String, Int> = emptyMap(),
    val lat: Double? = null,
    val lng: Double? = null

)