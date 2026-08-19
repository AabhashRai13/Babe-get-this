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

    // Normalize user-typed codes: uppercase, map look-alikes, drop everything
    // else (spaces, dashes, stray punctuation). Keeps entry forgiving without
    // weakening the stored hash — the mapping happens before hashing, so the
    // stored secret is unchanged.
    //
    // The mapping is the half that was missing. Excluding I/L/O from the output
    // alphabet is only useful if input maps them BACK: a written 0 read as "O",
    // or 1 read as "I"/"l", is the single most likely transcription slip, and the
    // generator can never legitimately produce those letters — so an O in user
    // input is unambiguously a misread zero. Filtering alone silently DELETED
    // them, shortening the code and failing with no explanation, on the one
    // credential that exists for when the PIN is already forgotten.
    //
    // U is excluded from the alphabet for a different reason (it keeps generated
    // codes from spelling words), so there is nothing to map it to — it stays
    // dropped, same as any other stray character.
    fun normalizeCode(input: String): String =
        input.uppercase()
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
            .filter { it in ALPHABET }
            .joinToString("")
}
