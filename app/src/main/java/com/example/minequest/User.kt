package com.example.minequest

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class User(
    val username: String? = null,
    val email: String? = null, // <-- Adicionei o email que também guardas
    val pontosXP: Int? = 0,
    val profileImage: String? = null,
    val pickaxeIndex: Int? = 0  // <-- O NOME CORRETO! (em vez de pickaxeIndex) ya bacanz
)