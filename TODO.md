# TODO

Consolidated backlog for **Babe, Get This**. Prioritized work up top; longer-term
feature ideas and the v1/v2 roadmap below.

## Open-source polish (do before making the repo public)

From a full-codebase review (2026-07-02). The code itself came back clean — no dead
code, no debug logs, no secrets in git, real unit tests. What's left is polish:

- [ ] **Extract the last 13 hardcoded UI strings to `strings.xml`** — `VoiceCaptureSheet` ("Allow microphone", "Stop", "Try again", "Type instead"), `AddItemDialog` and `CreateListDialog` character counters, `ProfileBottomSheet` delete-account dialog, `JoinListDialog`/`CreateListDialog` placeholders. Seven genuinely-unused strings were deleted while adopting the lint gate; these are the ones still inline in code.
- [ ] **Naming consistency** — rename `ShoppingListModel.kt` → `ShoppingListEntity.kt` (to match `ShoppingItemEntity`); pick one ViewModel package convention (`ui/` vs `ui/viewModels/`) and apply it to both features; rename `ItemDraft.shop` → `location` to match the DTO and backend.
- [ ] **Split the oversized composables** — `ShoppingListScreen` (462 lines: extract the create-list flow and the list pane), `AddItemDialog` (433: extract a `CategoryDropdownField`), `ShoppingItemsScreen` (357: extract the by-shop items section).
- [ ] **Accessibility** — add `contentDescription` to the stop icon in `VoiceCaptureSheet` and a semantics label to `TranscribingWaveform`. Broader: 40 `contentDescription = null` sites have never been reviewed against TalkBack, and touch targets have never been audited.
- [ ] **Update CLAUDE.md** — SDK versions are stale (says min 26 / target 35; actual is min 24 / target 36), and the token-refresh bug note is obsolete: the fix already shipped (`BabeGetThisApp` observes `sessionStatus` and writes rotated tokens back).

## Static analysis backlog

Android Lint runs as a blocking gate with `warningsAsErrors = true` (see the
`lint {}` block in `app/build.gradle.kts`). Findings that existed when the gate
was adopted are recorded in `app/lint-baseline.xml` so the gate could be enabled
immediately without a large unrelated cleanup.

That baseline is debt, not a resolution. It should shrink over time.

- **Baseline at adoption: 26 findings.** 20 × `UseKtx` (core-ktx extensions that
  would read better than the platform calls they replace, all in
  SharedPreferences writes) and 5 × `PluralsCandidate` (item-count strings that should be `<plurals>`; genuinely
  blocked until localization happens, since plurals only pay off across
  languages).

  The first run produced 78. Of those, 33 were dependency-freshness checks now
  owned by Renovate and disabled in the `lint {}` block, and 19 were real and
  fixed rather than baselined: 7 dead strings and the whole Android Studio
  template colour palette deleted, a redundant activity label removed, a raw
  dependency coordinate moved into the version catalog, the monochrome launcher
  icon added, and a min-SDK bug caught in this change's own new theme.
- [ ] **Shrink the lint baseline** — regenerate with `./gradlew lintProdDebug`
  after deleting `app/lint-baseline.xml`, and confirm the count went down rather
  than sideways.

## Deferred by the build-hardening change (2026-08-26)

These came up while enabling R8, Lint, Spotless, and the baseline profile, and
were deliberately left alone rather than folded into that change.

- [ ] **Move off Kotlin 2.0.21** — pinned by Supabase 3.0.x, which pins the
  Firebase BOM to 33.x. Renovate is configured to surface this as a standing
  dashboard item rather than silently omitting it. Moving is a toolchain-wide
  change and needs its own pass.
- [ ] **`listNotFoundException(listId)` in `ShoppingListRepository` ignores its
  `listId` parameter** — the message is a constant. Either use the id or drop
  the parameter.
- [ ] **`app/lint-baseline.xml` still holds 5 `PluralsCandidate` findings** —
  item-count strings that should be `<plurals>`. Genuinely blocked until
  localization, since plurals only pay off across languages.
- [ ] **Measure the baseline profile's effect on a physical device** —
  `StartupBenchmark` is written and runnable, but Macrobenchmark refuses to
  produce numbers on an emulator, correctly. Until it runs on real hardware the
  profile's benefit is assumed rather than measured. Do NOT set
  `androidx.benchmark.suppressErrors=EMULATOR` to get a number out of it.
- [ ] **Verify a deobfuscated release crash reaches Crashlytics** — the mapping
  upload runs on every release build, but nobody has confirmed a real
  obfuscated trace resolves in the console. Do this before the first minified
  release ships.
- [ ] **Themed icon and splash screen on real hardware** — both verified on an
  API 37 emulator only.

## Bugs & tech debt

- [ ] **Voice-to-note API is really slow** — transcription round-trip (Railway Node backend) takes noticeably long; investigate where the time goes (audio upload size, model latency, cold starts on Railway) and fix. Noticed 2026-08-08 while testing on a physical device.
- [ ] **Mic record button needs a press sound** — play an audio cue when the record button is pressed (acts as feedback / a "go ahead and talk" cue for people).
- [ ] **Edit mode in `AddItemDialog` passes a no-op `onAdd = { _, _, _, _, _ -> }`** — make the callback nullable or branch add vs edit at the call site so the modes are explicit.

## Highest-ROI feature before v1: auto-categorization for typed items

Voice items are already auto-categorized — the transcribe backend returns a category id
per item and the repository validates it against the local categories table. The gap is
typed items, where the category field sits empty unless the user picks one. Fill it from
the item name, offline, in two layers: (1) history first — reuse the category this user
last gave the same item name (one Room query, self-improving); (2) fall back to a small
keyword → category seed map ("eggs" → Food); else leave uncategorized. No backend, small
code, and typed items reach parity with voice.

---

## Good to have non priority Feature backlog

- [ ] **Stale-list WorkManager** — a worker that finds stale lists (criteria TBD) and moves them to a history list, with a way to move them back to active/complete. (Good UI + learning opportunity.)
- [ ] **Delete-with-reason** — when someone deletes an item, let them add an optional note on why, to make partner communication easier.
- [ ] **"Can't find the item" flow** — a fast way to suggest an alternative that beats call/chat: snap an image + a quick approve/reject. Plus a history-based suggestion algorithm.

---

## v1 release strategy

- [x] Complete the offline-first method (local-first, fully functional without internet).
- [x] Voice-to-list (capture a whole list by speaking) — shipped, with auto-naming.
- [x] Share text version of a list via message, email, whatever — shipped (`ShoppingListShareText`).
- [ ] Auto-categorization (see above).
- [ ] Open-source polish list above, then release.

## v2 roadmap

- [ ] Shareable real time list — built on `feat/real_time_list_sharing`; follow-ups:
  - [ ] **Explainer dialog for shared-list eviction** — a small visual/dialog telling the
    user "shared lists are only accessible while you're logged in — this protects your
    list" (rationale: docs/technical-decisions/004, the borrowed-phone chain). WHEN to
    show it is deliberately undecided — candidates: first share, first sign-out with
    shared lists present, or the sign-in that rediscovers them. Decide with real usage.
- [ ] Camera/gallery → auto-fill the add-item form from an image (incl. recipe photo → list).
- [ ] "Store room" — when a list is completed, move grocery items into a store; mark items as finished there to auto-carry into the next list.
