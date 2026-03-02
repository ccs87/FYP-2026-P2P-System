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
                                val duration = pattern[index]
                                if (duration == 200L) 255 else 0
                            }

                            Log.d("VibrationController", "Amplitudes: ${amplitudes.joinToString()}")
                            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                            vibrator.vibrate(effect)
                            Log.d("VibrationController", "Vibration started successfully")
                        } else {
                            @Suppress("DEPRECATION")
                            val amplitudes = IntArray(pattern.size) { index ->
                                val duration = pattern[index]
                                if (duration == 200L) 255 else 0
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

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrate(context: Context, payloadBits: String) {
        require(payloadBits.isNotEmpty())
        require(payloadBits.all { it == '0' || it == '1' }) { "payloadBits must be a bitstring" }

        Log.d("VibrationController", "Secure payload bits length=${payloadBits.length}")
        Log.d("VibrationController", "Secure payload bits=$payloadBits")

        val pattern = encodeOokBits(payloadBits)
        vibrate(context, pattern)
    }

    private fun encodeOokBits(bits: String): LongArray {
        val pattern = mutableListOf<Long>()
        for (bit in bits) {
            if (bit == '1') {
                pattern.add(400)
                pattern.add(200)
                pattern.add(400)
            } else {
                pattern.add(1000)
            }
        }
        return pattern.toLongArray()
    }

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