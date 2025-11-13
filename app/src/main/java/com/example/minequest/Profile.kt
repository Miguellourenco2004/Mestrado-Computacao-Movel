package com.example.minequest

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.minequest.navigation.Screens
import com.example.minequest.ui.theme.MineQuestFont
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase

@Composable
fun Profile(
    navController: NavController,
    currentUser: FirebaseUser?,
    modifier: Modifier = Modifier
) {
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().getReference("users")

    var username by remember { mutableStateOf("User") }
    var profileImageName by remember { mutableStateOf("") }
    var pontosXP by remember { mutableStateOf(0) }

    // Carrega os dados do utilizador (nome + imagem)
    LaunchedEffect(auth.currentUser) {
        auth.currentUser?.let { user ->
            database.child(user.uid).get()
                .addOnSuccessListener { snapshot ->
                    username = snapshot.child("username").getValue(String::class.java) ?: "User"
                    profileImageName = snapshot.child("profileImage").getValue(String::class.java)
                        ?: "minecraft_creeper_face"
                    pontosXP = snapshot.child("pontosXP").getValue(Int::class.java) ?: 0
                }
                .addOnFailureListener {
                    username = "User"
                    pontosXP = 0
                }
        }
    }

    val imageRes = getImageResourceByName(profileImageName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Imagem do utilizador
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Profile image",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 5.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Nome do utilizador
            Text(
                text = "$username",
                fontFamily = MineQuestFont,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$pontosXP XP Points",
                fontFamily = MineQuestFont,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFF9800)
            )



            Spacer(modifier = Modifier.height(32.dp))

            // Botão de logout
            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screens.Login.route) {
                        popUpTo(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Logout", fontFamily = MineQuestFont)
            }
        }
    }
}

// Helper para mapear nomes -> drawable
fun getImageResourceByName(name: String): Int {
    return when (name) {
        "fb9edad1e26f75" -> R.drawable._fb9edad1e26f75
        "4efed46e89c72955ddc7c77ad08b2ee" -> R.drawable._4efed46e89c72955ddc7c77ad08b2ee
        "578bfd439ef6ee41e103ae82b561986" -> R.drawable._578bfd439ef6ee41e103ae82b561986
        "faf3182a063a0f2a825cb39d959bae7" -> R.drawable._faf3182a063a0f2a825cb39d959bae7
        "a9a4ec03fa9afc407028ca40c20ed774" -> R.drawable.a9a4ec03fa9afc407028ca40c20ed774
        "big_villager_face" -> R.drawable.big_villager_face
        "images" -> R.drawable.images
        else -> R.drawable.minecraft_creeper_face
    }
}
