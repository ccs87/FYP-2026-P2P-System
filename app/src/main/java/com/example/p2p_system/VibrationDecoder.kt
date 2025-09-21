package com.example.p2p_system

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.removeFirst
import kotlin.compareTo
import kotlin.div
import kotlin.text.compareTo
import kotlin.text.toDouble
import kotlin.text.toFloat

class VibrationDecoder(private val sensorManager: SensorManager) : SensorEventListener {
    private var onDecoded: ((Char) -> Unit)? = null
    private var onAccelerationData: ((Float) -> Unit)? = null
    private var onTimeout: (() -> Unit)? = null
    private var onStatusUpdate: ((String) -> Unit)? = null
    private var timeoutJob: Job? = null

    private var isReceiving = false
    private var currentCharBits = StringBuilder()
    private var beaconDetected = false
    private var framesSinceBeacon = 0
    private var lastVibrationTime = 0L

    // Beacon detection variables
    private var beaconStartTime = 0L
    private var beaconEndTime = 0L
    private var inBeacon = false
    private val minBeaconDuration = 1000L // ms (1 second)
    private val maxBeaconDuration = 2000L // ms (2 seconds)

    // Frame timing
    private var frameStartTime = 0L
    private val frameDuration = 1000L // ms
    private var currentFrameValues = mutableListOf<Float>()

    // Adaptive threshold
    private val magnitudeHistory = LinkedList<Float>()
    private val historySize = 50
    private var baselineMagnitude = 0f
    private var magnitudeVariance = 0f

    // Logging
    private val logEntries = LinkedList<String>()
    private val maxLogEntries = 100

    var lastDecodedCommand: Char? = null
        private set

    fun startListening(
        onDataReceived: (Char) -> Unit,
        onAccelerationData: ((Float) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        timeoutMs: Long = 30000
    ) {
        this.onDecoded = onDataReceived
        this.onAccelerationData = onAccelerationData
        this.onTimeout = onTimeout
        this.onStatusUpdate = onStatusUpdate

        isReceiving = false
        beaconDetected = false
        framesSinceBeacon = 0
        currentCharBits.clear()
        lastVibrationTime = System.currentTimeMillis()
        beaconStartTime = 0L
        beaconEndTime = 0L
        inBeacon = false
        frameStartTime = 0L
        currentFrameValues.clear()
        magnitudeHistory.clear()
        baselineMagnitude = 0f
        magnitudeVariance = 0f
        logEntries.clear()

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)

        // Set timeout
        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            stopListening()
            onTimeout?.invoke()
        }

        addLog("Started listening for vibrations")
        onStatusUpdate?.invoke("Listening for commands...")
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        timeoutJob?.cancel()
        isReceiving = false
        addLog("Stopped listening")
        onStatusUpdate?.invoke("Stopped listening")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            val currentTime = System.currentTimeMillis()

            // Add to acceleration data for graphing
            onAccelerationData?.invoke(magnitude)

            // Update adaptive threshold
            updateAdaptiveThreshold(magnitude)

            // Calculate dynamic threshold
            val dynamicThreshold = baselineMagnitude + 3 * sqrt(magnitudeVariance).toFloat()

            // Log for debugging (less frequent to avoid spam)
            if (System.currentTimeMillis() % 200 < 10) {
                addLog("Mag: ${"%.2f".format(magnitude)}, Threshold: ${"%.2f".format(dynamicThreshold)}")
            }

            // Beacon detection using start/end timing
            if (!beaconDetected && !isReceiving) {
                if (!inBeacon && magnitude > dynamicThreshold) {
                    // Vibration started - potential beacon beginning
                    inBeacon = true
                    beaconStartTime = currentTime
                    addLog("Beacon vibration started")
                }
                else if (inBeacon && dynamicThreshold  <  magnitude) {
                    // Vibration ended - check if it was a valid beacon
                    inBeacon = false
                    beaconEndTime = currentTime
                    val beaconDuration = beaconEndTime - beaconStartTime

                    addLog("Beacon vibration ended, duration: ${beaconDuration}ms")

                    // Check if duration matches expected beacon length
                    if (beaconDuration in minBeaconDuration..maxBeaconDuration) {
                        // Valid beacon detected
                        beaconDetected = true
                        isReceiving = true
                        framesSinceBeacon = 0
                        currentCharBits.clear()
                        frameStartTime = currentTime
                        currentFrameValues.clear()

                        addLog("BEACON DETECTED! Duration: ${beaconDuration}ms")
                        onStatusUpdate?.invoke("Beacon detected! Receiving command...")
                    } else {
                        addLog("Invalid beacon duration: ${beaconDuration}ms (expected: ${minBeaconDuration}-${maxBeaconDuration}ms)")
                    }
                }
            } else if (isReceiving) {
                // Collect data for the current frame
                currentFrameValues.add(magnitude)

                // Check if frame time has elapsed
                if (currentTime - frameStartTime >= frameDuration) {
                    // Process this frame
                    val frameAverage = currentFrameValues.average().toFloat()
                    val frameMax = currentFrameValues.maxOrNull() ?: 0f
                    val frameMin = currentFrameValues.minOrNull() ?: 0f

                    // Use adaptive threshold for bit detection
                    val bit = if (frameAverage > dynamicThreshold) '1' else '0'
                    currentCharBits.append(bit)

                    addLog("Frame $framesSinceBeacon: $bit (avg: ${"%.2f".format(frameAverage)}, " +
                            "min: ${"%.2f".format(frameMin)}, max: ${"%.2f".format(frameMax)})")

                    onStatusUpdate?.invoke("Frame $framesSinceBeacon: $bit (avg: ${"%.1f".format(frameAverage)})")

                    // Reset for next frame
                    frameStartTime = currentTime
                    currentFrameValues.clear()
                    framesSinceBeacon++

                    if (framesSinceBeacon == 7) {
                        // All frames processed, decode the character
                        addLog("All frames collected: ${currentCharBits.toString()}")
                        decodeCharacter()

                        // Reset for next command
                        beaconDetected = false
                        framesSinceBeacon = 0
                        isReceiving = false
                    }
                }
            }
        }
    }

    private fun updateAdaptiveThreshold(currentMagnitude: Float) {
        magnitudeHistory.add(currentMagnitude)
        if (magnitudeHistory.size > historySize) {
            magnitudeHistory.removeFirst()
        }

        if (magnitudeHistory.size >= 10) {
            val sum = magnitudeHistory.sum()
            baselineMagnitude = sum / magnitudeHistory.size

            val varianceSum = magnitudeHistory.map { (it - baselineMagnitude).toDouble().pow(2) }.sum()
            magnitudeVariance = (varianceSum / magnitudeHistory.size).toFloat()
        }
    }

    private fun decodeCharacter() {
        if (currentCharBits.length == 7) {
            try {
                val charCode = currentCharBits.toString().toInt(2)
                val character = charCode.toChar()
                lastDecodedCommand = character
                addLog("Decoded command: '$character' (binary: ${currentCharBits.toString()})")
                onStatusUpdate?.invoke("Decoded command: '$character'")
                onDecoded?.invoke(character)
            } catch (e: Exception) {
                addLog("Error decoding command: ${e.message}")
                onStatusUpdate?.invoke("Error decoding command: ${e.message}")
            }
        } else {
            addLog("Incomplete frame: ${currentCharBits.length} bits (expected: 7)")
            onStatusUpdate?.invoke("Incomplete frame: ${currentCharBits.length} bits")
        }
    }

    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis() % 100000
        val logMessage = "[$timestamp] $message"
        logEntries.add(logMessage)

        // Keep only the most recent log entries
        while (logEntries.size > maxLogEntries) {
            logEntries.removeFirst()
        }

        // Also send to Android log
        Log.d("VibrationDecoder", logMessage)
    }

    fun getLogs(): List<String> {
        return logEntries.toList()
    }

    fun clearLogs() {
        logEntries.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}