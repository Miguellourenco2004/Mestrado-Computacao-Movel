package com.example.minequest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minequest.ui.theme.MineQuestTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.size

// Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.minequest.navigation.NavGraph
import com.example.minequest.navigation.Screens

import androidx.navigation.compose.currentBackStackEntryAsState


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MineQuestTheme {
                MineQuestApp()
            }
        }
    }
}

@Composable
fun MineQuestApp() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "MineQuest",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        bottomBar = {
            MineQuestBottomBar(navController = navController)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            NavGraph(navController = navController)
        }
    }
}



@Composable
fun MineQuestBottomBar(navController: NavController) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        unselectedIconColor = Color.LightGray,
        indicatorColor = Color(0xFF6B3B25)
    )



    BottomAppBar(
        containerColor = Color(0xFF513220)
    ) {

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Map",
                    tint = Color.White,
                    modifier = Modifier.size(45.dp)
                )
            },
            selected = currentRoute == Screens.Map.route,
            onClick = { navController.navigate(Screens.Map.route) },
                    colors = navItemColors
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Message,
                    contentDescription = "Chat",
                    tint = Color.White,
                    modifier = Modifier.size(45.dp)
                )
            },
            selected = currentRoute == Screens.Chat.route,
            onClick = { navController.navigate(Screens.Chat.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.diamond_pickaxe),
                    contentDescription = "Mineblock",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(50.dp)
                )
            },
            selected = currentRoute == Screens.MineBlock.route,
            onClick = { navController.navigate(Screens.MineBlock.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Leaderboard,
                    contentDescription = "Ranking",
                    tint = Color.White,
                    modifier = Modifier.size(45.dp)
                )
            },
            selected = currentRoute == Screens.Ranking.route,
            onClick = { navController.navigate(Screens.Ranking.route) },
            colors = navItemColors
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(45.dp)
                )
            },
            selected = currentRoute == Screens.Profile.route,
            onClick = { navController.navigate(Screens.Profile.route) },
                    colors = navItemColors
        )


    }
}

@Preview(showBackground = true)
@Composable
fun MineQuestAppPreview() {
    MineQuestTheme {
        MineQuestApp()
    }
}