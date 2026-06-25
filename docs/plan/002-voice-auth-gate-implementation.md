# 002 — Voice auth-gate UX implementation

**Status:** Planned (not yet started)
**Date drafted:** 2026-05-18
**Implements:** [product-decisions/003 — Voice-to-list requires authentication](../product-decisions/003-voice-to-list-requires-authentication.md)
**Builds on:** [plan/001 — Voice-to-list implementation](001-voice-to-list-implementation.md) (Steps 1–8 complete)

## Goal

Gate the Voice tile in the create-list chooser behind authentication. Signed-out users see a lock affordance; tapping it explains the trade and routes to login. Signed-in users see the existing behavior unchanged. The voice sheet itself, the ViewModel, the recorder, and the API contract require no changes — the entire gate lives in the chooser and in the navigation wiring.

This is a UX-only change. No new dependencies. No backend coordination needed.

## Architecture at a glance

```
ShoppingListScreen
  ├─ collects authStateManager.authState              ← already in place
  ├─ derives `isLoggedIn` boolean                     ← already in place
  └─ CreateListChooserSheet (NEW behavior)
        ├─ Type tile        → unchanged
        └─ Voice tile
              ├─ when isLoggedIn  → showVoiceSheet = true   (current behavior)
              └─ when !isLoggedIn → shows lock badge
                                  → tap opens small AuthGateDialog
                                  → "Sign in" routes to LoginScreen
                                  → "Not now" dismisses

BgtNavGraph
  └─ ShoppingListScreen gains an `onNavigateToLogin` callback
     (mirrors how ShoppingItemsScreen already does it)

LoginScreen (existing)
  └─ onLoginSuccess pops back; chooser reappears; user re-taps Voice
     (now unlocked) and proceeds as normal
```

## Decisions locked before coding

| Decision | Choice | Why |
|---|---|---|
| Where the gate lives | In the chooser tile, not inside the voice sheet | The sheet is reusable infrastructure. Auth is a v1 product policy. Keep policy at the call site, not in `core/voice/`. |
| Locked tile behavior | Tap opens a small explainer dialog (AuthGateDialog) with Sign in / Not now buttons | Decision doc 003 explicitly says the gate "has to explain *why*." A bare login redirect skips that obligation. |
| Post-login resume UX | Pop back to list screen; user re-taps "+" → "Voice" | Simpler than auto-reopening the chooser/sheet across a nav round-trip. We accept the one extra tap in exchange for zero state-restoration complexity. Revisit if user feedback flags this. |
| Voice sheet itself | No changes | The sheet only runs after the gate is cleared. It assumes a signed-in user and the existing 401-recovery flow (via `AuthAuthenticator`) handles mid-session token expiry. |
| Existing 401 behavior | Unchanged | `AuthInterceptor` already attaches the bearer token. `safeCall` already maps 401 → `AppError.UnauthorizedError`. The auto-logout flow already exists. We are not touching error handling. |
| Chooser layout when locked | Voice tile keeps its position and size; lock icon overlays the top-right corner; tile colors dim slightly | Layout shift between auth states would be jarring. Position constant; affordance signals the state. |

## What's already in place (don't rebuild)

- `authStateManager.authState` is collected in `ShoppingListScreen` and a local `isLoggedIn: Boolean` is derived (`feature/shoppinglist/ui/ShoppingListScreen.kt:104`).
- `Routes.LOGIN` exists; `LoginScreen` is wired with `onLoginSuccess = popBackStack(LOGIN, inclusive = true)` (`navigation/BgtNavGraph.kt:74`).
- `ShoppingItemsScreen` already takes `onNavigateToLogin` — copy that pattern for `ShoppingListScreen`.
- `AuthInterceptor`, `AuthAuthenticator`, `Result`, `AppError` — all handle the mid-session 401 case already. No changes there.
- `material-icons-extended` is in deps as of plan 001 Step 8 — `Icons.Outlined.Lock` is available.

## Step-by-step

### Step 1 — Add `onNavigateToLogin` to `ShoppingListScreen`

**File touched:** `feature/shoppinglist/ui/ShoppingListScreen.kt`

Add a new optional parameter to the composable signature, defaulting to `{}` so existing call sites don't break:

```kotlin
fun ShoppingListScreen(
    authStateManager: AuthStateManager,
    onNavigateToList: (listId: String, listName: String) -> Unit = { _, _ -> },
    onNavigateToNewList: (listId: String, listName: String) -> Unit = { _, _ -> },
    onNavigateToLogin: () -> Unit = {},                 // ← NEW
    viewModel: ShoppingListViewModel = hiltViewModel(),
)
```

**File touched:** `navigation/BgtNavGraph.kt`

Wire it in the `composable(Routes.SHOPPING_LIST)` block:

```kotlin
ShoppingListScreen(
    authStateManager = authStateManager,
    onNavigateToList = { ... },
    onNavigateToNewList = { ... },
    onNavigateToLogin = { navController.navigate(Routes.LOGIN) },   // ← NEW
)
```

**Verify:** project builds (`./gradlew assembleDebug`).

---

### Step 2 — Lock affordance on the Voice tile

**File touched:** `feature/shoppinglist/ui/ShoppingListScreen.kt`

Pass `isLoggedIn` (or just `voiceLocked: Boolean = !isLoggedIn`) into `CreateListChooserSheet`. Update `ChooserTile` to accept an optional `locked: Boolean` parameter and render a small lock badge in the top-right corner when true (use `Icons.Outlined.Lock` at ~14dp inside a tinted circle; align via a `Box` with `Alignment.TopEnd`).

Visual rule: locked tile keeps the same size and position; tint shifts to a slightly lower-emphasis container color so the lock state is read at a glance without layout shift.

The tile's `onClick` should still fire when locked — the click now opens the explainer dialog instead of the sheet. Pass two callbacks down from the screen:

```kotlin
CreateListChooserSheet(
    onDismiss = ...,
    onPickType = ...,
    onPickVoice = {
        if (isLoggedIn) {
            showCreateChooser = false
            showVoiceSheet = true
        } else {
            showCreateChooser = false
            showAuthGate = true                          // new local state
        }
    },
    voiceLocked = !isLoggedIn,                           // ← NEW
)
```

**Verify:** signed-out: lock badge visible; signed-in: tile renders identically to today.

---

### Step 3 — `AuthGateDialog` (small explainer)

**New file (kept inline in `ShoppingListScreen.kt` per the file-structure convention from plan 001):** a private composable, ~30 lines, using `AlertDialog`.

```kotlin
@Composable
private fun AuthGateDialog(
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in to use voice") },
        text = {
            Text(
                "Voice creates lists in seconds — and your account lets you " +
                "sync them with your partner across devices."
            )
        },
        confirmButton = { TextButton(onClick = onSignIn) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
```

Copy notes:
- Lead with the user's benefit ("voice creates lists in seconds"), not the policy ("we require login").
- Name the unlocked value ("sync them with your partner") — this is what makes the trade feel fair, per decision doc 003.
- Keep "Not now" as the dismiss (not "Cancel") — softer, less of a dead-end feel.

Wire it in the screen:

```kotlin
var showAuthGate by remember { mutableStateOf(false) }

if (showAuthGate) {
    AuthGateDialog(
        onSignIn = {
            showAuthGate = false
            onNavigateToLogin()
        },
        onDismiss = { showAuthGate = false },
    )
}
```

**Verify:** signed-out → tap Voice → dialog appears with both buttons. Sign in routes to login. Not now dismisses cleanly.

---

### Step 4 — Post-login return path (confirm, don't build)

`LoginScreen.onLoginSuccess = popBackStack(Routes.LOGIN, inclusive = true)` already returns the user to the list screen. After this lands, `isLoggedIn` recomposes to true automatically (via the existing `authStateManager.authState` Flow), so the Voice tile in the chooser will be unlocked the next time the user taps "+".

**No code to write — verify only:**
- Signed out → tap "+" → tap Voice → "Sign in" → login screen → enter creds → land back on list screen → tap "+" → tap Voice → voice sheet opens (no lock).
- Register flow same: register screen → success → land back on list screen → unlocked.

If this doesn't work in practice (e.g., `authState` doesn't recompose the chooser), revisit by hoisting `isLoggedIn` into a stable state holder or by passing a `LaunchedEffect(isLoggedIn)` signal.

---

### Step 5 — Profile sheet "Sign in" entry (optional, same PR)

The profile icon in the top app bar only shows when `isLoggedIn`. Anonymous users currently have no obvious in-app login entry. The voice auth-gate gives us one (and shipping voice without it would leave a dead chooser tile for users who dismiss the gate once and forget where to find login).

**Lightweight option:** add a small "Sign in" text button to the top app bar's action slot when `!isLoggedIn`, mirroring how the profile icon shows when logged in. Tap → `onNavigateToLogin()`. Five lines in `ShoppingListScreen.kt`'s `topBar` config.

**Defer if:** scope creep concern. The voice-gate dialog is itself an entry point, which is enough for v1. Re-evaluate after launch if users complain about discoverability.

---

### Step 6 — Update technical-decisions/002 with the auth-gate note

**File touched:** `docs/technical-decisions/002-voice-to-list-input.md`

Add a short subsection or one-paragraph note under "Architecture" that says:

> The voice flow assumes an authenticated user — gated at the chooser per product-decisions/003. The sheet, ViewModel, and recorder are unchanged; auth lives at the call site so `core/voice/` stays reusable.

Cross-link to `product-decisions/003` and to this plan.

---

### Step 7 — Manual test pass

On device (or emulator that has a logout-then-recompose loop):

1. Fresh install / signed-out state → "+" → chooser → Voice tile shows lock badge.
2. Tap locked Voice → AuthGateDialog appears with correct copy and two buttons.
3. Tap "Not now" → dialog dismisses, chooser does not reopen, no nav.
4. Tap "Sign in" → login screen appears, dialog gone, chooser gone.
5. Cancel login (back press) → land on list screen, anonymous-state unchanged.
6. Complete login → land on list screen, profile icon now visible.
7. "+" → chooser → Voice tile no longer locked → tap → voice sheet opens.
8. Run a full voice capture end-to-end → list created → navigates as expected.
9. Sign out from profile sheet → list screen recomposes anonymous → "+" → Voice tile locked again. Confirms reactivity.

## Suggested ordering (≈half day window)

| Day | Steps | Notes |
|---|---|---|
| Morning | 1, 2 | Plumbing + tile lock. Verify in isolation. |
| Midday | 3, 4 | Dialog + post-login round-trip. Most of the UX testing happens here. |
| Afternoon | 5 (decide), 6, 7 | Profile-sheet entry call, doc update, full manual pass. |

## What to skip / not do

- **Don't** add new error types or change `safeCall`. The auth-gate prevents anonymous users from ever reaching the voice endpoint, so the existing 401 path only needs to handle mid-session token expiry — already handled.
- **Don't** modify `core/voice/` files. Auth gating is a host responsibility.
- **Don't** auto-reopen the voice sheet after login. Accept the one extra tap; revisit only if user feedback says it matters.
- **Don't** introduce a "trial" / quota / device-token flow. Explicitly rejected in decision doc 003 (Alternatives A and B). If we ever want them, write a new decision doc first.
- **Don't** change the API doc for voice. The endpoint contract is unchanged; only the *client-side enforcement* of auth changes.

## Open follow-ups (after the feature works)

- Analytics on the AuthGateDialog: dialog-shown count, "Sign in" tap-through rate, login-completion rate. Feeds the decision-doc 003 success criteria.
- Copy A/B: "Sign in to use voice" vs. an alternative framing. Park until baseline conversion data exists.
- Consider whether the locked tile should be a longer-press educational moment instead of a tap-into-dialog. Park.

## Acceptance checklist (before merging)

- [ ] Steps 1, 2, 3, 4, 6, 7 complete; Step 5 explicitly decided one way or the other.
- [ ] Build green on all variants (`./gradlew assembleDebug`).
- [ ] Manual test pass #1–#9 above all clear.
- [ ] No changes to `core/voice/`, `VoiceModule.kt`, `VoiceRepository.kt`, `AudioRecorder.kt`, or any ViewModel under `core/voice/`.
- [ ] Decision doc 003 and technical-decisions/002 cross-reference each other.
