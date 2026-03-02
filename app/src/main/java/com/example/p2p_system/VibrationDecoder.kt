package com.example.p2p_system

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt
import kotlinx.coroutines.*
import java.util.*

data class AccelValue(val time: Long, val value: Float)

class VibrationDecoder(private val sensorManager: SensorManager) : SensorEventListener {
    private var onDecoded: ((Char) -> Unit)? = null
    private var onPossibleCommands: ((List<Char>) -> Unit)? = null
    private var onAccelerationData: ((Float) -> Unit)? = null
    private var onTimeout: (() -> Unit)? = null
    private var onStatusUpdate: ((String) -> Unit)? = null
    private var forcedDecodeJob: Job? = null
    private var isReceiving = false

    private val logEntries = LinkedList<String>()
    private val maxLogEntries = 200

    var lastDecodedCommand: Char? = null
        private set

    private val accelList = mutableListOf<AccelValue>()
    private var startTime: Long = 0

    private enum class DecodeMode { LEGACY_17BIT, SECURE_41BIT }
    private var decodeMode: DecodeMode = DecodeMode.LEGACY_17BIT
    private var receiverPssKey: ByteArray? = null

    fun startListening(
        onDataReceived: (Char) -> Unit,
        onPossibleCommands: (List<Char>) -> Unit,
        onAccelerationData: ((Float) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        forcedDecodeDelayMs: Long = 20000
    ) {
        this.onDecoded = onDataReceived
        this.onPossibleCommands = onPossibleCommands
        this.onAccelerationData = onAccelerationData
        this.onTimeout = onTimeout
        this.onStatusUpdate = onStatusUpdate
        isReceiving = true
        accelList.clear()
        logEntries.clear()
        lastDecodedCommand = null
        startTime = System.currentTimeMillis()

        decodeMode = DecodeMode.LEGACY_17BIT
        receiverPssKey = null

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)

        forcedDecodeJob = CoroutineScope(Dispatchers.Main).launch {
            addLog("SCHEDULED FORCED DECODE in ${forcedDecodeDelayMs}ms")
            onStatusUpdate?.invoke("Waiting for transmission...")
            delay(forcedDecodeDelayMs)
            if (isReceiving) {
                onStatusUpdate?.invoke("Analyzing vibration pattern...")
                attemptDecode()

                if (isReceiving && lastDecodedCommand == null) {
                    addLog("FORCED DECODE COMPLETE: No valid command \u2192 timeout")
                    onStatusUpdate?.invoke("Timeout \u002D no valid pattern detected")
                    stopListening()
                    onTimeout?.invoke()
                }
            }
        }

        addLog("STARTED LISTENING \u002D Forced decode: ${forcedDecodeDelayMs}ms")
    }

    fun startListeningSecure(
        pssKey: ByteArray,
        onDataReceived: (Char) -> Unit,
        onPossibleCommands: (List<Char>) -> Unit,
        onAccelerationData: ((Float) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        forcedDecodeDelayMs: Long = 45000
    ) {
        startListening(
            onDataReceived = onDataReceived,
            onPossibleCommands = onPossibleCommands,
            onAccelerationData = onAccelerationData,
            onTimeout = onTimeout,
            onStatusUpdate = onStatusUpdate,
            forcedDecodeDelayMs = forcedDecodeDelayMs
        )

        require(pssKey.size == 16) { "PSS key must be 128-bit (16 bytes)" }
        decodeMode = DecodeMode.SECURE_41BIT
        receiverPssKey = pssKey
        addLog("SECURE MODE ENABLED \u002D expecting 41-bit payload (N||C)")
        Log.d("VibrationDecoder", "Secure receive enabled: forcedDecodeDelayMs=$forcedDecodeDelayMs")
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        forcedDecodeJob?.cancel()
        isReceiving = false
        addLog("STOPPED LISTENING")
        onStatusUpdate?.invoke("Stopped listening")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isReceiving) return

        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            val currentTime = System.currentTimeMillis()

            onAccelerationData?.invoke(magnitude)
            accelList.add(AccelValue(currentTime, magnitude))

            if (magnitude > 9.65f) {
                val elapsed = currentTime - startTime
                addLog("*** HIGH VIBRATION: ${magnitude.format(2)} at ${elapsed}ms ***")
            }

            if (accelList.size % 100 == 0) {
                updateStatusBasedOnData()
            }
        }
    }

    private fun updateStatusBasedOnData() {
        if (accelList.size < 50) return

        val recentData = accelList.takeLast(100)
        val maxValue = recentData.maxOfOrNull { it.value } ?: 0f
        val vibrationCount = recentData.count { it.value > 11.0f }

        addLog("Data: ${accelList.size} points, Max: ${maxValue.format(2)}, Vibrations: $vibrationCount")

        when {
            vibrationCount > 10 -> onStatusUpdate?.invoke("Strong vibrations detected \u002D analyzing...")
            vibrationCount > 5 -> onStatusUpdate?.invoke("Moderate vibrations detected")
            vibrationCount > 0 -> onStatusUpdate?.invoke("Weak vibrations detected")
            else -> {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > 10000) {
                    onStatusUpdate?.invoke("Waiting for vibrations... (${elapsed / 1000}s)")
                }
            }
        }
    }

    private fun attemptDecode() {
        if (accelList.size < 50) {
            addLog("DECODE FAILED: Not enough data (${accelList.size} points)")
            onStatusUpdate?.invoke("Insufficient data for decoding")
            return
        }

        addLog("STARTING DECODE \u002D ${accelList.size} data points collected")
        onStatusUpdate?.invoke("Processing vibration data...")

        val targetBits = if (decodeMode == DecodeMode.SECURE_41BIT) 41 else 17
        val binaryString = detectBitsFromVibrationPattern(targetBits)
        addLog("RAW BINARY DETECTED: $binaryString (${binaryString.length} bits)")
        Log.d("VibrationDecoder", "rawBits(len=${binaryString.length})=$binaryString")

        when (decodeMode) {
            DecodeMode.SECURE_41BIT -> {
                if (binaryString.length >= 41) {
                    val ok = tryDecodeSecurePayload(binaryString)
                    if (!ok) {
                        addLog("SECURE DECODE FAILED \u2192 integrity/authentication check failed or pattern invalid")
                        onStatusUpdate?.invoke("Secure decode failed \u002D message rejected")
                    }
                } else {
                    addLog("SECURE DECODE FAILED: Need 41 bits, got ${binaryString.length}")
                    onStatusUpdate?.invoke("Incomplete secure pattern (${binaryString.length}/41 bits)")
                }
            }

            DecodeMode.LEGACY_17BIT -> {
                if (binaryString.length >= 17) {
                    findAndDecodeCommand(binaryString)
                } else {
                    addLog("DECODE FAILED: Need 17 bits, got ${binaryString.length}")
                    onStatusUpdate?.invoke("Incomplete pattern detected (${binaryString.length}/17 bits)")
                    if (binaryString.length >= 12) {
                        findAndDecodeCommand(binaryString)
                    }
                }
            }
        }
    }

    private fun detectBitsFromVibrationPattern(targetBits: Int): String {
        val binary = StringBuilder()
        val vibrationThreshold = 9.65f
        val bitDuration = 1000L

        if (accelList.isEmpty()) {
            addLog("No data available for bit detection")
            return ""
        }

        val startTimestamp = accelList.first().time
        val totalDuration = accelList.last().time - startTimestamp
        val expectedBits = (totalDuration / bitDuration).toInt().coerceAtLeast(1)

        addLog("BIT DETECTION: Duration=${totalDuration}ms, Expected bits=${expectedBits}, Target bits=${targetBits}")

        var bitsDetected = 0

        for (bitIndex in 0 until expectedBits) {
            val bitStartTime = startTimestamp + (bitIndex * bitDuration)
            val bitEndTime = bitStartTime + bitDuration

            val bitData = accelList.filter {
                it.time >= bitStartTime && it.time < bitEndTime
            }

            if (bitData.isNotEmpty()) {
                val maxInBit = bitData.maxOf { it.value }
                val avgInBit = bitData.map { it.value }.average().toFloat()
                val vibrationPoints = bitData.count { it.value > vibrationThreshold }

                val isOne = vibrationPoints > 5 && maxInBit > vibrationThreshold

                binary.append(if (isOne) "1" else "0")
                bitsDetected++

                addLog(
                    "Bit $bitIndex: max=${maxInBit.format(2)}, avg=${avgInBit.format(2)}, vibPts=$vibrationPoints \u2192 ${if (isOne) "1" else "0"}"
                )
            } else {
                binary.append("0")
                addLog("Bit $bitIndex: NO DATA \u2192 0")
            }

            if (bitsDetected >= targetBits) break
        }

        addLog("COMPLETED BIT DETECTION: $bitsDetected bits")
        return binary.toString()
    }

    private fun tryDecodeSecurePayload(bits41: String): Boolean {
        val key = receiverPssKey
        if (key == null) {
            addLog("SECURE DECODE FAILED: PSS key not set on receiver")
            Log.d("VibrationDecoder", "secureDecode: missing pssKey")
            return false
        }
        if (bits41.length < 41) return false

        val nonceBits8 = bits41.substring(0, 8)
        val ciphertext33 = bits41.substring(8, 41)

        val nonce = try {
            nonceBits8.toInt(2)
        } catch (e: Exception) {
            addLog("SECURE DECODE FAILED: invalid nonce bits")
            return false
        }

        addLog("SECURE RX: payloadBits41=$bits41")
        addLog("SECURE RX: N(bits)=$nonceBits8 N(int)=$nonce")
        addLog("SECURE RX: C(33)=$ciphertext33")

        Log.d("VibrationDecoder", "secureRx payload41=$bits41")
        Log.d("VibrationDecoder", "secureRx nonceBits8=$nonceBits8 nonce=$nonce")
        Log.d("VibrationDecoder", "secureRx ciphertext33=$ciphertext33")

        val keystream33 = aesCtrKeystream33Bits(key, nonce)
        val plaintext33 = xorBits(ciphertext33, keystream33)

        addLog("SECURE RX: K(33)=$keystream33")
        addLog("SECURE RX: P'(33)=C XOR K =$plaintext33")

        Log.d("VibrationDecoder", "secureRx keystream33=$keystream33")
        Log.d("VibrationDecoder", "secureRx plaintext33=$plaintext33")

        val messageBits17 = plaintext33.substring(0, 17)
        val tag16 = plaintext33.substring(17, 33)

        addLog("SECURE RX: M'(17)=$messageBits17")
        addLog("SECURE RX: T_recv(16)=$tag16")

        val expectedTag16 = hmacSha256Trunc16Bits(
            key16 = key,
            data = (messageBits17 + nonceBits8).toByteArray(Charsets.UTF_8)
        )

        addLog("SECURE RX: T_exp(16)=$expectedTag16")
        Log.d("VibrationDecoder", "secureRx tagRecv=$tag16 tagExp=$expectedTag16")

        if (expectedTag16 != tag16) {
            addLog("SECURE RX: TAG MISMATCH \u2192 reject")
            onStatusUpdate?.invoke("Secure message rejected \u002D invalid tag")
            return false
        }

        addLog("SECURE RX: TAG OK \u2192 decode legacy 17-bit message")
        onStatusUpdate?.invoke("Secure message verified \u002D decoding...")

        findAndDecodeCommand(messageBits17)
        return true
    }

    private fun hmacSha256Trunc16Bits(key16: ByteArray, data: ByteArray): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key16, "HmacSHA256"))
        val full = mac.doFinal(data)

        val b0 = full[0].toInt() and 0xFF
        val b1 = full[1].toInt() and 0xFF
        val v = (b0 shl 8) or b1
        return v.toString(2).padStart(16, '0')
    }

    private fun aesCtrKeystream33Bits(pssKey16: ByteArray, nonce0to255: Int): String {
        val aes = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
        aes.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(pssKey16, "AES"))

        val block = ByteArray(16)
        block[0] = (nonce0to255 and 0xFF).toByte()
        block[1] = 0x00

        val out = aes.doFinal(block)
        val outBits = out.toBitString()
        return outBits.substring(0, 33)
    }

    private fun xorBits(a: String, b: String): String {
        require(a.length == b.length) { "xorBits requires equal length" }
        val sb = StringBuilder(a.length)
        for (i in a.indices) {
            sb.append(if (a[i] == b[i]) '0' else '1')
        }
        return sb.toString()
    }

    private fun ByteArray.toBitString(): String {
        val sb = StringBuilder(this.size * 8)
        for (byte in this) {
            val v = byte.toInt() and 0xFF
            sb.append(v.toString(2).padStart(8, '0'))
        }
        return sb.toString()
    }

    private fun findAndDecodeCommand(binaryString: String) {
        addLog("FINAL DECODE: Searching for pattern in ${binaryString.length} bits")

        val startPattern = "00010"  // 5-bit start
        val endPattern = "00011"    // 5-bit end
        val commands = mutableListOf<Char>()

        val validCommands = listOf('a', 'b')
        for (i in 0..binaryString.length - 17) {
            val potentialStart = binaryString.substring(i, i + 5)

            if (potentialStart == startPattern) {
                if (i + 17 <= binaryString.length) {
                    val middleBits = binaryString.substring(i + 5, i + 12)  // 7 data bits
                    val potentialEnd = binaryString.substring(i + 12, i + 17)
                    if (potentialEnd == endPattern) {
                        val asciiValue = middleBits.toInt(2)
                        val decodedChar = asciiValue.toChar()
                        if (decodedChar in validCommands) {
                            commands.add(decodedChar)
                            addLog("FOUND COMMAND: '$decodedChar' at index=$i (ASCII=$asciiValue)")
                        } else {
                            addLog("IGNORED COMMAND: '$decodedChar' (not in supported set)")
                        }
                    }
                }
            }
        }

        when {
            commands.isEmpty() -> {
                addLog("DECODE FAILED: No valid commands found in pattern")
                onStatusUpdate?.invoke("No valid payment command detected")
            }
            commands.size == 1 -> {
                val command = commands.first()
                addLog("SUCCESS: Single command '$command' decoded")
                lastDecodedCommand = command
                onStatusUpdate?.invoke("Payment command '$command' received!")
                stopListening()
                onDecoded?.invoke(command)
            }
            else -> {
                addLog("MULTIPLE COMMANDS: $commands")
                onStatusUpdate?.invoke("Multiple commands detected \u002D please select")
                stopListening()
                onPossibleCommands?.invoke(commands)
            }
        }
    }

    fun manualDecode() {
        if (isReceiving) {
            addLog("MANUAL DECODE TRIGGERED")
            attemptDecode()
        }
    }

    fun getStatus(): String {
        val elapsed = if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000 else 0
        return when {
            !isReceiving -> "Not listening"
            accelList.isEmpty() -> "No data collected (${elapsed}s)"
            else -> {
                val maxVal = accelList.maxOfOrNull { it.value }?.format(2) ?: "0.0"
                "Collecting: ${accelList.size} points, max: $maxVal (${elapsed}s)"
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis() % 100000
        val logMessage = "[$timestamp] $message"
        logEntries.add(logMessage)

        while (logEntries.size > maxLogEntries) {
            logEntries.removeFirst()
        }

        Log.d("VibrationDecoder", logMessage)
    }

    fun getLogs(): List<String> = logEntries.toList()
    fun clearLogs() { logEntries.clear() }
    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}