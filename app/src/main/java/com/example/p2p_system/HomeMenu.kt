package com.example.p2p_system

import android.content.Context
import android.hardware.SensorManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp




@Composable
fun HomeMenu(
    username: String,
    onReturnToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val vibrationDecoder = remember { VibrationDecoder(sensorManager) }
    var transactionStatus by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isBalanceVisible by remember { mutableStateOf(false) }
    var decodingStatus by remember { mutableStateOf("") }
    var accelerationData by remember { mutableStateOf(listOf<Float>()) }
    var isTransmitting by remember { mutableStateOf(false) }
    var transmittedPattern by remember { mutableStateOf<List<Float>?>(null) }
    var possibleCommands by remember { mutableStateOf<List<Char>>(emptyList()) }
    val balance = AuthService.getBalance(username) ?: 0.0
    val coroutineScope = rememberCoroutineScope()
    // Get the other user for transaction
    val otherUser = if (username == "test1") "test2" else "test1"
    var showDebugInfo by remember { mutableStateOf(false) }
    var decoderLogs by remember { mutableStateOf<List<String>>(emptyList()) }



    // Reset function to clean up all states
    fun resetAllStates(keepTimeoutMessage: Boolean = false) {
        isListening = false
        isTransmitting = false
        if (!keepTimeoutMessage) {
            transactionStatus = ""
        }
        decodingStatus = ""
        accelerationData = emptyList()
        transmittedPattern = null
        possibleCommands = emptyList()
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
            text = "Your balance: ${if (isBalanceVisible) "$${balance}" else "***"}",
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
// Payment buttons
        Text("Send Payment:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (!isListening && !isTransmitting) {
                        sendPayment(100, username, otherUser, context,
                            coroutineScope, ::resetAllStates,
                            { status -> transactionStatus = status },
                            { transmitting -> isTransmitting = transmitting },
                            { pattern -> transmittedPattern = pattern })
                    }
                },
                enabled = !isListening && !isTransmitting
            ) {
                Text("$100")
            }
            Button(
                onClick = {
                    if (!isListening && !isTransmitting) {
                        sendPayment(200, username, otherUser, context,
                            coroutineScope, ::resetAllStates,
                            { status -> transactionStatus = status },
                            { transmitting -> isTransmitting = transmitting },
                            { pattern -> transmittedPattern = pattern })
                    }
                },
                enabled = !isListening && !isTransmitting
            ) {
                Text("$200")
            }
            Button(
                onClick = {
                    if (!isListening && !isTransmitting) {
                        sendPayment(300, username, otherUser, context,
                            coroutineScope, ::resetAllStates,
                            { status -> transactionStatus = status },
                            { transmitting -> isTransmitting = transmitting },
                            { pattern -> transmittedPattern = pattern })
                    }
                },
                enabled = !isListening && !isTransmitting
            ) {
                Text("$300")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
// Listen for commands
        Button(
            onClick = {
                if (!isListening) {
                    resetAllStates()
                    isListening = true
                    transactionStatus = "Listening for commands..."
                    decodingStatus = "Waiting for transmission start..."
                    accelerationData = emptyList()

                    vibrationDecoder.startListening(
                        onDataReceived = { command ->
                            val amount = VibrationEncoder.getAmountForCommand(command) ?: return@startListening
                            AuthService.transfer(otherUser, username, amount.toDouble())
                            transactionStatus = "Transaction completed. Received $$amount from $otherUser"
                            resetAllStates()
                        },
                        onPossibleCommands = { cmds ->
                            possibleCommands = cmds
                        },
                        onAccelerationData = { data ->
                            accelerationData = accelerationData.takeLast(150) + data // Keep more data for graph
                        },
                        onTimeout = {
                            transactionStatus = "Listening timeout - no vibration pattern detected"
                            decodingStatus = "Please try again"
                            // Keep states for a moment to show message
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(3000)
                                resetAllStates()
                            }
                        },
                        onStatusUpdate = { status ->
                            decodingStatus = status
                        },
                        timeoutMs = 35000, // 35 second total timeout
                        forcedDecodeDelayMs = 25000 // Decode 25s after starting (5s buffer + 20s pattern)
                    )
                }
            },
            enabled = !isListening && !isTransmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isListening) "Listening... (Auto-decode in 25s)" else "Listen for Commands")
        }





        Spacer(modifier = Modifier.height(16.dp))
// Display transaction status
        if (transactionStatus.isNotEmpty()) {
            Text(
                text = transactionStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = if (transactionStatus.contains("timeout", ignoreCase = true)) Color.Red else Color.Unspecified
            )
        }



// Display decoding status
        if (decodingStatus.isNotEmpty() && isListening) {
            Text(
                text = decodingStatus,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
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
// Display vibration pattern graph when listening or transmitting
        if (isListening && accelerationData.isNotEmpty()) {
            Text(
                text = "Received Vibration Pattern",
                style = MaterialTheme.typography.bodySmall
            )
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
// Multiple commands choice dialog
    if (possibleCommands.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                possibleCommands = emptyList()
                resetAllStates()
                transactionStatus = "Transaction cancelled"
            },
            title = { Text("Multiple Possible Commands Detected") },
            text = {
                Column {
                    Text("Please select the correct command:")
                    possibleCommands.forEach { cmd ->
                        val amt = VibrationEncoder.getAmountForCommand(cmd) ?: 0
                        Button(
                            onClick = {
                                AuthService.transfer(otherUser, username, amt.toDouble())
                                transactionStatus = "Transaction completed. Received  amt from $otherUser"
                                possibleCommands = emptyList()
                                resetAllStates()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Command '$cmd' -  amt")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = {
                        possibleCommands = emptyList()
                        resetAllStates()
                        transactionStatus = "Transaction cancelled"
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Debug info card
    if (showDebugInfo) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("DEBUG INFO", style = MaterialTheme.typography.titleSmall)
                Text("Status: ${vibrationDecoder.getStatus()}")
                Text("Data points: ${accelerationData.size}")

                Button(
                    onClick = {
                        decoderLogs = vibrationDecoder.getLogs()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Logs")
                }

                Button(
                    onClick = {
                        vibrationDecoder.manualDecode()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Force Decode")
                }

                // Display recent logs
                LazyColumn(
                    modifier = Modifier.height(200.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(decoderLogs.takeLast(10)) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Toggle debug info
    Button(
        onClick = { showDebugInfo = !showDebugInfo },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (showDebugInfo) "Hide Debug Info" else "Show Debug Info")
    }

}



fun sendPayment(
    amount: Int,
    username: String,
    otherUser: String,
    context: Context,
    coroutineScope: CoroutineScope,
    resetAllStates: () -> Unit,
    setTransactionStatus: (String) -> Unit,
    setIsTransmitting: (Boolean) -> Unit,
    setTransmittedPattern: (List<Float>) -> Unit
) {
    val command = VibrationEncoder.getCommandForAmount(amount)
    if (command != null) {
        setIsTransmitting(true)
        setTransactionStatus("Preparing to send payment of $$amount...")

        coroutineScope.launch {
            try {
                delay(1000)
                setTransactionStatus("Sending payment of $$amount...")

                val pattern = VibrationEncoder.encodeCommand(command)
                setTransmittedPattern(generatePatternVisualization(pattern))

                VibrationController.vibrate(context, pattern)

                val vibrationDuration = pattern.sum()
                delay(vibrationDuration + 1000) // Wait for pattern + buffer

                // Deduct money after vibration is finished
                if (AuthService.transfer(username, otherUser, amount.toDouble())) {
                    setTransactionStatus("Transaction completed! Sent $$amount to $otherUser")
                } else {
                    setTransactionStatus("Transaction failed: Insufficient balance")
                }

            } catch (e: Exception) {
                setTransactionStatus("Error: ${e.message}")
            } finally {
                resetAllStates()
            }
        }
    } else {
        setTransactionStatus("Invalid amount: $$amount")
        resetAllStates()
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
// Generate visualization data for a vibration pattern
fun generatePatternVisualization(pattern: LongArray): List<Float> {
    val visualization = mutableListOf<Float>()

    for (i in pattern.indices) {
        val duration = pattern[i]
        val points = (duration / 50).coerceAtLeast(1).toInt()

        if (i % 2 == 0) {
            // Even indices: vibration or no vibration
            val value = if (duration > 0) 10f else 0f
            repeat(points) { visualization.add(value) }
        } else {
            // Odd indices: always pause
            repeat(points) { visualization.add(0f) }
        }
    }

    return visualization
}