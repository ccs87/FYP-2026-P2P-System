package com.example.p2p_system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeMenu(
    username: String,
    onReturnToLogin: () -> Unit,
    onNavigateToTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var balance by remember { mutableStateOf<Double?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome $username",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                balance = AuthService.getBalance(username)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Check Balance")
        }

        Spacer(modifier = Modifier.height(8.dp))

        balance?.let {
            Text(
                text = "Your balance: $it HKD",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToTransaction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Go to Transaction Menu")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onReturnToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Return to Login")
        }
    }
}