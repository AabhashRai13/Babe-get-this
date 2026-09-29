package com.babegetthis.android.core.auth.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.babegetthis.android.R
import com.babegetthis.android.core.network.NetworkMonitor
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

// What the Google account sheet hands back. rawNonce goes to Supabase, which
// hashes it and checks it against the nonce Google signed into idToken — so a
// token lifted from somewhere else can't be replayed into a session.
data class GoogleSignInToken(val idToken: String, val rawNonce: String)

// Seam over Credential Manager, so LoginViewModel's Google flow is unit-tested
// and the end-to-end suite can sign in without Play services.
fun interface GoogleTokenRequester {
    // Shows the "Sign in with Google" sheet; activityContext must be an
    // Activity (the sheet is UI). Returns null when the user closes the sheet.
    // Throws GoogleSignInOfflineException before opening it with no internet,
    // and GetCredentialException / GoogleIdTokenParsingException on real
    // failures.
    suspend fun request(activityContext: Context): GoogleSignInToken?
}

// Offline the sheet still lists the device's accounts but can never fetch a
// token for the one picked, so it isn't opened at all.
class GoogleSignInOfflineException : Exception("No internet connection")

class CredentialManagerTokenRequester @Inject constructor(private val networkMonitor: NetworkMonitor) :
    GoogleTokenRequester {

    override suspend fun request(activityContext: Context): GoogleSignInToken? {
        if (!networkMonitor.isOnline()) throw GoogleSignInOfflineException()
        val rawNonce = UUID.randomUUID().toString()
        // Generated from this flavor's google-services.json, which is what ties
        // each build to its own Firebase + Supabase environment.
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(sha256Hex(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            return null
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleSignInToken(idToken = idToken, rawNonce = rawNonce)
    }
}

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
