package com.example.p2p_system
import android.util.Log

object VibrationEncoder {
    private val commandMap = mapOf(
        'a' to 100,
        'b' to 200
    )

    private val amountToCommand = mapOf(
        100 to 'a',
        200 to 'b'
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
        val startBinary = "00010"  // Start marker (5 bits)
        val endBinary = "00011"    // End marker (5 bits)
        val fullBinary = startBinary + binaryString + endBinary  // 5 + 7 + 5 = 17 bits

        Log.d("VibrationEncoder", "Encoding command '$command' (ASCII: $asciiCode)")
        Log.d("VibrationEncoder", "Full binary: $fullBinary (${fullBinary.length} bits)")

        val pattern = mutableListOf<Long>()

        for (bit in fullBinary) {
            if (bit == '1') {
                pattern.add(400)   // Pause 400ms
                pattern.add(200)   // Vibrate 200ms
                pattern.add(400)   // Pause 400ms
            } else {
                pattern.add(1000)  // Pause 1000ms
            }
        }

        val totalDuration = pattern.sum()
        Log.d("VibrationEncoder", "Generated pattern: ${pattern.joinToString()}")
        Log.d("VibrationEncoder", "Pattern entries: ${pattern.size}")
        Log.d("VibrationEncoder", "Total duration: ${totalDuration}ms (${totalDuration / 1000.0}s)")

        return pattern.toLongArray()
    }
}
