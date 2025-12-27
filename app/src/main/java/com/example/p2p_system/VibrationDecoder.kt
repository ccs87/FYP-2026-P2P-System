package com.example.p2p_system

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt
import kotlinx.coroutines.*
import java.util.*
import kotlin.invoke

data class AccelValue(val time: Long, val value: Float)

class VibrationDecoder(private val sensorManager: SensorManager) : SensorEventListener {
    private var onDecoded: ((Char) -> Unit)? = null
    private var onPossibleCommands: ((List<Char>) -> Unit)? = null
    private var onAccelerationData: ((Float) -> Unit)? = null
    private var onTimeout: (() -> Unit)? = null
    private var onStatusUpdate: ((String) -> Unit)? = null
    private var timeoutJob: Job? = null
    private var forcedDecodeJob: Job? = null
    private var isReceiving = false

    private val logEntries = LinkedList<String>()
    private val maxLogEntries = 200

    var lastDecodedCommand: Char? = null
        private set

    private val accelList = mutableListOf<AccelValue>()
    private var startTime: Long = 0

    fun startListening(
        onDataReceived: (Char) -> Unit,
        onPossibleCommands: (List<Char>) -> Unit,
        onAccelerationData: ((Float) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        timeoutMs: Long = 35000,
        forcedDecodeDelayMs: Long = 25000
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

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)

        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            if (isReceiving) {
                addLog("TIMEOUT: No valid pattern detected within ${timeoutMs}ms")
                stopListening()
                onStatusUpdate?.invoke("Timeout - no valid pattern detected")
                onTimeout?.invoke()
            }
        }

        forcedDecodeJob = CoroutineScope(Dispatchers.Main).launch {
            addLog("SCHEDULED FORCED DECODE in ${forcedDecodeDelayMs}ms")
            onStatusUpdate?.invoke("Waiting for transmission...")
            delay(forcedDecodeDelayMs)
            if (isReceiving) {
                addLog("AUTOMATIC FORCED DECODE TRIGGERED - Analyzing collected data")
                onStatusUpdate?.invoke("Analyzing vibration pattern...")
                attemptDecode()

                // New: if forced decode finished and nothing valid was decoded, end immediately.
                if (isReceiving && lastDecodedCommand == null) {
                    addLog("FORCED DECODE COMPLETE: No valid command -> immediate timeout")
                    stopListening()
                    onStatusUpdate?.invoke("Timeout - no valid pattern detected")
                    onTimeout?.invoke()
                }
            }
        }

        addLog("STARTED LISTENING - Timeout: ${timeoutMs}ms, Forced decode: ${forcedDecodeDelayMs}ms")
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        timeoutJob?.cancel()
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

            if (magnitude > 10.0) {
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
            vibrationCount > 10 -> onStatusUpdate?.invoke("Strong vibrations detected - analyzing...")
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

        addLog("STARTING DECODE - ${accelList.size} data points collected")
        onStatusUpdate?.invoke("Processing vibration data...")

        val binaryString = detectBitsFromVibrationPattern()
        addLog("RAW BINARY DETECTED: $binaryString (${binaryString.length} bits)")

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

    private fun detectBitsFromVibrationPattern(): String {
        val binary = StringBuilder()
        val vibrationThreshold = 10.2f
        val bitDuration = 1000L // 1 second per bit

        if (accelList.isEmpty()) {
            addLog("No data available for bit detection")
            return ""
        }

        val startTimestamp = accelList.first().time
        val totalDuration = accelList.last().time - startTimestamp
        val expectedBits = (totalDuration / bitDuration).toInt().coerceAtLeast(1)

        addLog("BIT DETECTION: Duration=${totalDuration}ms, Expected bits=${expectedBits}")

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
                    "Bit $bitIndex: max=${maxInBit.format(2)}, avg=${avgInBit.format(2)}, " +
                            "vibrations=$vibrationPoints -> ${if (isOne) "1" else "0"}"
                )
            } else {
                binary.append("0")
                addLog("Bit $bitIndex: NO DATA -> 0")
            }

            if (bitsDetected >= 17) break
        }

        addLog("COMPLETED BIT DETECTION: $bitsDetected bits")
        return binary.toString()
    }

    private fun findAndDecodeCommand(binaryString: String) {
        addLog("FINAL DECODE: Searching for pattern in ${binaryString.length} bits")

        val startPattern = "00010"  // 5-bit start
        val endPattern = "00011"    // 5-bit end
        val commands = mutableListOf<Char>()

        // Fix: allow both supported commands
        val validCommands = listOf('a', 'b')

        for (i in 0..binaryString.length - 17) {
            val potentialStart = binaryString.substring(i, i + 5)

            if (potentialStart == startPattern) {
                if (i + 17 <= binaryString.length) {
                    val middleBits = binaryString.substring(i + 5, i + 12)  // 7 data bits
                    val potentialEnd = binaryString.substring(i + 12, i + 17)
                    if (potentialEnd == endPattern) {
                        addLog("PATTERN FOUND at position $i: start=$potentialStart, data=$middleBits, end=$potentialEnd")

                        try {
                            val asciiCode = middleBits.toInt(2)
                            val command = asciiCode.toChar()

                            if (command in validCommands) {
                                commands.add(command)
                                addLog("✅ VALID COMMAND: '$command' (binary: $middleBits, ASCII: $asciiCode)")
                            } else {
                                addLog("❌ INVALID COMMAND: '$command' (not in $validCommands)")
                            }
                        } catch (e: Exception) {
                            addLog("❌ BINARY DECODE ERROR: $middleBits - ${e.message}")
                        }
                    } else {
                        addLog("END PATTERN MISMATCH: expected $endPattern, got $potentialEnd")
                    }
                }
            }
        }

        when {
            commands.isEmpty() -> {
                addLog("❌ DECODE FAILED: No valid commands found in pattern")
                onStatusUpdate?.invoke("No valid payment command detected")
            }
            commands.size == 1 -> {
                val command = commands.first()
                addLog("🎉 SUCCESS: Single command '$command' decoded")
                lastDecodedCommand = command
                onStatusUpdate?.invoke("Payment command '$command' received!")
                stopListening()
                onDecoded?.invoke(command)
            }
            else -> {
                addLog("⚠️ MULTIPLE COMMANDS: $commands")
                onStatusUpdate?.invoke("Multiple commands detected - please select")
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