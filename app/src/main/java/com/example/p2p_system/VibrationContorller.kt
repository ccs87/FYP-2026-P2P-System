package com.example.p2p_system

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.*
import androidx.annotation.RequiresPermission
import android.util.Log

object VibrationController {
    private var vibrationJob: Job? = null

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrate(context: Context, pattern: LongArray) {
        vibrationJob?.cancel()
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            val totalPatternDuration = pattern.sum()

            // Log the pattern details
            Log.d("VibrationController", "Pattern duration: ${totalPatternDuration}ms")
            Log.d("VibrationController", "Pattern: ${pattern.joinToString()}")

            vibrationJob = CoroutineScope(Dispatchers.Default).launch {
                // Cancel any existing vibration first
                withContext(Dispatchers.Main) {
                    vibrator.cancel()
                }

                // Short delay to ensure cancellation takes effect
                delay(100)

                withContext(Dispatchers.Main) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Create amplitudes array - vibrate only when pattern indicates vibration
                            val amplitudes = IntArray(pattern.size) { index ->
                                // Only vibrate for the middle part of '1' bits (the 200ms duration)
                                // In our pattern: for '1' we have [400(pause), 200(vibrate), 400(pause)]
                                // So we need to identify which entries are the vibration parts
                                var isVibration = false
                                var currentIndex = 0

                                // Simulate the pattern building to find vibration points
                                for (bit in getBinaryPatternForCommand('a')) { // We use 'a' as reference
                                    if (bit == '1') {
                                        // For '1': positions currentIndex(pause), currentIndex+1(vibrate), currentIndex+2(pause)
                                        if (index == currentIndex + 1) {
                                            isVibration = true
                                            break
                                        }
                                        currentIndex += 3
                                    } else {
                                        // For '0': position currentIndex(pause)
                                        currentIndex += 1
                                    }
                                }

                                if (isVibration) 255 else 0
                            }

                            Log.d("VibrationController", "Amplitudes: ${amplitudes.joinToString()}")
                            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                            vibrator.vibrate(effect)
                            Log.d("VibrationController", "Vibration started successfully")
                        } else {
                            @Suppress("DEPRECATION")
                            // For older Android versions
                            val amplitudes = IntArray(pattern.size) { index ->
                                // Same vibration detection logic
                                var isVibration = false
                                var currentIndex = 0

                                for (bit in getBinaryPatternForCommand('a')) {
                                    if (bit == '1') {
                                        if (index == currentIndex + 1) {
                                            isVibration = true
                                            break
                                        }
                                        currentIndex += 3
                                    } else {
                                        currentIndex += 1
                                    }
                                }

                                if (isVibration) 255 else 0
                            }

                            // For API < 26, we need to create a pattern with on/off durations
                            val compatiblePattern = mutableListOf<Long>()
                            for (i in pattern.indices) {
                                if (amplitudes[i] > 0) {
                                    compatiblePattern.add(0) // No delay before vibration
                                    compatiblePattern.add(pattern[i]) // Vibration duration
                                } else {
                                    compatiblePattern.add(pattern[i]) // Pause duration
                                }
                            }

                            @Suppress("DEPRECATION")
                            vibrator.vibrate(compatiblePattern.toLongArray(), -1)
                            Log.d("VibrationController", "Vibration started (legacy API)")
                        }
                    } catch (e: Exception) {
                        Log.e("VibrationController", "Vibration error: ${e.message}")
                    }
                }
            }
        } else {
            Log.e("VibrationController", "No vibrator available")
        }
    }

    // Helper function to get binary pattern for a command
    private fun getBinaryPatternForCommand(command: Char): String {
        val asciiCode = command.code
        val binaryString = asciiCode.toString(2).padStart(7, '0')
        val startBinary = "0000010"
        val endBinary = "0000011"
        return startBinary + binaryString + endBinary
    }

    fun cancelVibration(context: Context) {
        vibrationJob?.cancel()
        vibrationJob = null
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.cancel()
        Log.d("VibrationController", "Vibration cancelled")
    }
}