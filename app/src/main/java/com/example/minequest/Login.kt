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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.example.minequest.navigation.Screens
import com.example.minequest.ui.theme.MineQuestFont
import androidx.compose.ui.res.stringResource


@Composable
fun LoginScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(id = R.string.login), style = MaterialTheme.typography.headlineMedium, fontFamily = MineQuestFont, color = Color(0xFF513220), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    handleLoginClick(
                        auth = auth,
                        email = email,
                        password = password,
                        setLoading = { loading = it },
                        setError = { errorMessage = it },
                        onSuccess = {
                            navController.navigate(Screens.Map.route) {
                                popUpTo(Screens.Login.route) { inclusive = true }
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF513220),
                    contentColor = Color.White
                ),
                shape = RectangleShape
            ) {
                Text(stringResource(id = R.string.password), fontFamily = MineQuestFont)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { navController.navigate(Screens.Register.route) }) {
                Text(stringResource(id = R.string.no_account), fontFamily = MineQuestFont, color = Color(0xFF513220))
            }

            if (loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            // If the message it's not null, it's displayed
            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun handleLoginClick(
    auth: FirebaseAuth,
    email: String,
    password: String,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    setLoading(true)
    setError(null)

    auth.signInWithEmailAndPassword(email.trim(), password)
        .addOnSuccessListener {
            setLoading(false)
            onSuccess()
        }
        .addOnFailureListener {
            setLoading(false)
            setError("Error logging in: ${it.message}")
        }
}

