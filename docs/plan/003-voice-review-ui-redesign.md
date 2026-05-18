# 003 — Voice review sheet redesign

**Status:** Planned (not yet started)
**Date drafted:** 2026-05-18
**Builds on:** [plan/001 — Voice-to-list implementation](001-voice-to-list-implementation.md)
**Mockup:** `~/Desktop/reviewingmode.png`

## Goal

Replace the current `ReviewingMode` (inside `VoiceCaptureSheet.kt`) with a polished review screen that matches the mockup. The sheet becomes the moment the user *names their list* and *cleans up what voice heard*, not a robotic confirmation prompt.

In the new design:
- The top is an **editable list title**, pre-filled with a timestamp default. Pencil icon hints at editability.
- Each row shows **name on the left, a vertical divider, quantity right-aligned**, inside a rounded card. Both fields are editable.
- **No trailing trash icons.** Removal happens via swipe-to-delete, matching the pattern already used for shopping items / shopping lists (`core/ui/components/SwipeableCard.kt`).
- "Create list" pill CTA + "Cancel" text button at the bottom — unchanged in behavior.

The component is now big enough to deserve its own file.

## Architecture at a glance

```
core/voice/ui/
  VoiceCaptureSheet.kt              ← stays as the orchestrator (state-machine switcher)
  reviewing/
    ReviewingMode.kt                ← NEW. The whole reviewing UI moves here.
    ReviewItemRow.kt                ← NEW. One swipeable row (name + qty + divider).

core/voice/model/
  VoiceCaptureUiState.kt
    Reviewing(drafts, listName)     ← state gains `listName` so VM owns it,
                                       not `remember { ... }` in the composable.

core/voice/ui/viewModels/
  VoiceCaptureViewModel.kt
    + editListName(newName: String)
    + editDraftQuantity(index, newQty: String)
    onTranscribeComplete()          ← seeds Reviewing.listName with default

feature/shoppinglist/ui/
  ShoppingListScreen.kt
    onConfirm signature             ← now passes the name the user typed
                                       through to createListWithVoice.
  ShoppingListViewModel.kt
    createListWithVoice(name, drafts)  ← takes the name from the sheet
                                          instead of auto-generating it here.
    autoNameVoiceList()             ← becomes the *default seed* helper, not
                                       a fallback at persist time.
```

The sheet stays dumb about naming policy: it just edits whatever default it was seeded with. The shopping-list feature owns the seed (because "what's the default name" is a list-feature decision, not a voice-flow concern).

## Decisions locked before coding

| Decision | Choice | Why |
|---|---|---|
| Where ReviewingMode lives | New file `core/voice/ui/reviewing/ReviewingMode.kt`; row extracted to `ReviewItemRow.kt` | Sheet is already ~325 lines. Two small files beat one big one. Mirrors how `ShoppingItemRow` is split out in the lists feature. |
| Who owns the list-name state | `VoiceCaptureUiState.Reviewing.listName` (ViewModel) | Survives recomposition + process death. `remember { mutableStateOf(...) }` would lose the typed name if the sheet recomposes from outside. Same reason we already keep `drafts` in the VM. |
| Who computes the default name | Host (`ShoppingListViewModel.autoNameVoiceList()`), passed into the sheet | Naming format is a shopping-list product decision. `core/voice/` should not know about list naming conventions. Pass it down via a `defaultListName: String` param on the sheet. |
| Sheet → host signature | `onConfirm: suspend (name: String, drafts: List<ItemDraft>) -> Result<String>` | The sheet now has the user's typed name; the host should not re-generate one. Single source of truth for the name = what the user saw on screen. |
| Default qty when API returns `null` | Empty value + `"qty"` placeholder text in onSurfaceVariant gray. `ItemDraft.quantity` stays `null` until user types something. | "1" alone is ambiguous (1 egg? 1 dozen? 1 crate?) and forcing a default that the user has to delete is nag-UX. Empty + placeholder is honest about what the model knew, preserves the "model failed to extract qty" signal for analytics, and still gives a tap target. |
| Delete affordance | Swipe-left only (reuse `SwipeableCard`); no right-swipe action | Matches `ShoppingItemRow`. Right-swipe (mark as picked up) is meaningless here — items don't exist yet. |
| Name field UX | Plain `OutlinedTextField` styled to look flat (mockup has no visible border on title); pencil icon = decorative affordance, taps focus the field | One field, no separate "edit mode" toggle. Less code, more discoverable. |
| Row layout | Single `Row` with two `BasicTextField`s separated by a fixed-width vertical `Divider`; weight 1f on name, weight 0.4f on qty | Mockup shows name dominant + small qty. `BasicTextField` (not Outlined) so the card itself supplies the border, not each field. |

## Open questions for [[you]] before we start

~~1. Default name format.~~ **Resolved:** keep existing `"List · 18 May"` format. User can rename anyway.
~~2. Quantity placeholder vs value.~~ **Resolved:** empty value + `"qty"` placeholder when API returned `null`. See decision table above.

## Step-by-step plan

Each step is small enough to commit on its own. Stop after any step if you want to ship intermediate progress.

### Step 1 — State + signature plumbing (no UI changes yet)

**Files:** `VoiceCaptureUiState.kt`, `VoiceCaptureViewModel.kt`, `VoiceCaptureSheet.kt` (signature only), `ShoppingListScreen.kt`, `ShoppingListViewModel.kt`.

- Add `listName: String` to `VoiceCaptureUiState.Reviewing`.
- Seed it in the VM when transitioning from `Transcribing → Reviewing` (param flows in from the sheet host).
- Add `defaultListName: String` param to `VoiceCaptureSheet` composable + thread through to VM.
- Add `editListName(newName: String)` and `editDraftQuantity(index: Int, newQty: String)` to the VM.
- Change `onConfirm` to `suspend (name: String, drafts: List<ItemDraft>) -> Result<String>`.
- Update `ShoppingListScreen.kt` call site: pass `viewModel.autoNameVoiceList()` as `defaultListName`; pass `name` through to `createListWithVoice(name, drafts)`.
- Update `ShoppingListViewModel.createListWithVoice` to accept the name from the sheet instead of generating it.

**Verify:** app compiles and still works exactly as before — UI is unchanged in this step. The auto-name shouldjust now come from the sheet round-trip rather than the VM generating it at persist time.

### Step 2 — Extract current ReviewingMode to its own file (no UI changes)

**Files:** `core/voice/ui/reviewing/ReviewingMode.kt` (new), `VoiceCaptureSheet.kt`.

- Move the existing `private fun ReviewingMode(...)` verbatim into `ReviewingMode.kt`.
- Make it `internal` (same module) and import in the sheet.
- Pure refactor: no behavior change. Easy diff to review.

**Verify:** voice flow still works end-to-end identically.

### Step 3 — Add the editable title row

**Files:** `ReviewingMode.kt`.

- Replace the "We heard X items" `Text` with an editable title.
- Use `OutlinedTextField` with `colors = TextFieldDefaults.colors(...)` set to transparent borders to mimic the flat look.
- Trailing icon: pencil (`Icons.Default.Edit`), decorative.
- Wire to `state.listName` + `viewModel::editListName`.

**Verify:** title is editable, persists across rotation (because VM-owned), still seeds with the default from the host.

### Step 4 — New row layout (name | qty) without swipe yet

**Files:** `core/voice/ui/reviewing/ReviewItemRow.kt` (new), `ReviewingMode.kt`.

- New composable `ReviewItemRow(name, quantity, onNameChange, onQtyChange)`.
- Card container (`Surface` with `tonalElevation`, rounded 16.dp).
- Inside: `Row` of `BasicTextField` (name, weight 1f) → vertical `Divider` (1.dp wide, 24.dp tall) → `BasicTextField` (qty, weight 0.4f, text aligned end).
- Keep the existing trash `IconButton` for now — we'll remove it in Step 5 once swipe replaces it. (Two affordances briefly = safer than zero if Step 5 has a bug.)

**Verify:** names and quantities both editable, layout matches mockup, deletion still works via trash.

### Step 5 — Replace trash with swipe-to-delete

**Files:** `ReviewingMode.kt`, `ReviewItemRow.kt`.

- Wrap each `ReviewItemRow` with `SwipeableCard(onSwipeLeft = { onRemove(index) })`.
- Remove the trash `IconButton` from the row.
- Sanity check: an empty list (`drafts.isEmpty()`) should disable "Create list" — already does, but worth re-confirming after the refactor.

**Verify:** swipe-left removes an item with the red background + trash icon, identical to lists/items elsewhere. Confirm haptics + animation feel right.

### Step 6 — Polish pass

**Files:** `ReviewingMode.kt`, `ReviewItemRow.kt`.

- Stagger entry animation (50ms per row) using `AnimatedVisibility` inside `itemsIndexed`.
- Tighten paddings to match mockup spacing.
- Verify dark mode looks correct (cards use `MaterialTheme.colorScheme.surfaceContainer` not a hardcoded color).
- Verify long names truncate or wrap sensibly.

**Verify:** side-by-side with mockup, screenshot diff.

## What we are NOT doing in this plan

- Category icons / emoji per item (deferred — needs categorization data we don't have yet).
- "+ Add item" row (deferred — useful but not in mockup; add only if you ask).
- Real `RemoteVoiceRepository` integration (separate plan, separate session).
- Touching the recording / transcribing / failed states. Those screens are out of scope.

## Rollback plan

Each step is a single commit on `feat/voice_translator`. If any step regresses behavior, `git revert <sha>` returns to the previous working state. Step 1 is the riskiest because it changes a signature across feature boundaries; everything after that is layered on top.
