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
    var transmittedPattern by remember { mutableStateOf<List<Float>?>(null) }

    var possibleCommands by remember { mutableStateOf<List<Char>>(emptyList()) }

    var balance by remember { mutableStateOf(Database.getBalance(username) ?: 0.0) }
    var showBalance by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val otherUser = if (username == "test1") "test2" else "test1"

    fun resetAllStates() {
        isListening = false
        isTransmitting = false
        decodingStatus = ""
        transactionStatus = ""
        accelerationData = emptyList()
        transmittedPattern = null
        possibleCommands = emptyList()
        vibrationDecoder.stopListening()
        VibrationController.cancelVibration(context)
    }

    fun startListening() {
        if (isListening) return
        transactionStatus = "Listening for payment..."
        decodingStatus = "Waiting for transmission..."
        isListening = true
        accelerationData = emptyList()
        possibleCommands = emptyList()

        vibrationDecoder.startListening(
            onDataReceived = { command ->
                coroutineScope.launch {
                    isListening = false
                    val amount = VibrationEncoder.getAmountForCommand(command)
                    if (amount == 100) {
                        // Receiver logs: from otherUser -> username
                        Database.transfer(otherUser, username, 100.0)
                        balance = Database.getBalance(username) ?: 0.0
                        transactionStatus = "Received $100 from $otherUser"
                    } else {
                        transactionStatus = "Unknown command received"
                    }
                }
            },
            onPossibleCommands = { cmds ->
                possibleCommands = cmds
            },
            onAccelerationData = { value ->
                // Keep last 400 points for the graph
                accelerationData = (accelerationData + value).takeLast(400)
            },
            onTimeout = {
                isListening = false
                transactionStatus = "Listening timed out"
            },
            onStatusUpdate = { status ->
                decodingStatus = status
            }
        )
    }

    fun sendPayment() {
        if (isTransmitting) return
        val command = VibrationEncoder.getCommandForAmount(100) ?: run {
            transactionStatus = "Invalid amount"
            return
        }
        val pattern = VibrationEncoder.encodeCommand(command)
        transmittedPattern = generatePatternVisualization(pattern)
        isTransmitting = true
        transactionStatus = "Sending $100 to $otherUser..."

        // Start device vibration
        VibrationController.vibrate(context, pattern)

        // Simulate transaction completion after the waveform finishes
        coroutineScope.launch {
            delay(pattern.sum())
            // Sender logs: from username -> otherUser
            Database.transfer(username, otherUser, 100.0)
            balance = Database.getBalance(username) ?: 0.0
            transactionStatus = "Payment of $100 sent to $otherUser"
            isTransmitting = false
        }
    }

    fun stopListening() {
        vibrationDecoder.stopListening()
        isListening = false
        decodingStatus = "Stopped listening"
    }

    fun stopTransmitting() {
        VibrationController.cancelVibration(context)
        isTransmitting = false
        transactionStatus = "Transmission cancelled"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Welcome, $username", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenHistory) { Text("Payment History") }
        }

        Spacer(Modifier.height(8.dp))

        // Balance
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                showBalance = !showBalance
                if (showBalance) balance = Database.getBalance(username) ?: 0.0
            }) {
                Text(if (showBalance) "Hide Balance" else "Show Balance")
            }
            if (showBalance) {
                Spacer(Modifier.width(12.dp))
                Text("Balance: $${"%.2f".format(balance)}")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { sendPayment() }, enabled = !isTransmitting && !isListening) {
                Text("Send \$100")
            }
            Button(onClick = { startListening() }, enabled = !isListening && !isTransmitting) {
                Text("Receive")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stop controls
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { stopTransmitting() }, enabled = isTransmitting) {
                Text("Stop Transmission")
            }
            Button(onClick = { stopListening() }, enabled = isListening) {
                Text("Stop Listening")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Status
        if (transactionStatus.isNotEmpty()) {
            Text(transactionStatus, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
        }
        if (decodingStatus.isNotEmpty() && isListening) {
            Text("Decoder: $decodingStatus", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
        }

        // Possible commands (if multiple found)
        if (possibleCommands.isNotEmpty()) {
            Text("Possible commands: ${possibleCommands.joinToString()}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
        }

        // Graphs
        if (isListening && accelerationData.isNotEmpty()) {
            Text("Received vibration data", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            VibrationGraph(
                data = accelerationData,
                isTransmittedPattern = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        if (transmittedPattern != null) {
            Text("Transmitted pattern preview", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            VibrationGraph(
                data = transmittedPattern!!,
                isTransmittedPattern = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        // Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { resetAllStates() }) { Text("Reset") }
            Button(onClick = onReturnToLogin) { Text("Return to Login") }
        }
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

        // Optional threshold indicator for received data
        if (!isTransmittedPattern) {
            val threshold = 11.0f
            val y = size.height - ((threshold - minValue) / range * size.height)
            drawLine(
                color = Color.Red,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
    }
}

fun generatePatternVisualization(pattern: LongArray, sampleMs: Long = 50L): List<Float> {
    if (pattern.isEmpty()) return emptyList()
    val vis = mutableListOf<Float>()
    var vibratePhase = false
    for (duration in pattern) {
        val count = (duration / sampleMs).toInt().coerceAtLeast(1)
        repeat(count) {
            vis.add(if (vibratePhase) 1f else 0f)
        }
        vibratePhase = !vibratePhase
    }
    return vis
}
