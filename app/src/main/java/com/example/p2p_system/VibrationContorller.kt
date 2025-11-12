// File: 'app/src/main/java/com/example/p2p_system/VibrationContorller.kt'
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

            Log.d("VibrationController", "Pattern duration: ${totalPatternDuration}ms")
            Log.d("VibrationController", "Pattern: ${pattern.joinToString()}")

            vibrationJob = CoroutineScope(Dispatchers.Default).launch {
                withContext(Dispatchers.Main) {
                    vibrator.cancel()
                }
                delay(100)

                withContext(Dispatchers.Main) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val amplitudes = IntArray(pattern.size) { index ->
                                var isVibration = false
                                var currentIndex = 0
                                for (bit in getBinaryPatternForCommand('a')) { // reference shape
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

                            Log.d("VibrationController", "Amplitudes: ${amplitudes.joinToString()}")
                            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                            vibrator.vibrate(effect)
                            Log.d("VibrationController", "Vibration started successfully")
                        } else {
                            @Suppress("DEPRECATION")
                            val amplitudes = IntArray(pattern.size) { index ->
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

                            val compatiblePattern = mutableListOf<Long>()
                            for (i in pattern.indices) {
                                if (amplitudes[i] > 0) {
                                    compatiblePattern.add(0)
                                    compatiblePattern.add(pattern[i])
                                } else {
                                    compatiblePattern.add(pattern[i])
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

    // Helper function to get binary pattern for a command (5-bit start/end + 7-bit data = 17 bits)
    private fun getBinaryPatternForCommand(command: Char): String {
        val asciiCode = command.code
        val binaryString = asciiCode.toString(2).padStart(7, '0')
        val startBinary = "00010"
        val endBinary = "00011"
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
