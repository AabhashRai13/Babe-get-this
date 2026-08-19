# 002 — Voice-to-list: speak a list, create it, populate it

**Status:** Accepted (revised 2026-05-14)
**Date:** 2026-05-05 (revised 2026-05-14 — second revision same day)
**Area:** `feature/shoppinglist` — list creation flow; new `core/voice/` capability
**Related plan:** [`docs/plan/001-voice-to-list-implementation.md`](../plan/001-voice-to-list-implementation.md)

## Context

The single biggest friction point in a grocery list app is **capture**: the user remembers they need things while doing something else (loading the dishwasher, toddler yelling, one hand wet). Typing into a phone keyboard at that moment is the friction that causes items to never make it onto a list at all.

Voice-to-list is the v1 marquee feature (see product-decisions/002). This doc is the **technical design** for the feature itself.

### What "voice-to-list" means in v1 (decided 2026-05-14)

The v1 voice flow is **list creation, not item appending**. User opens the app, hits the existing "Create list" CTA on the Lists screen, picks **Voice** from a two-option chooser (Type / Voice), speaks naturally — *"one crate eggs, two-litre coke, dish soap"* — and the app:

1. Creates a new list.
2. Auto-names it (naming strategy is an open product question — v1 ships with a placeholder).
3. Inserts the spoken items as rows on that list.
4. Returns the user to the Lists screen (or opens the new list — open UX question).

This shape was chosen over "open an existing list and dictate items to add to it" because the capture-friction moment voice is meant to remove is almost always **starting** a list (post-fridge-check, pre-store-trip), not editing one that's already open. Forcing the user to navigate into a target list first re-introduces the friction.

Adding items to an existing list via voice is **not in v1 scope**. It is a natural second consumer of the same `core/voice/` capability and will be picked up later if it lands as a v1.5 / v2 ask.

### Why this doc was revised

Two pivots from the original 2026-05-05 draft, both decided on 2026-05-14:

1. **Recognition + parsing moved off-device to a backend** (Groq Whisper for transcription, Claude Haiku 4.5 for item extraction). Original design used framework `SpeechRecognizer` + a pure-Kotlin delimiter parser, motivated by offline-first. Rejected because recognition quality on framework SR varies wildly across OEMs, parsing natural sentences with a delimiter splitter produces "Get Milk" / "Also Some Bananas" garbage, and voice is an enhancement input — typing remains the fully-offline core write path.
2. **Voice creates a list rather than adding to one.** Original design put a mic icon inside the existing list's add-item row. Reframed per the section above.

Old drafts are preserved as rejected alternatives below.

## Goals

1. From the Lists screen, the user can tap "Create list," choose **Voice**, speak items, see them parsed and reviewable, confirm, and have a new list created with those items in one transaction.
2. Recognition and parsing quality is high enough that the confirmation step is "verify and tap," not "fix every row."
3. The feature degrades clearly when the network is unavailable. Voice does not work offline; the **Type** option on the same CTA does.
4. Permission flow follows current Android best practice (rationale → system prompt → settings deep-link if denied permanently).
5. The Android side is fully implementable and demoable against a mock repository before the backend exists. Swap one Hilt binding to go live.
6. Architecture supports a future second consumer (voice → add items to existing list) without modifying the voice capability itself.

## Non-goals (v1)

- Voice editing / deleting / completing items.
- Voice inside an existing list (no mic anywhere except the create-list chooser).
- Quantity + unit extraction as structured fields ("two pounds of bananas" → qty=2, unit=lb). v1 keeps the whole phrase as the item name string. Quantity parsing is v1.5.
- Auto-categorization (eggs → "Food"). Separate TODO in CLAUDE.md.
- Multilingual recognition. v1 is whatever the backend defaults to (English).
- Offline recognition. Explicitly rejected (see Alternatives).
- Streaming / partial transcripts. v1 records → stops → uploads → response. Live transcription is a v2 polish item.
- A clever auto-naming algorithm. v1 ships a placeholder name — see Open questions.

## High-level flow

```
[ListsScreen] — user taps "Create list" (existing CTA)
        │
        ▼
[Create-list chooser] — two options
        ├─ Type  → existing create-list dialog (unchanged)
        └─ Voice → opens VoiceCaptureSheet
                          │
                          ├─ check RECORD_AUDIO permission ──► request if missing
                          │
                          ▼
                 [AudioRecorder] — MediaRecorder → .m4a in cacheDir, 30s cap
                          │
                          ▼
                 [VoiceRepository]
                          ├─ MockVoiceRepository    (used now: fake delay, canned items)
                          └─ RemoteVoiceRepository  (used when API lands)
                                  │
                                  ▼
                          POST /api/voice (multipart audio)
                                  │
                                  ▼
                          { transcript, items[] }
                          │
                          ▼
                 [Reviewing in same sheet] — editable rows of drafts
                          │  user taps "Create list"
                          ▼
                 onConfirm callback (provided by ListsScreen):
                  ShoppingListRepository.createListWithItems(
                      name = autoName(drafts),
                      drafts = drafts,
                  )
                          │
                          ▼
                 New list visible on Lists screen; sheet dismisses
```

## Where the code lives

**`core/voice/`** — the reusable capability. No knowledge of lists or items. Emits `List<ItemDraft>` on confirm; persistence is the caller's responsibility.

```
core/voice/
├── AudioRecorder.kt
├── VoiceRepository.kt        ← interface + ItemDraft data class
├── MockVoiceRepository.kt
├── RemoteVoiceRepository.kt  ← stub now, wired when API lands
├── VoiceCaptureViewModel.kt  ← takes persistence lambda on confirm()
└── VoiceCaptureSheet.kt      ← Composable, takes onConfirm
```

DI module lives at `core/data/di/VoiceModule.kt` (matches existing `AuthModule`, `DatabaseModule`, `NetworkModule` convention).

**`feature/shoppinglist/`** — the v1 consumer.

- `ui/ListsScreen.kt` — owns the Create-list CTA; presents the Type/Voice chooser; on Voice → shows `VoiceCaptureSheet` with an `onConfirm` lambda that calls `createListWithItems(...)`.
- `data/repository/ShoppingListRepository.kt` — gains one method, `createListWithItems(name, drafts)`, single transaction.

**`feature/shoppingitems/`** — **untouched** in v1. The earlier draft of this doc placed voice here; that was wrong. Items feature is the *target* of the inserted rows, not the *invoker* of voice.

## Components

### 1. `AudioRecorder` (new, `core/voice/`)

Thin wrapper around Android's `MediaRecorder`. The framework API has a finicky state machine (idle → initialized → prepared → recording → released) and a constructor difference at API 31. Wrapping it lets the ViewModel see three suspend functions (`start`, `stop`, `cancel`) instead of seven state transitions.

Output: AAC in MPEG_4 container (`.m4a`), 16 kHz mono. Whisper-optimal, small files (~20 KB/s).

Recording cap: **30 seconds**, enforced via `setMaxDuration(30_000)`. Auto-stops and transitions to Transcribing.

File location: `context.cacheDir/voice-<timestamp>.m4a`. Files are short-lived — uploaded once then discarded.

### 2. `VoiceRepository` interface (new, `core/voice/`)

```kotlin
data class ItemDraft(val name: String)

interface VoiceRepository {
    suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>>
}
```

`Result` here is the codebase's `core.error.Result` sealed class (`Success<T>` / `Error(AppError)`), **not** `kotlin.Result<T>`. ViewModel branches on it with `when (result) { is Result.Success -> …; is Result.Error -> … }`.

Two implementations:
- **`MockVoiceRepository`** — delays ~800 ms, returns canned items. Used during Android development before the backend lands.
- **`RemoteVoiceRepository`** — Retrofit-based, uploads the `.m4a` as multipart, deserializes the response.

### 3. `VoiceCaptureViewModel` (new, `core/voice/`)

```kotlin
sealed interface VoiceCaptureUiState {
    data object Idle : VoiceCaptureUiState
    data object NeedsPermission : VoiceCaptureUiState
    data class Recording(val elapsedMs: Long = 0) : VoiceCaptureUiState
    data object Transcribing : VoiceCaptureUiState
    data class Reviewing(val drafts: List<ItemDraft>) : VoiceCaptureUiState
    data object Saving : VoiceCaptureUiState
    data object Done : VoiceCaptureUiState
    data class Failed(val message: String) : VoiceCaptureUiState
}
```

Actions: `startRecording()`, `stopRecording()`, `editDraft(index, newName)`, `removeDraft(index)`, `confirm(persist)`, `cancel()`, `onPermissionResult(granted)`.

The ViewModel has **no knowledge of `ShoppingListRepository` or `ShoppingItemRepository`**. It drives the recorder, hands the resulting file to `VoiceRepository.transcribeAndParse(...)`, holds drafts during Reviewing, and on `confirm(persist)` calls the persistence lambda the caller supplied:

```kotlin
fun confirm(persist: suspend (List<ItemDraft>) -> Result<String>) =
    viewModelScope.launch {
        val current = _state.value as? VoiceCaptureUiState.Reviewing ?: return@launch
        _state.value = VoiceCaptureUiState.Saving
        when (val result = persist(current.drafts)) {
            is Result.Success -> {
                _state.value = VoiceCaptureUiState.Done
                _navigateToList.emit(result.data) // forward the new list id
            }
            is Result.Error -> _state.value = VoiceCaptureUiState.Failed(result.error.message)
        }
    }
```

This is what makes the voice layer reusable. v1's caller is the Lists screen with a `createListWithItems` lambda; v2 could add an items-screen caller with an `addItems` lambda; the voice code doesn't change either way.

### 4. `VoiceCaptureSheet` (new Compose, `core/voice/`)

Modal bottom sheet with one Composable per `VoiceCaptureUiState`.

Signature:
```kotlin
@Composable
fun VoiceCaptureSheet(
    onDismiss: () -> Unit,
    onConfirm: suspend (List<ItemDraft>) -> Result<String>,
    viewModel: VoiceCaptureViewModel = hiltViewModel(),
)
```

Visual modes:

| State | What the user sees |
|---|---|
| `NeedsPermission` | One-line rationale + "Allow microphone" button → launches permission launcher |
| `Recording` | Pulsing mic icon, elapsed timer, big "Stop" button |
| `Transcribing` | Indeterminate spinner + "Listening to your list…" |
| `Reviewing` | `OutlinedTextField` rows (one per draft) with per-row delete icon; primary CTA "Create list"; secondary "Cancel" |
| `Saving` | Spinner |
| `Done` | Auto-dismiss via `LaunchedEffect(state)` calling `onDismissRequest` |
| `Failed` | Error message + "Try again" (back to Recording) + "Type instead" (closes sheet, host can re-open the typed dialog) |

### 5. Lists-screen wiring (`feature/shoppinglist/ui/`)

The existing "Create list" CTA opens a small chooser (a simple `AlertDialog` or `ModalBottomSheet` with two `ListItem`s — implementation detail) offering **Type** or **Voice**.

- **Type** → opens the existing create-list dialog. Unchanged.
- **Voice** → shows `VoiceCaptureSheet` with:

```kotlin
VoiceCaptureSheet(
    onDismiss = { showVoiceSheet = false },
    onConfirm = { drafts ->
        // Returns Result<String> (the new list id); voice VM forwards it
        // via navigateToList for the host to consume.
        shoppingListRepository.createListWithItems(
            name = autoName(drafts),
            drafts = drafts,
        )
    },
)
```

`autoName(drafts)` is a v1 placeholder — see Open questions.

### 6. Repository changes

`ShoppingListRepository` gains one method:

```kotlin
suspend fun createListWithItems(
    name: String,
    drafts: List<ItemDraft>,
): Result<String> = safeCall {
    // Single transaction:
    // 1. Generate list id + insert list row
    // 2. Map drafts → ShoppingItemEntity (mirror addItem defaults exactly)
    // 3. Insert all item rows via shoppingItemDao.insertItems(...)
    // Returns the new list id.
}
```

DAO already exposes `shoppingItemDao.insertItems(List<ShoppingItemEntity>)` and `shoppingListDao.insertList(...)`. Wrap in `@Transaction` (room) or rely on `safeCall` plus a single-call ordering — match whatever pattern the existing `restoreListWithItems` uses for consistency (it already does both inserts under one `safeCall`).

**Voice-inserted items must be indistinguishable from typed ones** — read `ShoppingItemRepository.addItem(...)` and mirror its defaults / id generation / timestamp logic exactly.

`ShoppingItemRepository.addItems(...)` (the batch insert from the earlier draft) is **not needed for v1** because items always flow through `createListWithItems`. It can be added later when voice gains a second consumer.

### 7. Permission handling

`RECORD_AUDIO` is a runtime permission. Use `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` in the sheet composable.

State machine for permission:
1. Sheet opens → check `ContextCompat.checkSelfPermission`.
2. Granted → call `viewModel.startRecording()` immediately.
3. Not granted, never asked → show one-line rationale ("So you can speak your list without typing"), then launch system prompt.
4. Denied once → same as 3.
5. Denied with "Don't ask again" (`shouldShowRequestPermissionRationale == false` after a denial) → show CTA to open app settings.

We are explicitly **not** adding `accompanist-permissions` — AndroidX's `RequestPermission` contract is enough for a single permission and accompanist's permissions module is in maintenance.

## Backend contract

The Android side is built against this contract. The backend dev implements it independently.

**Endpoint:** `POST /api/voice`

**Request:** `multipart/form-data` with one part:
- `audio` — the recorded `.m4a` file (AAC-LC, 16 kHz mono, ≤ 30 s, ≤ ~700 KB)

**Response (200):** `application/json`
```json
{
  "transcript": "one crate eggs two litre coke and dish soap",
  "items": ["1 crate Eggs", "2 L Coke", "Dish soap"]
}
```

- `transcript` — raw Whisper output. Hidden in v1 UI; useful for debugging and v2 features.
- `items` — Claude Haiku 4.5 extraction, one element per shopping item, casing already cleaned up.

**Error responses:** standard `4xx`/`5xx`. The Android client surfaces a generic "Couldn't transcribe" Failed state with a Try again CTA. Specific error mapping is a v1.1 polish item.

**Auth:** TBD — likely the same bearer-token pattern as the existing auth API. Confirmed at integration time.

**Rate limiting / usage caps:** backend concern, not Android. The Android client does not pre-emptively gate.

## State machine

```
                       (Create list tapped)
ListsScreen ────────────────────────────────► [Type / Voice chooser]
                                                       │
                                              (Voice picked)
                                                       │
                                                       ▼
                                              NeedsPermission
                                                       │ (granted)
                                                       ▼
                                                   Recording ◄──────┐
                                                       │            │ (Try again)
                                              (stop tapped /        │
                                               30 s auto-stop)      │
                                                       ▼            │
                                                  Transcribing      │
                                                       │            │
                                              (items returned)      │
                                                       ▼            │
                                                   Reviewing        │
                                                   │       │        │
                                            (confirm)   (cancel)    │
                                                   ▼       ▼        │
                                                Saving   Idle       │
                                                   │                │
                                                 (ok)                │
                                                   ▼                 │
                                                 Done ───► sheet     │
                                                          dismisses  │
                                                                      │
                                                   (any error) ──► Failed ──┘
```

## Edge cases

| Case | v1 behavior |
|---|---|
| Permission denied permanently | Sheet shows "Open settings" CTA; primary CTA becomes "Type instead" → closes sheet and re-opens the typed create-list dialog |
| No network | `Failed("Couldn't reach the server")` with "Try again" and "Type instead" |
| User says nothing / silence | Empty `items` array from backend → `Failed("Didn't catch any items")` with Try again |
| User cancels mid-recording | `AudioRecorder.cancel()` stops + deletes the partial `.m4a`. No upload occurs. No list created. |
| Recording hits 30 s cap | `MediaRecorder` fires `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED`; auto-stops; ViewModel transitions to Transcribing |
| User rotates device mid-recording | ViewModel survives config change; recorder is `@Singleton` so it survives too |
| Process death mid-Reviewing | Drafts lost. No list created (good — partial state isn't persisted until confirm). Accepted v1. |
| User confirms but `createListWithItems` fails (DB error) | `Failed("Couldn't save")` with Try again. No partial list (single transaction). |
| Duplicate items inside one utterance ("milk milk") | Backend handles. v1 inserts whatever the backend returns. |

## What is required (implementation checklist)

Concrete, in dependency order. Full step-by-step lives in [`docs/plan/001-voice-to-list-implementation.md`](../plan/001-voice-to-list-implementation.md).

1. **Manifest:** add `<uses-permission android:name="android.permission.RECORD_AUDIO" />`.
2. **`AudioRecorder`** wrapper around `MediaRecorder` in `core/voice/`. Manual test on a device.
3. **`VoiceRepository` interface + `MockVoiceRepository`** in `core/voice/` — fake delay, canned items.
4. **`VoiceModule` (Hilt)** in `core/data/di/` — `@Binds` `MockVoiceRepository` as `VoiceRepository`. One-line swap later.
5. **`ShoppingListRepository.createListWithItems(name, drafts)`** — single-transaction insert.
6. **`VoiceCaptureViewModel`** in `core/voice/` — owns state, decoupled from persistence.
7. **`VoiceCaptureSheet`** in `core/voice/` — Composable, one mode per state, takes `onConfirm`.
8. **Lists-screen wiring** in `feature/shoppinglist/ui/` — Type/Voice chooser on Create-list CTA; voice path passes `createListWithItems` lambda.
9. **Update this doc + plan doc** when the design shifts (this revision is that step from the plan).
10. **`RemoteVoiceRepository`** + Retrofit `VoiceApi` when the backend lands. Swap the `@Binds` in `VoiceModule`. Manual end-to-end test.

### Dependencies

None new. We use:
- `MediaRecorder` (framework, no Gradle dep).
- `androidx.activity:activity-compose` for the permission launcher (already present).
- `androidx.compose.material3:material3` for `ModalBottomSheet` (already present).
- Retrofit + OkHttp + kotlinx.serialization (already in `libs.versions.toml`) — used by `RemoteVoiceRepository` when wired.

### Files touched

New (under `core/voice/`):
- `AudioRecorder.kt`
- `VoiceRepository.kt` (interface + `ItemDraft`)
- `MockVoiceRepository.kt`
- `RemoteVoiceRepository.kt` (stub now)
- `VoiceCaptureViewModel.kt`
- `VoiceCaptureSheet.kt`

New (DI):
- `core/data/di/VoiceModule.kt`

Edited:
- `AndroidManifest.xml` — `RECORD_AUDIO` permission.
- `feature/shoppinglist/data/repository/ShoppingListRepository.kt` — add `createListWithItems(name, drafts)`.
- `feature/shoppinglist/ui/ListsScreen.kt` (+ any chooser-related child Composables) — Type/Voice chooser on Create-list CTA; sheet wiring.
- `core/data/di/NetworkModule.kt` (or a new `VoiceNetworkModule.kt`) — Retrofit provider for `VoiceApi`, when the API lands.
- `app/build.gradle.kts` — expose `VOICE_API_URL` via `BuildConfig`, when the API lands.

**Not touched in v1:**
- `feature/shoppingitems/` — items feature is the *target* of inserted rows, not the *invoker* of voice.

## Alternatives considered

### Alt A — Framework `SpeechRecognizer` + pure-Kotlin delimiter parser (original 2026-05-05 design)

Use `android.speech.SpeechRecognizer` for on-device recognition (`EXTRA_PREFER_OFFLINE = true`), and a small Kotlin parser that splits on commas / "and" / pauses, title-cases tokens, drops stopwords.

- **Pros:** Fully offline on capable devices. No backend dependency.
- **Cons:** Recognition quality varies wildly across OEMs. Pure-Kotlin parsing produces "Get Milk" / "Also Some Bananas" on natural sentences. The capture-friction the feature exists to remove is partially re-introduced by transcription/parse errors forcing per-row cleanup.

**Rejected on 2026-05-14.** Offline-first remains true for the typing path, which is the core write path.

### Alt B — Voice inside an existing list (mic icon in add-item row) (original 2026-05-14 morning design)

Mic icon next to the keyboard input on `ShoppingItemsScreen`. User opens a list and dictates rows to add.

- **Pros:** Smaller surface change. Items feature is already the "items lives here" feature.
- **Cons:** Doesn't fit the actual user moment. People say "I need to make a list" before opening the app, not "let me open my Costco list and add to it." Forcing the user to first pick a target list re-introduces the capture friction.

**Rejected on 2026-05-14.** Reframed as v1: voice creates a *new* list. Adding to an existing list via voice may return as a v1.5 / v2 second consumer.

### Alt C — Cloud speech (Google Cloud Speech-to-Text, OpenAI Whisper API direct from device)

Same recognition quality as the chosen path, but bypassing our own backend.

- **Pros:** No backend dev needed.
- **Cons:** API key on-device is a leak risk. Cost per request lands on us with no way to gate or batch. No place to swap in better parsing later. Couples the Android app to a third-party SDK we don't control.

Rejected.

### Alt D — On-device Whisper / `whisper.cpp` bundled in the APK

- **Pros:** Offline + high quality.
- **Cons:** Adds 50–500 MB to APK. Significant build/integration work. Battery + thermal cost. Way past v1 scope.

Rejected.

### Alt E — Skip the Reviewing step, create the list directly

- **Pros:** One tap fewer.
- **Cons:** Even with backend Whisper + Haiku, recognition is not perfect. User has no signal something went wrong until they're at the store looking at "Hello milk" on their list.

Rejected. Revisit if telemetry shows users overwhelmingly confirm without edits.

### Alt F — Hybrid: framework recognizer for the happy path, fall back to backend on failure

- **Pros:** Free + offline when the device cooperates.
- **Cons:** Two code paths to maintain, two quality tiers users experience inconsistently, hard "is this transcript good enough?" heuristic.

Rejected as scope.

## Consequences

### Positive

- v1 attacks the highest-friction moment in the user's day (starting a list, not editing one).
- Recognition + parsing quality is high enough that confirmation is verification, not cleanup.
- Backend ownership of parsing means item extraction can improve without an app update.
- `VoiceRepository` lets us build and demo the entire Android side against a mock before the backend lands. One Hilt binding swap to go live.
- `core/voice/` capability with decoupled ViewModel sets up cleanly for future consumers (existing-list dictation, list editing) without rewriting recording or UI.

### Negative / known tradeoffs

- **Voice requires network.** Documented up-front; **Type** option on the same CTA is the offline fallback. Typing remains fully offline.
- **Latency.** Record → upload → Whisper → Haiku → response is realistically 2–5 s on a good connection. Slower than framework SR's ~instant. v1 shows a clear Transcribing state.
- **Backend dependency on the critical path.** v1 ships only when the backend ships. Mock decouples development but not release.
- **Backend cost per use.** Real but small. Rate limiting is the backend's problem.
- **Microphone permission denial.** Some users will deny `RECORD_AUDIO` once and never re-grant. "Type instead" fallback on the chooser preserves the create-list flow entirely.
- **No SavedStateHandle preservation.** Drafts in Reviewing are lost on process death. Accepted for v1.
- **`MediaRecorder` is a finicky API.** Wrapping it in `AudioRecorder` contains the mess in one file but does not eliminate it. Expect device-specific fixes after launch.
- **List naming is unsolved.** v1 ships with a placeholder (see Open questions). A bad name is a recoverable papercut; not a blocker.

## Open questions

1. **List naming strategy.** v1 ships with a placeholder. Candidates:
   - **Timestamp** — `"List · 14 May"` or `"List · 14 May, 3:45 PM"`. Boring, predictable, always works.
   - **First item** — `"Eggs and 2 more"`. More descriptive; degenerates oddly on one-item lists.
   - **Untitled + counter** — `"Untitled list 3"`. Worst feel, easiest to write.
   - **Server-side from transcript** — Claude can name the list. Adds a backend ask; defer.
   
   **Default for v1:** timestamp. Cheap, deterministic, never produces a bad name. Revisit when there's usage data.
2. After successful list creation, do we **stay on the Lists screen** (highlight the new list) or **navigate into it**? Default: navigate into it — the user just dictated its contents, they probably want to see them.
3. Surface the raw `transcript` to users? **Default v1 answer:** hidden. Reconsider for debug builds.
4. The Type/Voice chooser: full-screen sheet, modal dialog, or inline two-button row? **Default:** inline two-button row inside the existing create-list entry — minimal UX surface.

## When to revisit

- Backend is unhealthy / slow more often than expected → reconsider Alt F (hybrid with framework fallback) or local retry caching.
- Users overwhelmingly confirm without edits → graduate to Alt E (skip Reviewing) with an "edit last batch" affordance.
- Users ask for voice-add-to-existing-list → add a second consumer of `core/voice/`. Voice code itself doesn't change; only a new call site with an `addItems`-shaped persistence lambda.
- Quantity / unit support added to the data model → backend extends response; `ItemDraft` gains optional `quantity` + `unit` fields.
- Auto-categorization TODO picked up → backend returns category per item; UI renders it in Reviewing.
