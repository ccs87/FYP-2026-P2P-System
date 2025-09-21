package com.example.p2p_system

import android.util.Log

object VibrationEncoder {
    // Command mapping
    private val commandMap = mapOf(
        'a' to 100,  // Pay $100
        'b' to 200,  // Pay $200
        'c' to 300,  // Pay $300
        'o' to 0,    // Confirm payment
        'r' to -1    // Retry request
    )

    // Reverse mapping for decoding
    private val amountToCommand = mapOf(
        100 to 'a',
        200 to 'b',
        300 to 'c'
    )

    fun getCommandForAmount(amount: Int): Char? {
        return amountToCommand[amount]
    }

    fun getAmountForCommand(command: Char): Int? {
        return commandMap[command]
    }

    fun encodeCommand(command: Char): LongArray {
        val asciiCode = command.code
        val binaryString = asciiCode.toString(2).padStart(7, '0')
        Log.d("VibrationEncoder", "Binary string for command '$command': $binaryString")
        val pattern = mutableListOf<Long>()

        // Add beacon pattern logging
        Log.d("VibrationEncoder", "=== BEACON PATTERN ===")
        Log.d("VibrationEncoder", "Adding initial pause: 400ms")
        pattern.add(400)   // Short pause

        Log.d("VibrationEncoder", "Adding beacon vibration: 200ms")
        pattern.add(200)   // Vibration - beacon

        Log.d("VibrationEncoder", "Adding post-beacon pause: 400ms")
        pattern.add(400)   // Pause after beacon

        Log.d("VibrationEncoder", "Total beacon pattern duration: 1000ms (1s)")
        Log.d("VibrationEncoder", "=== END BEACON ===")

        // Active frames (7 bits)
        for ((index, bit) in binaryString.withIndex()) {
            val second = index + 1
            Log.d("VibrationEncoder", "Frame $second: Bit=$bit (Second $second)")
            if (bit == '1') {
                pattern.add(400)   // Pause (0.4s)
                pattern.add(200)   // Vibrate (0.2s)
                pattern.add(400)   // Pause (0.4s)
            } else {
                pattern.add(1000)  // 1s pause (no vibration)
            }
        }

        // Inactive frames (5 bits, all silent)
        for (i in 1..5) {
            val second = 7 + i
            Log.d("VibrationEncoder", "Inactive Frame $second: No vibration (Second $second)")
            pattern.add(1000) // 1s pause (no vibration)
        }

        // Calculate total duration for logging
        val totalDuration = pattern.sum()
        Log.d("VibrationEncoder", "Total pattern duration: ${totalDuration}ms (${totalDuration/1000}s)")

        return pattern.toLongArray()
    }

}