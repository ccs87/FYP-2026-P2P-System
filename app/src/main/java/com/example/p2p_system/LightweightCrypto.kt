package com.example.p2p_system

import android.util.Log
import java.nio.charset.Charset
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object LightweightCrypto {
    private const val TAG = "LightweightCrypto"

    // Static salt + fixed iterations as requested (hard-coded so both devices derive same key)
    // NOTE: keep constant to preserve key derivation behavior across devices.
    private val STATIC_SALT: ByteArray =
        "P2P_SYSTEM_STATIC_SALT_V1".toByteArray(Charset.forName("UTF-8"))

    private const val ITERATION_COUNT = 10_000
    private const val KEY_LENGTH_BITS = 128

    /**
     * Derives a 128-bit PSS key using PBKDF2 (HMAC-SHA256).
     * Returns raw key bytes (16 bytes).
     */
    fun derivePssKey(passphrase: String): ByteArray {
        val normalized = passphrase.trim()
        Log.d(TAG, "Key derivation requested")
        Log.d(
            TAG,
            "PBKDF2 params: algo=PBKDF2WithHmacSHA256, iterations=$ITERATION_COUNT, keyLen=${KEY_LENGTH_BITS}bit"
        )
        Log.d(TAG, "Salt (static) length=${STATIC_SALT.size} bytes")

        require(normalized.isNotEmpty()) { "Passphrase must not be empty" }

        val spec = PBEKeySpec(
            normalized.toCharArray(),
            STATIC_SALT,
            ITERATION_COUNT,
            KEY_LENGTH_BITS
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded

        Log.d(TAG, "Derived PSS key length=${keyBytes.size} bytes")
        Log.d(TAG, "Derived PSS key (hex)=${keyBytes.toHex()}")

        // Best-effort cleanup of sensitive intermediate
        spec.clearPassword()
        return keyBytes
    }

    /**
     * Best-effort: zero out the provided key bytes and return null (caller should drop references).
     */
    fun clearKey(key: ByteArray?): ByteArray? {
        if (key == null) return null
        Log.d(TAG, "Clearing temporary PSS key from memory")
        java.util.Arrays.fill(key, 0)
        return null
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { b ->
            ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }
}
