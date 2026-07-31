# Setting in-app update priority in Play Console

Every release you publish needs its **in-app update priority** set
manually — it's a per-release Play Console field, not something in the
code or the AAB. If you skip it, Play defaults to priority `0`, which our
code (`InAppUpdateManager`) treats as **routine → FLEXIBLE** (silent
background download, no forced full-screen update). That's why forgetting
to set it doesn't break anything — you just always get the routine flow,
never the urgent one, until you set it deliberately.

See [`docs/technical-decisions/003-in-app-update-and-feature-flags.md`](../technical-decisions/003-in-app-update-and-feature-flags.md)
for how the app decides FLEXIBLE vs IMMEDIATE from this number.

## Where to set it

1. Play Console → your app → the release track you're publishing to
   (Internal testing / Closed testing / Production).
2. Open the release you're creating (or the release you just created,
   before rolling it out — it's editable up to that point).
3. Scroll to **"In-app update priority"** (sits near the release notes /
   rollout section on the release details page).
4. Pick a value **0–5**. Higher = more urgent.
5. Save, then continue to review/rollout as normal.

There's no separate "priorities" page — it's part of each individual
release, so **you set it every time**, per release, not once globally.

## What number to pick

| Priority | When to use | What the app does |
|---|---|---|
| 0–2 | Routine feature work, minor fixes, flag-gated dark features | FLEXIBLE — silent background download, optional Snackbar prompt if the app is in the foreground when it finishes |
| 3 | Notable fix, not urgent | FLEXIBLE |
| 4 | Important fix (data bug, broken flow for a subset of users) | IMMEDIATE — full-screen blocking update before the user can continue |
| 5 | Critical (security hole, app-breaking bug, backend contract change that makes old clients fail) | IMMEDIATE |

Default to **0–2** unless you have a specific reason to force-block
users. IMMEDIATE is disruptive — only reach for 4–5 when *not* updating
is worse than interrupting the user mid-session.

## How to actually verify it worked

Priority alone won't show you anything if Play Store's own background
auto-update grabs the release first — it'll silently install before you
ever open the app, and you'll just see "Open" instead of any prompt from
our code. To actually observe FLEXIBLE vs IMMEDIATE:

1. On the test device: **Play Store → profile icon → Settings → Network
   preferences → Auto-update apps → Don't auto-update apps.**
2. Publish the release with the priority set.
3. Open the app on that device — `InAppUpdateManager.checkForUpdate()`
   fires in `onResume()`. That's when the flow actually triggers, not
   before.
4. Check **Settings → app version** at the bottom of the Settings screen
   (added specifically for this — shows `BuildConfig.VERSION_NAME` /
   `VERSION_CODE`) to confirm whether the update landed.

## Common mistake (already made once)

Publishing a release **without** setting priority still "works" — you'll
see *something* happen (the FLEXIBLE flow, silently or via Snackbar), but
you can't distinguish that from IMMEDIATE unless you deliberately set a
high priority and compare. If you forget, you haven't broken anything —
you just tested the wrong flow. Re-run with priority explicitly set to
verify the other path.
