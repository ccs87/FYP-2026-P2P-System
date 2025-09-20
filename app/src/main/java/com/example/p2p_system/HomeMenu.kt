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
    var isSending by remember { mutableStateOf(false) }
    var isBalanceVisible by remember { mutableStateOf(false) }
    var decodingStatus by remember { mutableStateOf("") }
    var accelerationData by remember { mutableStateOf(listOf<Float>()) }
    var isTransmitting by remember { mutableStateOf(false) }
    var transmittedPattern by remember { mutableStateOf<List<Float>?>(null) }
    var currentCommand by remember { mutableStateOf<Char?>(null) }
    var isConfirmDialogOpen by remember { mutableStateOf(false) }
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
        decodingStatus = ""
        accelerationData = emptyList()
        transmittedPattern = null
        currentCommand = null
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

        // Payment buttons
        Text("Send Payment:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (!isSending && !isListening && !isTransmitting) {
                        sendPayment(100, username, otherUser, context, vibrationDecoder,
                            coroutineScope, ::resetAllStates,
                            { status -> transactionStatus = status },
                            { status -> decodingStatus = status },
                            { transmitting -> isTransmitting = transmitting },
                            { sending -> isSending = sending },
                            { pattern -> transmittedPattern = pattern })
                    }
                },
                enabled = !isSending && !isListening && !isTransmitting
            ) {
                Text("$100")
            }
            Button(
                onClick = {
                    if (!isSending && !isListening && !isTransmitting) {
                        sendPayment(200, username, otherUser, context, vibrationDecoder,
                            coroutineScope, ::resetAllStates,
                            { status -> transactionStatus = status },
                            { status -> decodingStatus = status },
                            { transmitting -> isTransmitting = transmitting },
                            { sending -> isSending = sending },
                            { pattern -> transmittedPattern = pattern })
                    }
                },
                enabled = !isSending && !isListening && !isTransmitting
            ) {
                Text("$200")
            }
            Button(
                onClick = {
                    if (!isSending && !isListening && !isTransmitting) {
                        sendPayment(300, username, otherUser, context, vibrationDecoder,
                            coroutineScope, ::resetAllStates,
                            { status -> transactionStatus = status },
                            { status -> decodingStatus = status },
                            { transmitting -> isTransmitting = transmitting },
                            { sending -> isSending = sending },
                            { pattern -> transmittedPattern = pattern })
                    }
                },
                enabled = !isSending && !isListening && !isTransmitting
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
                    decodingStatus = "Waiting for vibration..."
                    accelerationData = emptyList()

                    vibrationDecoder.startListening(
                        onDataReceived = { command ->
                            handleReceivedCommand(command, username, otherUser, context,
                                vibrationDecoder, coroutineScope,
                                ::resetAllStates,
                                { status -> transactionStatus = status },
                                { status -> decodingStatus = status },
                                { command -> currentCommand = command },
                                { open -> isConfirmDialogOpen = open },
                                { transmitting -> isTransmitting = transmitting },
                                { pattern -> transmittedPattern = pattern })
                        },
                        onAccelerationData = { data ->
                            accelerationData = accelerationData.takeLast(100) + data
                        },
                        onTimeout = {
                            // Show last decoded command if available
                            if (vibrationDecoder.lastDecodedCommand != null) {
                                transactionStatus = "Timeout. Last decoded command: '${vibrationDecoder.lastDecodedCommand}'"
                                decodingStatus = "Timeout reached. Sending retry request..."

                                // Send retry vibration ('r') back to sender
                                coroutineScope.launch {
                                    val pattern = VibrationEncoder.encodeCommand('r')
                                    transmittedPattern = generatePatternVisualization(pattern)
                                    VibrationController.vibrate(context, pattern)
                                    delay(2000)
                                    resetAllStates()
                                }
                            } else {
                                transactionStatus = "Listening timeout - no command received"
                                decodingStatus = "Timeout reached"
                                resetAllStates()
                            }
                        },
                        onStatusUpdate = { status ->
                            decodingStatus = status
                        }
                    )
                }
            },
            enabled = !isSending && !isListening && !isTransmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isListening) "Listening..." else "Listen for Commands")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display transaction status
        if (transactionStatus.isNotEmpty()) {
            Text(transactionStatus, style = MaterialTheme.typography.bodyMedium)
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
        if ((isListening && accelerationData.isNotEmpty()) || (isTransmitting && transmittedPattern != null)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isListening) "Received Vibration Pattern" else "Transmitted Vibration Pattern",
                style = MaterialTheme.typography.bodySmall
            )
            VibrationGraph(
                data = if (isListening) accelerationData else transmittedPattern!!,
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

    // Transaction confirmation dialog
    if (isConfirmDialogOpen && currentCommand != null) {
        val amount = VibrationEncoder.getAmountForCommand(currentCommand!!)
        if (amount != null && amount > 0) {
            AlertDialog(
                onDismissRequest = {
                    resetAllStates()
                    transactionStatus = "Transaction cancelled"
                },
                title = { Text("Transaction Request") },
                text = {
                    Column {
                        Text("Receive $$amount from $otherUser?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your balance will be: $${(balance + amount)}")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Update balance - transfer from sender to current user
                            if (AuthService.transfer(otherUser, username, amount.toDouble())) {
                                transactionStatus = "Transaction confirmed. Received $$amount from $otherUser"

                                // Send confirmation back
                                coroutineScope.launch {
                                    delay(2000) // Wait 2 seconds before sending confirmation
                                    val pattern = VibrationEncoder.encodeCommand('o')
                                    transmittedPattern = generatePatternVisualization(pattern)
                                    VibrationController.vibrate(context, pattern)
                                    resetAllStates()
                                }
                            }
                            else {
                                transactionStatus = "Transaction failed: Sender has insufficient balance"
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
                            // Send retry request
                            coroutineScope.launch {
                                delay(1000) // Wait 1 second before sending retry request
                                val pattern = VibrationEncoder.encodeCommand('r')
                                transmittedPattern = generatePatternVisualization(pattern)
                                VibrationController.vibrate(context, pattern)
                                resetAllStates()
                                transactionStatus = "Transaction rejected"
                            }
                        }
                    ) {
                        Text("Reject")
                    }
                }
            )
        }
    }
}

// Function to send a payment
fun sendPayment(
    amount: Int,
    username: String,
    otherUser: String,
    context: Context,
    vibrationDecoder: VibrationDecoder,
    coroutineScope: CoroutineScope,
    resetAllStates: () -> Unit,
    setTransactionStatus: (String) -> Unit,
    setDecodingStatus: (String) -> Unit,
    setIsTransmitting: (Boolean) -> Unit,
    setIsSending: (Boolean) -> Unit,
    setTransmittedPattern: (List<Float>) -> Unit
) {
    val command = VibrationEncoder.getCommandForAmount(amount)
    if (command != null) {
        setIsSending(true)
        setIsTransmitting(true)
        setTransactionStatus("Preparing to send payment...")

        coroutineScope.launch {
            // Wait 2 seconds before starting vibration
            delay(2000)

            // Send payment command
            val pattern = VibrationEncoder.encodeCommand(command)
            setTransmittedPattern(generatePatternVisualization(pattern))
            VibrationController.vibrate(context, pattern)
            setTransactionStatus("Payment sent. Waiting for confirmation...")

            // Start listening for confirmation
            vibrationDecoder.startListening(
                onDataReceived = { response ->
                    when (response) {
                        'o' -> {
                            // Confirmation received
                            if (AuthService.transfer(username, otherUser, amount.toDouble())) {
                                setTransactionStatus("Payment confirmed! Sent $$amount to $otherUser")
                            } else {
                                setTransactionStatus("Payment failed: Insufficient balance")
                            }
                            resetAllStates()
                        }
                        'r' -> {
                            // Retry request
                            setTransactionStatus("Receiver requested retry. Resending payment...")
                            // Resend the payment
                            coroutineScope.launch {
                                delay(2000)
                                VibrationController.vibrate(context, pattern)
                                setTransactionStatus("Payment resent. Waiting for confirmation...")
                            }
                        }
                        else -> {
                            setTransactionStatus("Invalid response received: $response")
                            resetAllStates()
                        }
                    }
                },
                onAccelerationData = { /* Not needed for confirmation */ },
                onTimeout = {
                    setTransactionStatus("No response received. Payment may not have been completed.")
                    resetAllStates()
                },
                onStatusUpdate = { status ->
                    setDecodingStatus(status)
                }
            )
        }
    } else {
        setTransactionStatus("Invalid amount: $$amount")
        resetAllStates()
    }
}

// Function to handle received commands
fun handleReceivedCommand(
    command: Char,
    username: String,
    otherUser: String,
    context: Context,
    vibrationDecoder: VibrationDecoder,
    coroutineScope: CoroutineScope,
    resetAllStates: () -> Unit,
    setTransactionStatus: (String) -> Unit,
    setDecodingStatus: (String) -> Unit,
    setCurrentCommand: (Char) -> Unit,
    setIsConfirmDialogOpen: (Boolean) -> Unit,
    setIsTransmitting: (Boolean) -> Unit,
    setTransmittedPattern: (List<Float>) -> Unit
) {
    when (command) {
        'a', 'b', 'c' -> {
            // Payment command received
            val amount = VibrationEncoder.getAmountForCommand(command)
            if (amount != null) {
                setCurrentCommand(command)
                setIsConfirmDialogOpen(true)
                setTransactionStatus("Received payment request: $$amount from $otherUser")
            } else {
                setTransactionStatus("Invalid payment command received: $command")
                resetAllStates()
            }
        }
        'o' -> {
            // Confirmation received
            setTransactionStatus("Payment confirmed by receiver")
            resetAllStates()
        }
        'r' -> {
            // Retry request received
            setTransactionStatus("Receiver requested retry")
            resetAllStates()
        }
        else -> {
            setTransactionStatus("Unknown command received: $command")
            resetAllStates()
        }
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
    var isVibrating = false

    for (duration in pattern) {
        if (isVibrating) {
            // Vibration phase - add high values
            val points = (duration / 50).coerceAtLeast(1)
            repeat(points.toInt()) {
                visualization.add(10f)
            }
        } else {
            // Pause phase - add low values
            val points = (duration / 50).coerceAtLeast(1)
            repeat(points.toInt()) {
                visualization.add(0f)
            }
        }
        isVibrating = !isVibrating
    }

    return visualization
}