package com.example.minequest.data

import com.example.minequest.model.DailyQuest
import com.example.minequest.model.GlobalDailyQuests
import com.example.minequest.model.QuestType
import com.example.minequest.model.UserQuestProgress
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import kotlin.math.min

class QuestRepository(private val userId: String) {

    private val globalQuestsRef: DatabaseReference = Firebase.database.getReference("daily_active_quests")
    private val availableQuestsRef: DatabaseReference = Firebase.database.getReference("available_quests")

    private val userProgressRootRef: DatabaseReference = Firebase.database.getReference("users").child(userId).child("quest_progress")

    // Verifica se o timestamp (data de atribuição) está dentro do dia atual.
    private fun isQuestAssignedToday(assignedDate: Long): Boolean {
        if (assignedDate == 0L) return false

        val assignedCal = Calendar.getInstance().apply { timeInMillis = assignedDate }
        val todayCal = Calendar.getInstance()

        return assignedCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                assignedCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }

    // Verifica o nó global e sorteia duas novas missões se o dia mudou
    suspend fun getOrCreateGlobalDailyQuests(): Map<String, DailyQuest> {

        val snapshot = globalQuestsRef.get().await()
        val globalQuests = snapshot.getValue<GlobalDailyQuests>() ?: GlobalDailyQuests()

        // Verifica se a Missão Global já foi sorteada hoje
        if (isQuestAssignedToday(globalQuests.assignedDate)) {
            // Se já foi sorteada, retorna as missões existentes.
            return globalQuests.activeQuests
        }

        // SE FOR O PRIMEIRO ACESSO DO DIA, realiza o sorteio
        val allQuests = fetchAllAvailableQuests()

        if (allQuests.size < 2) {
            throw IllegalStateException("O catálogo /available_quests/ deve ter pelo menos 2 missões para o sorteio diário.")
        }


        val questsByType = allQuests.groupBy { it.type }
        if (questsByType.size < 2) {
            throw IllegalStateException("É necessário pelo menos 2 tipos de missão diferentes.")
        }

        val selectedTypes = questsByType.keys.shuffled().take(2)

        // Para cada tipo, escolhe 1 missão aleatória
        val selectedQuests = selectedTypes.map { type ->
            questsByType[type]!!.random()
        }

        // Guarda a nova configuração global
        val newActiveQuestsMap = selectedQuests.associateBy { it.id }

        val newGlobalQuests = GlobalDailyQuests(
            assignedDate = System.currentTimeMillis(),
            activeQuests = newActiveQuestsMap
        )

        globalQuestsRef.setValue(newGlobalQuests).await()

        return newActiveQuestsMap
    }


    private suspend fun fetchAllAvailableQuests(): List<DailyQuest> {
        val snapshot = availableQuestsRef.get().await()
        return snapshot.children.mapNotNull { it.getValue<DailyQuest>() }
    }

    suspend fun getOrCreateIndividualProgress(
        quest: DailyQuest
    ): UserQuestProgress {

        val progressRef = userProgressRootRef.child(quest.id)
        val snapshot = progressRef.get().await()

        // Verifica-se se existe progresso para aquela missão naquele dia
        if (snapshot.exists()) {
            val existing = snapshot.getValue<UserQuestProgress>()
            if (existing != null && isQuestAssignedToday(existing.assignedDate)) {
                return existing
            }
        }

        // Caso não exita progresso para as missões do dia
        val newProgress = UserQuestProgress(
            questDetails = quest,
            currentProgress = 0,
            isCompleted = false,
            assignedDate = System.currentTimeMillis()
        )

        progressRef.setValue(newProgress).await()
        return newProgress
    }



}