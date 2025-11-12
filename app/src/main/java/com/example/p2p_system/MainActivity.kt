package com.example.p2p_system

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.p2p_system.ui.theme.P2PSystemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        setContent {
            P2PSystemTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

fun NavHostController.openHistory(username: String) = this.navigate("history/$username")
fun NavHostController.openHome(username: String) = this.navigate("home/$username")

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { username ->
                    navController.openHome(username)
                },
                onRegisterClick = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.popBackStack() // back to login
                },
                onBackToLogin = {
                    navController.popBackStack() // back to login
                }
            )
        }

        composable("home/{username}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: return@composable
            HomeMenu(
                username = username,
                onReturnToLogin = {
                    // return to login
                    navController.popBackStack(route = "login", inclusive = false)
                },
                onOpenHistory = {
                    navController.openHistory(username)
                }
            )
        }

        composable("history/{username}") { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: return@composable
            PaymentHistoryScreen(
                username = username,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
