# Coroutines, Async, and What I Was Getting Wrong

*A Flutter dev's journey into Kotlin coroutines — and the moment I realized I'd been thinking about `async` wrong for years.*

---

## Where I started

I'd been writing Flutter for a long time and assumed Kotlin coroutines were just "Dart `Future` with a different keyword." `suspend` instead of `async`, no explicit `await`, done. I figured I'd skim the docs in an afternoon and move on.

What actually happened: I started reading the code in `ShoppingItemsScreen.kt`, hit a `LaunchedEffect` block, asked why it lived in the UI layer, and the rabbit hole opened. Two hours later I realized the thing I was confused about wasn't coroutines at all — it was `async` itself. The concept I thought I'd understood since I first wrote `await fetchUser()` years ago.

This doc captures that whole conversation, in order, so I can reread it later and so it's reusable as blog material.

---

## Part 1 — Why `LaunchedEffect` lives in the UI

The block that started it all (from `ShoppingItemsScreen.kt`, lines 85–102):

```kotlin
LaunchedEffect(Unit) {
    viewModel.errorMessage.collect { message ->
        snackBarHostState.showSnackbar(message)
    }
}

LaunchedEffect(Unit) {
    viewModel.undoDeleteEvent.collect { itemName ->
        val result = snackBarHostState.showSnackbar(
            message = "$itemName deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDeleteItem()
        }
    }
}
```

### What it does

`LaunchedEffect(Unit)` is Compose's lifecycle-aware coroutine launcher. Think of it as `initState` in Flutter, but coroutine-aware:

- It launches a coroutine **when the composable enters the tree**.
- The coroutine is **cancelled automatically when the composable leaves**.
- The key (`Unit`) controls re-launching: with `Unit`, it runs exactly once per "lifetime."

`viewModel.errorMessage.collect { ... }` subscribes to a `Flow` on the VM. Each emission triggers a snackbar. Flutter analogue: like `StreamBuilder` listening to a `Stream` from a Bloc, except you don't rebuild — you just react.

The second block does the same for "item deleted" events. `showSnackbar(...)` is `suspend` — it returns when the snackbar dismisses, so you can `await` the user's choice. If they tapped "Undo", call `viewModel.undoDeleteItem()`.

### Why this lives in the UI, not the ViewModel

1. **`SnackbarHostState` is a Compose type.** ViewModels must not import Compose/Android UI APIs — that's what makes them unit-testable on plain JVM and survive configuration changes. In Flutter terms: you wouldn't call `ScaffoldMessenger.of(context).showSnackBar(...)` from inside a `ChangeNotifier`.
2. **The snackbar belongs to *this* screen's Scaffold.** Only the composable has a handle to its `SnackbarHostState`.
3. **Lifecycle correctness comes free.** `LaunchedEffect` cancels its coroutine when the screen leaves composition.
4. **Separation of concerns.** VM decides *what* should happen (an error occurred). UI decides *how* to express it (snackbar vs. dialog vs. toast).

> **Rule of thumb:** state → `StateFlow` collected with `collectAsState()`; events → `SharedFlow` / `Channel` collected inside `LaunchedEffect`.

---

## Part 2 — Coroutines explained like I'm 10

### The pizza shop story

You walk into a pizza shop and order a pizza. The chef says "15 minutes!"

**The dumb way (a regular thread):**
The cashier stares at you. Doesn't take other orders. Doesn't clean tables. Just stands frozen, watching the oven for 15 minutes. When your pizza is done, they hand it over and start serving the next person.

That's a normal thread doing a slow call. It **blocks** — sits there doing nothing.

**The smart way (a coroutine):**
The cashier takes your order, sticks the ticket on the oven, says "I'll come back when it's ready," and goes off to take 5 more orders, clean two tables, refill the soda machine. When the oven beeps, they grab your pizza and bring it out.

That's a coroutine. A function that can hit a slow step, **say "pause me,"** let the thread leave to do other work, and **resume exactly where it left off** when the slow thing is done.

### What it looks like in code

```kotlin
suspend fun makePizza(): Pizza {
    val dough = prepareDough()
    val baked = bakeInOven(dough)   // pauses here
    return baked
}
```

The magic word is `suspend`. It marks "this might pause." When `bakeInOven` is called, the coroutine pauses, the thread leaves to do other work, and when the oven is done, the coroutine wakes up on the next line and continues. To you reading, it looks like normal top-to-bottom code.

### Flutter side-by-side

```dart
// Dart
Future<Pizza> makePizza() async {
  final dough = await prepareDough();
  final baked = await bakeInOven(dough);
  return baked;
}
```

```kotlin
// Kotlin
suspend fun makePizza(): Pizza {
    val dough = prepareDough()
    val baked = bakeInOven(dough)
    return baked
}
```

Same idea, different keywords. `suspend` is roughly Kotlin's `async`, and **the call itself is the `await`** — the compiler figures it out from `suspend`.

### What coroutines add on top of Dart's futures

**1. Structured concurrency.** Every coroutine lives inside a scope (`viewModelScope`, `LaunchedEffect`, etc.). When the scope dies, **all its coroutines die automatically.** No "setState after dispose" crashes, no leaked subscriptions.

**2. Flows.** A `Flow` is "a coroutine that returns many values, over time." Like Dart's `Stream`, but with the same `suspend`-style readability:

```kotlin
viewModel.items.collect { items ->
    // runs every time the list changes
}
```

### Why coroutines are everywhere

1. **Sync-looking async code.** No callback hell, no `.then()` chains.
2. **Cheap.** A thread costs ~1 MB; a coroutine is a tiny state machine. You can launch hundreds of thousands.
3. **Structured concurrency kills a real bug class.** "Forgot to cancel" leaks are basically gone.
4. **Official Android stack.** Room, Retrofit, DataStore, WorkManager, Lifecycle, Compose — all coroutine-first.
5. **Flow replaced RxJava.** Cleaner, ships with the language.

---

## Part 3 — The misconception that broke my brain

I asked the question that everyone secretly has but feels too embarrassed to ask:

> "If async means we can do many things at once, and we have `a()` (make base) and `b()` (add sauce) where `b` needs `a` to finish, we have to await `a` before `b`. Doesn't that just make it synchronous?"

The answer: **awaiting makes the *order* look sync, but it's not the same as sync.** And the difference is not about your one task — it's about **what the thread is doing during the wait.**

> Async = don't waste time standing still while waiting.

It's not about doing two things at once. It's about what the rest of the system does **during** the wait.

### The pizza example, two versions

```kotlin
val base = makeBase()      // takes 5 min
val pizza = addSauce(base) // needs base
```

**Synchronous:**
- Chef puts dough in oven.
- Chef **stands frozen for 5 minutes.**
- Beep. Sauce. Done.
- Total chef time on your pizza: 5:30.
- During those 5 min: **the entire shop is paused.** No other orders. Phone ringing, no answer.

**Async + await:**
- Chef puts dough in oven, says "wake me when it beeps," walks away.
- During those 5 min: **takes 4 orders, washes 6 dishes, answers the phone.**
- Beep. Comes back. Adds sauce.
- Total chef time on your pizza: still 5:30.
- But the shop served 4 other customers in the meantime.

For *your* pizza, the order is identical to sync. What's different is what happens to **everyone else** during your wait.

### Who's frozen

| | Your pizza takes | Other work during wait |
|---|---|---|
| Sync | 5:30 | **Nothing — thread frozen** |
| Async + await | 5:30 | Thread serves other coroutines |

`await` ≠ sync. From your one function's view it looks the same. But the **thread** isn't paused — it's off doing other work. When the result is ready, your function gets resumed.

A sync wait freezes the cashier. An async wait sticks your ticket on a peg and helps the next person.

### When async actually buys parallelism

If two steps **don't depend on each other**, you can fire them off side by side:

```kotlin
coroutineScope {
    val base = async { makeBase() }       // starts now
    val cheese = async { grateCheese() }  // also starts now
    val pizza = assemble(base.await(), cheese.await())
}
```

5 min + 3 min = **5 min wall-clock**, not 8. They overlap.

For dependent steps like `a → b`, no concurrency is possible. Async doesn't change the order. It just lets the rest of the program keep moving during the wait.

### The cleaner mental model

Two separate ideas:

1. **Async** — "I'll yield the thread while I wait. Wake me when my result is ready." (One task, thread not wasted.)
2. **Concurrency / parallelism** — "Start multiple async tasks at once and let them overlap." (Many tasks, overlapping waits.)

Async is the *enabler*. Concurrency is what you build *on top* of it. Conflating them is the source of half the confusion.

---

## Part 4 — Then who's actually making the base?

If the main thread is off handling taps, who's doing the slow work?

**It depends on what kind of slow it is.**

### Case A — I/O wait (network, disk, DB)

Surprise: **nobody is doing CPU work.** Your CPU is genuinely free.

When you do a network call, the **OS kernel + network card** handle the actual sending/receiving. Your code says to the OS:

> "Hey OS, here's the URL. Poke me when bytes arrive. I'm out."

The OS uses hardware interrupts (`epoll`, `kqueue`, `IOCP`) to wake the right coroutine when data is ready. During a 2-second network wait, **zero threads are doing anything** for that call. That's why a server can hold 100,000 concurrent connections on a few threads.

### Case B — CPU work (heavy math, image processing)

Now we **do** need a worker. Coroutines hand off to a **thread pool** kept alive for exactly this purpose. Kotlin gives you these as **Dispatchers**:

| Dispatcher | What it's for | Pool size |
|---|---|---|
| `Dispatchers.Main` | UI work | 1 (the main thread) |
| `Dispatchers.IO` | Network, disk, DB | ~64, mostly idle |
| `Dispatchers.Default` | CPU-heavy math | ≈ number of CPU cores |

```kotlin
suspend fun heavyMath() = withContext(Dispatchers.Default) {
    crunchNumbers()  // runs on a background CPU-pool thread
}
```

`withContext` moves the coroutine to a different thread for that block, then moves it back. From your code's perspective it still reads top-to-bottom.

### Are coroutines the same as Flutter Isolates? No.

This is the part that bit me hardest as a Dart dev.

Dart by default is **single-threaded.** Even with 1000 `Future`s, only one piece of Dart code runs at a time. To get real parallelism you need **Isolates** — separate Dart VMs with their **own memory heap**, communicating via message-passing (you can't share objects).

Kotlin/JVM is the opposite. Multi-threaded by default with **shared memory**. Coroutines multiplex over real OS threads. Two coroutines on `Dispatchers.Default` can genuinely run in parallel on two CPU cores **and read the same object in memory** (which is also why thread safety becomes your problem).

| | Dart `Future` | Dart `Isolate` | Kotlin Coroutine |
|---|---|---|---|
| Threads | 1 (event loop) | N separate VMs | N (thread pool) |
| Memory | Shared | Isolated, message-pass | Shared |
| Parallel CPU work | No | Yes | Yes |
| Overhead | Cheap | Expensive (new heap) | Cheap |

**A coroutine is closer to a Dart `Future` (lightweight, shared memory) but with the *power* of an Isolate (real parallel CPU work, via dispatchers).** Best of both worlds — at the cost of having to think about thread safety, which Dart hid from you.

---

## Part 5 — The "what if I just don't bother with coroutines" question

I asked: what if I have

```kotlin
fun e() {
    c()  // very heavy math
    d()  // prints a million strings
}
```

and they don't depend on each other? Do they fight for the thread? Does the app freeze?

### My wrong instinct

> "since they are not coroutines or future they don't wait for each other, so they both try to run at the same thread and freeze the app"

### The actual answer

**They absolutely wait for each other.** Plain function calls are sequential by definition.

```kotlin
fun e() {
    c()   // runs to completion. Thread frozen on c.
    d()   // only starts after c returns. Thread frozen on d.
}
```

No parallelism. No "trying to run at the same time." `d()` does not exist as far as the CPU is concerned until `c()` returns. Same thread, taking turns.

If `e()` is on the main thread:
- Main thread enters `c()`. Frozen.
- `c()` returns. Main thread enters `d()`. Frozen.
- `d()` returns. Main thread free.
- **Total freeze = time(c) + time(d).** App unresponsive the whole time.

You're not getting "concurrency you didn't ask for." You're getting old-school sequential code, exactly like sync. Fine if the work is fast. Disaster if it's slow.

### My other instinct was right

> "if c was just 2*1 and d was print('a'), they're so small synchronous order works seamlessly"

**Yes — exactly.** That's the whole point. You don't reach for coroutines for `2 * 1`. You reach for them when:

- the work **blocks for a long time** (network, disk, DB) → `Dispatchers.IO`
- the work **chews CPU for a long time** (image filter, crypto, ML) → `Dispatchers.Default`
- the work **needs to overlap with other work** to be fast → launch concurrently

> **Threshold:** if a piece of code can take longer than ~16 ms on the main thread, it shouldn't run there. That's one frame at 60fps. Past 16ms = jank. Past 5 seconds = ANR ("Application Not Responding") dialog.

### How to actually parallelize independent c and d

```kotlin
suspend fun e() = coroutineScope {
    val cResult = async(Dispatchers.Default) { c() }
    val dResult = async(Dispatchers.Default) { d() }
    cResult.await() + dResult.await()
}
```

Two `async` blocks = two coroutines on real threads from `Default`. Total time ≈ `max(time(c), time(d))` instead of `time(c) + time(d)`. **Concurrency is opt-in.** Plain sequential code stays plain sequential, even when wrapped in coroutines.

---

## My final view

Three rules I'm carrying out of this:

1. **Plain function calls are always sequential.** No matter how heavy. They share whatever thread called them and freeze it.
2. **`suspend` + `await` is also sequential in *order*.** But the thread is free during waits — that's the whole win. `await` ≠ sync.
3. **`async { }` / `launch { }` is where real concurrency starts.** Opt-in, explicit. Plain code never gets concurrency for free.

The question I'll ask myself before any function:

> "Will this freeze the main thread for more than 16 ms?"

If no → just call it. Don't overcomplicate. If yes → coroutine + the right dispatcher.

### What I was actually wrong about

Not coroutines. **Async itself.** I'd been writing `await` for years thinking "this is the part that makes it sequential" — and I was technically right about the order, technically wrong about why it matters. Async isn't about doing things at once. It's about **not freezing the thread while one task waits.** Doing things at once (concurrency) is a separate thing you build on top.

Coming from Dart hid this from me because Dart's event loop is single-threaded by default. There's only ever one thing running, so "thread is free" doesn't feel like a meaningful difference — there are no other threads to free *for*. On the JVM, where the same process has many threads sharing memory, the distinction suddenly matters a lot.

Not embarrassing. Just a concept that finally clicked properly after several years of using it without understanding it.
