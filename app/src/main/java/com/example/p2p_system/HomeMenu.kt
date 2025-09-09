// HomeMenu.kt
package com.example.p2p_system

import android.content.Context
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun HomeMenu(
    username: String,
    onReturnToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val vibrationDecoder = remember { VibrationDecoder(sensorManager) }
    var isTransactionDialogOpen by remember { mutableStateOf(false) }
    var transactionAmount by remember { mutableStateOf("") }
    var transactionStatus by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var isBalanceVisible by remember { mutableStateOf(false) }
    var receivedTransaction by remember { mutableStateOf<TransactionData?>(null) }
    var isConfirmDialogOpen by remember { mutableStateOf(false) }
    var accelerationData by remember { mutableStateOf(listOf<Float>()) }
    var isTransmitting by remember { mutableStateOf(false) }
    val balance = AuthService.getBalance(username) ?: 0.0
    val coroutineScope = rememberCoroutineScope()

    // Get the other user for transaction
    val otherUser = if (username == "test1") "test2" else "test1"

    // Reset function to clean up all states
    fun resetAllStates() {
        isSending = false
        isListening = false
        isTransmitting = false
        transactionStatus = ""
        accelerationData = emptyList()
        receivedTransaction = null
        vibrationDecoder.stopListening()
        VibrationController.cancelVibration(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome message
        Text(
            text = "Welcome, $username",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Display balance
        Text(
            text = "Your balance: ${if (isBalanceVisible) "$$balance" else "***"}",
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

        // Transaction button
        Button(
            onClick = { isTransactionDialogOpen = true },
            enabled = !isSending && !isListening && !isTransmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Transaction")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Listen for vibration
        Button(
            onClick = {
                if (!isListening) {
                    resetAllStates()
                    isListening = true
                    transactionStatus = "Listening for transactions..."
                    accelerationData = emptyList()

                    vibrationDecoder.startListening(
                        onDataReceived = { message ->
                            try {
                                val transaction = parseTransactionMessage(message)
                                receivedTransaction = transaction
                                isConfirmDialogOpen = true
                                transactionStatus = "Received transaction request from ${transaction.sender}"
                            } catch (e: Exception) {
                                transactionStatus = "Error parsing transaction: ${e.message}"
                                resetAllStates()
                            }
                        },
                        onAccelerationData = { data ->
                            accelerationData = accelerationData.takeLast(100) + data
                        },
                        onTimeout = {
                            transactionStatus = "Listening timeout - no transaction received"
                            resetAllStates()
                        }
                    )
                }
            },
            enabled = !isSending && !isListening && !isTransmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isListening) "Listening..." else "Listen for Transaction")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display transaction status
        if (transactionStatus.isNotEmpty()) {
            Text(transactionStatus, style = MaterialTheme.typography.bodyMedium)
        }

        // Stop transmission button (only show when transmitting)
        if (isTransmitting) {
            Button(
                onClick = {
                    resetAllStates()
                    transactionStatus = "Transaction cancelled"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Transmission")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Stop listening button (only show when listening)
        if (isListening) {
            Button(
                onClick = {
                    resetAllStates()
                    transactionStatus = "Listening stopped"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Listening")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Display vibration pattern graph when listening
        if (isListening && accelerationData.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vibration Pattern", style = MaterialTheme.typography.bodySmall)
            VibrationGraph(
                data = accelerationData,
                isTransmittedPattern = false,
                modifier = Modifier.height(100.dp).fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Return to Login
        Button(
            onClick = {
                resetAllStates()
                onReturnToLogin()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Return to Login")
        }
    }

    // Transaction dialog
    if (isTransactionDialogOpen) {
        AlertDialog(
            onDismissRequest = { isTransactionDialogOpen = false },
            title = { Text("Enter Transaction Amount") },
            text = {
                Column {
                    OutlinedTextField(
                        value = transactionAmount,
                        onValueChange = { newValue ->
                            // Allow only digits and limit to 3 characters
                            if (newValue.isEmpty() || (newValue.length <= 3 && newValue.all { it.isDigit() })) {
                                transactionAmount = newValue
                            }
                        },
                        label = { Text("Amount (100-999)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = transactionAmount.toIntOrNull()
                        if (amount != null && amount in 100..999) {
                            isTransactionDialogOpen = false
                            isSending = true
                            isTransmitting = true
                            transactionStatus = "Preparing to send transaction..."

                            coroutineScope.launch {
                                // Wait 2 seconds before starting vibration
                                delay(2000)

                                // Create transaction message
                                val message = "AMOUNT=$amount&SENDER=$username"
                                val pattern = VibrationEncoder.encodeMessage(message)

                                // Send vibration
                                VibrationController.vibrate(context, pattern)
                                transactionStatus = "Transaction sent. Waiting for confirmation..."

                                // Start listening for confirmation
                                vibrationDecoder.startListening(
                                    onDataReceived = { response ->
                                        if (response == "RECEIVED") {
                                            // Update balance - transfer to the other user
                                            if (AuthService.transfer(username, otherUser, amount.toDouble())) {
                                                transactionStatus = "Transaction completed successfully! Sent $$amount to $otherUser"
                                            } else {
                                                transactionStatus = "Transaction failed: Insufficient balance"
                                            }
                                            resetAllStates()
                                        }
                                    },
                                    onTimeout = {
                                        transactionStatus = "Confirmation timeout - transaction failed"
                                        resetAllStates()
                                    },
                                    timeoutMs = 90000
                                )
                            }
                        } else {
                            transactionStatus = "Please enter a valid amount between 100-999"
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                Button(onClick = { isTransactionDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Transaction confirmation dialog
    if (isConfirmDialogOpen && receivedTransaction != null) {
        AlertDialog(
            onDismissRequest = {
                resetAllStates()
                transactionStatus = "Transaction cancelled"
            },
            title = { Text("Transaction Request") },
            text = {
                Column {
                    Text("Receive $${receivedTransaction!!.amount} from ${receivedTransaction!!.sender}?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your balance will be: $${(balance + receivedTransaction!!.amount)}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Update balance - transfer from sender to current user
                        if (AuthService.transfer(receivedTransaction!!.sender, username, receivedTransaction!!.amount.toDouble())) {
                            transactionStatus = "Transaction confirmed. Received $${receivedTransaction!!.amount} from ${receivedTransaction!!.sender}"
                        } else {
                            transactionStatus = "Transaction failed: Sender has insufficient balance"
                        }

                        // Send confirmation back
                        coroutineScope.launch {
                            delay(2000) // Wait 2 seconds before sending confirmation
                            val pattern = VibrationEncoder.encodeMessage("RECEIVED")
                            VibrationController.vibrate(context, pattern)
                            resetAllStates()
                        }
                    }
                ) {
                    Text("Accept")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        resetAllStates()
                        transactionStatus = "Transaction rejected"
                    }
                ) {
                    Text("Reject")
                }
            }
        )
    }
}

@Composable
fun VibrationGraph(data: List<Float>, isTransmittedPattern: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxValue = data.maxOrNull() ?: 1f
        val minValue = data.minOrNull() ?: 0f
        val range = max(1f, maxValue - minValue)

        val stepX = size.width / (data.size - 1).coerceAtLeast(1)

        for (i in 1 until data.size) {
            val x1 = (i - 1) * stepX
            val y1 = size.height - ((data[i - 1] - minValue) / range * size.height)

            val x2 = i * stepX
            val y2 = size.height - ((data[i] - minValue) / range * size.height)

            drawLine(
                color = if (isTransmittedPattern) Color.Green else Color.Blue,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f
            )
        }

        // Draw a threshold line for vibration detection (only for received patterns)
        if (!isTransmittedPattern) {
            drawLine(
                color = Color.Red,
                start = Offset(0f, size.height - (2.0f - minValue) / range * size.height),
                end = Offset(size.width, size.height - (2.0f - minValue) / range * size.height),
                strokeWidth = 1f
            )
        }
    }
}

data class TransactionData(val amount: Int, val sender: String)

fun parseTransactionMessage(message: String): TransactionData {
    val parts = message.split("&")
    if (parts.size != 2) throw IllegalArgumentException("Invalid message format")

    val amountPart = parts[0]
    val senderPart = parts[1]

    if (!amountPart.startsWith("AMOUNT=")) throw IllegalArgumentException("Invalid amount format")
    if (!senderPart.startsWith("SENDER=")) throw IllegalArgumentException("Invalid sender format")

    val amount = amountPart.substring(7).toInt()
    val sender = senderPart.substring(7)

    return TransactionData(amount, sender)
}