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

private enum class TxAction { SEND, RECEIVE }

@Composable
fun HomeMenu(
    username: String,
    onReturnToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val vibrationDecoder = remember { VibrationDecoder(sensorManager) }

    var transactionStatus by remember { mutableStateOf("") }
    var decodingStatus by remember { mutableStateOf("") }

    var isListening by remember { mutableStateOf(false) }
    var isTransmitting by remember { mutableStateOf(false) }

    var accelerationData by remember { mutableStateOf<List<Float>>(emptyList()) }
    var possibleCommands by remember { mutableStateOf<List<Char>>(emptyList()) }

    var balance by remember { mutableStateOf(Database.getBalance(username) ?: 0.0) }
    var showBalance by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val otherUser = if (username == "test1") "test2" else "test1"

    var showSendPicker by remember { mutableStateOf(false) }
    var showTxnStatusDialog by remember { mutableStateOf(false) }

    // New: method picker shown before send/receive
    var showMethodPicker by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<TxAction?>(null) }

    fun resetAllStates() {
        isListening = false
        isTransmitting = false
        decodingStatus = ""
        transactionStatus = ""
        accelerationData = emptyList()
        possibleCommands = emptyList()
        vibrationDecoder.stopListening()
        VibrationController.cancelVibration(context)
        showSendPicker = false
        showTxnStatusDialog = false
        showMethodPicker = false
        pendingAction = null
    }

    fun startListening() {
        if (isListening) return
        transactionStatus = "Listening for payment..."
        decodingStatus = "Waiting for transmission..."
        isListening = true
        accelerationData = emptyList()
        possibleCommands = emptyList()
        showTxnStatusDialog = true

        vibrationDecoder.startListening(
            onDataReceived = { command ->
                val amount = VibrationEncoder.getAmountForCommand(command)
                if (amount != null) {
                    val ok = Database.transfer(otherUser, username, amount.toDouble())
                    balance = Database.getBalance(username) ?: balance
                    transactionStatus = if (ok) {
                        "Received \$$amount from $otherUser"
                    } else {
                        "Receive failed"
                    }
                } else {
                    transactionStatus = "Unknown command received"
                }
                isListening = false
                showTxnStatusDialog = true
            },
            onPossibleCommands = { cmds ->
                possibleCommands = cmds
                decodingStatus = "Multiple commands detected"
                transactionStatus = "Select the correct command"
                showTxnStatusDialog = true
            },
            onAccelerationData = { value ->
                accelerationData = (accelerationData + value).takeLast(250)
            },
            onTimeout = {
                isListening = false
                decodingStatus = "Timeout"
                if (transactionStatus.isBlank()) transactionStatus = "Listening timed out"
                showTxnStatusDialog = true
            },
            onStatusUpdate = { status ->
                decodingStatus = status
            }
        )
    }

    fun sendPayment(amount: Int) {
        if (isTransmitting) return
        val command = VibrationEncoder.getCommandForAmount(amount) ?: run {
            transactionStatus = "Invalid amount"
            return
        }
        val pattern = VibrationEncoder.encodeCommand(command)

        accelerationData = emptyList()
        possibleCommands = emptyList()
        decodingStatus = ""

        isTransmitting = true
        transactionStatus = "Sending \$$amount to $otherUser..."
        showTxnStatusDialog = true

        VibrationController.vibrate(context, pattern)

        coroutineScope.launch {
            delay(pattern.sum())
            val ok = Database.transfer(username, otherUser, amount.toDouble())
            balance = Database.getBalance(username) ?: balance
            transactionStatus = if (ok) {
                "Payment of \$$amount sent to $otherUser"
            } else {
                "Payment failed"
            }
            isTransmitting = false
            showTxnStatusDialog = true
        }
    }

    fun stopListening() {
        vibrationDecoder.stopListening()
        isListening = false
        decodingStatus = "Stopped listening"
        if (transactionStatus.isBlank()) transactionStatus = "Listening stopped"
        showTxnStatusDialog = true
    }

    fun stopTransmitting() {
        VibrationController.cancelVibration(context)
        isTransmitting = false
        transactionStatus = "Transmission cancelled"
        showSendPicker = false
        showTxnStatusDialog = true
    }

    if (showTxnStatusDialog) {
        val inProgress = isListening || isTransmitting
        AlertDialog(
            onDismissRequest = { /* keep open until Close/OK is clicked */ },
            title = {
                Text(
                    text = when {
                        isListening -> "Receiving"
                        isTransmitting -> "Sending"
                        else -> "Transaction Status"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (transactionStatus.isNotBlank()) Text("Transaction: $transactionStatus")
                    if (decodingStatus.isNotBlank()) Text("Status: $decodingStatus")

                    if (isListening && accelerationData.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Vibration Graph", style = MaterialTheme.typography.bodyMedium)
                        VibrationGraph(
                            data = accelerationData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }

                    if (possibleCommands.isNotEmpty()) {
                        Text("Possible commands: ${possibleCommands.joinToString(", ")}")
                    }
                }
            },
            confirmButton = {
                when {
                    isListening -> {
                        Button(onClick = { stopListening() }) { Text("Stop Listening") }
                    }
                    isTransmitting -> {
                        Button(onClick = { stopTransmitting() }) { Text("Stop Sending") }
                    }
                    else -> {
                        TextButton(onClick = { showTxnStatusDialog = false }) { Text("OK") }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTxnStatusDialog = false },
                    enabled = !inProgress
                ) { Text("Close") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Welcome, $username!",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (showBalance) "Balance: \$${"%.2f".format(balance)}" else "Balance: ••••",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    balance = Database.getBalance(username) ?: balance
                    showBalance = !showBalance
                }) {
                    Text(if (showBalance) "Hide" else "Show")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    pendingAction = TxAction.SEND
                    showMethodPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isListening && !isTransmitting
            ) { Text("Send Transaction") }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    pendingAction = TxAction.RECEIVE
                    showMethodPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isListening && !isTransmitting
            ) { Text("Receive Transaction") }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { onOpenHistory() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isListening && !isTransmitting
            ) { Text("Payment History") }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "© CCS87-CS4514, 2025-2026",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )

                Button(onClick = {
                    resetAllStates()
                    onReturnToLogin()
                }) { Text("Return to Login") }
            }
        }
    }

    // New: method picker dialog (shown before send/receive flows)
    if (showMethodPicker) {
        AlertDialog(
            onDismissRequest = {
                showMethodPicker = false
                pendingAction = null
            },
            title = { Text("Select transaction method") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showMethodPicker = false
                            when (pendingAction) {
                                TxAction.SEND -> showSendPicker = true
                                TxAction.RECEIVE -> startListening()
                                null -> Unit
                            }
                            pendingAction = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isListening && !isTransmitting
                    ) { Text("Without protection") }

                    Button(
                        onClick = {
                            // No crypto implementation yet: just return to HomeMenu (close popup)
                            showMethodPicker = false
                            pendingAction = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isListening && !isTransmitting
                    ) { Text("With cryptographic") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMethodPicker = false
                    pendingAction = null
                }) { Text("Close") }
            }
        )
    }

    if (showSendPicker) {
        AlertDialog(
            onDismissRequest = { showSendPicker = false },
            title = { Text("Send Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose amount to send:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                showSendPicker = false
                                sendPayment(100)
                            },
                            enabled = !isTransmitting && !isListening
                        ) { Text("\$100") }
                        Button(
                            onClick = {
                                showSendPicker = false
                                sendPayment(200)
                            },
                            enabled = !isTransmitting && !isListening
                        ) { Text("\$200") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSendPicker = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun VibrationGraph(
    data: List<Float>,
    isTransmittedPattern: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxValue = data.maxOrNull() ?: 1f
        val minValue = data.minOrNull() ?: 0f
        val range = max(1f, maxValue - minValue)
        val stepX = if (data.size > 1) size.width / (data.size - 1) else size.width

        for (i in 1 until data.size) {
            val x1 = (i - 1) * stepX
            val y1 = size.height - ((data[i - 1] - minValue) / range * size.height)

            val x2 = i * stepX
            val y2 = size.height - ((data[i] - minValue) / range * size.height)

            drawLine(
                color = if (isTransmittedPattern) Color(0xFF2E7D32) else Color(0xFF1565C0),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f
            )
        }
    }
}
