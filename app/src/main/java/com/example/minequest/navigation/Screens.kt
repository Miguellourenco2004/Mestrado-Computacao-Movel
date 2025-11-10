package com.example.minequest.navigation

sealed class Screens (val route: String) {
    object Map : Screens("map_screen")
    object Chat : Screens("chat_screen")
    object Profile : Screens("profile_screen")
    object Ranking : Screens("ranking_screen")
    object MineBlock : Screens("mineblock_screen")
    object Login : Screens("login_screen")
    object Register : Screens("register_screen")
}