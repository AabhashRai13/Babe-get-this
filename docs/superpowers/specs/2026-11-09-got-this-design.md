# Got This — design

**Date:** 2026-11-09 · **Status:** draft, awaiting review · **Branch:** `feat/got-this`

## Summary

Got This remembers what each person has bought, from the items they tick, and
uses it two ways:

1. **"Bought 3 days ago"** — a quiet line under an unticked item that was
   bought recently, so nobody buys the same thing twice by accident.
2. **"Running low?"** — the items a person buys regularly, suggested on the
   list screen when they are due again, plus at most one notification a week.

Nobody sets anything up. There is no "for home" mark on lists, no family
groups and no "picked up by". Whether something is a household regular is
worked out from the buying pattern: eggs come back every week, BBQ sausages
bought once never become a regular. All judging runs on the phone with simple
rules. Both parts ship in the same release.

The release also lays the ground for a paid tier ("Plus", its own spec): the
running-out part counts how much of it each person has used, behind a flag
that stays off until Plus exists.

## Why

People buy things they already have. A retailer survey found 68% of people
had bought food without realising the same item was waiting at home. A
shared list makes it worse: one partner ticks milk on Monday, the other adds
milk on Tuesday.

The first Got This design was lost with the machine it lived on, and its
first replacement depended on users marking each list as "for home" or not.
In practice people will not do that, and they do not name lists in a way the
app could read either. So the app has to tell a weekly shop from a BBQ on
its own, from what is bought and when.

## Decisions

Recorded with the alternative that was rejected, so they are not re-argued.

| # | Decision | Rejected alternative, and why |
|---|---|---|
| D1 | **Household regulars are inferred from repeat purchases.** No list mark, no list-name parsing. | A "for home" mark on lists — users will not set it, and list names are too inconsistent to parse. |
| D2 | **No family groups.** | Family groups with invites and their own server tables — a large build, and a shared list already covers the couples this app is for (tradeoff 001 describes lists as "effectively couples"). |
| D3 | **No "picked up by".** Nobody records who ticked an item. | Attribution (`updated_by` from tradeoff 001) — it says who ticked, not whether the thing went home, and D4 makes the ticker irrelevant. It also only keeps the last editor. |
| D4 | **A tick becomes a purchase for every member of the list at that moment, each owning their own copy.** History survives list deletion; a person who joins later gets nothing from before; account deletion removes only that person's copies. This revises 008's D5: history is still per person, but a shared list's ticks reach everyone on it. | History for the ticker only — the partner who did not shop gets no "bought 3 days ago" for the milk the other bought, which is the main case. History scoped to the list — lost when the list is deleted, and finished lists get deleted. |
| D5 | **Plain rules on the phone, no machine learning, no server model.** | A trained model — published comparisons find simple "what you buy most" methods beat deep models for repeat grocery items, and the one production system with numbers was trained on about 100M shoppers. Rules are explainable and testable. |
| D6 | **The hint states a fact ("Bought 3 days ago") and never blocks or asks.** The item is always added. | A warning dialog on add — friction on every false hint. "You have this" wording — false after a BBQ, which is the case that started this. "You bought" — we do not know who ticked (D3). |
| D7 | **Both parts ship in one release.** | Hint first, running-out later — rejected by the product owner. |
| D8 | **The server writes members' purchases**, in a trigger on `items`, with a deterministic id. Clients write purchases only for guests and for the one-time device backfill. | Every device derives purchases from synced tick changes — misses a tick and untick that happen between two pulls, and two phones on one account would each write one. |
| D9 | **Unticking within 2 hours of the tick undoes the purchase; a later untick keeps it.** | Any untick deletes the purchase — people reuse lists by unticking last week's items, which would erase real history. Never undo — an accidental tick becomes a false "bought today". |
| D10 | **Backfill from items already ticked**, using their `updated_at` as the purchase time. | Start empty — running-out would say nothing for weeks after release, and the ticked items on a phone today are usually ticked once and left alone, so `updated_at` is close to the tick time. |
| D11 | **Item matching is conservative**: clean the name, then match exactly or by one typo. | Fuzzy matching on word overlap — "milk" would match "coconut milk". A missed match costs one missing hint; a wrong match puts a wrong one on every list. |
| D12 | **Doubt means silence** (as in the reminders spec, D9): an item that does not meet every rule shows nothing. | Hedged suggestions for borderline items — wrong reminders train people to ignore all of them. |
| D13 | **The running-out part is counted and flag-gated for Plus, but free in this release.** Each week in which it shows anything counts as one week used. When Plus ships, anyone past 3 free weeks gets the Plus trial before anything locks. | Ship billing in this release — Plus needs Play Billing plus server-side purchase checks so one subscription covers a household; that is its own subsystem and its own spec. Gate nothing and add counting later — the first Plus release would have no idea who has used what. |
| D14 | **A 10% holdout for each install's first 8 weeks**: history is recorded, nothing is shown, and telemetry logs what would have been shown. | No holdout — removal and add rates would have nothing to compare against, so a useless hint would still look like it worked. A permanent holdout — those users never get the feature. |
| D15 | **History is kept until the person clears it or deletes their account.** Settings gets "Clear purchase history". | A fixed retention period — running-out needs months for monthly items, and a clear button is a more direct privacy control. |

## What users see

### "Bought 3 days ago"

An unticked item whose name matches a recent purchase shows one extra line
in the item row, in the secondary text colour:

> Milk
> Bought 3 days ago

- Wording by age: "Bought today", "Bought yesterday", "Bought N days ago".
- It appears on any unticked item, however it got there: typed, spoken, or
  added by the partner. It is worked out when the row is drawn and stored
  nowhere.
- Tapping the line opens a small menu: **Remove from list** (deletes the item
  through the existing delete, with its undo) and **Don't remind me about
  this** (mutes the item for 60 days).
- An item that is ticked while its hint is showing counts as "kept". Two kept
  in a row mute the item for 60 days on that phone.
- Ticked items never show the line.

### "Usually bought around now"

The items screen of a list that is not finished shows a row above the items
when anything is due:

> **Usually bought around now**
> [+ Eggs] [+ Milk] [+ Bread] [+3]

- Tapping a chip adds the item to this list with quantity "1" and the
  category it was last bought with, through the existing add path. The chip
  leaves the row.
- An item already unticked on any of the person's lists is never suggested.
- **Not now** (an icon at the end of the row) hides the row on every list for
  24 hours.
- Up to 6 chips, most overdue first; the rest collapse into "+N", which
  expands the row.
- When notifications are not allowed, the row ends with **Get a weekly
  heads-up**, which opens the existing permission sheet with Got This copy.

### The weekly notification

At most one a week, at about 18:00 on the evening before the person's usual
shopping day:

> **Running low?**
> Eggs, milk and bread are usually bought around now.

- It names up to 3 items that are due by the next day, most overdue first.
  With more than 3: "Eggs, milk, bread and 2 more".
- Tapping it opens the items screen of the most recently edited list that
  still has unticked items, or the list catalog when there is none. The
  suggestions row is there.
- With no clear usual shopping day (see Running low), it goes out at about
  18:00 on the first day something becomes due, still at most once a week.
- Its own notification channel, "Running-low reminders", separate from
  shopping reminders, so either can be muted in system settings.

### Settings

A new **Got This** section:

- **Bought-recently hints** — switch, on by default.
- **Running-low reminders** — switch, on by default. Shows "Notifications are
  off for this app" with a link to system settings when the permission is
  missing, like the shopping reminders row.
- **Clear purchase history** — confirmation dialog: "This removes everything
  Got This has learned, on all your devices. Your lists stay as they are."

### Guests

Everything works the same, from the phone's own history. When a guest signs
in, their history moves into the account with their lists (008 D3).

## How judging works

Everything below is pure Kotlin with no Android types, in
`feature/gotthis/domain/`, so it can be tested and replayed against recorded
data on the JVM. The numbers are starting values, set in one file
(`GotThisRules.kt`) and tuned from telemetry (see Measuring whether it works).

### Same item?

`ItemKey.of(name)` turns a name into a key:

1. Unicode NFKD, strip accents, lowercase with `Locale.ROOT`.
2. Punctuation to spaces, collapse whitespace, trim.
3. Drop a leading or trailing quantity: numbers, fractions, and the units
   `x g kg ml l pack packs bag bags bottle bottles can cans box boxes dozen`,
   plus a following "of". "2 bags of rice" → "rice", "milk 2l" → "milk".
4. Drop the size and freshness words `fresh organic large small big medium`.
5. Make each word singular with plain English rules: "-ies" → "-y"
   (berries), "-oes" → "-o" (tomatoes), "-ses/-xes/-ches/-shes" lose "-es",
   a final "s" goes unless the word ends in "ss", "us" or "is".

Two keys match when they are equal, or when both are at least 5 characters,
have the same number of words, and differ by one edit (Damerau–Levenshtein
distance 1: "bannana" and "banana"). "Milk" and "coconut milk" never match.

The key is stored with each purchase in Room. `ItemKey.VERSION` is stored in
prefs; when it changes, every stored key is recomputed at start-up. The
server stores only the name.

Names in other languages still match themselves exactly; only the plural
rules are English.

### What counts as a purchase

- A tick that is not undone within 2 hours (D9). Each tick is one purchase,
  so a list that is reused and ticked again next week records a new one.
- Purchases of the same key are grouped into **shops**: a purchase less than
  2 days after the first day of the current shop joins it. A BBQ bought over
  Friday and Saturday is one shop.
- Days are local calendar days.

### Regular or one-off?

An item is **regular** when all of these hold:

| Rule | Value |
|---|---|
| Shops | at least 3 |
| First shop to last shop | at least 14 days |
| Longest gap between shops | at most 3 × the shortest gap |

Its **usual gap** is the median of the gaps between its last 7 shops (up to 6
gaps). An item is **lapsed** when the time since its last shop is more than
2.5 × its usual gap: the person has stopped buying it, and it is treated as
not regular until it is bought again.

A party prepared over three days in one week fails the 14-day rule. A monthly
BBQ host's sausages do become a regular; that is accepted (see Accepted
trade-offs).

### "Bought N days ago": the window

The hint shows when the last purchase of the item was fewer than *window*
days ago:

- **Regular item:** half its usual gap, at least 1 day. Weekly eggs get a
  3-day window; monthly fuel gets 15.
- **Anything else:** a window by the category of its most recent purchase
  that had one:

| Group | Window | Default categories (ids without `cat-`) |
|---|---|---|
| Fresh | 3 days | fruits-vegetables, meat-seafood, bakery-bread |
| Short-life | 5 days | dairy-eggs, ready-meals |
| Weeks | 14 days | snacks, sweets-chocolate, beverages, breakfast-cereal, alcohol |
| Long-life and consumables | 30 days | frozen-foods, pantry-dry-goods, canned-jarred, baking-supplies, spices-seasonings, sauces-condiments, oils-vinegars, spreads, coffee-tea, household-cleaning, paper-disposables, toiletries-personal-care, health-pharmacy, baby-kids, pet-supplies, tobacco-vaping, office-stationery, automotive |
| Durable | 90 days | kitchen-dining, appliances, furniture-storage, home-decor, bedding-bath, lighting-electrical, hardware-diy, garden-outdoor, clothing, underwear-sleepwear, shoes, accessories-jewelry, bags-luggage, electronics, toys-games, books-media, sports-outdoors, arts-crafts, party-gifts |
| Unknown | 7 days | other, any custom category, no category |

The BBQ case: sausages (Fresh) re-added a week later get no hint; charcoal
(Long-life) re-added 12 days later does, and is worth asking about.

A test fails if a default category id is missing from this table, so a new
default category cannot ship without a window.

### Running low: when something is due

A regular, not lapsed, not muted item is **due** when the days since its last
shop are at least 90% of its usual gap, rounded up. It stays due until it is
bought, added to a list, or it lapses.

**Usual shopping day:** among the distinct days in the last 56 that had at
least one purchase, take the weekday with the most. It counts only with at
least 4 such days and when that weekday has at least 40% of them. People buy
on shopping days, which is why repeat orders cluster at 7, 14 and about 30
days.

## Components

All new code lives in `feature/gotthis/`, following the existing feature
layout.

| Unit | Does | Depends on |
|---|---|---|
| `domain/ItemKey` | Name → key, and key matching | nothing |
| `domain/GotThisRules` | Every threshold and the category window table | nothing |
| `domain/ItemJudge` | Purchases of one key → `ItemProfile` (shops, regular, usual gap, lapsed, due, window) for a given day | `GotThisRules` |
| `domain/ShopDay` | Purchase days → usual shopping weekday or none | `GotThisRules` |
| `data/local/PurchaseDao`, `PurchaseEntity`, `ItemMuteEntity` | Room access | Room |
| `data/PurchaseRecorder` | Writes and undoes local purchases on tick and untick | `PurchaseDao` |
| `data/GotThisRepository` | Hints for a list's items, due items for an owner, clearing history | DAO, judge |
| `data/GotThisBackfill` | One-time guest backfill and key recompute | DAO, prefs |
| `data/GotThisAccess` | Whether running-low may show (holdout, Plus gate, kill switch) | flags, prefs |
| `reminder/RunningLowWorker`, `RunningLowScheduler` | Daily 18:00 check and the weekly notification | WorkManager, repository |
| `ui/BoughtHintLine`, `ui/UsualsRow` | The two pieces of UI | view models |

`ItemProfile` is computed on demand. With at most a few thousand purchases
per person, the list screen reads the purchases for the keys on that list in
one query; the daily worker reads all of the owner's.

### Repository hooks

- `ShoppingItemRepository.togglePickedUp(itemId, true)` sets `pickedUpAt =
  now` and calls `PurchaseRecorder.onTick`.
- `togglePickedUp(itemId, false)`: within 2 hours of `pickedUpAt`, it clears
  `pickedUpAt` and calls `PurchaseRecorder.onUndo`. After that,
  `pickedUpAt` stays, so the purchase stands and the server trigger leaves
  it alone.
- `onTick` writes one local purchase for the current owner: the signed-in
  user, or the guest (no account). On an account list this row is a
  placeholder with the same id the server trigger will create (see Ids), so
  the pull replaces it rather than duplicating it.
- The `ShoppingItemsViewModel` tick and delete handlers report the hint
  outcome when the item had a hint showing.

## Data model

### Room (version 5 → 6, additive)

`shopping_items` gains `pickedUpAt INTEGER` (nullable, epoch millis): the time
of the current tick, or of the last tick that was not undone.

New table `purchases`:

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | deterministic (see Ids) |
| `accountId` | TEXT, null | owner; null means a guest purchase on this phone |
| `sourceItemId` | TEXT | the ticked item; no foreign key, history outlives items |
| `name` | TEXT | as ticked, for display and for chips |
| `itemKey` | TEXT | computed locally, indexed with `accountId` |
| `categoryId` | TEXT, null | the item's category at tick time |
| `boughtAt` | INTEGER | epoch millis |
| `deletedAt` | INTEGER, null | tombstone (undo, clear) |
| `pendingSync` | INTEGER | true only for guest-era rows being uploaded |

New table `item_mutes` (this phone only, never syncs): `accountId`,
`itemKey`, `keptInARow`, `mutedUntil`; primary key (`accountId`, `itemKey`),
with guests stored under the empty string.

New table `hint_impressions` (this phone only, never syncs): `itemId` PK,
`shownAt`, `holdout`. Written when a hint first applies to an item; read and
deleted when that item is removed, ticked or muted, to send the outcome
event. Rows older than 30 days are pruned at start-up.

Device prefs (`GotThisPrefs`): `keyVersion`, `backfillDone`,
`holdoutBucket` (0–99, drawn once), `holdoutStartedAt`, `runningLowWeeksUsed`
and the last counted ISO week, `lastNotifiedAt`, `usualsHiddenUntil`, the
pending reminder follow-ups (posting time and the item keys named, at most
one at a time), and the two Settings switches.

### Server (Supabase migration `006_got_this.sql`)

1. `items.picked_up_at timestamptz` (nullable).
2. A `before insert or update` trigger on `items`: when `is_picked_up` turns
   true and `picked_up_at` did not change, set `picked_up_at = now()`. This
   makes ticks from app versions that never send the column still count.
3. Table `purchases`: `id uuid primary key`, `user_id uuid not null
   references auth.users on delete cascade`, `source_item_id uuid not null`
   (no foreign key), `name text not null`, `category_id text`, `bought_at
   timestamptz not null`, `updated_at timestamptz not null default now()`
   (set by the existing `set_updated_at` trigger), `deleted_at timestamptz`.
   Index on (`user_id`, `updated_at`).
4. RLS: select and insert where `user_id = auth.uid()`. No update or delete
   policy; clearing goes through the RPC.
5. An `after insert or update` trigger on `items`, security definer. Not
   `update of picked_up_at`: a column trigger fires only when the statement
   names the column, and old app versions never do, so the value set by
   step 2 would be missed. It compares old and new itself (on insert, old
   counts as null):
   - new `picked_up_at` set and different from the old one: insert a
     purchase for every row in `list_members` for that list, `on conflict
     (id) do nothing`;
   - old set, new null (an undo): tombstone the purchases with the ids built
     from the old value.
6. RPC `clear_purchase_history()`: for `auth.uid()`, sets `deleted_at = now()`
   and `name = ''` on every row, so other phones receive the clear and the
   names are gone.
7. Backfill, once: for every item with `is_picked_up` and no `deleted_at`,
   insert a purchase for each current member with `bought_at = updated_at`.
   `picked_up_at` stays null on those items, so unticking one later never
   removes the backfilled purchase.
8. Manual negative tests as comments, as in `003_account_lists.sql`: user B
   cannot select user A's purchases; a member's tick creates rows for both
   members; an undo tombstones both; a stranger cannot insert for another
   user.

`delete_user()` needs no change: purchases cascade with `auth.users`.

### Ids

A purchase id is the MD5 of the UTF-8 text `"<user_id>:<source_item_id>:<epoch
millis of bought_at>"`, written as a UUID. In SQL that is
`md5(user_id::text || ':' || source_item_id::text || ':' ||
floor(extract(epoch from bought_at) * 1000)::bigint)::uuid`. Kotlin formats
the 16 digest bytes directly, without setting UUID version bits, so both
sides produce the same value. A test pins one input to its expected id on
both sides. Guest purchases use `"guest:<source_item_id>:<millis>"` and are
re-keyed with the user id when claimed.

## Sync

### Pull

`catchUpAccount` gains a third step, after items: fetch purchases changed
since a separate sync point `purchases`, paged like every multi-row read
(`fetchAll`, PostgREST's 1000-row cap). Each row is upserted into Room by id
with its key computed locally; a tombstone replaces the row. The point moves
only after everything landed, as for items.

`ItemRow` gains `picked_up_at`; `ItemRow.toEntity` copies it.

### Push

- `ItemRow` carries `pickedUpAt`, so ticks and undos reach the trigger with
  the normal item push.
- Purchases with `pendingSync` (guest-era rows after sign-in) go up with
  `insertPurchases(rows)`, `on conflict do nothing`. Nothing else pushes
  purchases.

### Sign-in, sign-out, session loss

- **Sign-in from guest** (`claimDeviceLists`): guest purchases get the user
  id, new ids, and `pendingSync = true`. If the trigger has already made the
  same purchase from the uploaded item, the insert is a no-op.
- **Explicit sign-out** (`evictAccountLists`): the account's purchases and
  mutes leave the phone with the lists; the purchases sync point is cleared,
  so signing back in pulls the history again.
- **Session lost** (008 D6): history stays and keeps working.

### Backfill on the phone

`GotThisBackfill` runs once at start-up, off the main thread. For guest lists
only, every item with `isPickedUp` and no `deletedAt` becomes a guest
purchase with `boughtAt = updatedAt`. Account lists are backfilled on the
server (step 7) and arrive with the next pull.

## Measuring whether it works

### Telemetry

New events in `AnalyticsEvent`, following its rules: bounded enums, buckets
and booleans, never an item name or key.

| Event | Fires | Parameters |
|---|---|---|
| `GotThisHintShown` | first time a hint applies to an item id on this phone | `source` (regular / category), `daysAgo` bucket, `holdout` |
| `GotThisHintOutcome` | that item is deleted, ticked, or muted while the hint applies | `outcome` (removed / ticked / muted), `holdout` |
| `GotThisUsualsShown` | the suggestions row first shows on a day | `count` bucket, `holdout` |
| `GotThisUsualAdded` | a chip is tapped | none |
| `GotThisReminderPosted` | the weekly notification posts, or would have (holdout) | `itemCount` bucket, `holdout` |
| `GotThisReminderOpened` | the notification is tapped | none |
| `GotThisReminderFollowUp` | 7 days after a posted or would-have-posted reminder | `addedAny` (was any named item added to a list), `holdout` |
| `GotThisPredictionScored` | a regular item is bought again | `errorDays` bucket (< −7, −7..−3, −2..2, 3..7, > 7) |
| `GotThisWeeklySummary` | once a week from the daily worker | `regulars` bucket, `purchases` bucket |

The holdout reports the same events with `holdout = true` for what it would
have seen, which gives the baseline: a holdout "removed" rate is how often
people delete such items anyway.

`GotThisPredictionScored` works without showing anything: every real repeat
purchase grades the earlier prediction, so accuracy is known even for people
who never open the suggestions.

### Holdout

`holdoutBucket < 10` and fewer than 8 weeks since `holdoutStartedAt` puts an
install in the holdout, when the feature flag `got_this_holdout` is on. The
holdout records purchases as usual; hints, suggestions and notifications are
replaced by their telemetry.

### Targets, to review 4 weeks after release

| Measure | Target |
|---|---|
| Hint removal rate | at least 10%, and above the holdout's |
| Reminder follow-up (`addedAny`) | at least 50%, and above the holdout's |
| Predictions within ±2 days | at least 60% |

Missing a target means tuning `GotThisRules` (windows, the 90% due point),
not adding UI. Hints that are removed less than 5% of the time after 4 weeks
mean the windows are too long.

### Before release, if licensing allows

Replay `ItemJudge` on a public household purchase dataset (dunnhumby "The
Complete Journey" is the candidate; its licence for this use must be
confirmed first) with a JVM test harness that is not shipped, to sanity-check
the thresholds. Without a usable dataset, the release relies on
`GotThisPredictionScored`.

## Feature flags and Plus readiness

- `got_this` (kill switch, read like the reminders flag at UI time): off hides
  hints, suggestions and notifications. Recording continues; the server
  trigger cannot be switched off from the app.
- `got_this_holdout`: turns the holdout on.
- `got_this_paywall`: off in this release. `GotThisAccess.canShowRunningLow()`
  returns true while it is off. `runningLowWeeksUsed` counts each ISO week in
  which the suggestions row or the notification showed anything, from the
  first day, so Plus can honour D13.

Hints and history are never gated.

## Privacy and release

- **Privacy policy:** a new section on purchase history: what is kept (item
  names, categories and times of ticked items), that it outlives list
  deletion, that it is shared with the members of the list it was ticked on,
  that it is kept until cleared in Settings or the account is deleted, and
  that it is never sold or used for advertising.
- **Play Data safety form:** declare purchase history (Financial info →
  Purchase history, the closest category; confirm at submission), collected,
  not shared, for app functionality, deletable. Both change **before**
  release, as in 008 D4.
- **Release order:** run `006_got_this.sql` on staging, then production
  (`babegetthis-staging` is production), then release the app. Old app
  versions keep working: they never send `picked_up_at`, and the trigger
  fills it in.
- **Notification channel:** created on first use, "Running-low reminders".

## Error handling and edge cases

- **Offline:** everything except the cross-device part works from Room. An
  offline tick is a local placeholder purchase until the item push reaches
  the trigger.
- **Clock skew:** `pickedUpAt` uses the phone's clock; a phone that is days
  off records the wrong day. Accepted.
- **Undo after a sync:** the item push carries `pickedUpAt = null`, and the
  trigger tombstones every member's copy.
- **Tick on one phone, untick on another within 2 hours:** the second phone
  applies the same rule against `pickedUpAt`, so it undoes too.
- **Item renamed after the tick:** the purchase keeps the name it was bought
  under.
- **Item deleted after the tick:** the purchase stays (no foreign key).
- **A partner on an old app version:** their ticks count through the server
  trigger; their unticks never undo (they cannot send the null), so an
  accidental tick by them stands.
- **Clear history while offline:** the RPC runs on the next sync; local rows
  are tombstoned immediately. A guest's clear deletes the rows outright, since
  they never leave the phone.
- **Custom categories:** use the Unknown window.
- **The worker finds nothing due, or no notification permission:** nothing
  posts and nothing retries.

## Testing

### Unit (`app/src/test`)

- `ItemKeyTest`: a table of names to keys (plurals, quantities, accents,
  modifiers) and match pairs, including "milk" ≠ "coconut milk" and
  "bannana" = "banana".
- `ItemJudgeTest`: weekly eggs become regular on the third shop after 14
  days; BBQ sausages bought once never do; three purchases in one week do
  not; a monthly item gets a 15-day window; an item unbought for 2.5 gaps
  lapses; the due point at 90%; uneven gaps beyond 3× stay one-off.
- `GotThisRulesTest`: every default category id has a window.
- `ShopDayTest`: a clear Saturday shopper, a shopper with no pattern, fewer
  than 4 days.
- `PurchaseIdTest`: the pinned id vector (shared with the SQL comment).
- Repository tests: tick then untick within 2 hours undoes; untick after 2
  hours keeps; a reused list ticked again records a second purchase; claim
  re-keys guest purchases; sign-out evicts account purchases.
- `GotThisAccessTest`: holdout bucket and the 8-week end; the paywall flag off
  always allows; week counting counts once per ISO week.
- Robolectric for `RunningLowWorker`: posts once a week at most, names up to
  3 items, stays silent with nothing due or without permission.
- Migration 5 → 6 in `MigrationTest`, as for 4 → 5.

### End-to-end (`app/src/androidTest`)

1. Guest: tick "Milk" on one list, add "milk" to another, see "Bought today",
   remove it from the hint menu.
2. Two signed-in members on a shared list (phone and emulator, see the e2e
   setup): A ticks "Eggs", B adds "eggs" to B's own list and sees the hint
   after sync.
3. Seeded history with a weekly regular: the suggestions row shows it, a chip
   adds it, and the row no longer offers it.

### Deliberately not tested

Notification timing under Doze, and the Play Data safety wording. Telemetry
shows the first in the field; the second is a submission checklist item.

## Out of scope

- **Plus:** billing, the household subscription, the trial and the lock
  screen (own spec; this release only counts and flags, D13).
- Family groups (D2) and list marks (D1).
- Quantities ("2 bags") in the rhythm: a bigger pack shows up as a longer gap
  anyway.
- Prices or spending.
- Matching across languages, or with a language model.
- Server-side push for running-low reminders.
- Auto-categorization of typed items (TODO.md). Until it ships, typed items
  often have no category and get the 7-day window.
- Viewing or editing the history itself.

## Accepted trade-offs

- People who delete bought items instead of ticking them are invisible to
  Got This.
- A monthly BBQ host's sausages become a regular, and get a monthly reminder.
- A member of a shared trip list gets history for what others ticked on it.
  Running-low is unaffected (one trip is one shop); a hint may show on a later
  list.
- Backfilled purchase times are the items' last edit times, which can be
  later than the tick.
- The category windows are judgment, not data. They tell us how long food
  keeps, not how fast it is used up, and are tuned from telemetry.
- A partner on an old app version cannot undo an accidental tick.
- Running-low says nothing until an item has 3 shops over 14 days; for people
  with no ticked items on their phone at release, that is at least 2 weeks.
- Mutes and "kept" counts are per phone.
