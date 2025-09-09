package com.example.p2p_system

object VibrationEncoder {
    fun encodeMessage(message: String): LongArray {
        val pattern = mutableListOf<Long>()

        // Add a preamble for synchronization (2 seconds of silence)
        pattern.add(2000) // Pause

        for (char in message) {
            val binaryString = char.code.toString(2).padStart(7, '0')

            // Beacon frame - 1 second vibration to mark start of character
            pattern.add(1000) // Vibrate for beacon
            pattern.add(500) // Short pause after beacon

            // Data frames - 7 bits
            for (bit in binaryString) {
                if (bit == '1') {
                    pattern.add(300) // Vibrate for 1
                    pattern.add(700) // Pause
                } else {
                    pattern.add(1000) // Pause for 0
                }
            }

            // Inter-character pause
            pattern.add(1000)
        }

        return pattern.toLongArray()
    }
}