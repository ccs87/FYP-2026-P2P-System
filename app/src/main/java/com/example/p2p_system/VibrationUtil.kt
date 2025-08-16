package com.example.p2p_system

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission

object VibrationUtil {

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun triggerVibration(context: Context, duration: Long = 500, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(duration, amplitude)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(duration)
            }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun triggerVibrationPattern(context: Context, pattern: LongArray) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(pattern, -1) // -1 means no repeat
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}