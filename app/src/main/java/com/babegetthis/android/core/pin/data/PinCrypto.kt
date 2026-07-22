package com.babegetthis.android.core.pin.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// PBKDF2 hashing for the PIN and the recovery code. A 4-digit PIN has only
// 10,000 candidates, so this does NOT make offline guessing hard — throttling
// does that. Its job is to ensure a casual look at storage yields nothing
// usable. Salt is per-secret and random.
internal object PinCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    // Constant-time — MessageDigest.isEqual does not early-exit on the first
    // differing byte, so it leaks no timing signal about how much matched.
    fun matches(secret: String, salt: ByteArray, expectedHash: ByteArray): Boolean =
        MessageDigest.isEqual(hash(secret, salt), expectedHash)

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun decode(str: String): ByteArray = Base64.decode(str, Base64.NO_WRAP)

    // Recovery code: 10 Crockford base32 chars ≈ 50 bits of entropy, drawn from
    // SecureRandom. No I/L/O/U (avoids look-alikes and accidental words).
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    fun newRecoveryCode(): String {
        val rng = SecureRandom()
        return buildString { repeat(10) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) } }
    }

    // Normalize user-typed codes: strip spaces/dashes, uppercase. Keeps entry
    // forgiving without weakening the stored hash.
    fun normalizeCode(input: String): String =
        input.uppercase().filter { it in ALPHABET }
}
