package com.example.p2p_system

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.*
import androidx.annotation.RequiresPermission
import kotlin.text.compareTo

object VibrationController {
    private var vibrationJob: Job? = null

    // Track the total pattern duration for timing
    private var totalPatternDuration = 0L

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrate(context: Context, pattern: LongArray, repeatCount: Int = 1, delayBetween: Long = 0) {
        // Cancel any existing vibration job
        vibrationJob?.cancel()

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            // Calculate total pattern duration
            totalPatternDuration = pattern.sum()
            addLog("Pattern duration: ${totalPatternDuration}ms")

            vibrationJob = CoroutineScope(Dispatchers.Default).launch {
                // Cancel any previous vibration
                withContext(Dispatchers.Main) {
                    vibrator.cancel()
                }

                // Short delay to ensure previous vibration is cancelled
                delay(50)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitudes = pattern.map { duration ->
                        if (duration == 200L) 255 else 0
                    }.toIntArray()

                    withContext(Dispatchers.Main) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        vibrator.vibrate(pattern, -1)
                    }
                }

                addLog("Vibration started")
            }
        }
    }


    fun cancelVibration(context: Context) {
        vibrationJob?.cancel()
        vibrationJob = null
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.cancel()
        addLog("Vibration cancelled")
    }

    // Helper to get the estimated total time for all vibrations
    fun getTotalVibrationTime(pattern: LongArray, repeatCount: Int = 3, delayBetween: Long = 5000): Long {
        val patternDuration = pattern.sum()
        return (patternDuration * repeatCount) + (delayBetween * (repeatCount - 1))
    }

    private fun addLog(message: String) {
        android.util.Log.d("VibrationController", message)
    }
}