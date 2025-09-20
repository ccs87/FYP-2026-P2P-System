package com.example.p2p_system

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission
import kotlin.text.compareTo

object VibrationController {

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use max amplitude (255) for vibration, 0 for pause
                val amplitudes = pattern.mapIndexed { i, _ -> if (i % 2 == 0) 255 else 0 }.toIntArray()
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        }
    }
    fun cancelVibration(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.cancel()
    }
}