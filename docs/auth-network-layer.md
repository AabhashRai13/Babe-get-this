# Authentication & Network Layer Implementation

**Date:** April 2026
**Status:** Phases 1-5 complete, Phase 6 (WebSocket sync) pending

## Overview
Added user authentication (login/register) with a multi-environment network layer. The app now starts on a login screen and navigates to the main app after successful auth. In the dev flavor, no real API calls are made — fake responses are returned.

## Build Configuration
- **Product flavors** added: `dev`, `staging`, `prod` — each with its own `BASE_URL`, `WS_URL`, and app ID suffix (`.dev`, `.staging`)
- **New dependencies**: Retrofit + OkHttp (networking), kotlinx.serialization (JSON parsing), EncryptedSharedPreferences (secure token storage)
- **INTERNET permission** added to manifest
- **`buildConfig = true`** enabled for `BuildConfig.BASE_URL` / `BuildConfig.WS_URL` access

## Auth State Management
- `AuthState` sealed class — `Loading`, `Authenticated`, `Unauthenticated`
- `TokenManager` — stores auth token + user ID in AES-256 encrypted SharedPreferences
- `AuthStateManager` — singleton exposing `StateFlow<AuthState>`. On app start, checks for saved token. Navigation reacts automatically.

## Network Infrastructure
- `NetworkModule` (Hilt) — provides `OkHttpClient`, `Retrofit`, and API service instances
- `AuthInterceptor` — attaches Bearer token to every outgoing request
- `AuthAuthenticator` — handles 401 responses by auto-logging the user out
- `AuthDtos` — `@Serializable` data classes for login/register request and response payloads
- `AppError` extended with `AuthError` and `UnauthorizedError`
- `safeCall()` extended to handle Retrofit `HttpException`, `ConnectException`, `SSLException`

## Auth Repository (Interface + Flavor Swap)
- `AuthRepository` interface — `login()`, `register()`, `logout()`
- `RealAuthRepository` — calls Retrofit `AuthApiService`, saves token on success (used in staging/prod)
- `FakeAuthRepository` — returns fake success after 800ms delay, no network calls (used in dev). Special test emails: `error@test.com` → server error, `taken@test.com` → email taken, `wrong@test.com` → invalid credentials
- Flavor-specific Hilt `AuthModule` binds the correct implementation per flavor

## Auth UI
- `LoginScreen` — email, password, sign-in button, link to register
- `RegisterScreen` — name, email, password, confirm password, create account button
- Both have loading spinners, snackbar error display, and input validation
- `LoginViewModel` / `RegisterViewModel` — call `AuthRepository`, expose `UiState` via `StateFlow`

## Navigation
- `BgtNavGraph` updated — start destination is now auth-aware (`LOGIN` or `SHOPPING_LIST` based on `AuthState`)
- `MainActivity` injects `AuthStateManager`, calls `initialize()` on create
- New routes: `LOGIN`, `REGISTER`

## File Structure (new files)
```
core/auth/
  model/AuthState.kt
  model/User.kt
  data/AuthRepository.kt          (interface)
  data/RealAuthRepository.kt
  data/AuthApiService.kt           (Retrofit)
  data/TokenManager.kt
  data/AuthStateManager.kt
  ui/LoginScreen.kt
  ui/LoginViewModel.kt
  ui/RegisterScreen.kt
  ui/RegisterViewModel.kt

core/data/
  di/NetworkModule.kt
  network/AuthInterceptor.kt
  network/AuthAuthenticator.kt
  network/dto/AuthDtos.kt

app/src/dev/.../FakeAuthRepository.kt
app/src/dev/.../AuthModule.kt       (binds Fake)
app/src/staging/.../AuthModule.kt   (binds Real)
app/src/prod/.../AuthModule.kt      (binds Real)
```

## How to Test
1. Switch build variant to **devDebug** in Android Studio
2. Run the app → login screen appears
3. Enter any email/password → fake login succeeds → navigates to shopping lists
4. Kill and reopen → goes straight to shopping lists (token persisted)
5. Use `error@test.com` to test error handling

## Architecture Decisions

### Why Retrofit + OkHttp (not Ktor)?
Industry standard for Android + Hilt. OkHttp interceptors handle auth token injection and 401 auto-logout out of the box. Ktor is better for KMP projects.

### Why kotlinx.serialization (not Gson/Moshi)?
Kotlin-native, compile-time code generation, no reflection. Already using KSP for Room and Hilt.

### Why Build Flavors (not runtime config)?
Compile-time separation means dev fakes never ship to production. Each flavor gets its own app ID so dev/staging/prod install side-by-side. Flavor-specific Hilt modules swap implementations cleanly.

### Why EncryptedSharedPreferences (not DataStore)?
Auth tokens are sensitive. EncryptedSharedPreferences uses AES-256 via Android Keystore. DataStore doesn't encrypt by default.

### Why Interface-based fakes (not interceptor mocks)?
Cleaner separation. FakeAuthRepository can simulate delays, specific error scenarios, and edge cases. No OkHttp needed in dev at all.

## Still Pending (Phase 6)
- `SyncManager` — WebSocket for real-time list sync between partners
- `NetworkMonitor` — connectivity state observer
- Repository modifications for outgoing sync notifications
- Socket architecture: incoming WebSocket messages → write to Room → Flow propagates to UI
