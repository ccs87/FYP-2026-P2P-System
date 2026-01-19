package com.example.p2p_system

import android.util.Log
import java.nio.charset.Charset
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LightweightCrypto {
    private const val TAG = "LightweightCrypto"

    // Static salt + fixed iterations as requested (hard-coded so both devices derive same key)
    // NOTE: keep constant to preserve key derivation behavior across devices.
    private val STATIC_SALT: ByteArray =
        byteArrayOf(
            0x43, 0x43, 0x53, 0x38, 0x37, 0x2D, 0x43, 0x53,
            0x34, 0x35, 0x31, 0x34, 0x2D, 0x50, 0x32, 0x50
        )

    private const val ITERATION_COUNT = 10_000
    private const val KEY_LENGTH_BITS = 128

    /**
     * Derives a 128-bit PSS key using PBKDF2 (HMAC-SHA256).
     * Returns raw key bytes (16 bytes).
     */
    fun derivePssKey(passphrase: String): ByteArray {
        val normalized = passphrase.trim()
        Log.d(TAG, "Key derivation requested")
        Log.d(TAG, "Passphrase length=${normalized.length}")
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
     * Builds the 41-bit payload for sender:
     * Payload = N || C
     * N = 8-bit nonce
     * M = original 17-bit vibration message bits
     * T = truncate(HMAC(PSS_key, M || N), 16 bits)
     * P = M || T (33 bits)
     * K = 33-bit keystream derived from AES-CTR with (PSS_key, N)
     * C = P XOR K (33 bits)
     */
    fun buildSecurePayloadBits(
        pssKey: ByteArray,
        messageBits17: String,
        nonceByte: Int? = null
    ): String {
        require(messageBits17.length == 17 && messageBits17.all { it == '0' || it == '1' }) {
            "messageBits17 must be exactly 17 bits"
        }
        require(pssKey.size == 16) { "PSS key must be 128-bit (16 bytes)" }

        val n = (nonceByte ?: SecureRandom().nextInt(256)).coerceIn(0, 255)
        val nonceBits8 = n.toString(2).padStart(8, '0')

        Log.d(TAG, "=== Secure Sender Build Start ===")
        Log.d(TAG, "M (17-bit)=$messageBits17")
        Log.d(TAG, "Nonce N (0..255)=$n")
        Log.d(TAG, "N (8-bit)=$nonceBits8")

        // HMAC input: M || N, as bytes of ASCII '0'/'1' to keep it deterministic for both ends.
        val mnBits = messageBits17 + nonceBits8
        val mnBytes = mnBits.toByteArray(Charset.forName("UTF-8"))

        val tag16Bits = hmacSha256Trunc16Bits(pssKey, mnBytes)
        Log.d(TAG, "T (16-bit, truncated HMAC-SHA256)=$tag16Bits")

        val plaintext33 = messageBits17 + tag16Bits
        Log.d(TAG, "P = M || T (33-bit)=$plaintext33")

        val keystream33 = aesCtrKeystream33Bits(pssKey, n)
        Log.d(TAG, "K (33-bit keystream via AES-CTR)=$keystream33")

        val ciphertext33 = xorBits(plaintext33, keystream33)
        Log.d(TAG, "C = P XOR K (33-bit)=$ciphertext33")

        val payload41 = nonceBits8 + ciphertext33
        Log.d(TAG, "Payload = N || C (41-bit)=$payload41")
        Log.d(TAG, "=== Secure Sender Build End ===")

        return payload41
    }

    private fun hmacSha256Trunc16Bits(key16: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key16, "HmacSHA256"))
        val full = mac.doFinal(data) // 32 bytes

        // Take first 2 bytes (16 bits) and render as 16-bit binary
        val b0 = full[0].toInt() and 0xFF
        val b1 = full[1].toInt() and 0xFF
        val v = (b0 shl 8) or b1
        return v.toString(2).padStart(16, '0')
    }

    /**
     * Generates exactly 33 bits of keystream using AES-CTR-like construction.
     * Implementation detail: use AES-ECB on (nonce || counter || zero padding) to
     * generate a 128-bit block, then take first 33 bits.
     *
     * This avoids padding and yields deterministic stream bits for the nonce.
     */
    private fun aesCtrKeystream33Bits(pssKey16: ByteArray, nonce0to255: Int): String {
        val aes = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
        aes.init(javax.crypto.Cipher.ENCRYPT_MODE, SecretKeySpec(pssKey16, "AES"))

        // Build 16-byte input block:
        // [ nonce(1) | counter(1) | 14 bytes 0 ]
        // Counter is fixed to 0 for 33-bit keystream (single block is enough).
        val block = ByteArray(16)
        block[0] = (nonce0to255 and 0xFF).toByte()
        block[1] = 0x00
        // rest already 0

        val out = aes.doFinal(block) // 16 bytes
        val outBits = out.toBitString()

        return outBits.substring(0, 33)
    }

    private fun xorBits(a: String, b: String): String {
        require(a.length == b.length) { "Bitstrings must match length" }
        val sb = StringBuilder(a.length)
        for (i in a.indices) {
            val x = a[i] == '1'
            val y = b[i] == '1'
            sb.append(if (x.xor(y)) '1' else '0')
        }
        return sb.toString()
    }

    private fun ByteArray.toBitString(): String {
        val sb = StringBuilder(this.size * 8)
        for (byte in this) {
            val v = byte.toInt() and 0xFF
            sb.append(v.toString(2).padStart(8, '0'))
        }
        return sb.toString()
    }

    /**
     * Best-effort: zero out the provided key bytes and return null (caller should drop references).
     */
    fun clearKey(key: ByteArray?): ByteArray? {
        if (key == null) return null
        for (i in key.indices) key[i] = 0
        return null
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { b ->
            ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }
}