package com.example.p2p_system

import android.Manifest
import android.os.Handler
import android.os.Looper
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

        // Button for Model 1: Placing device interaction
        Button(
            onClick = {
                vibrationMessage = "Waiting for device placement..."
                Handler(Looper.getMainLooper()).postDelayed({
                    val success = triggerTransactionVibration(context, "user1", 100.0)
                    vibrationMessage = if (success) "Transaction vibration sent!" else "Failed to send vibration."
                }, 3000) // Simulate 3-second delay
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Place Device to Send Vibration")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button for Model 2: Immediate vibration
        Button(
            onClick = {
                val success = triggerTransactionVibration(context, "user1", 100.0)
                vibrationMessage = if (success) "Transaction vibration sent immediately!" else "Failed to send vibration."
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Send Vibration Immediately")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (vibrationMessage.isNotEmpty()) {
            Text(
                text = vibrationMessage,
                color = if (vibrationMessage.contains("sent")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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

@RequiresPermission(Manifest.permission.VIBRATE)
fun triggerTransactionVibration(context: android.content.Context, sender: String, amount: Double): Boolean {
    return try {
        // Create a vibration pattern based on the transaction details
        val vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 200) // Example pattern
        VibrationUtil.triggerVibrationPattern(context, vibrationPattern)
        true
    } catch (e: Exception) {
        false
    }
}