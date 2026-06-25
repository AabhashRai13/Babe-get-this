# Pending Refactor — voice-transcribe-auto-list

Created 2026-06-24. Worked through 2026-06-25.

Staff-level review of the `voice-transcribe-auto-list` change. Items 1–9 are
**done**; 10 is deferred (it's a feature, not a refactor). Manual device checks
at the bottom still stand.

---

## ✅ Done

### 1. Resolve the in-progress merge — DONE
All unmerged paths staged; merge concluded.

### 2. Remove the dead navigation flow — DONE
Deleted `_navigateToList` / `navigateToList` (and the emit in `persistDrafts`)
from `VoiceCaptureViewModel`. Navigation is driven solely by
`ShoppingListViewModel.navigateToList`. Fixed the stale comment in
`VoiceCaptureSheet`.

### 3. Read timeout too tight for transcription — DONE
`provideTranscribeApiService` now builds a transcribe-only OkHttp client via
`newBuilder().readTimeout(60s)` (copies the shared interceptors), on its own
Retrofit. The 30s default is untouched for every other call.

### 4. `safeCall` maps 400–499 → `AuthError` — DONE
Added an `onClientError: (code) -> AppError` hook to `safeCall` (mirrors
`onUnauthorized`). Default unchanged for back-compat; `RemoteVoiceRepository`
overrides it to "Couldn't process that recording. Please try again."

### 5. Silent, un-undoable empty-list deletion — DONE (decision made)
Scoped the `onCleared → deleteListIfEmpty` auto-clean to **newly-created** lists
only (via the existing `isNew` nav arg). Opening an existing empty list and
backing out no longer deletes it. No toast — the data is empty so nothing is
lost, and a cross-screen snackbar from `onCleared` isn't worth the plumbing.

### 6. Tests for the new logic — DONE
- `ToItemDraftTest` — quantity+unit flatten (all four shapes).
- `AutoNameVoiceListTest` — single / multi / empty / blank / over-long.
- `DeleteListIfEmptyTest` — deletes-when-empty / keeps-when-not (mockk DAOs).
- Also fixed the pre-existing `ShoppingItemsViewModelTest` (was missing
  `listRepository` / `applicationScope` and never compiled).

### 7. `WS_URL` pointed at dead `babegetthis.com` — DONE
Repointed staging/prod to the live Railway host and commented them as
placeholders (websockets not implemented yet).

### 9. `autoNameVoiceList` unbounded length — DONE
`.take(40)` on the first item's name. Extracted to a top-level `internal`
function so it's unit-testable without building the ViewModel.

---

## ⏭ Deferred

### 10. Voice-flow analytics + error logging
A feature, not a refactor — instrument start/stop/transcribe-fail/persist-fail.
Tracked in CLAUDE.md's TODO. Do it before debugging-by-device gets painful.

### 8. Verify MIME `"audio/mp4"` (nit)
Correct for AAC-in-MPEG4 `.m4a`; confirm the backend doesn't insist on
`audio/m4a`. Covered by the manual first-call test below — no code change.

---

## Still open (manual, needs device)
- Record on **staging** → list auto-creates with first-item name → navigates in;
  items carry quantity + note, and now a resolved `categoryId` (no longer null).
- Failure paths: empty/garbled audio → `Failed` (no empty list); airplane mode →
  `Failed` + retry. Verify "Type instead" opens the type dialog.
- Re-test the reopen bug: record → open list → back → tap mic → records on first tap.
