package com.example.minequest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.minequest.data.QuestRepository
import com.example.minequest.model.DailyQuest
import com.example.minequest.model.QuestCatalog
import com.example.minequest.model.UserQuestProgress
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlin.collections.forEach

const val topint = 7;

@Composable
fun Ranking(navController: NavController, currentUser: FirebaseUser?) {

    val auth = FirebaseAuth.getInstance()

    val database = FirebaseDatabase.getInstance().getReference("users")

    val userId = auth.currentUser?.uid ?: "GUEST_USER_ID"

    val questRepository = remember(userId) { QuestRepository(userId) }
    var questUiState by remember { mutableStateOf(DailyQuestUiState()) }

    // Add quests to the database
    uploadInitialQuestsToFirebase(auth)

    // Lista de topPlayers: username, XP, UID
    var topPlayers by remember { mutableStateOf<List<Triple<String, Int, String>>>(emptyList()) }

    LaunchedEffect(currentUser) {
        database.get()
            .addOnSuccessListener { snapshot ->
                val tempList = mutableListOf<Triple<String, Int, String>>()
                snapshot.children.forEach { userSnapshot ->
                    val username = userSnapshot.child("username").getValue(String::class.java) ?: "Unknown"
                    val pontosXP = userSnapshot.child("pontosXP").getValue(Int::class.java) ?: 0
                    val uid = userSnapshot.key ?: ""
                    tempList.add(Triple(username, pontosXP, uid))
                }

                topPlayers = tempList.sortedByDescending { it.second }.take(topint)
            }
            .addOnFailureListener {
                topPlayers = emptyList()
            }
    }

    var questReloadTrigger by remember { mutableIntStateOf(0) }


    // Missões globais e progresso individual
    LaunchedEffect(userId, questReloadTrigger) {
        questUiState = DailyQuestUiState(isLoading = true)
        questUiState = loadDailyQuestsState(questRepository)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
            Text(
                text = stringResource( id = R.string.top_message, topint),
                fontFamily = MineQuestFont,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                topPlayers.forEachIndexed { index, (username, pontosXP) ->
                    RankingItem(
                        rank = index + 1,
                        username = username,
                        pontosXP = pontosXP
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            DailyQuestsDisplay(
                quests = questUiState.quests,
                progressMap = questUiState.progress,
                isLoading = questUiState.isLoading,
                errorMessage = questUiState.error
            )
        }
    }
}


val ItemBackground = Color(0xFFFFFFFF)
val XPTextColor = Color(0xFFFF9900)

@Composable
fun RankingItem(rank: Int, username: String, pontosXP: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = ItemBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    Text(
                        text = "$rank. ",
                        fontFamily = MineQuestFont,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = username,
                        fontFamily = MineQuestFont,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Text(
                    text = "$pontosXP XP",
                    fontFamily = MineQuestFont,
                    color = XPTextColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (rank < topint) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

@Composable
fun DailyQuestsDisplay(
    quests: Map<String, DailyQuest>,
    progressMap: Map<String, UserQuestProgress>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.missons),
            fontFamily = MineQuestFont,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(10.dp))

        when {
            isLoading -> CircularProgressIndicator(Modifier.size(30.dp))
            errorMessage != null -> Text("Erro: $errorMessage", color = Color.Red, fontFamily = MineQuestFont)
            quests.isEmpty() -> Text(stringResource(R.string.no_active_missions), fontFamily = MineQuestFont)
            else -> quests.values.forEach { quest ->
                val progress = progressMap[quest.id] ?: UserQuestProgress()
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF8D8D8D))
                        .border(1.dp, Color.Black)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        // ALTERAÇÃO AQUI:
                        // O visto aparece se a missão estiver marcada como completa OU se o progresso já atingiu o alvo
                        checked = progress.isCompleted || progress.currentProgress >= quest.target,

                        onCheckedChange = {},
                        enabled = false,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF52A435),
                            checkmarkColor = Color.White
                        )
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(quest.description, fontFamily = MineQuestFont, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = (progress.currentProgress / quest.target.toFloat()).coerceIn(0f,1f),
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text("${progress.currentProgress} / ${quest.target}", fontSize = 12.sp)
                    }
                    Text("+${quest.reward} XP", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

suspend fun loadDailyQuestsState(
    questRepository: QuestRepository
): DailyQuestUiState {

    val loadedQuests = questRepository.getOrCreateGlobalDailyQuests()

    val progressMap = loadedQuests.mapValues { (_, quest) ->
        questRepository.getOrCreateIndividualProgress(quest)
    }

    return DailyQuestUiState(
        isLoading = false,
        quests = loadedQuests,
        progress = progressMap
    )
}



// Helper function to add the Quests to the database
fun uploadInitialQuestsToFirebase(auth: FirebaseAuth) {
    val currentUser = auth.currentUser
    if (currentUser == null) {
        return
    }

    val database = Firebase.database
    val questRef = database.getReference("available_quests")

    QuestCatalog.allQuests.forEach { quest ->
        questRef.child(quest.id).setValue(quest)
            .addOnSuccessListener {
                println("Missão ${quest.id} adicionada/atualizada com sucesso.")
            }
            .addOnFailureListener { e ->
                println("Erro ao adicionar missão ${quest.id}: ${e.message}")
            }
    }
}
