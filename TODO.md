# TODO

Consolidated backlog for **Babe, Get This**. Prioritized work up top; longer-term
feature ideas and the v1/v2 roadmap below.

---

## P1 — quick, high-leverage

- [ ] **Inline validation on the Register form** (`RegisterScreen.kt` / `RegisterViewModel.kt`) — validate email, password length, and confirm-match live; show per-field errors via Material 3 `supportingText` / `isError`; enable the submit button only when valid. Drop the submit-only snackbar.
- [ ] **Notification permission pre-prompt screen** before the OS `POST_NOTIFICATIONS` dialog — value-framed copy ("We'll nudge you when your partner adds something"), Allow / Not now, shown once, never blocks list creation.

## P2 — deepen the first-value moment

- [ ] **Celebrate the first aha moment** — one-time light celebration when the first list is created or first item checked, beyond the existing "List created!" toast.

## P3 — polish

- [ ] **Tighten auth + empty-state copy** in `strings.xml` to sell the shared-couple outcome (strings only; approve a before/after table first).

---

## Bugs & tech debt

- [ ] **Mic record button needs a press sound** — play an audio cue when the record button is pressed (acts as feedback / a "go ahead and talk" cue for people).
- [ ] **Session expires too often + app records audio then fails with "session expired".**
  Root cause: Supabase Auth auto-refreshes the session, but the app caches `accessToken`
  ONCE at login (`TokenManager`) and `AuthInterceptor` sends that stale copy forever — the
  refreshed token is never read. NOT a Supabase dashboard / JWT-expiry issue.
  Short fix: observe `supabaseClient.auth.sessionStatus` at app start →
  on Authenticated `tokenManager.saveToken(session.accessToken)` (write the rotated token
  back), on NotAuthenticated `authStateManager.logout()`. Optional belt-and-suspenders:
  pre-flight ensure-session before recording as a fail-fast.

---

## Good to have non priority Feature backlog

- [ ] **Stale-list WorkManager** — a worker that finds stale lists (criteria TBD) and moves them to a history list, with a way to move them back to active/complete. (Good UI + learning opportunity.)
- [ ] **Delete-with-reason** — when someone deletes an item, let them add an optional note on why, to make partner communication easier.
- [ ] **"Can't find the item" flow** — a fast way to suggest an alternative that beats call/chat: snap an image + a quick approve/reject. Plus a history-based suggestion algorithm.

---

## v1 release strategy

- [ ] Complete the offline-first method (local-first, fully functional without internet).
- [ ] Voice-to-list (capture a whole list by speaking). *(Voice add-items to an existing list already shipped.)*
- [ ] Share Pdf version list via, message, email, whatever

## v2 roadmap

- [ ] Shareable real time list
- [ ] Camera/gallery → auto-fill the add-item form from an image (incl. recipe photo → list).
- [ ] "Store room" — when a list is completed, move grocery items into a store; mark items as finished there to auto-carry into the next list.
