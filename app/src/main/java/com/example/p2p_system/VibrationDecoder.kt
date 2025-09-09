package com.example.p2p_system

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.*

class VibrationDecoder(private val sensorManager: SensorManager) : SensorEventListener {
    private val accelerometerData = mutableListOf<Float>()
    private var onDecoded: ((String) -> Unit)? = null
    private var onAccelerationData: ((Float) -> Unit)? = null
    private var onTimeout: (() -> Unit)? = null
    private var job: Job? = null
    private var timeoutJob: Job? = null

    private var receivedMessage = StringBuilder()
    private var isReceiving = false
    private var currentCharBits = StringBuilder()
    private var beaconDetected = false
    private var framesSinceBeacon = 0

    fun startListening(
        onDataReceived: (String) -> Unit,
        onAccelerationData: ((Float) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        timeoutMs: Long = 90000
    ) {
        this.onDecoded = onDataReceived
        this.onAccelerationData = onAccelerationData
        this.onTimeout = onTimeout

        receivedMessage.clear()
        isReceiving = false
        beaconDetected = false
        framesSinceBeacon = 0
        accelerometerData.clear()

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)

        // Set timeout
        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            stopListening()
            onTimeout?.invoke()
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        timeoutJob?.cancel()
        job?.cancel()
        isReceiving = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val magnitude = sqrt(
                it.values[0] * it.values[0] +
                        it.values[1] * it.values[1] +
                        it.values[2] * it.values[2]
            )

            // Add to acceleration data for graphing
            onAccelerationData?.invoke(magnitude)

            // Check for beacon (long vibration)
            if (!beaconDetected && magnitude > 15.0) {
                beaconDetected = true
                framesSinceBeacon = 0
                isReceiving = true
                currentCharBits.clear()
                return
            }

            if (isReceiving) {
                framesSinceBeacon++

                // We're expecting 7 data frames after the beacon
                if (framesSinceBeacon > 0 && framesSinceBeacon <= 7) {
                    // Detect bit based on vibration intensity
                    val bit = if (magnitude > 2.0) '1' else '0'
                    currentCharBits.append(bit)

                    // If we've collected 7 bits, decode the character
                    if (framesSinceBeacon == 7) {
                        if (currentCharBits.length == 7) {
                            val charCode = currentCharBits.toString().toInt(2)
                            receivedMessage.append(charCode.toChar())
                            currentCharBits.clear()
                        }

                        // Reset for next character
                        beaconDetected = false
                        framesSinceBeacon = 0
                    }
                } else if (framesSinceBeacon > 7) {
                    // We've passed the data frames, check if message is complete
                    if (receivedMessage.isNotEmpty() && receivedMessage.toString().contains('&')) {
                        // Assume message is complete when we have both amount and sender
                        onDecoded?.invoke(receivedMessage.toString())
                        stopListening()
                    }

                    // Reset for possible next character
                    beaconDetected = false
                    framesSinceBeacon = 0
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}