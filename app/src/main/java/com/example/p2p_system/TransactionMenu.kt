package com.example.p2p_system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TransactionMenu(
    username: String,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var transferAmount by remember { mutableStateOf("") }
    var transferTo by remember { mutableStateOf("") }
    var transferMessage by remember { mutableStateOf("") }
    val favorites = AuthService.getFavorites(username)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Transaction Menu", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Favorites:")
        favorites.forEach { favorite ->
            Button(
                onClick = { transferTo = favorite },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = favorite)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = transferTo,
            onValueChange = { transferTo = it },
            label = { Text("Transfer To (Username)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = transferAmount,
            onValueChange = { transferAmount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val error = ErrorHandler.validateTransfer(username, transferTo, transferAmount)
                transferMessage = error ?: "Transfer successful!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Transfer")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (transferMessage.isNotEmpty()) {
            Text(
                text = transferMessage,
                color = if (transferMessage == "Transfer successful!") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Back to Home")
        }
    }
}