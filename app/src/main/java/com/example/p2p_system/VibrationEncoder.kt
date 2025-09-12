package com.example.p2p_system

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
        // Convert character to 7-bit ASCII code
        val asciiCode = command.code
        val binaryString = asciiCode.toString(2).padStart(7, '0')

        val pattern = mutableListOf<Long>()

        // Add beacon frame (1s vibration)
        pattern.add(1000) // Vibrate for beacon
        pattern.add(500)  // Short pause after beacon

        // Add data frames (7 bits)
        for (bit in binaryString) {
            if (bit == '1') {
                pattern.add(300) // Vibrate for 1
                pattern.add(700) // Pause
            } else {
                pattern.add(1000) // Pause for 0
            }
        }

        // Add inter-command pause
        pattern.add(1000)

        return pattern.toLongArray()
    }

    fun encodeAmount(amount: Int): LongArray {
        val command = getCommandForAmount(amount)
        return if (command != null) {
            encodeCommand(command)
        } else {
            longArrayOf() // Empty pattern for invalid amount
        }
    }
}