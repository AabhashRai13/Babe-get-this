# Pending Bugs

No known open bugs. 🎉

---

## Resolved

1) ~~Empty state showed two create-list buttons — the floating button should hide on empty state.~~
   **Fixed** — FAB is gated by `if (uiState.isActiveTab && !uiState.hasNoLists)` in `ShoppingListScreen.kt`, so it's hidden when there are no lists.

2) ~~Deleting a list in the Completed tab then undoing it sent the list to the Active tab and dropped its items.~~
   **Fixed** — delete now captures items before the CASCADE (`deleteListAndCaptureItems`) and undo re-inserts the list **and** its items (`restoreListWithItems`), so `isCompleted` recomputes and the list returns to the Completed tab.

3) ~~Voice recording silently failed to start on the second use: record → transcribe → open list → come back → tap mic does nothing → tap again works.~~
   **Fixed** — root cause was a stale ViewModel state, not mic flakiness. `VoiceCaptureViewModel` is scoped to the Lists screen's nav entry, so it survived the navigation into the new list and came back stuck in `Done`. On reopen, the sheet's auto-dismiss `LaunchedEffect(state)` saw the stale `Done` and closed the sheet, eating the first tap. The success path now resets the VM to `Idle` (via `cancel()`) when it dismisses, so a reopened sheet records on the first tap. See `VoiceCaptureSheet.kt`.

4) ~~Voice review sheet swipe-delete cascade (deleting one draft deleted the next; last item unswipeable) — missing `key` on `itemsIndexed`.~~
   **Moot** — the review step was removed entirely in the voice-transcribe-auto-list change. Voice now persists parsed drafts directly with no review list, so there's no swipeable draft list to break (`core/voice/ui/reviewing/` no longer exists).
