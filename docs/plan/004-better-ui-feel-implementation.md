# 004 — Better UI Feel: Implementation Plan

Branch: `feat/better_ui_feel`
Source checklist: `docs/ui/better_ui_feel.md`

**Scope:** Standardize Material icons + add animations & haptics. Defer illustrations/mascot/icon-pack swap until art is commissioned.

## Commit 1 — Haptics utility

- **New file:** `app/src/main/java/com/babegetthis/android/core/ui/haptics/Haptics.kt`
- Expose `rememberHaptic(): (Haptic) -> Unit` with enum `Light / Medium / Heavy / Success`
- Wire into:
  - Checkbox toggle — `ShoppingItemsScreen.kt` (`ShoppingItemCard`, ~L523) — `Light`
  - Tab tap — `TabPillRow.kt` L84 — `Medium`
  - FAB "Create list" — `ShoppingListScreen.kt` L152 — `Medium`
  - Swipe-delete commit (list + item) — `SwipeableCard` — `Medium`
  - Back button — `BgtTopAppBar` — `Light`
  - Snackbar undo tap — `Light`
  - List just completed (last item ticked) — `Success` (heaviest)
- Rule of thumb: `Light` for frequent/low-stakes, `Medium` for context shifts, `Success` reserved for the list-complete moment. No haptics on text input.

## Commit 2 — Icon consistency pass

- Rule: **filled = action/destructive, outlined = passive/structural**
- `TabPill` data class → accept `iconActive` + `iconInactive`
- `TabPillRow.kt` L97 — `Icon(...)` picks variant by `isSelected`
- Tab icons:
  - Active tab: `Outlined.ShoppingCart` ↔ `Filled.ShoppingCart`
  - Completed tab: `Outlined.CheckCircle` ↔ `Filled.CheckCircle`
- Other swaps:
  - `Icons.Default.Person` → `Icons.Outlined.Person` (profile, `ShoppingListScreen.kt` L136)
  - `Icons.AutoMirrored.Filled.ArrowBack` → `Icons.AutoMirrored.Outlined.ArrowBack` (structural nav)
- Keep filled: FAB `Add`, swipe `Delete`, badge `Check` (all actions)
- Acceptance: `git grep "Icons.Default."` returns zero hits

## Commit 3 — Tab content `AnimatedContent`

- `ShoppingListScreen.kt` L190 — wrap the `if (uiState.displayedListsAreEmpty) … else LazyColumn { … }` block in `AnimatedContent(targetState = uiState.selectedTab)`
- Enter: `slideInHorizontally + fadeIn`; matching exit
- Direction by sign of `(target - initial)` so Active→Completed slides left, reverse slides right

## Commit 4 — NavHost screen transitions

- `navigation/BgtNavGraph.kt` L55–71 — add to the `SHOPPING_ITEMS` `composable(...)`:
  ```kotlin
  enterTransition    = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() }
  exitTransition     = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() }
  popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() }
  popExitTransition  = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
  ```
- Use `tween(300, easing = FastOutSlowInEasing)` on all four
- Skip LOGIN/REGISTER for now — rarely hit

## Commit 5 — "Item picked up" animation sequence

File: `ShoppingItemsScreen.kt` (`ShoppingItemCard`, ~L523). Break into sub-animations:

1. **Checkbox icon morph** — `Crossfade(targetState = item.isPickedUp, animationSpec = tween(180))` around the icon (empty circle → filled check)
2. **Scale pop** — `animateFloatAsState` on `scale` modifier, `1f → 1.15f → 1f`, driven by `LaunchedEffect(item.isPickedUp)` with `spring(Spring.DampingRatioMediumBouncy)`
3. **Strikethrough fade-in** — animate alpha of a strikethrough overlay 0 → 1 over 200ms (don't toggle `textDecoration` instantly)
4. **Row color fade** — `animateColorAsState` on surface tint; keep existing `animateContentSize`
5. **Haptics** — `Light` on toggle; `Success` if this was the last unchecked item

ViewModel: `ShoppingItemsViewModel.kt` — expose `Channel<UiEvent>` for the "list just completed" one-shot signal, consumed in the screen via `LaunchedEffect`.

## Commit 6 — "List complete" celebration *(partially done, needs more work)*

File: `ShoppingItemsScreen.kt` `ProgressCard` composable.

1. Progress bar fills to 100% — existing tween, keep ✅
2. "All done!" card scales in `0.85 → 1.0` with `spring(DampingRatioLowBouncy)` ✅ (implemented via `Animatable.snapTo` + `animateTo`)
3. Small `Filled.CheckCircle` rotates `-90° → 0°` + fades in (200ms tween) ⚠️ **rotation not visible on device**
4. `Haptic.Success` fires once via the SharedFlow from Commit 5 ✅
5. Guard with `hasInitialized` so it only plays on transition, not initial composition ✅

### ⚠️ Open: rotation animation not landing

**Symptom:** the icon appears and fades in, but the rotation from -90° to 0° isn't visibly happening on device. The pop + fade reads as "subtle" overall.

**Possible causes to investigate:**
- The `Icon` size is 20.dp — at that size, a rotation may be too small to perceive. Try 28–32.dp.
- `graphicsLayer { rotationZ = checkRotation.value; alpha = checkAlpha.value }` — both values may be reading the initial settled state because the `if (allDone)` outer gate causes the Icon to only mount AFTER `LaunchedEffect` already snapped values to settled. The `snapTo(-90f)` may be running but the Icon isn't composed yet to display it. Try always composing the Icon (with `alpha = 0` when not done) instead of gating with `if (allDone)`.
- Or: the `LaunchedEffect` runs all branches in one go — `snapTo(-90f)` then `animateTo(0f)` may collapse to no visible interpolation because both happen in the same effect body, and the Icon doesn't re-compose between them.

**Suggested next step:** drop the `if (allDone)` gate inside the Row; always compose the Icon with `alpha = checkAlpha.value` controlling visibility. Then split the rotation `snapTo` and `animateTo` into separate suspending calls with `delay(0)` or `withFrameNanos { }` between them, so the layer composes once with `-90°` before easing to `0°`.

Also worth dialing the celebration up if it still feels muted: bigger icon (28–32.dp), longer scale window (give the spring more time), or add a brief "primaryContainer glow" via background pulse.

## Files touched

| File | Change |
|---|---|
| `core/ui/haptics/Haptics.kt` *(new)* | Haptic util |
| `feature/shoppinglist/ui/components/TabPillRow.kt` | Active/inactive icon swap + haptic |
| `feature/shoppinglist/ui/ShoppingListScreen.kt` | `AnimatedContent`, icon imports, FAB haptic, TabPill icon pairs |
| `feature/shoppinglist/ui/components/TabEmptyState.kt` | Icon style audit |
| `feature/shoppinglist/ui/ShoppingListEmptyState.kt` | Icon style audit |
| `feature/shoppinglist/ui/components/SwipeableCard.kt` | Haptic on swipe commit |
| `feature/shoppingitems/ui/ShoppingItemsScreen.kt` | Pick-up sequence + list-complete celebration + haptics |
| `feature/shoppingitems/ui/ShoppingItemsViewModel.kt` | `Channel<UiEvent>` for list-complete one-shot |
| `navigation/BgtNavGraph.kt` | Enter/exit/pop transitions on `SHOPPING_ITEMS` |
| `core/ui/components/BgtTopAppBar.kt` | Outlined back arrow + haptic |

## Verification

After each commit:
```bash
./gradlew assembleDebug
./gradlew lint
```

Manual test (real device — emulators don't do haptics):

1. **Haptics** — each call site buzzes; list-complete buzz is distinctly heavier
2. **Tab swap** — Active ↔ Completed slides horizontally with fade, not a snap
3. **Screen nav** — Tap list → slides in from right; back → slides out right
4. **Icon states** — Tap a tab; icon visibly fills/unfills (not just color change)
5. **Pick-up** — Checkbox morphs, row pops, strikethrough eases in; last item triggers heavier haptic + "All done!" card pops in with rotating check
6. **Greppable** — `git grep "Icons.Default."` returns nothing

Run `./gradlew test` if any ViewModel tests exist (Commit 5 is the only VM-touching change).

## Deferred

- Empty-state illustrations / mascot / Rive — needs commissioned art. Drop `// TODO(illustration)` next to empty-state composables.
- Lucide / Phosphor / Tabler icon-pack swap — revisit later if we want a more distinct visual identity.

## Resume notes

- Plan source (more detailed rationale): `/Users/sadaqadeveloper/.claude/plans/fuzzy-jingling-pearl.md`
- Suggested commit order matches the section order above; each is small enough to review in isolation.
- User types code themselves — when sitting down to write, ask Claude for a specific snippet if a piece is unfamiliar (e.g., `Channel<UiEvent>` + `LaunchedEffect` for one-shot events is the Compose/Kotlin equivalent of a Flutter `Stream`/`Cubit` listener).
