package com.example.p2p_system

import android.content.Context
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    var showMethodPicker by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<TxAction?>(null) }

    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf("") }
    val pssKeyState = remember { mutableStateOf<ByteArray?>(null) }

    var useCryptoForPendingAction by remember { mutableStateOf(false) }

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
        showPassphraseDialog = false
        passphrase = ""
        passphraseError = ""
        useCryptoForPendingAction = false
        pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
    }

    fun startListening() {
        if (isListening) return
        transactionStatus = "Listening for payment..."
        decodingStatus = "Waiting for transmission..."
        isListening = true
        accelerationData = emptyList()
        possibleCommands = emptyList()
        showTxnStatusDialog = true

        if (!useCryptoForPendingAction) {
            vibrationDecoder.startListening(
                onDataReceived = { command ->
                    val amount = VibrationEncoder.getAmountForCommand(command)
                    if (amount == null) {
                        decodingStatus = "Unknown command received: $command"
                        showTxnStatusDialog = true
                        return@startListening
                    }

                    val ok = Database.transfer(otherUser, username, amount.toDouble())
                    balance = Database.getBalance(username) ?: balance
                    decodingStatus =
                        if (ok) "Received $$amount from $otherUser" else "Receive failed"
                    transactionStatus = decodingStatus
                    showTxnStatusDialog = true

                    isListening = false
                    vibrationDecoder.stopListening()

                    pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
                },
                onPossibleCommands = { commands ->
                    possibleCommands = commands
                    decodingStatus = "Multiple commands detected: ${commands.joinToString()}"
                    showTxnStatusDialog = true
                },
                onAccelerationData = { magnitude ->
                    accelerationData = (accelerationData + magnitude).takeLast(300)
                },
                onTimeout = {
                    isListening = false
                    decodingStatus = "Timeout - no valid pattern detected"
                    transactionStatus = decodingStatus
                    showTxnStatusDialog = true

                    pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
                },
                onStatusUpdate = { status ->
                    decodingStatus = status
                },
                forcedDecodeDelayMs = 20000
            )
            return
        }

        val pssKey = pssKeyState.value
        if (pssKey == null) {
            transactionStatus = "Cryptographic key not set"
            isListening = false
            showTxnStatusDialog = true
            return
        }

        vibrationDecoder.startListeningSecure(
            pssKey = pssKey,
            onDataReceived = { command ->
                val amount = VibrationEncoder.getAmountForCommand(command)
                if (amount == null) {
                    decodingStatus = "Unknown command received: $command"
                    showTxnStatusDialog = true
                    return@startListeningSecure
                }

                val ok = Database.transfer(otherUser, username, amount.toDouble())
                balance = Database.getBalance(username) ?: balance
                decodingStatus =
                    if (ok) "Received $$amount from $otherUser" else "Receive failed"
                transactionStatus = decodingStatus
                showTxnStatusDialog = true

                isListening = false
                vibrationDecoder.stopListening()

                pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
            },
            onPossibleCommands = { commands ->
                possibleCommands = commands
                decodingStatus = "Multiple commands detected: ${commands.joinToString()}"
                showTxnStatusDialog = true
            },
            onAccelerationData = { magnitude ->
                accelerationData = (accelerationData + magnitude).takeLast(300)
            },
            onTimeout = {
                isListening = false
                decodingStatus = "Timeout - no valid pattern detected"
                transactionStatus = decodingStatus
                showTxnStatusDialog = true

                pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
            },
            onStatusUpdate = { status ->
                decodingStatus = status
            },
            forcedDecodeDelayMs = 45000
        )
    }

    fun sendPayment(amount: Int) {
        if (isTransmitting) return

        val command = VibrationEncoder.getCommandForAmount(amount) ?: run {
            transactionStatus = "Invalid amount"
            showTxnStatusDialog = true
            return
        }

        accelerationData = emptyList()
        possibleCommands = emptyList()
        decodingStatus = ""

        isTransmitting = true
        transactionStatus = "Sending $$amount to $otherUser..."
        showTxnStatusDialog = true

        if (!useCryptoForPendingAction) {
            val pattern = VibrationEncoder.encodeCommand(command)
            VibrationController.vibrate(context, pattern)

            coroutineScope.launch {
                delay(pattern.sum() + 800L)

                if (isTransmitting) {
                    val ok = Database.transfer(username, otherUser, amount.toDouble())
                    balance = Database.getBalance(username) ?: balance
                    transactionStatus = if (ok) {
                        "$username send $$amount to $otherUser"
                    } else {
                        "Payment failed"
                    }
                    isTransmitting = false
                    showTxnStatusDialog = true

                    pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
                }
            }
            return
        }

        val pssKey = pssKeyState.value
        if (pssKey == null) {
            transactionStatus = "Cryptographic key not set"
            isTransmitting = false
            showTxnStatusDialog = true
            return
        }

        val messageBits17 = run {
            val asciiCode = command.code
            val binaryString = asciiCode.toString(2).padStart(7, '0')
            val startBinary = "00010"
            val endBinary = "00011"
            startBinary + binaryString + endBinary
        }

        val payload41 = try {
            LightweightCrypto.buildSecurePayloadBits(
                pssKey = pssKey,
                messageBits17 = messageBits17
            )
        } catch (e: Exception) {
            Log.e("HomeMenu", "Secure payload build error: ${e.message}")
            transactionStatus = e.message ?: "Secure payload build failed"
            isTransmitting = false
            showTxnStatusDialog = true
            return
        }

        VibrationController.vibrate(context, payload41)

        val transmitDurationMs = run {
            payload41.length * 1000L
        }

        coroutineScope.launch {
            delay(transmitDurationMs + 800L)

            if (isTransmitting) {
                val ok = Database.transfer(username, otherUser, amount.toDouble())
                balance = Database.getBalance(username) ?: balance
                transactionStatus = if (ok) {
                    "$username send $$amount to $otherUser"
                } else {
                    "Payment failed"
                }
                isTransmitting = false
                showTxnStatusDialog = true

                pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
            }
        }
    }

    fun stopListening() {
        vibrationDecoder.stopListening()
        isListening = false
        decodingStatus = "Stopped listening"
        if (transactionStatus.isBlank()) transactionStatus = "Listening stopped"
        showTxnStatusDialog = true

        pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
    }

    fun stopTransmitting() {
        VibrationController.cancelVibration(context)
        isTransmitting = false
        transactionStatus = "Transmission cancelled"
        showSendPicker = false
        showTxnStatusDialog = true

        pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
    }

    fun beginSelectedFlowWithoutProtection() {
        when (pendingAction) {
            TxAction.SEND -> showSendPicker = true
            TxAction.RECEIVE -> startListening()
            null -> {}
        }
        pendingAction = null
    }

    fun beginSelectedFlowWithCrypto() {
        showPassphraseDialog = true
    }

    if (showTxnStatusDialog) {
        val inProgress = isListening || isTransmitting
        AlertDialog(
            onDismissRequest = {
                if (!inProgress) {
                    showTxnStatusDialog = false
                }
            },
            title = { Text("Transaction Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (transactionStatus.isNotBlank()) Text(transactionStatus)
                    if (decodingStatus.isNotBlank()) Text(decodingStatus)

                    if (accelerationData.isNotEmpty()) {
                        Text("Vibration Data", fontWeight = FontWeight.SemiBold)
                        VibrationGraph(
                            data = accelerationData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                    }

                    if (possibleCommands.isNotEmpty()) {
                        Text("Possible Commands: ${possibleCommands.joinToString()}")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!inProgress) {
                            showTxnStatusDialog = false
                        }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                if (inProgress) {
                    TextButton(
                        onClick = {
                            if (isListening) stopListening()
                            if (isTransmitting) stopTransmitting()
                        }
                    ) { Text("Stop") }
                }
            }
        )
    }

    val primaryButtonColors = ButtonDefaults.buttonColors()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
        ) {
            Text(
                text = "Welcome, $username!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (showBalance) "Balance: \$${"%.2f".format(balance)}" else "Balance: \u2022\u2022\u2022\u2022",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = { showBalance = !showBalance }) {
                    Text(if (showBalance) "Hide" else "Show")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    pendingAction = TxAction.SEND
                    showMethodPicker = true
                },
                colors = primaryButtonColors,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send Transaction") }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    pendingAction = TxAction.RECEIVE
                    showMethodPicker = true
                },
                colors = primaryButtonColors,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Receive Transaction") }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onOpenHistory,
                colors = primaryButtonColors,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Payment History") }
        }

        Text(
            text = "© CCS87-CS4514, 2025-2026",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 25.dp)
        )

        Button(
            onClick = {
                resetAllStates()
                onReturnToLogin()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp)
        ) { Text("Return to Login") }
    }

    if (showMethodPicker) {
        AlertDialog(
            onDismissRequest = { showMethodPicker = false },
            title = { Text("Select Transaction Method") },
            text = { Text("Choose whether to use the original method or the cryptographic method.") },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            useCryptoForPendingAction = false
                            showMethodPicker = false
                            beginSelectedFlowWithoutProtection()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Without Protection") }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            useCryptoForPendingAction = true
                            showMethodPicker = false
                            beginSelectedFlowWithCrypto()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("With Cryptographic") }
                }
            }
        )
    }

    if (showPassphraseDialog) {
        AlertDialog(
            onDismissRequest = {
                showPassphraseDialog = false
                passphrase = ""
                passphraseError = ""
                pendingAction = null
                useCryptoForPendingAction = false
                pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
            },
            title = { Text("Enter Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            if (passphraseError.isNotBlank()) passphraseError = ""
                        },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passphraseError.isNotBlank()) {
                        Text(passphraseError, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = passphrase.trim()
                        if (p.isBlank()) {
                            passphraseError = "Passphrase cannot be empty"
                            return@Button
                        }

                        try {
                            pssKeyState.value = LightweightCrypto.derivePssKey(p)
                            showPassphraseDialog = false
                            passphrase = ""
                            passphraseError = ""
                            beginSelectedFlowWithoutProtection()
                        } catch (e: Exception) {
                            passphraseError = e.message ?: "Failed to derive key"
                            Log.e("HomeMenu", "Key derivation error: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPassphraseDialog = false
                        passphrase = ""
                        passphraseError = ""
                        pendingAction = null
                        useCryptoForPendingAction = false
                        pssKeyState.value = LightweightCrypto.clearKey(pssKeyState.value)
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (showSendPicker) {
        AlertDialog(
            onDismissRequest = { showSendPicker = false },
            title = { Text("Send Transaction") },
            text = { Text("Select amount to send") },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            showSendPicker = false
                            sendPayment(100)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send \$100") }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            showSendPicker = false
                            sendPayment(200)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send \$200") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSendPicker = false }
                ) { Text("Cancel") }
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