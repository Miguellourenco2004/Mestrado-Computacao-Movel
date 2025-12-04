package com.example.minequest

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MineQuestViewModel : ViewModel() {

    private val dbRef = Firebase.database.getReference("users")
    private val auth = Firebase.auth
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var userId: String? = null

    private val _pickaxeIndex = MutableStateFlow(0)
    val pickaxeIndex: StateFlow<Int> = _pickaxeIndex.asStateFlow()
    private val _pontosXP = MutableStateFlow(0)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                userId = user.uid
                loadUserProgress()
            } else {
                userId = null
                _pickaxeIndex.value = 0
                _pontosXP.value = 0
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    private fun loadUserProgress() {
        if (userId == null) {
            _pickaxeIndex.value = 0
            _pontosXP.value = 0
            return
        }

        dbRef.child(userId!!)
            .get()
            .addOnSuccessListener { dataSnapshot ->
                if (dataSnapshot.exists()) {
                    val user = dataSnapshot.getValue(User::class.java)

                    _pickaxeIndex.value = user?.pickaxeIndex ?: 0
                    _pontosXP.value = user?.pontosXP ?: 0
                } else {
                    _pickaxeIndex.value = 0
                    _pontosXP.value = 0
                }
            }
            .addOnFailureListener {
                _pickaxeIndex.value = 0
                _pontosXP.value = 0
            }
    }

    fun upgradePickaxe(custoDoUpgrade: Int, custoDoUpgradebloco: Map<String, Int>, maxIndex: Int) {
        if (userId == null) return

        val currentIndex = _pickaxeIndex.value
        val currentXP = _pontosXP.value

        if (currentIndex >= maxIndex) return

        if (currentXP >= custoDoUpgrade) {
            val newIndex = currentIndex + 1
            val newXP = currentXP - custoDoUpgrade

            _pickaxeIndex.value = newIndex
            _pontosXP.value = newXP

            val updates = mapOf<String, Any>(
                "pickaxeIndex" to newIndex,
                "pontosXP" to newXP
            )

            dbRef.child(userId!!).updateChildren(updates)
                .addOnFailureListener {
                    _pickaxeIndex.value = currentIndex
                    _pontosXP.value = currentXP
                    _errorMessage.value = "Erro de rede. Tenta novamente."
                }
        } else {
            _errorMessage.value = "Não tens pontos de XP necessários!"
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        authListener?.let {
            auth.removeAuthStateListener(it)
        }
    }
}