package com.example.minequest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.minequest.navigation.Screens
import com.example.minequest.ui.theme.MineQuestFont
import androidx.compose.ui.res.stringResource


@Composable
fun RegisterScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().getReference("users")

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(id = R.string.registo), style = MaterialTheme.typography.headlineMedium, fontFamily = MineQuestFont, color = Color(0xFF513220))
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(id = R.string.username), fontFamily = MineQuestFont,color = Color(0xFF513220)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RectangleShape)
                    .border(
                        width = 3.dp,
                        color = Color(0xFF513220),
                        shape = RectangleShape
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF513220),
                    focusedLabelColor = Color(0xFF513220),
                    unfocusedLabelColor = Color(0xFF513220)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(id = R.string.email), fontFamily = MineQuestFont, color = Color(0xFF513220)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RectangleShape)
                    .border(
                        width = 3.dp,
                        color = Color(0xFF513220),
                        shape = RectangleShape
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF513220),
                    focusedLabelColor = Color(0xFF513220),
                    unfocusedLabelColor = Color(0xFF513220)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(id = R.string.password), fontFamily = MineQuestFont, color = Color(0xFF513220)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RectangleShape)
                    .border(
                        width = 3.dp,
                        color = Color(0xFF513220),
                        shape = RectangleShape
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF513220),
                    focusedLabelColor = Color(0xFF513220),
                    unfocusedLabelColor = Color(0xFF513220)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(id = R.string.confirmar_passoword), fontFamily = MineQuestFont, color = Color(0xFF513220)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RectangleShape)
                    .border(
                        width = 3.dp,
                        color = Color(0xFF513220),
                        shape = RectangleShape
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFF513220),
                    focusedLabelColor = Color(0xFF513220),
                    unfocusedLabelColor = Color(0xFF513220)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    errorMessage = null

                    if (username.isBlank() || email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all fields."
                        return@Button
                    }

                    if (password != confirmPassword) {
                        errorMessage = "Passwords do not match."
                        return@Button
                    }

                    loading = true

                    // Check if username already exists
                    database.orderByChild("username").equalTo(username).get()
                        .addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                loading = false
                                errorMessage = "This username is already taken."
                            } else {
                                auth.createUserWithEmailAndPassword(email.trim(), password)
                                    .addOnSuccessListener { result ->
                                        val userId = result.user?.uid ?: return@addOnSuccessListener

                                        // Define a random image to the user profile
                                        val randomImageName = randomImage()

                                        val initialXP = 0

                                        val pickaxeIndex = 0

                                        // Save the user in the Firebase DB
                                        val userData = mapOf(
                                            "username" to username,
                                            "email" to email,
                                            "profileImage" to randomImageName,
                                            "pontosXP" to initialXP,
                                            "pickaxeIndex" to pickaxeIndex,
                                            // Inicializa o inventário vazio
                                            "inventory" to mapOf(
                                                "dirt" to 1
                                            )
                                        )

                                        database.child(userId).setValue(userData)
                                            .addOnSuccessListener {
                                                loading = false
                                                // Navegar para a tela principal
                                                navController.navigate(Screens.Map.route) {
                                                    popUpTo(Screens.Register.route) { inclusive = true }
                                                }
                                            }
                                            .addOnFailureListener {
                                                loading = false
                                                errorMessage = "Failed to save user data: ${it.message}"
                                            }
                                    }
                                    .addOnFailureListener {
                                        loading = false
                                        errorMessage = "Registration failed: ${it.message}"
                                    }
                            }
                        }
                        .addOnFailureListener {
                            loading = false
                            errorMessage = "Error checking username: ${it.message}"
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF513220),
                    contentColor = Color.White
                ),
                shape = RectangleShape,
            ) {
                Text(stringResource(id = R.string.registo), fontFamily = MineQuestFont)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { navController.navigate(Screens.Login.route) }) {
                Text("Already have an account? Log in", fontFamily = MineQuestFont, color = Color(0xFF513220))
            }

            if (loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun randomImage(): String {
    return when ((1..8).random()) {
        1 -> "fb9edad1e26f75"
        2 -> "4efed46e89c72955ddc7c77ad08b2ee"
        3 -> "578bfd439ef6ee41e103ae82b561986"
        4 -> "faf3182a063a0f2a825cb39d959bae7"
        5 -> "a9a4ec03fa9afc407028ca40c20ed774"
        6 -> "big_villager_face"
        7 -> "images"
        else -> "minecraft_creeper_face"
    }
}


