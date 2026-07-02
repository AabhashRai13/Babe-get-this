# TODO

Consolidated backlog for **Babe, Get This**. Prioritized work up top; longer-term
feature ideas and the v1/v2 roadmap below.

## Open-source polish (do before making the repo public)

From a full-codebase review (2026-07-02). The code itself came back clean — no dead
code, no debug logs, no secrets in git, real unit tests. What's left is polish:

- [ ] **Add a LICENSE file** — the repo has none, so nobody can legally use the code. MIT or Apache-2.0.
- [ ] **Merge `feat/cicd`** — `.github/workflows/build-aab.yml` already exists on that branch. Merge it and add a build badge to the README.
- [ ] **Extract ~15 hardcoded UI strings to `strings.xml`** — `AddItemDialog` (note/shop/category field labels), `ProfileBottomSheet` ("Name", "Save", "Log out"), `CreateListChooserSheet` ("Type", "Voice"), `ShoppingListScreen` ("Sign in").
- [ ] **Naming consistency** — rename `ShoppingListModel.kt` → `ShoppingListEntity.kt` (to match `ShoppingItemEntity`); pick one ViewModel package convention (`ui/` vs `ui/viewModels/`) and apply it to both features; rename `ItemDraft.shop` → `location` to match the DTO and backend.
- [ ] **Delete template stubs** — `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` are untouched Android Studio boilerplate.
- [ ] **Split the oversized composables** — `ShoppingListScreen` (462 lines: extract the create-list flow and the list pane), `AddItemDialog` (433: extract a `CategoryDropdownField`), `ShoppingItemsScreen` (357: extract the by-shop items section).
- [ ] **Accessibility** — add `contentDescription` to the stop icon in `VoiceCaptureSheet` and a semantics label to `TranscribingWaveform`.
- [ ] **Update CLAUDE.md** — SDK versions are stale (says min 26 / target 35; actual is min 24 / target 36), and the token-refresh bug note is obsolete: the fix already shipped (`BabeGetThisApp` observes `sessionStatus` and writes rotated tokens back).

## Bugs & tech debt

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

- [ ] Shareable real time list
- [ ] Camera/gallery → auto-fill the add-item form from an image (incl. recipe photo → list).
- [ ] "Store room" — when a list is completed, move grocery items into a store; mark items as finished there to auto-carry into the next list.
