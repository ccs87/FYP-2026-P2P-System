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
            }
            else {
                // Fallback for devices with API < 26
                vibrator.vibrate(duration)
            }
        }
    }
}