# Authentication via Supabase (and removing the custom auth API)

**Status:** Accepted · **Date:** 2026-06-20

## ⏳ Pending setup (do this before auth can run)

- [ ] Create the Supabase project, then add credentials to **`local.properties`**
      (gitignored — safe; the anon key is public by design):
      ```properties
      SUPABASE_URL=https://your-project.supabase.co
      SUPABASE_ANON_KEY=your-anon-public-key
      ```
- [ ] In the Supabase dashboard: **Authentication → Providers → Email** enabled,
      and **turn OFF "Confirm email"** for now so sign-up logs in immediately
      during testing. (Turn it back on before launch.)
- [ ] To test the **real** Supabase path, run the **staging** variant. The **dev**
      variant still uses `FakeAuthRepository` (no network), by design.

## Context

The app gained a "logged-in users only" requirement. An earlier session had
already scaffolded auth against our **own Node backend** (Retrofit endpoints
`auth/register`, `auth/login`). Separately, we decided the Node backend should
**not** own auth — it's a secure AI gateway for audio transcription, and
**Supabase** owns identity (auth now, realtime later).

So the custom-auth network code was correct work, but now redundant.

## Decision

1. **Use Supabase (supabase-kt SDK) for authentication.** `SupabaseAuthRepository`
   now calls `supabase.auth` for sign-up / sign-in / sign-out.
2. **Remove the custom-auth networking** that talked to our Node backend.
3. **Keep the rest of the network layer** — it's still needed for the upcoming
   audio-transcribe API call to the Node gateway.

## What was removed

- `core/auth/data/AuthApiService.kt` — Retrofit auth endpoints.
- `core/data/network/dto/AuthDtos.kt` — login/register request + response DTOs.
- `NetworkModule.provideAuthApiService()` — the Hilt provider for the above.

## What was kept (and why)

| Kept | Reason |
| --- | --- |
| `Retrofit` + `OkHttpClient` (in `NetworkModule`) | the **audio-transcribe** API still calls our Node backend (`BASE_URL`) |
| `AuthInterceptor` | will attach the **Supabase** access token to transcribe calls |
| `AuthAuthenticator` | 401 handling on those calls |
| `AuthStateManager`, `TokenManager` | unchanged single source of truth for "is the user logged in?" |
| `AuthRepository` interface, Login/Register UI, navigation | untouched — only the implementation behind the interface changed |

## How it fits together now

```
Auth      → Supabase SDK (supabase.auth)  → bridged into AuthStateManager/TokenManager
Transcribe → Retrofit (BASE_URL, Node)     → AuthInterceptor attaches Supabase JWT
```

`SupabaseAuthRepository` signs the user in with Supabase, then copies the access
token + user info into our existing encrypted storage. The rest of the app
(navigation, profile screen) is unaware Supabase exists — we only swapped the
data source behind the repository interface.

## SDK version note

Pinned to **supabase-kt 3.0.3 / Ktor 3.0.3**, not the latest 3.6.x. The newer
line requires Kotlin 2.3, but this project is on Kotlin 2.0.21. Upgrading the
whole toolchain right before a deadline isn't worth it; bumping Kotlin + SDK
together is deferred to its own task.

## Follow-ups

- **Google social login** — Stage 3 (needs Google Cloud OAuth client + SHA-1).
- **Token refresh for transcribe** — Supabase auto-refreshes its session, but
  `TokenManager` holds the token captured at login. Before shipping the
  transcribe call, have `AuthInterceptor` read the *current* token from
  `supabase.auth` instead of the cached one.
- Supersedes the auth-API parts of `docs/auth-network-layer.md`.
