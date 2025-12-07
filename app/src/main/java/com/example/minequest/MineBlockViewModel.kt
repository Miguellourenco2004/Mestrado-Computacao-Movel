package com.example.minequest

import androidx.lifecycle.ViewModel
import com.example.minequest.model.User
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

    // Variável privada que guarda o estado do inventário (Mapa de Nome do Bloco -> Quantidade)
    private val _inventory = MutableStateFlow<Map<String, Int>>(emptyMap())
    // Variável pública para ser lida pela UI
    val inventory: StateFlow<Map<String, Int>> = _inventory.asStateFlow()
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
            _inventory.value = emptyMap()
            return
        }

        dbRef.child(userId!!)
            .get()
            .addOnSuccessListener { dataSnapshot ->
                if (dataSnapshot.exists()) {
                    val user = dataSnapshot.getValue(User::class.java)

                    _pickaxeIndex.value = user?.pickaxeIndex ?: 0
                    _pontosXP.value = user?.pontosXP ?: 0
                    _inventory.value = user?.inventory ?: emptyMap()
                } else {
                    _pickaxeIndex.value = 0
                    _pontosXP.value = 0
                    _inventory.value = emptyMap()
                }
            }
            .addOnFailureListener {
                _pickaxeIndex.value = 0
                _pontosXP.value = 0
                _inventory.value = emptyMap()
            }
    }

    fun upgradePickaxe(custoDoUpgrade: Int, custoDoUpgradebloco: Map<String, Int>, maxIndex: Int) {
        if (userId == null) return

        val currentIndex = _pickaxeIndex.value
        val currentXP = _pontosXP.value
        val currentInventory = _inventory.value

        if (currentIndex >= maxIndex) return

        // Verifica se o inventário tem todos os blocos necessários
        val hasAllBlocks = custoDoUpgradebloco.all { (block, requiredAmount) ->
            currentInventory.getOrDefault(block, 0) >= requiredAmount
        }

        if (currentXP >= custoDoUpgrade && hasAllBlocks) {
            val newIndex = currentIndex + 1
            val newXP = currentXP - custoDoUpgrade

            // Remove os blocos usados do inventário
            val newInventory = currentInventory.toMutableMap()
            custoDoUpgradebloco.forEach { (block, amount) ->
                newInventory[block] = (newInventory[block] ?: 0) - amount
            }

            _pickaxeIndex.value = newIndex
            _pontosXP.value = newXP
            _inventory.value = newInventory

            val updates = mapOf<String, Any>(
                "pickaxeIndex" to newIndex,
                "pontosXP" to newXP,
                "inventory" to newInventory
            )

            dbRef.child(userId!!).updateChildren(updates)
                .addOnFailureListener {
                    _pickaxeIndex.value = currentIndex
                    _pontosXP.value = currentXP
                    _inventory.value = currentInventory
                    _errorMessage.value = "Erro de rede. Tenta novamente."
                }
        } else {
            var mensagem = ""
            if(currentXP < custoDoUpgrade && hasAllBlocks) { mensagem += "Não tens pontos de XP necessários!" }
            else if(currentXP >= custoDoUpgrade && !hasAllBlocks) { mensagem += "Não tens os blocos necessários!" }
            else {mensagem += "Não tens pontos de XP e os blocos necessários!"}
            _errorMessage.value = mensagem
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