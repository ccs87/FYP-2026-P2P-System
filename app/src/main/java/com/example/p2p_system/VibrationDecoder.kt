package com.example.p2p_system

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt
import kotlinx.coroutines.*
import kotlin.collections.get
import kotlin.compareTo
import kotlin.inc
import kotlin.invoke
import kotlin.text.append
import kotlin.text.toInt
import kotlin.times
import kotlin.toString

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

    var lastDecodedCommand: Char? = null
        private set

    fun startListening(
        onDataReceived: (Char) -> Unit,
        onAccelerationData: ((Float) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null,
        timeoutMs: Long = 30000 // 30 seconds timeout
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

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)

        // Set timeout
        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            stopListening()
            onTimeout?.invoke()
        }

        onStatusUpdate?.invoke("Listening for commands...")
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        timeoutJob?.cancel()
        isReceiving = false
        onStatusUpdate?.invoke("Stopped listening")
    }

    // Kotlin - VibrationDecoder.kt
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            val magnitudePower = x * x + y * y + z * z
            val magnitude = sqrt(magnitudePower)

//            Log.d("VibrationDecoder", "Magnitude: $magnitude, beaconDetected=$beaconDetected, isReceiving=$isReceiving, framesSinceBeacon=$framesSinceBeacon")
            Log.d("VibrationDecoder", "MagnitudePower: $magnitudePower, beaconDetected=$beaconDetected, isReceiving=$isReceiving, framesSinceBeacon=$framesSinceBeacon")


            val currentTime = System.currentTimeMillis()

            // Add to acceleration data for graphing
            onAccelerationData?.invoke(magnitude)

            // Simplified beacon detection: first vibration above threshold
            if (!beaconDetected && magnitudePower > 95.0) {
                beaconDetected = true
                isReceiving = true
                framesSinceBeacon = 0
                currentCharBits.clear()
                onStatusUpdate?.invoke("Beacon detected. Receiving command...")
                return
            }

            if (isReceiving) {
                framesSinceBeacon++

                // Collect 7 bits after beacon
                if (framesSinceBeacon > 0 && framesSinceBeacon <= 7) {
                    val bit = if (magnitude > 10.0) '1' else '0'
                    currentCharBits.append(bit)
                    onStatusUpdate?.invoke("Frame $framesSinceBeacon: $bit (${magnitude.toInt()})")

                    if (framesSinceBeacon == 7) {
                        if (currentCharBits.length == 7) {
                            try {
                                val charCode = currentCharBits.toString().toInt(2)
                                val character = charCode.toChar()
                                lastDecodedCommand = character
                                onStatusUpdate?.invoke("Decoded command: '$character'")
                                onDecoded?.invoke(character)
                            } catch (e: Exception) {
                                onStatusUpdate?.invoke("Error decoding command: ${e.message}")
                            }
                        }
                        // Reset for next command
                        beaconDetected = false
                        framesSinceBeacon = 0
                        isReceiving = false
                    }
                } else if (framesSinceBeacon > 7) {
                    beaconDetected = false
                    framesSinceBeacon = 0
                    isReceiving = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}