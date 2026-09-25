package com.babegetthis.android.core.auth.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

// What the Google account sheet hands back. rawNonce goes to Supabase, which
// hashes it and checks it against the nonce Google signed into idToken — so a
// token lifted from somewhere else can't be replayed into a session.
data class GoogleSignInToken(val idToken: String, val rawNonce: String)

// Shows the "Sign in with Google" sheet. Needs an Activity context (the sheet
// is UI). Returns null when the user closes the sheet. Throws
// GetCredentialException / GoogleIdTokenParsingException on real failures.
suspend fun requestGoogleSignInToken(context: Context, webClientId: String): GoogleSignInToken? {
    val rawNonce = UUID.randomUUID().toString()
    val option = GetSignInWithGoogleOption.Builder(webClientId)
        .setNonce(sha256Hex(rawNonce))
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = try {
        CredentialManager.create(context).getCredential(context, request).credential
    } catch (e: GetCredentialCancellationException) {
        return null
    }
    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
    return GoogleSignInToken(idToken = idToken, rawNonce = rawNonce)
}

internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
