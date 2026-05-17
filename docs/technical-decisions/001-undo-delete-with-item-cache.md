# 001 — Undo-delete for shopping lists: cache items in the ViewModel

**Status:** Accepted
**Date:** 2026-05-01
**Area:** `feature/shoppinglist` — list deletion + undo

## Context

A user can swipe a shopping list to delete it and is shown a snack bar with an "Undo" action for ~5 seconds. The list contains zero or more shopping items, stored in a separate `shopping_items` table.

The `shopping_items` table has a foreign key to `shopping_lists` with `ON DELETE CASCADE`. So a single `DELETE FROM shopping_lists WHERE id = ?` wipes all child items from the DB immediately.

The original undo flow only cached the list row in the ViewModel and called `restoreList` on undo — which re-inserted only the list, not the items.

### Symptoms this caused

1. **Items disappeared on undo.** A list deleted with 5 items would come back empty.
2. **Restored list landed in the wrong tab.** `isCompleted` is derived in SQL as `itemCount > 0 && completedItemCount == itemCount`. With items gone, `itemCount = 0`, so the list always restored as "not completed" and appeared in the Active tab — even if it was deleted from the Completed tab.

## Decision

Adopted **Option B**: capture items in the ViewModel before delete, re-insert them on undo.

Concretely:

- `ShoppingItemDao` exposes `getItemsByListIdOnce(listId): List<ShoppingItemEntity>` (one-shot, suspending) and a batch `insertItems(items: List<…>)`.
- `ShoppingListRepository.deleteListAndCaptureItems(listId)` reads items, then deletes the list, returning the captured items.
- `ShoppingListRepository.restoreListWithItems(list, items)` re-inserts both.
- `ShoppingListViewModel` holds `pendingDeleteList` and `pendingDeleteItems` for the undo window.

Once both rows + their child items are back, the SQL aggregation in `getAllListsWithItemCount` re-derives `isCompleted` correctly, so the list returns to the tab it came from.

## Alternatives considered

### Option A — Soft delete (`isDeleted` flag on the list entity)

Add a column, update writes to use `UPDATE … SET isDeleted = 1`, filter all read queries to exclude deleted rows.

- **Pros:** No data ever leaves the DB; trivial undo (flip the flag); supports longer / multi-step undo windows; cleaner if undo windows ever grow beyond a snackbar.
- **Cons:** Requires a Room schema migration; touches every list query; meaningful overhead for an MVP that does not yet need "trash" semantics.

Rejected for MVP — overkill for a 5-second undo snack bar. Worth revisiting if we ever add a "Recently deleted" screen or extend the undo window.

### Option C — DB-level transaction with rollback

Open a transaction, delete, and roll it back if the user taps undo.

- **Pros:** Single source of truth in the DB.
- **Cons:** Holding a transaction open across UI interaction (a 5-second snack bar) is a bad pattern — locks the DB, blocks other writes, fragile across process death. No real upside over Option B.

Rejected.

## Consequences

### Positive

- Items survive delete + undo.
- Restored lists return to their original tab because derived state recomputes from the restored items.
- No schema migration; isolated to three files.

### Negative / known tradeoffs

- **Layering compromise.** `ShoppingListViewModel` now imports `ShoppingItemEntity`, a data-layer type. CLAUDE.md states "Repositories return Kotlin data classes, never raw API/DB models." The cache is transient and never rendered, so we accept the leak rather than introducing a `toDomain` / `toEntity` round-trip on data the user just deleted. If undo-cached items ever become user-visible (e.g. shown in a "deleted items" preview), refactor to map to the domain `ShoppingItem` model on capture.
- **Cache lives only in memory.** If the process dies during the undo window, the items are lost. Acceptable for a snackbar-length window; not acceptable if we extend it. Revisit alongside Option A if we do.
- **No transaction wrapping the restore.** `insertList` followed by `insertItems` are two separate suspend calls. If the second one fails, we end up with a list and no items. Low risk for local Room writes, but worth wrapping in `@Transaction` if it ever becomes a concern.

## When to revisit

- We add a "Recently deleted" screen → switch to Option A (soft delete).
- Undo window extends beyond the snack bar lifetime → switch to Option A.
- We add multi-device sync → soft delete becomes necessary anyway (tombstones for conflict resolution).
