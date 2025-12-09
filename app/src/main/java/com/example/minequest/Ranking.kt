package com.example.minequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase

const val topint = 7;

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

                topPlayers = tempList.sortedByDescending { it.second }.take(topint)
            }
            .addOnFailureListener {
                topPlayers = emptyList()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }
    }
}


val ItemBackground = Color(0xFFFFFFFF)
val XPTextColor = Color(0xFFFF9900)

@Composable
fun RankingItem(
    rank: Int,
    username: String,
    pontosXP: Int
) {

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
