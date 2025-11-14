package com.example.minequest

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.minequest.ui.theme.MineQuestFont


@Composable
fun Ranking(navController: NavController, currentUser: FirebaseUser?) {
    val database = FirebaseDatabase.getInstance().getReference("users")

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

                topPlayers = tempList.sortedByDescending { it.second }.take(10)
            }
            .addOnFailureListener {
                topPlayers = emptyList()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MineQuestGreen)
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🏆 Ranking - Top 10 Players",
                fontFamily = MineQuestFont,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                topPlayers.forEachIndexed { index, (username, pontosXP, uid) ->
                    val isCurrentUser = currentUser?.uid == uid
                    RankingItem(
                        rank = index + 1,
                        username = username,
                        pontosXP = pontosXP,
                        isCurrentUser = isCurrentUser
                    )
                }
            }
        }
    }
}


// Definir cores customizadas (ajuste conforme necessário)
val MineQuestGreen = Color(0xFF388E3C) // Verde do fundo (ex: #6AA84F)
val ItemBackground = Color(0xFFFFFFFF) // Fundo dos itens (branco)
val XPTextColor = Color(0xFFFF9900)    // Cor do XP (ex: #FF9900)

@Composable
fun RankingItem(
    rank: Int,
    username: String,
    pontosXP: Int,
    isCurrentUser: Boolean
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
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

            if (rank < 10) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MineQuestGreen.copy(alpha = 0.5f))
                )
            }
        }
    }
}
