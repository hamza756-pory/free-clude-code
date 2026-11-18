package com.hamza.claudecode.mobile.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hamza.claudecode.mobile.ui.screens.ChatScreen
import com.hamza.claudecode.mobile.ui.screens.HistoryScreen
import com.hamza.claudecode.mobile.ui.screens.SettingsScreen
import com.hamza.claudecode.mobile.ui.screens.WelcomeScreen
import com.hamza.claudecode.mobile.ui.viewmodel.AppViewModelProvider
import com.hamza.claudecode.mobile.ui.viewmodel.ChatViewModel
import com.hamza.claudecode.mobile.ui.viewmodel.KeyViewModel

object Routes {
    const val WELCOME = "welcome"
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    // Activity-scoped view models shared by every screen.
    val chatViewModel: ChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val keyViewModel: KeyViewModel = viewModel(factory = AppViewModelProvider.Factory)

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                keyViewModel = keyViewModel,
                onAuthenticated = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(
                chatViewModel = chatViewModel,
                onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                keyViewModel = keyViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onKeyDeleted = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
