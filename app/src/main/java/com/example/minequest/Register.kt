package com.example.minequest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.minequest.navigation.Screens

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
            Text("Create Account", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
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

                                        // Save the email and username in the Firebase DB
                                        val userData = mapOf(
                                            "username" to username,
                                            "email" to email
                                        )

                                        database.child(userId).setValue(userData)
                                            .addOnSuccessListener {
                                                loading = false
                                                // Go to the main screen
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { navController.navigate(Screens.Login.route) }) {
                Text("Already have an account? Log in")
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
