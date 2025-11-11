package com.example.minequest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.minequest.navigation.Screens
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase


@Composable
fun Profile(navController: NavController, currentUser: FirebaseUser?, modifier: Modifier = Modifier) {
    val currentUserEmail = currentUser?.email


    // Access to the RealTime BD
    val auth = FirebaseAuth.getInstance()
    // Gets the users node
    val database = FirebaseDatabase.getInstance().getReference("users")

    var username by remember { mutableStateOf("User") }

    LaunchedEffect(auth.currentUser) {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            database.child(user.uid).get().addOnSuccessListener { snapshot ->
                username = snapshot.child("username").getValue(String::class.java) ?: "User"
            }.addOnFailureListener {
                username = "User"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome, $username!",
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    // Logout the user
                    FirebaseAuth.getInstance().signOut()
                    // Navigate back to login screen and clear backstack
                    navController.navigate(Screens.Login.route) {
                        popUpTo(0) // remove all previous screens
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Logout")
            }
        }
    }
}
