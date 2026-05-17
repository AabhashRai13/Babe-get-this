# 001 — Voice-to-list implementation plan

**Status:** In progress
**Created:** 2026-05-13
**Revised:** 2026-05-14 (architecture moved to `core/voice/`; v1 flow is **list creation**, not item appending)
**Target window:** ~3 days (Android side), then API swap when backend lands
**Related decision doc:** [`docs/technical-decisions/002-voice-to-list-input.md`](../technical-decisions/002-voice-to-list-input.md)

## Goal

Implement the Android side of voice-to-list end-to-end against a **mock repository**. When the backend dev delivers `POST /api/voice`, swap one Hilt binding and ship.

**v1 user flow (locked 2026-05-14):**

> User taps "Create list" on the Lists screen → picks **Voice** from a Type/Voice chooser → speaks items → reviews drafts → confirms → a **new list** is created with those items inserted in one transaction.

Voice does **not** appear anywhere else in v1. Adding items to an existing list via voice is a v1.5 / v2 second consumer of the same capability.

## Architecture at a glance

```
[ListsScreen]
   │  ("Create list" tapped)
   ▼
[Type/Voice chooser] ── Type ─► existing create-list dialog (unchanged)
   │
  Voice
   ▼
[VoiceCaptureSheet] ◄──── VoiceCaptureViewModel ────┐
                                  │                  │
                                  ▼                  │
                          AudioRecorder ── .m4a file ┘
                                  │
                                  ▼
                          VoiceRepository (interface)
                              │              │
                              ├─ MockVoiceRepository    (used now)
                              └─ RemoteVoiceRepository  (used when API lands)
                                  │
                                  ▼
                          drafts: List<ItemDraft>
                                  │
                                  ▼  (onConfirm lambda supplied by ListsScreen)
                          ShoppingListRepository.createListWithItems(
                              name = autoName(drafts),
                              drafts = drafts,
                          )
```

Key principle: `core/voice/` knows nothing about lists or items. Persistence is a lambda passed in by the caller. v1 caller is `feature/shoppinglist`; v2 could add `feature/shoppingitems` without modifying voice code.

## What's already in place (don't rebuild)

- INTERNET permission (manifest).
- Retrofit + OkHttp + kotlinx.serialization in `libs.versions.toml`.
- `ShoppingItemDao.insertItems(List<ShoppingItemEntity>)` — used by the new `createListWithItems`.
- `ShoppingListRepository.createList(name)` and `restoreListWithItems(list, items)` — existing examples of single-transaction multi-row writes wrapped in `safeCall`.
- Custom `Result` sealed class at `core.error.Result` with `Success<T>` / `Error(AppError)`. **Not** `kotlin.Result<T>`. Branch with `when (result) { is Result.Success -> …; is Result.Error -> … }` — no `.onSuccess`/`.onFailure`.
- `safeCall { … }` helper at `core.error.safeCall` — wraps DB / network exceptions into `Result.Error(AppError.*)`.
- Hilt convention: DI modules live under `core/data/di/` (see `NetworkModule.kt`, `DatabaseModule.kt`, `AuthModule.kt`).
- IDs are `String` (UUIDs), not `Long`.

## Decisions locked before coding

| Question | Answer |
|---|---|
| Where does voice code live? | `core/voice/` — capability, not a feature. Used by `feature/shoppinglist` in v1; potentially other features later. |
| Hilt module location? | `core/data/di/VoiceModule.kt` — matches existing convention. |
| What does voice do in v1? | Creates a **new list** populated with spoken items. Entry point: "Create list" CTA on Lists screen → Type/Voice chooser. |
| ViewModel ↔ persistence | Decoupled. `confirm(persist: suspend (List<ItemDraft>) -> Result<String>)` — caller supplies the persistence lambda. Returns the new list id so the host can navigate. |
| Push-to-talk or tap-to-toggle? | **Tap-to-toggle.** Tap mic → records → tap stop. |
| Max recording duration? | **30 seconds.** Hard cap. Auto-stop → Transcribing. |
| Audio format? | **AAC in MPEG_4 container (.m4a), 16 kHz mono.** Whisper-optimal, small files. |
| List naming for v1 | Placeholder. Default: timestamp (`"List · 14 May"`). See decision doc Open Questions. |

---

## Step-by-step

### Step 1 — Manifest + dependency check

**Files touched:**
- `app/src/main/AndroidManifest.xml`

**Do:** add the RECORD_AUDIO permission under the existing INTERNET line:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

No dependency changes needed.

**Verify:** Project builds (`./gradlew assembleDebug`).

---

### Step 2 — `AudioRecorder` wrapper around MediaRecorder

**New file:** `core/voice/AudioRecorder.kt`

**Why a wrapper:** `MediaRecorder` is callback-soup with a finicky state machine (idle → initialized → prepared → recording → released). One bad transition crashes the app. We hide it behind three suspend functions.

```kotlin
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = buildRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setMaxDuration(30_000) // 30s hard cap
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    suspend fun stop(): File = withContext(Dispatchers.IO) {
        recorder?.apply {
            try { stop() } catch (_: RuntimeException) { /* no audio recorded */ }
            release()
        }
        recorder = null
        outputFile!!.also { outputFile = null }
    }

    fun cancel() {
        recorder?.runCatching { stop(); release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun buildRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
}
```

**Flutter analogue:** like `record` plugin's recorder, but Android's MediaRecorder is more finicky — wrapping it lets the ViewModel treat it as 3 simple calls instead of 7 state transitions.

**Manual test on device:**
1. Temporarily inject `AudioRecorder` into an existing ViewModel and call `start()` / `stop()` from any button.
2. `adb pull /data/data/com.babegetthis.android/cache/voice-*.m4a` to your machine.
3. Open in any media player — you should hear yourself.

**Common gotchas:**
- API < 31 vs ≥ 31 constructor difference (covered above).
- Calling `stop()` immediately after `start()` with no audio recorded throws `RuntimeException` — caught above.
- Forgetting `release()` leaks the recorder permanently for the process lifetime.

---

### Step 3 — `VoiceRepository` interface + `MockVoiceRepository`

**New file:** `core/voice/VoiceRepository.kt`

```kotlin
data class ItemDraft(val name: String)

interface VoiceRepository {
    suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>>
}
```

**New file (or same file):** `core/voice/MockVoiceRepository.kt`

```kotlin
class MockVoiceRepository @Inject constructor() : VoiceRepository {
    override suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>> {
        delay(800) // simulate Whisper + Haiku round-trip
        return Result.Success(
            listOf(
                ItemDraft("1 crate Eggs"),
                ItemDraft("2 L Coke"),
                ItemDraft("Dish soap"),
            )
        )
    }
}
```

The mock ignores the input file but proves the contract end-to-end. When the API lands you swap the binding, not the call sites.

**Important:** `Result` here is `com.babegetthis.android.core.error.Result` — the codebase's sealed class. Use `Result.Success(...)` / `Result.Error(AppError.*)`. **Do not** use `kotlin.Result`'s `.onSuccess` / `.onFailure`.

---

### Step 4 — Hilt module: `VoiceModule.kt`

**New file:** `core/data/di/VoiceModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceRepository(impl: MockVoiceRepository): VoiceRepository
    //                                     ^^^ change to RemoteVoiceRepository in Step 10
}
```

That's the only line you change when the API arrives.

---

### Step 5 — `createListWithItems` on `ShoppingListRepository`

**File touched:** `feature/shoppinglist/data/repository/ShoppingListRepository.kt`

Add one method. Pattern is the same shape as the existing `restoreListWithItems` — both DAO inserts inside a single `safeCall`. Return the new list id so the caller can navigate into it.

```kotlin
suspend fun createListWithItems(
    name: String,
    drafts: List<ItemDraft>,
): Result<String> = safeCall {
    val now = System.currentTimeMillis()
    val listId = UUID.randomUUID().toString()

    val list = ShoppingList(
        id = listId,
        name = name,
        createdAt = now,
        updatedAt = now,
    )
    shoppingListDao.insertList(list.toEntity())

    // Mirror addItem(...) defaults exactly — read it first.
    val itemEntities = drafts.map { draft ->
        ShoppingItemEntity(
            id = UUID.randomUUID().toString(),
            listId = listId,
            name = draft.name,
            isPickedUp = false,
            createdAt = now,
            updatedAt = now,
            // …match whatever other fields ShoppingItemRepository.addItem uses.
        )
    }
    if (itemEntities.isNotEmpty()) {
        shoppingItemDao.insertItems(itemEntities)
    }

    listId
}
```

**Read `ShoppingItemRepository.addItem(...)` first** and copy its defaults / field set verbatim so voice-inserted items are indistinguishable from typed ones.

**`ItemDraft` import:** this method imports `com.babegetthis.android.core.voice.ItemDraft`. That introduces a `core → core` import inside `feature/shoppinglist` — fine.

---

### Step 6 — `VoiceCaptureViewModel`

**New file:** `core/voice/VoiceCaptureViewModel.kt`

**State machine:**

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

**Class shape — decoupled from any repository:**

```kotlin
@HiltViewModel
class VoiceCaptureViewModel @Inject constructor(
    private val recorder: AudioRecorder,
    private val voiceRepository: VoiceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceCaptureUiState>(VoiceCaptureUiState.Idle)
    val state: StateFlow<VoiceCaptureUiState> = _state.asStateFlow()

    fun onPermissionResult(granted: Boolean) { /* Idle ↔ NeedsPermission */ }

    fun startRecording() = viewModelScope.launch {
        _state.value = VoiceCaptureUiState.Recording()
        recorder.start()
        // optional: launch a 100ms-tick coroutine to update elapsedMs for the UI
    }

    fun stopRecording() = viewModelScope.launch {
        _state.value = VoiceCaptureUiState.Transcribing
        val file = recorder.stop()
        when (val result = voiceRepository.transcribeAndParse(file)) {
            is Result.Success -> {
                _state.value =
                    if (result.data.isEmpty()) VoiceCaptureUiState.Failed("Didn't catch any items")
                    else VoiceCaptureUiState.Reviewing(result.data)
            }
            is Result.Error -> {
                _state.value = VoiceCaptureUiState.Failed(result.error.message ?: "Couldn't transcribe")
            }
        }
    }

    fun editDraft(index: Int, newName: String) { /* update Reviewing.drafts */ }
    fun removeDraft(index: Int) { /* update Reviewing.drafts */ }

    // Persistence is the caller's responsibility. v1 caller passes createListWithItems(...).
    fun confirm(persist: suspend (List<ItemDraft>) -> Result<String>) = viewModelScope.launch {
        val current = _state.value as? VoiceCaptureUiState.Reviewing ?: return@launch
        _state.value = VoiceCaptureUiState.Saving
        when (val result = persist(current.drafts)) {
            is Result.Success -> _state.value = VoiceCaptureUiState.Done
            is Result.Error -> _state.value = VoiceCaptureUiState.Failed(result.error.message ?: "Couldn't save")
        }
    }

    fun cancel() {
        recorder.cancel()
        _state.value = VoiceCaptureUiState.Idle
    }
}
```

**Flutter analogue:** `_state` is your `ValueNotifier<UiState>`; `viewModelScope` is like binding lifecycles to the widget tree — coroutines auto-cancel on ViewModel clear, no `dispose()` needed.

**Key shape note:** the ViewModel has **no `ShoppingListRepository` or `ShoppingItemRepository` dependency**. This is what makes `core/voice/` reusable.

---

### Step 7 — `VoiceCaptureSheet` Compose UI

**New file:** `core/voice/VoiceCaptureSheet.kt`

A `ModalBottomSheet` that reads ViewModel state and switches between visual modes.

**Signature:**

```kotlin
@Composable
fun VoiceCaptureSheet(
    onDismiss: () -> Unit,
    onConfirm: suspend (List<ItemDraft>) -> Result<String>,
    viewModel: VoiceCaptureViewModel = hiltViewModel(),
) { … }
```

**Permission handling pattern:**

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> viewModel.onPermissionResult(granted) }
)

LaunchedEffect(Unit) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    if (granted) viewModel.startRecording()
    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
}
```

**Visual modes (one Composable per state):**

| State | What user sees |
|---|---|
| `NeedsPermission` | One-line rationale + "Allow microphone" button → launches permission launcher |
| `Recording` | Pulsing mic icon, elapsed timer, big "Stop" button |
| `Transcribing` | Indeterminate spinner + "Listening to your list…" |
| `Reviewing` | List of `OutlinedTextField` rows (one per draft) with per-row delete icon; primary CTA "**Create list**"; secondary "Cancel" |
| `Saving` | Spinner |
| `Done` | Auto-dismiss the sheet (use `LaunchedEffect(state)` to call `onDismissRequest`) |
| `Failed` | Error message + "Try again" (back to Recording) + "Type instead" (closes sheet; host re-opens typed dialog) |

**On confirm:** call `viewModel.confirm(onConfirm)` — the sheet hands the caller's lambda back into the ViewModel.

**Material 3:** use `ModalBottomSheet`, `OutlinedTextField`, `FilledIconButton` for the mic, `Button` for primary CTAs.

---

### Step 8 — Lists-screen wiring: Type/Voice chooser on Create-list CTA

**File touched:** `feature/shoppinglist/ui/ListsScreen.kt` (and possibly a new small `CreateListChooser.kt` Composable if it gets gnarly inline).

**Do:**

1. Find the existing "Create list" entry point (currently opens a typed dialog).
2. Replace its direct action with a small chooser — two `ListItem`s or two `Button`s, "Type" and "Voice."
3. **Type** path: keep the existing typed dialog. Unchanged.
4. **Voice** path: hoist `showVoiceSheet: Boolean` state; when true, show `VoiceCaptureSheet(...)`.

**Wiring the sheet:**

```kotlin
if (showVoiceSheet) {
    VoiceCaptureSheet(
        onDismiss = { showVoiceSheet = false },
        onConfirm = { drafts ->
            // Returns Result<String> (the new list id) — voice VM forwards it
            // via its navigateToList SharedFlow so the host can route into the
            // new list. No mapping needed.
            val name = autoName(drafts) // v1 placeholder helper, see below
            shoppingListRepository.createListWithItems(name, drafts)
        },
    )
}
```

`autoName` for v1 is a tiny private helper in this screen (or in a small `feature/shoppinglist/util/` file). Default: timestamp via `java.time.LocalDate`:

```kotlin
private fun autoName(@Suppress("UNUSED_PARAMETER") drafts: List<ItemDraft>): String {
    val today = java.time.LocalDate.now()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")
    return "List · ${today.format(fmt)}"
}
```

**Important architectural note:** the `onConfirm` lambda above calls `shoppingListRepository` directly from a Composable. In this codebase that's not the convention — calls should go through a ViewModel. The cleanest shape is to expose a `createListWithVoice(drafts): Result<String>` method on the existing Lists-screen ViewModel and have the lambda call that instead. Match whatever pattern `ListsScreen.kt` already uses for the typed create flow — mirror it.

---

### Step 9 — Update decision doc 002 + this plan

**Already done as of 2026-05-14.** Both docs reflect the `core/voice/` capability, the list-creation flow, the type/voice chooser, the codebase's `Result` type, and the decoupled ViewModel design.

If the design shifts again, update both together. Decision doc is portfolio material; keep the rationale visible.

---

### Step 10 — When the API lands: swap to `RemoteVoiceRepository`

**New file (can scaffold now, leave unbound):** `core/voice/RemoteVoiceRepository.kt`

Use **Retrofit** (codebase convention; don't introduce raw OkHttp):

```kotlin
interface VoiceApi {
    @Multipart
    @POST("voice")
    suspend fun transcribeAndParse(
        @Part audio: MultipartBody.Part
    ): VoiceResponse
}

@Serializable
data class VoiceResponse(
    val transcript: String,
    val items: List<String>,
)

class RemoteVoiceRepository @Inject constructor(
    private val api: VoiceApi,
) : VoiceRepository {

    override suspend fun transcribeAndParse(audioFile: File): Result<List<ItemDraft>> = safeCall {
        val part = MultipartBody.Part.createFormData(
            name = "audio",
            filename = audioFile.name,
            body = audioFile.asRequestBody("audio/m4a".toMediaType()),
        )
        api.transcribeAndParse(part).items.map { ItemDraft(it) }
    }
}
```

`safeCall` already maps `UnknownHostException`, `HttpException`, etc. into `Result.Error(AppError.*)` — reuse it instead of `runCatching`.

**Wiring (when API URL is known):**
1. Add `VOICE_API_URL` to `local.properties` and expose via `BuildConfig` in `app/build.gradle.kts`.
2. Add a Retrofit provider for `VoiceApi` in `NetworkModule.kt` (or a new `VoiceNetworkModule.kt`) using the existing OkHttp client + kotlinx.serialization converter.
3. In `VoiceModule.kt` (Step 4), switch the `@Binds` from `MockVoiceRepository` to `RemoteVoiceRepository`. **One-line change.**

**End-to-end manual test on device:**
1. Open the app, tap "Create list," pick **Voice**.
2. Grant permission.
3. Speak: *"one crate eggs, two-litre coke, and dish soap."*
4. Tap stop → confirm spinner appears → confirm Reviewing state shows 3 items capitalized correctly.
5. Tap "Create list" → confirm sheet dismisses, new list appears on Lists screen with those items.

**Edge case checks:**
- Airplane mode → expect Failed("…couldn't reach server…") with retry + "Type instead" CTA.
- Deny permission → expect rationale; deny twice → expect settings deep-link CTA.
- Cancel mid-recording → no temp file left behind in cache dir; no list created.
- Empty utterance ("…uhh…") → expect Failed("Didn't catch any items").
- Recording longer than 30 s → auto-stops + transitions to Transcribing.
- DB error during `createListWithItems` → Failed("Couldn't save"); no partial list inserted (single `safeCall`).

---

## Suggested ordering (≈3 day window)

| Day | Steps | Why |
|---|---|---|
| **1** | 1, 2 | Get the most Android-specific piece (MediaRecorder) working in isolation. Manifest is trivial. |
| **2** | 3, 4, 5, 6 | Pure data + state layer. No device needed for most of this. Mock unblocks ViewModel testing. |
| **3** | 7, 8 | UI + Lists-screen wiring. End the day with a working end-to-end flow against the mock. |
| **+1 (when API)** | 4 (rebind), 10 (verify) | One Hilt line + a manual test pass. |

Step 9 (doc update) was completed alongside the architecture pivot; redo it if the design shifts again.

## What to skip / not do

- **Don't write a `VoiceListParser`.** The backend does this. Old doc 002 references it; that section is obsolete (preserved in the Rejected alternatives).
- **Don't build framework `SpeechRecognizer` integration.** Removed from the design.
- **Don't add `accompanist-permissions`.** `rememberLauncherForActivityResult` is sufficient for one permission.
- **Don't put voice under `feature/shoppingitems/` or `feature/shoppinglist/`.** It's a `core/voice/` capability. The consumer feature owns wiring, not voice code.
- **Don't add `ShoppingItemRepository.addItems(...)` for v1.** v1 only has one path (`createListWithItems`); items batch insert is needed when voice gains a second consumer.
- **Don't persist drafts across process death** (no `SavedStateHandle` for the Reviewing list). Accepted in doc 002 — not worth the complexity for v1.
- **Don't add a usage cap on the Android side.** That's a backend concern.
- **Don't sweat list naming.** v1 ships with a placeholder (timestamp). Iterate later when there's usage data.

## Open follow-ups (after the feature works)

- Decide list naming strategy (timestamp / first-item / server-side / etc.) once there's real usage to look at.
- Decide whether successful create navigates into the new list or stays on Lists screen with the new list highlighted. Current default: navigate into it.
- Decide where the `transcript` field is shown to users (debug-only? hidden? small subtitle in Reviewing?). Default: hidden in v1.
- Telemetry: when we add analytics later, instrument `voice_started`, `voice_transcribed`, `voice_confirmed`, `voice_failed{reason}` to learn which failures matter.
- Second consumer: voice-add-to-existing-list. Wire from `feature/shoppingitems/ui/ShoppingItemsScreen.kt` with an `addItems` persistence lambda. Voice code itself doesn't change.
