# Google Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add "Continue with Google" to the login screen, so each build flavor signs in against its own Supabase project with no manual switching.

**Architecture:** Credential Manager shows the Google account sheet and returns a Google ID token. `SupabaseAuthRepository` exchanges that token with `auth.signInWith(IDToken)`, which creates the account on first use, and then reuses the existing `persistCurrentSession()`. The environment config follows each flavor with no new build config: every flavor's `google-services.json` already points at the right Firebase/GCP project, and the Google Services Gradle plugin generates `R.string.default_web_client_id` from it. The staging flavor also gets its own Supabase URL/key (Task 1), so a staging Google token is only ever sent to the staging Supabase project.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Hilt, supabase-kt 3.0.3 (`auth-kt`), `androidx.credentials` 1.3.0, `googleid` 1.1.1, JUnit4 + MockK + Turbine + Robolectric.

**Spec:** No separate spec. The decisions were agreed in chat on 2026-09-24 and are recorded under "Design decisions" below.

## Design decisions

How the environments map:

| Flavor    | Auth impl                | Firebase / GCP project | Supabase project | Web client ID source                   |
|-----------|--------------------------|------------------------|------------------|----------------------------------------|
| `dev`     | `FakeAuthRepository`     | `babe-get-this-stg`    | none (fake)      | `app/src/dev/google-services.json`     |
| `staging` | `SupabaseAuthRepository` | `babe-get-this-stg`    | staging          | `app/src/staging/google-services.json` |
| `prod`    | `SupabaseAuthRepository` | `babe-get-this`        | production       | `app/src/prod/google-services.json`    |

- **One source of truth per environment.** The web client ID comes from the flavor's `google-services.json` (`default_web_client_id`), not from a hand-maintained `buildConfigField`. A flavor therefore can't get another environment's client ID. If the JSON has no web client, the build fails at compile time (`Unresolved reference: default_web_client_id`) instead of failing at runtime.
- **Dev still runs the real Google sheet**, but `FakeAuthRepository` ignores the token and signs in "Dev User". This keeps the UI path identical in every flavor, so there is no dev-only branch to maintain.
- **Nonce is on.** A random raw nonce is generated per attempt. Google receives its SHA-256 hex digest; Supabase receives the raw value and checks that they match. "Skip nonce check" stays **off** in both Supabase projects.
- **No `compose-auth` module.** It would put the Supabase client in the composable and bypass `AuthRepository`, the ViewModel tests and the flavor fakes. We use the same Credential Manager versions it pins (credentials 1.3.0, googleid 1.1.1), which are known to work with supabase 3.0.3 and Kotlin 2.0.21.
- **Login screen only.** Google sign-in creates the account on first use, so the Register screen doesn't need its own button. Add one later if users look for it there.

## Global Constraints

- Kotlin stays at `2.0.21`. Do not bump supabase (`3.0.3`) or firebase-bom (`33.16.0`).
- `androidx.credentials:credentials` and `credentials-play-services-auth` stay at exactly `1.3.0`, and `com.google.android.libraries.identity.googleid:googleid` at exactly `1.1.1`. Newer lines may ship Kotlin metadata that 2.0.21 can't read.
- The user must see generic, friendly messages only. Raw Supabase or Google error text must never reach the UI (same rule as `friendlyAuthMessage`).
- No email addresses, tokens or nonces in analytics events, crash breadcrumbs or logs. Login analytics stay `AnalyticsEvent.AccountLoggedIn` with no payload.
- Review gate: stop after each task for manual review. Do not commit until the reviewer approves, and never push.
- Merge note: Task 1 is byte-identical to Task 10b of `docs/superpowers/plans/2026-09-24-backup-all-lists.md` on `feat/backup-all-lists`. Copy the text exactly so whichever branch merges second merges cleanly. Do not reword it.

---

### Task 1: Point the staging flavor at the staging Supabase project

**Files:**
- Modify: `app/build.gradle.kts:30-31` (read the staging keys) and `:60-65` (comment), plus the staging flavor block (`create("staging")`)
- Modify: `README.md` (the `staging` row of the flavor table)

**Interfaces:**
- Consumes: the `local.properties` keys `STAGING_SUPABASE_URL` and `STAGING_SUPABASE_ANON_KEY` (gitignored; already present on the dev machine).
- Produces: in `staging*` variants, `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_ANON_KEY` resolve to the staging project. `dev` and `prod` are unchanged.

- [x] **Step 1: Read the staging keys**

After the existing `supabaseAnonKey` line in `app/build.gradle.kts`:

```kotlin
// The staging flavor has its own Supabase project, so its data and its
// destructive manual tests never touch production. Empty when absent (CI
// builds only prod and dev), which fails loudly at runtime rather than
// silently pointing staging at production.
val stagingSupabaseUrl: String = localProperties.getProperty("STAGING_SUPABASE_URL") ?: ""
val stagingSupabaseAnonKey: String = localProperties.getProperty("STAGING_SUPABASE_ANON_KEY") ?: ""
```

- [x] **Step 2: Override in the staging flavor**

Replace the `defaultConfig` comment above the two `SUPABASE_*` buildConfigFields with:

```kotlin
        // Supabase config, exposed to Kotlin as BuildConfig.SUPABASE_URL / _ANON_KEY.
        // defaultConfig holds the production project (dev and prod use it);
        // the staging flavor overrides both with its own project below.
```

and add to the end of `create("staging") { … }`:

```kotlin
            buildConfigField("String", "SUPABASE_URL", "\"$stagingSupabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$stagingSupabaseAnonKey\"")
```

- [x] **Step 3: README**

In the flavors table, change the `staging` row's description from "Hits the staging backend" to "Hits the staging backend and the staging Supabase project".

- [x] **Step 4: Verify**

Run: `./gradlew assembleStagingDebug assembleProdDebug`
Expected: BUILD SUCCESSFUL.

Then confirm the generated values without printing the keys:

Run: `grep -c "vdiheceziirgvbudxdxg" app/build/generated/source/buildConfig/staging/debug/com/babegetthis/android/BuildConfig.java`
Expected: `1`

Run: `grep -c "vdiheceziirgvbudxdxg" app/build/generated/source/buildConfig/prod/debug/com/babegetthis/android/BuildConfig.java`
Expected: `0`

- [x] **Step 5: Review gate, then commit**

```bash
git add app/build.gradle.kts README.md
git commit -m "build: point the staging flavor at its own Supabase project"
```

---

### Task 2: Console setup and refreshed `google-services.json` (manual, done by the user)

Only the account owner can do this task (Firebase, Google Cloud, Supabase and Play Console). The executor gives the user this checklist, waits, then runs the verification.

**Files:**
- Replace: `app/src/dev/google-services.json`, `app/src/staging/google-services.json`, `app/src/prod/google-services.json`
- Modify: `README.md` (add a "Google sign-in setup" subsection under "Build Variants")

**Interfaces:**
- Produces: `R.string.default_web_client_id` in every variant, each value belonging to that flavor's Firebase project. Tasks 5 and 6 depend on it.

- [x] **Step 1: Collect SHA-1 fingerprints**

Run: `./gradlew signingReport`
Note the **debug** SHA-1, and the **release** (upload key) SHA-1 if `RELEASE_STORE_FILE` is set.
Then open Play Console → the app → Test and release → App integrity → App signing, and copy the **App signing key certificate** SHA-1.

- [x] **Step 2: Firebase project `babe-get-this-stg` (dev + staging)**

1. Authentication → Sign-in method → Add provider → **Google** → Enable. Set the public-facing name to `Babe, Get This (staging)` and choose a support email. Save. This creates the Web OAuth client. The app does not use the Firebase Auth SDK; enabling the provider only creates the client.
2. Open the Google provider again → Web SDK configuration. Copy the **Web client ID** and **Web client secret** for step 4.
3. Project settings → app `com.babegetthis.android.dev` → Add fingerprint → debug SHA-1.
4. Project settings → app `com.babegetthis.android.staging` → Add fingerprint → debug SHA-1, and the upload-key SHA-1 if you ever build staging release.
5. Download `google-services.json` (either app; the file covers both) and overwrite **both** `app/src/dev/google-services.json` and `app/src/staging/google-services.json`.

- [x] **Step 3: Firebase project `babe-get-this` (prod)**

1. Authentication → Sign-in method → Google → Enable. Set the public-facing name to `Babe, Get This` and choose a support email.
2. Copy the Web client ID and secret from Web SDK configuration.
3. Project settings → app `com.babegetthis.android` → add **three** fingerprints: debug SHA-1, upload-key SHA-1, and the Play App Signing SHA-1. A missing Play SHA-1 is the classic "works locally, fails from Play" bug. (As done on 2026-09-24: Play's app signing key turned out to be our own upload key, so the same SHA-1 `B9:FE…D7:BD` covers both, and only two fingerprints were needed.)
4. Download `google-services.json` and overwrite `app/src/prod/google-services.json`.

- [x] **Step 4: OAuth consent screens**

In Google Cloud Console, check each of the two projects: APIs & Services → OAuth consent screen. The publishing status must be **In production**. "Testing" limits sign-in to listed test users. The basic scopes (openid, email, profile) need no Google verification.

- [x] **Step 5: Supabase providers**

- **Staging** Supabase → Authentication → Sign In / Providers → Google → Enable. Client IDs = the `babe-get-this-stg` web client ID. Leave Client Secret **empty**: the native ID-token flow never uses it (it's only for the browser OAuth redirect), and Firebase no longer reveals it. Watch for the browser autofilling a saved password into that field. "Skip nonce check" = **off**. Save.
- **Production** Supabase → same steps, using the `babe-get-this` web client ID.

Never paste the prod client into the staging project, or the staging client into prod.

- [x] **Step 6: Verify the new JSON files carry a web client**

Run: `grep -c '"client_type": 3' app/src/dev/google-services.json app/src/staging/google-services.json app/src/prod/google-services.json`
Expected: every count ≥ 1.

Run: `grep -c '"client_type": 1' app/src/dev/google-services.json app/src/staging/google-services.json app/src/prod/google-services.json`
Expected: every count ≥ 1 (Android clients created from the SHA-1s).

Run: `./gradlew processDevDebugGoogleServices processStagingDebugGoogleServices processProdReleaseGoogleServices && grep -rl default_web_client_id app/build/generated/res`
Expected: three `values.xml` paths, one per task.

- [x] **Step 7: README section**

Add a `### Google sign-in setup` subsection under the Build Variants table in `README.md`. It covers the flavor → Firebase → Supabase table, the SHA-1 rule (debug and release per Firebase app; the Play signing key is our upload key), and the Supabase settings (only that environment's web client ID, empty secret, nonce checks on). Done 2026-09-24; the README is the source of truth.

- [x] **Step 8: Review gate, then commit**

```bash
git add app/src/dev/google-services.json app/src/staging/google-services.json app/src/prod/google-services.json README.md
git commit -m "chore: add Google OAuth clients to each flavor's Firebase config"
```

---

### Task 3: Repository method and friendly Google error messages

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/data/AuthErrorMapper.kt:44-60`
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/data/AuthRepository.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/data/SupabaseAuthRepository.kt`
- Modify: `app/src/dev/java/com/babegetthis/android/core/auth/data/FakeAuthRepository.kt`
- Modify: `app/src/androidTest/java/com/babegetthis/android/testing/TestModules.kt` (`TestAuthRepository`)
- Test: `app/src/test/java/com/babegetthis/android/core/auth/data/AuthErrorMapperTest.kt`

**Interfaces:**
- Produces: `suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<User>` on `AuthRepository` (`com.babegetthis.android.core.error.Result`, `com.babegetthis.android.core.auth.model.User`). Task 4 calls it.
- Produces: `friendlyAuthMessage` maps ID-token and nonce failures to `"Google sign-in failed. Please try again."`

Why the mapper changes: Supabase's Google errors mention `id_token` (for example "Unacceptable audience in id_token", "Bad ID token") or `nonce`. Today these fall into the `"token"` branch and tell a Google user "Invalid code. Check the email and try again.", which is wrong.

- [x] **Step 1: Write the failing tests**

Append to `AuthErrorMapperTest.kt`, inside the class, under the `// --- friendlyAuthMessage ---` section:

```kotlin
    // Google ID-token rejections mention "id_token"/"ID token" or "nonce". They
    // must not fall through to the OTP "token" branch, which would tell a
    // Google user to check an email code that was never sent.
    @Test
    fun `google id token rejections get the google message`() {
        val google = "Google sign-in failed. Please try again."

        assertEquals(google, friendlyAuthMessage(Exception("Unacceptable audience in id_token: [abc]")))
        assertEquals(google, friendlyAuthMessage(Exception("Bad ID token")))
        assertEquals(google, friendlyAuthMessage(Exception("Nonces mismatch")))
        assertEquals(google, friendlyAuthMessage(Exception("id_token has expired")))
    }

    // The new branch must not swallow the OTP messages it sits in front of.
    @Test
    fun `otp token messages are unchanged by the google branch`() {
        assertEquals(
            "Invalid code. Check the email and try again.",
            friendlyAuthMessage(Exception("Bad token supplied")),
        )
        assertEquals(
            "That code has expired. Request a new one.",
            friendlyAuthMessage(Exception("Token has expired")),
        )
    }
```

- [x] **Step 2: Run the tests to verify they fail**

(As built: the second test above was dropped because the existing `an otp or token problem is explained` and `an expired code is explained` tests already pin those two messages.)

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.data.AuthErrorMapperTest"`
Expected: `google id token rejections get the google message` FAILS (it gets "Invalid code…" / "Authentication failed…"). The OTP test passes.

- [x] **Step 3: Add the branch**

In `friendlyAuthMessage`, make this the **first** branch of the `when`, before `"Invalid login"`. It must come before `expired` and `token`, which would otherwise match first:

```kotlin
        // Google ID-token sign-in. Checked first: these messages also contain
        // "token"/"expired" and would otherwise get the email-OTP wording.
        raw.contains("id_token", ignoreCase = true) ||
            raw.contains("ID token", ignoreCase = true) ||
            raw.contains("nonce", ignoreCase = true) ->
            "Google sign-in failed. Please try again."
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.data.AuthErrorMapperTest"`
Expected: all PASS.

- [x] **Step 5: Add the interface method**

In `AuthRepository.kt`, after `login`:

```kotlin
    // Exchanges a Google ID token (from Credential Manager) for a Supabase
    // session. Creates the account on first use, so there is no Google
    // "register". rawNonce is the unhashed nonce whose SHA-256 went to Google.
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<User>
```

- [x] **Step 6: Implement in `SupabaseAuthRepository`**

Add the imports:

```kotlin
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
```

Add after `login`:

```kotlin
    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<User> =
        runCatchingAuth {
            supabaseClient.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
            }
            // Google always supplies the email and normally the name (both land
            // in user_metadata), so the fallbacks only matter for an account
            // with no display name.
            val email = supabaseClient.auth.currentUserOrNull()?.email.orEmpty()
            persistCurrentSession(
                fallbackName = email.substringBefore("@"),
                fallbackEmail = email,
            )
        }
```

- [x] **Step 7: Implement in `FakeAuthRepository` (dev flavor)**

Add after `login`:

```kotlin
    // The Google sheet still runs in dev (same UI path as every flavor), but
    // the token is ignored — there is no Supabase to verify it against.
    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<User> {
        delay(800)
        val userId = UUID.randomUUID().toString()
        authStateManager.login(
            token = "fake-token-${UUID.randomUUID()}",
            userId = userId,
            userName = "Dev User",
            userEmail = "dev@gmail.com",
        )
        return Result.Success(User(id = userId, email = "dev@gmail.com", name = "Dev User"))
    }
```

- [x] **Step 8: Implement in `TestAuthRepository` (androidTest)**

In `TestModules.kt`, after the `login` override:

```kotlin
    override suspend fun signInWithGoogle(idToken: String, rawNonce: String) =
        guarded { signIn("google@test.com", "google") }
```

- [x] **Step 9: Verify every implementer compiles and nothing regressed**

Run: `./gradlew compileDevDebugKotlin compileStagingDebugKotlin compileProdDebugKotlin compileDevDebugAndroidTestKotlin testDevDebugUnitTest koverVerifyDevDebug`
Expected: BUILD SUCCESSFUL. (`SupabaseAuthRepository` and `FakeAuthRepository` are already on the Kover exclude list; the new mapper branch is covered by Step 1.)

- [x] **Step 10: Review gate, then commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/auth/data/ app/src/dev/java/com/babegetthis/android/core/auth/data/FakeAuthRepository.kt app/src/androidTest/java/com/babegetthis/android/testing/TestModules.kt app/src/test/java/com/babegetthis/android/core/auth/data/AuthErrorMapperTest.kt
git commit -m "feat(auth): add Google ID-token sign-in to the auth repository"
```

---

### Task 4: `LoginViewModel` Google sign-in

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/ui/LoginViewModel.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/auth/ui/LoginViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthRepository.signInWithGoogle(idToken: String, rawNonce: String): Result<User>` (Task 3).
- Produces: `LoginViewModel.signInWithGoogle(idToken: String, rawNonce: String)` and `LoginViewModel.onGoogleSignInFailed()`. Task 5 calls both. Success still emits `loginSuccess`; errors land in `uiState.errorMessage`.

- [x] **Step 1: Write the failing tests**

Append inside `LoginViewModelTest`:

```kotlin
    // --- Google ---

    // Deliberately on a pristine form: Google sign-in must not depend on the
    // email/password fields being valid.
    @Test
    fun `google sign-in forwards token and nonce and emits loginSuccess`() = runTest {
        coEvery { authRepository.signInWithGoogle("id-token", "raw-nonce") } returns
            Result.Success(User(id = "u1", email = "a@gmail.com", name = "Ann"))

        val viewModel = buildViewModel()

        viewModel.loginSuccess.test {
            viewModel.signInWithGoogle("id-token", "raw-nonce")
            awaitItem()
        }
        coVerify(exactly = 1) { authRepository.signInWithGoogle("id-token", "raw-nonce") }
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed google sign-in surfaces the error and does not signal success`() = runTest {
        coEvery { authRepository.signInWithGoogle(any(), any()) } returns
            Result.Error(AppError.AuthError("Google sign-in failed. Please try again."))

        val viewModel = buildViewModel()

        viewModel.loginSuccess.test {
            viewModel.signInWithGoogle("id-token", "raw-nonce")
            expectNoEvents()
        }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            "Google sign-in failed. Please try again.",
            viewModel.uiState.value.errorMessage,
        )
    }

    // The account sheet itself failed (no Play services, misconfigured client):
    // there is no token, so the repository must not be called.
    @Test
    fun `picker failure shows the google message without calling the repository`() {
        val viewModel = buildViewModel()

        viewModel.onGoogleSignInFailed()

        assertEquals(
            "Google sign-in failed. Please try again.",
            viewModel.uiState.value.errorMessage,
        )
        coVerify(exactly = 0) { authRepository.signInWithGoogle(any(), any()) }
    }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.ui.LoginViewModelTest"`
Expected: compilation FAILS with `Unresolved reference: signInWithGoogle` / `onGoogleSignInFailed`.

- [x] **Step 3: Implement**

In `LoginViewModel.kt`, add the import `com.babegetthis.android.core.auth.model.User`, then replace the whole `login()` function with the block below. The existing success/error handling moves unchanged into `signIn`, so both paths share it:

```kotlin
    fun login() {
        val state = _uiState.value
        if (!state.isFormValid) return
        signIn { authRepository.login(state.email, state.password) }
    }

    // Called with what the Google account sheet returned. Supabase verifies
    // the token and creates the account on first use — no separate sign-up.
    fun signInWithGoogle(idToken: String, rawNonce: String) {
        signIn { authRepository.signInWithGoogle(idToken, rawNonce) }
    }

    // The sheet failed for a reason other than the user closing it: no Play
    // services, no network, or an OAuth client missing this build's SHA-1.
    fun onGoogleSignInFailed() {
        _uiState.value = _uiState.value.copy(errorMessage = "Google sign-in failed. Please try again.")
    }

    private fun signIn(call: suspend () -> Result<User>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = call()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    // The event carries nothing. The email is exactly the kind
                    // of value that must never be attached, and identity is set
                    // separately from the Supabase session as a UUID.
                    analytics.track(AnalyticsEvent.AccountLoggedIn)
                    _loginSuccess.emit(Unit)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.ui.LoginViewModelTest"`
Expected: all PASS, including the existing email/password tests.

- [x] **Step 5: Coverage gate**

Run: `./gradlew koverVerifyDevDebug`
Expected: BUILD SUCCESSFUL.

- [x] **Step 6: Review gate, then commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/auth/ui/LoginViewModel.kt app/src/test/java/com/babegetthis/android/core/auth/ui/LoginViewModelTest.kt
git commit -m "feat(auth): Google sign-in in LoginViewModel"
```

---

### Task 5: Credential Manager sheet and the "Continue with Google" button

**Requires Task 2**: `R.string.default_web_client_id` must exist or this won't compile.

**Files:**
- Modify: `gradle/libs.versions.toml` (versions after `ktor = "3.0.3"`; libraries after `supabase-realtime`)
- Modify: `app/build.gradle.kts` (dependencies, after `implementation(libs.ktor.client.okhttp)`)
- Modify: `app/proguard-rules.pro` (append)
- Create: `app/src/main/java/com/babegetthis/android/core/auth/ui/GoogleSignIn.kt`
- Create: `app/src/main/res/drawable/ic_google_logo.xml`
- Modify: `app/src/main/res/values/strings.xml` (after `auth_sign_in`)
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/ui/LoginScreen.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/auth/ui/GoogleSignInTest.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/auth/ui/AuthScreensTest.kt`

**Interfaces:**
- Consumes: `LoginViewModel.signInWithGoogle(idToken, rawNonce)` and `LoginViewModel.onGoogleSignInFailed()` (Task 4); `R.string.default_web_client_id` (Task 2).
- Produces: `data class GoogleSignInToken(val idToken: String, val rawNonce: String)`, `suspend fun requestGoogleSignInToken(context: Context, webClientId: String): GoogleSignInToken?`, `internal fun sha256Hex(value: String): String`.

- [x] **Step 1: Dependencies**

`gradle/libs.versions.toml`, after `ktor = "3.0.3"`:

```toml
# Google sign-in: Credential Manager shows the account sheet, googleid parses
# the returned ID token. Pinned to the exact versions supabase-kt 3.0.3's own
# compose-auth module uses — proven against this Kotlin 2.0.21 toolchain.
credentials = "1.3.0"
googleid = "1.1.1"
```

After `supabase-realtime = …`:

```toml
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
```

`app/build.gradle.kts`, after `implementation(libs.ktor.client.okhttp)`:

```kotlin
    // Google sign-in. play-services-auth is the Credential Manager backend on
    // devices without a built-in provider (everything below Android 14).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
```

`app/proguard-rules.pro`, append (minify is off on this branch, but the uncommitted work on `development` turns R8 on, and without this rule Credential Manager fails in release builds):

```proguard
# Credential Manager loads its Play-services backend by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
```

- [x] **Step 2: Write the failing nonce-hash test**

Create `app/src/test/java/com/babegetthis/android/core/auth/ui/GoogleSignInTest.kt`:

```kotlin
package com.babegetthis.android.core.auth.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInTest {

    // Supabase hashes the raw nonce as lowercase SHA-256 hex and compares it
    // with the nonce Google signed into the token. Any encoding slip (missing
    // zero-padding, uppercase, base64) fails every sign-in with a nonce
    // mismatch. "abc" is the FIPS 180-2 vector; its digest has bytes < 0x10.
    @Test
    fun `sha256Hex matches the standard test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }
}
```

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.ui.GoogleSignInTest"`
Expected: compilation FAILS with `Unresolved reference: sha256Hex`.

- [x] **Step 3: Create `GoogleSignIn.kt`**

```kotlin
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
```

- [x] **Step 4: Run the hash test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.ui.GoogleSignInTest"`
Expected: PASS.

- [x] **Step 5: Google logo and string**

Create `app/src/main/res/drawable/ic_google_logo.xml`. This is the standard four-colour "G"; Google's branding rules require it on sign-in buttons, and it's drawn untinted:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="20dp"
    android:height="20dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path
        android:fillColor="#EA4335"
        android:pathData="M24,9.5c3.54,0 6.71,1.22 9.21,3.6l6.85,-6.85C35.9,2.38 30.47,0 24,0 14.62,0 6.51,5.38 2.56,13.22l7.98,6.19C12.43,13.72 17.74,9.5 24,9.5z" />
    <path
        android:fillColor="#4285F4"
        android:pathData="M46.98,24.55c0,-1.57 -0.15,-3.09 -0.38,-4.55H24v9.02h12.94c-0.58,2.96 -2.26,5.48 -4.78,7.18l7.73,6c4.51,-4.18 7.09,-10.36 7.09,-17.65z" />
    <path
        android:fillColor="#FBBC05"
        android:pathData="M10.53,28.59c-0.48,-1.45 -0.76,-2.99 -0.76,-4.59s0.27,-3.14 0.76,-4.59l-7.98,-6.19C0.92,16.46 0,20.12 0,24c0,3.88 0.92,7.54 2.56,10.78l7.97,-6.19z" />
    <path
        android:fillColor="#34A853"
        android:pathData="M24,48c6.48,0 11.93,-2.13 15.89,-5.81l-7.73,-6c-2.15,1.45 -4.92,2.3 -8.16,2.3 -6.26,0 -11.57,-4.22 -13.47,-9.91l-7.98,6.19C6.51,42.62 14.62,48 24,48z" />
</vector>
```

In `app/src/main/res/values/strings.xml`, after `auth_sign_in`:

```xml
    <string name="auth_continue_with_google">Continue with Google</string>
```

- [x] **Step 6: Write the failing screen test**

In `AuthScreensTest.kt`, after `login shows its fields and primary action`:

```kotlin
    // Rendering only: tapping it opens the real Credential Manager sheet, which
    // needs Play services — that path is checked on a device (plan Task 6).
    @Test
    fun `login offers Google sign-in`() {
        login()

        compose.onNodeWithText("Continue with Google").assertExists()
    }
```

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.ui.AuthScreensTest"`
Expected: `login offers Google sign-in` FAILS (node not found).

- [x] **Step 7: Add the button to `LoginScreen`**

Add imports:

```kotlin
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
```

After `val snackbarHostState = remember { SnackbarHostState() }`:

```kotlin
    // The Google sheet is UI, so it runs in this composable's scope with the
    // Activity context; only the resulting token reaches the ViewModel. The
    // client ID comes from this flavor's google-services.json, which is what
    // ties each build to its own Firebase + Supabase environment.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id)
```

Between the login `Button { … }` and the `Spacer(modifier = Modifier.height(24.dp))` above "Navigate to register", insert:

```kotlin
            Spacer(modifier = Modifier.height(12.dp))

            // Only the two credential failures are caught — a blanket catch
            // would also swallow CancellationException when the screen leaves
            // composition mid-sheet. Closing the sheet returns null: no-op.
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            requestGoogleSignInToken(context, webClientId)
                                ?.let { viewModel.signInWithGoogle(it.idToken, it.rawNonce) }
                        } catch (e: GetCredentialException) {
                            viewModel.onGoogleSignInFailed()
                        } catch (e: GoogleIdTokenParsingException) {
                            viewModel.onGoogleSignInFailed()
                        }
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.auth_continue_with_google),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
```

- [x] **Step 8: Run the screen tests, then the full gate**

Run: `./gradlew testDevDebugUnitTest --tests "com.babegetthis.android.core.auth.ui.AuthScreensTest"`
Expected: all PASS.

Run: `./gradlew testDevDebugUnitTest koverVerifyDevDebug assembleStagingDebug assembleProdRelease lintDevDebug`
Expected: BUILD SUCCESSFUL. (`GoogleSignInKt` is outside the Kover include list, like every other `ui` file except `AuthValidationKt`.)

- [x] **Step 9: Review gate, then commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/proguard-rules.pro app/src/main/java/com/babegetthis/android/core/auth/ui/GoogleSignIn.kt app/src/main/java/com/babegetthis/android/core/auth/ui/LoginScreen.kt app/src/main/res/drawable/ic_google_logo.xml app/src/main/res/values/strings.xml app/src/test/java/com/babegetthis/android/core/auth/ui/GoogleSignInTest.kt app/src/test/java/com/babegetthis/android/core/auth/ui/AuthScreensTest.kt
git commit -m "feat(auth): Continue with Google on the login screen"
```

---

### Task 6: Device verification per environment (manual)

No code. Each check shows that a flavor reaches **its own** Supabase project. Use an emulator or phone with a Google account signed in (a Play-services image, not AOSP).

- [ ] **Step 1: dev** — `./gradlew installDevDebug`. Login → Continue with Google → pick an account. Expected: the app lands signed in as "Dev User". No Supabase project gets a new user.
- [ ] **Step 2: staging** — `./gradlew installStagingDebug`. Same flow. Expected: signed in with your real Google name. The user appears under **staging** Supabase → Authentication → Users with provider `google`, and does **not** appear in production.
- [ ] **Step 3: prod (local release)** — `./gradlew installProdRelease` (upload-key signed). Same flow. Expected: the user appears in **production** Supabase only.
- [ ] **Step 4: prod (Play-signed)** — upload to the internal testing track, install from Play, same flow. Expected: works. If the sheet fails with "Google sign-in failed", the Play App Signing SHA-1 is missing (Task 2 Step 3).
- [ ] **Step 5: account linking** — in staging, register with email/password using the same Gmail address and confirm it. Sign out, then Continue with Google. Expected: same Supabase user ID (identities linked), and lists/shares still present.
- [ ] **Step 6: cancel and offline** — close the sheet: no snackbar, still on login. Turn on airplane mode and tap the button: a snackbar appears with no crash.
- [ ] **Step 7: delete account** — as a Google user in staging: Profile → Delete account. Expected: signed out, and the user is gone from staging Supabase.
- [ ] **Step 8: Review gate** — report the results. Nothing to commit.

---

## Out of scope (deliberately)

- A Google button on the Register screen or in `AuthPromptDialog`. Login creates accounts, so add these only if users look for them there.
- Apple, Facebook or GitHub login. These need the browser OAuth flow and deep links; each is a separate plan.
- Telling Google and email logins apart in analytics. `AccountLoggedIn` carries no payload today; add a `method` only when someone needs the split.
- Recording sheet failures to Crashlytics. Add it if Task 6 Step 4 ever fails in the field without a clear cause.

## Post-review changes (2026-09-25)

A staff-level review of Tasks 1–5 led to these changes:
- The Google flow moved out of `LoginScreen` into `LoginViewModel.signInWithGoogle(activityContext)`, behind a `GoogleTokenRequester` seam (`CredentialManagerTokenRequester` in production, bound in `core/auth/di/GoogleSignInModule`). The flow runs in `viewModelScope`, so a rotation mid-sheet no longer drops the chosen account.
- `signIn` sets `isLoading` before launching and ignores re-entry, so a double tap can't open two sheets.
- The offline check lives in the requester (`GoogleSignInOfflineException`), which keeps the journey test independent of the device's network.
- Sheet failures are recorded as `AppError.UnknownError` (the reporting policy drops `AuthError`). `NoCredentialException` gets its own user message and isn't reported.
- There is one `GOOGLE_SIGN_IN_FAILED` constant. `persistCurrentSession` derives a missing name from the email.
- New journey test: `AuthAndVoiceGateTest.continueWithGoogleSignsIn`.
