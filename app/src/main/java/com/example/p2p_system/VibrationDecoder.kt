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

    // Beacon detection variables
    private var beaconStartTime = 0L
    private var beaconEndTime = 0L
    private var inBeacon = false

    // Frame timing
    private var frameStartTime = 0L
    private val frameDuration = 1000L // ms
    private var currentFrameValues = mutableListOf<Float>()

    // Adaptive threshold
    private val magnitudeHistory = LinkedList<Float>()
    private val historySize = 50
    private var baselineMagnitude = 0f
    private var magnitudeVariance = 0f
    private var baselineEnergy = 0f

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
        beaconStartTime = 0L
        beaconEndTime = 0L
        inBeacon = false
        frameStartTime = 0L
        currentFrameValues.clear()
        magnitudeHistory.clear()
        baselineMagnitude = 0f
        magnitudeVariance = 0f
        baselineEnergy = 0f
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

            // Calculate signal energy in current window
            val recentValues = currentFrameValues.takeLast(10)
            val signalEnergy = if (recentValues.isNotEmpty()) {
                recentValues.map { it * it }.average().toFloat()
            } else {
                magnitude * magnitude
            }

            // Dynamic threshold with energy component
            val dynamicThreshold = baselineMagnitude + 2.5f * sqrt(magnitudeVariance).toFloat()
            val energyThreshold = baselineEnergy * 1.5f

            // Log for debugging (less frequent to avoid spam)
            if (System.currentTimeMillis() % 200 < 10) {
                addLog("Mag: ${magnitude.format(2)}, Energy: ${signalEnergy.format(2)}, " +
                        "Threshold: ${dynamicThreshold.format(2)}")
            }

            // Beacon detection using energy pattern
            if (!beaconDetected && !isReceiving) {
                if (!inBeacon && signalEnergy > energyThreshold) {
                    // Vibration started - potential beacon beginning
                    inBeacon = true
                    beaconStartTime = currentTime
                    addLog("Beacon vibration started (energy: ${signalEnergy.format(2)})")
                }
                else if (inBeacon && signalEnergy < energyThreshold * 0.7f) {
                    // Vibration ended - check if it was a valid beacon
                    inBeacon = false
                    beaconEndTime = currentTime
                    val beaconDuration = beaconEndTime - beaconStartTime

                    addLog("Beacon vibration ended, duration: ${beaconDuration}ms")

                    // Pattern matching for beacon (200ms vibration surrounded by pauses)
                    if (beaconDuration in 150..250) {
                        // Valid beacon detected
                        beaconDetected = true
                        isReceiving = true
                        framesSinceBeacon = 0
                        currentCharBits.clear()
                        frameStartTime = currentTime
                        currentFrameValues.clear()

                        addLog("BEACON DETECTED! Duration: ${beaconDuration}ms")
                        onStatusUpdate?.invoke("Beacon detected! Receiving command...")
                    }
                }
            } else if (isReceiving) {
                // Collect data for the current frame
                currentFrameValues.add(magnitude)

                // Check if frame time has elapsed
                if (currentTime - frameStartTime >= frameDuration) {
                    // Process this frame using vibration pattern recognition
                    processFrame(currentFrameValues, framesSinceBeacon)

                    // Reset for next frame
                    frameStartTime = currentTime
                    currentFrameValues.clear()
                    framesSinceBeacon++

                    if (framesSinceBeacon == 7) {
                        // All frames processed, decode the character
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

            // Update energy baseline
            baselineEnergy = magnitudeHistory.map { it * it }.average().toFloat()
        }
    }

    // Process a single frame using pattern recognition
    private fun processFrame(frameData: List<Float>, frameIndex: Int) {
        // Calculate energy in different segments of the frame
        val segments = 5
        val segmentSize = frameData.size / segments
        val segmentEnergies = (0 until segments).map { segmentIndex ->
            val start = segmentIndex * segmentSize
            val end = minOf(start + segmentSize, frameData.size)
            val segmentData = frameData.subList(start, end)
            segmentData.map { it * it }.average().toFloat()
        }

        // Check for '1' bit pattern (vibration in middle segment)
        // Pattern for '1': [400ms pause, 200ms vibration, 400ms pause]
        val midSegmentIndex = segments / 2
        val hasMidVibration = segmentEnergies[midSegmentIndex] > segmentEnergies.average() * 1.5f

        // Detect '1' bit if middle segment has much higher energy
        val bit = if (hasMidVibration) '1' else '0'
        currentCharBits.append(bit)

        addLog("Frame $frameIndex: $bit (energies: ${segmentEnergies.joinToString { it.format(1) }})")
        onStatusUpdate?.invoke("Frame $frameIndex: $bit")
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

    // Extension function for formatting floats
    private fun Float.format(digits: Int) = "%.${digits}f".format(this)

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
