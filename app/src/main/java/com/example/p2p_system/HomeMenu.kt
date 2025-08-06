package com.example.p2p_system

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresPermission

@Composable
fun HomeMenu(
    username: String,
    onReturnToLogin: () -> Unit,
    onNavigateToTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var balance by remember { mutableStateOf(AuthService.getBalance(username)) }
    var isBalanceVisible by remember { mutableStateOf(false) }
    var showGraph by remember { mutableStateOf(false) }
    var vibrationMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

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

        Text(
            text = "Your balance: ${if (isBalanceVisible) "$balance HKD" else "***"}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { isBalanceVisible = !isBalanceVisible },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isBalanceVisible) "Hide Balance" else "Show Balance")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val success = triggerVibrationWithPermission(context)
                vibrationMessage = if (success) "Successfully used vibration!" else "Failed to use vibration."
                showGraph = success
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Use Vibration")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (vibrationMessage.isNotEmpty()) {
            Text(
                text = vibrationMessage,
                color = if (vibrationMessage.contains("Successfully")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showGraph) {
            VibrationGraph()
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

@RequiresPermission(Manifest.permission.VIBRATE)
fun triggerVibrationWithPermission(context: android.content.Context): Boolean {
    return try {
        VibrationUtil.triggerVibration(context)
        true
    }
    catch (e: Exception) {
        false
    }
}

@Composable
fun VibrationGraph() {
    Text(
        text = "Graph Output: Vibration Intensity Over Time",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp)
    )
}