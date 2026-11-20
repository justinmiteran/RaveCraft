# Queue Reorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let any player drag a queue entry to a new place among the entries still to play, for everyone at the speaker.

**Architecture:** A pure `SpeakerQueue.move` reorders the list (or the shuffle round). A new `MOVE` speaker command carries it to the server, which reuses the existing update path. On screen, `RowList` gets a generic drag (press, 3 px threshold, insertion line, edge auto-scroll, drop), with its index math in a pure `core.ui.RowDrag`, and `QueuePanel` maps a drop onto `MOVE`.

**Tech Stack:** Java 25, Fabric 0.166.2+26.3 (Mojang names), JUnit 5, Fabric GameTest.

**Spec:** [docs/superpowers/specs/2026-11-20-speaker-ui-5b-design.md](../specs/2026-11-20-speaker-ui-5b-design.md) §3 (PR 1, branch `feat/queue-reorder`).

## Global Constraints

- `core` is pure Java (no Minecraft import); `src/main` never imports `net.minecraft.client`.
- Verify every Minecraft/Fabric API before use (MCP `minecraft-dev` or decompiled sources); Mojang names only.
- Code, comments, commit messages in English; no Claude attribution in commits.
- `MOVE` reuses `SpeakerCommandPayload` fields: `entryId` = moved entry, `value` = index in "to play"; the packet format does not change.
- Anyone in reach may move any entry (D1). Only "to play" accepts drops; the current entry and the separator never drag (D2).
- Drag threshold 3 px; insertion line 1 px in `SpeakerSprites.CURRENT`; dragged row drawn dimmed in place.
- `./gradlew build` green before the PR; `docs/SPEC.md` updated when done.

## Review Focus

- Moving an entry while shuffle is on and repeat is ALL: the round must keep every other pending entry, in order.
- Dragging the last "already played" entry above the separator: the separator disappears once nothing is played; no exception, and `nextRoundIndex()` stays consistent.
- A `MOVE` arriving after the entry was removed, or with a negative or huge `value`: ignored or clamped, never an exception on the server.
- The queue state changing (another player's command) while a drag is in progress: the drag must cancel rather than send a move for a row that now holds another entry.
- Dropping on the dragged row's own place (just above or just below itself): nothing is sent.

---

### Task 1: `SpeakerQueue.move`

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/core/queue/SpeakerQueue.java`
- Test: `src/test/java/com/ravecraft/mod/core/queue/SpeakerQueueTest.java`

**Interfaces:**
- Produces: `public SpeakerQueue move(UUID entryId, int index)` — puts `entryId` at position `index` of the entries still to play this round (0 = right after the current entry; without a current entry, 0 = first). `index` is clamped to `[0, upcoming size once the entry is taken out]`. Unknown id or the current entry: returns `this`. Afterwards the entry sits at `displayOrder()` index `(current present ? 1 : 0) + clamped index`.

- [ ] **Step 1: Write the failing tests** (use the file's `entries(n)` helper; build queues with the 3-arg constructor, then `withRepeat` / `withShuffle(true, new Random(1))` as needed)

```java
@Test void moveReordersUpcoming()            // [a,b,c,d], current a: move(d,0) → displayOrder a,d,b,c
@Test void moveDownwards()                  // current a: move(b,2) → a,c,d,b
@Test void moveClampsIndex()                // current a: move(b,99) → a,c,d,b ; move(d,-5) → a,d,b,c
@Test void moveBringsPlayedEntryBack()      // repeat ALL, [a,b,c,d], current c: move(a,0) → c,a,d,(sep) b ; nextRoundIndex()==3
@Test void moveLastPlayedRemovesSeparator() // repeat ALL, [a,b,c], current b: move(a,1) → b,c,a ; nextRoundIndex()==-1
@Test void moveWithShuffleReordersRound()   // shuffle on: take u = upcoming ids from displayOrder; move(u.last,0) → displayOrder index 1 is u.last and the rest of u keeps its order; entries() unchanged
@Test void moveWithShuffleBringsPlayedBack()// shuffle + repeat ALL after one afterAdvance: a played id moved to 0 appears at index 1 and next(ONLINE,false,rng) returns it
@Test void moveWithoutCurrent()             // [a,b,c], no current: move(c,0) → c,a,b
@Test void moveIgnoresCurrentAndUnknown()   // move(current,1) and move(randomUUID,0) return the same instance
```

- [ ] **Step 2: Run them, expect FAIL** (compile error: `move` undefined)

Run: `./gradlew test --tests "com.ravecraft.mod.core.queue.SpeakerQueueTest"`

- [ ] **Step 3: Implement `move`**

Without shuffle: remove the entry from `entries`, then insert it right after the element that will precede it in `upcoming()` (after the current entry for index 0; at the head when there is no current entry), recomputed on the list without the entry. With shuffle: copy `pending`, remove the id, insert it at the clamped index; `entries` untouched. Keep `current`, `positionMs`, `repeat`, `shuffle`.

- [ ] **Step 4: Run the tests, expect PASS** (same command, whole class green)

- [ ] **Step 5: Commit** — `git commit -m "Let the queue move an entry within what is still to play"`

---

### Task 2: `MOVE` command on the server

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/network/payload/SpeakerCommandPayload.java` (append `MOVE` to `Command`, **last**, so existing ordinals keep their ids)
- Modify: `src/main/java/com/ravecraft/mod/world/audio/SpeakerPlayback.java` (`command` switch, and the javadoc listing what `value` means)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/SharedQueueGameTest.java`

**Interfaces:**
- Consumes: `SpeakerQueue.move(UUID, int)` (Task 1).
- Produces: `Command.MOVE`; `SpeakerPlayback.command(player, pos, Command.MOVE, Optional.of(entryId), index)` applies `update(be, queueOf(be).move(id, (int) Math.clamp(value, 0, SpeakerQueue.MAX_ENTRIES)))` when the id is queued; nothing otherwise.

- [ ] **Step 1: Write the failing GameTests** (helpers `poweredSpeaker`, `online`, `newEntries`, `entryId`, `queue`, `finish` already exist)

```java
@GameTest public void moveReordersQueue(GameTestHelper h)
// add 3 entries, play; MOVE entryId(h,2) to 0 → queue(h).displayOrder() titles: track 0, track 2, track 1
@GameTest public void moveUnknownOrHugeIndexIsHarmless(GameTestHelper h)
// MOVE random UUID → order unchanged; MOVE entryId(h,1) with value Long.MAX_VALUE → it is last; value -3 → it is first after current
@GameTest public void moveOutOfReachIgnored(GameTestHelper h)
// player at distance beyond reach (as addOutOfReachIgnored does) → order unchanged
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew runGameTest` (compile error: `MOVE` undefined)

- [ ] **Step 3: Add `MOVE` and its case** as in Interfaces; extend the payload round-trip test in `QueuePayloadsTest` if it enumerates commands.

- [ ] **Step 4: Run, expect PASS** — `./gradlew runGameTest` then `./gradlew test`

- [ ] **Step 5: Commit** — `git commit -m "Add a MOVE speaker command"`

---

### Task 3: `core.ui.RowDrag`

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/ui/RowDrag.java`
- Test: `src/test/java/com/ravecraft/mod/core/ui/RowDragTest.java`

**Interfaces:**
- Produces (all `public static`, pure):
  - `int THRESHOLD = 3`
  - `boolean started(double pressY, double mouseY)` — true once `|mouseY - pressY| > THRESHOLD`.
  - `int gap(double contentY, int rowH, int minGap, int maxGap)` — the insertion gap (0 = above row 0, n = below row n−1) nearest to `contentY` (y inside the scrolled content: `round(contentY / rowH)`), clamped to `[minGap, maxGap]`.
  - `int moveIndex(int fromRow, int gap, int firstGap)` — the `MOVE` index for dropping row `fromRow` at `gap`, where `firstGap` is the gap right after the current row: `gap - firstGap`, minus 1 when `firstGap <= fromRow < gap` (the row leaves a slot above the gap). Returns `-1` when the drop is a no-op (`gap == fromRow` or `gap == fromRow + 1`).

- [ ] **Step 1: Write the failing tests**

```java
@Test void startsPastThreshold()   // started(10,13)==false, started(10,13.5)==true, started(10,6.5)==true
@Test void gapRoundsAndClamps()    // gap(0,12,1,5)==1 ; gap(17,12,0,5)==1 ; gap(19,12,0,5)==2 ; gap(500,12,1,5)==5
@Test void moveIndexUp()           // rows: 0 current, 1..3 upcoming; firstGap 1: moveIndex(3,1,1)==0
@Test void moveIndexDown()         // moveIndex(1,4,1)==2
@Test void moveIndexNoOp()         // moveIndex(2,2,1)==-1 ; moveIndex(2,3,1)==-1
@Test void moveIndexFromPlayed()   // played row 6 (below separator row 5), gap 2, firstGap 1 → 1
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew test --tests "com.ravecraft.mod.core.ui.RowDragTest"`
- [ ] **Step 3: Implement `RowDrag`** (final class, private constructor)
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `git commit -m "Add the row drag arithmetic"`

---

### Task 4: Drag in `RowList`, wired in `QueuePanel`

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/client/screen/widget/ListRow.java`
- Modify: `src/client/java/com/ravecraft/mod/client/screen/widget/RowList.java`
- Modify: `src/client/java/com/ravecraft/mod/client/screen/widget/QueuePanel.java`
- Modify: `docs/SPEC.md` (5b line: mark PR 1 delivered, "(à vérifier en jeu)")

**Interfaces:**
- Consumes: `RowDrag` (Task 3), `Command.MOVE` (Task 2).
- Produces:
  - `ListRow.draggable()` — `default boolean draggable() { return false; }`
  - `RowList`: `protected int[] dropRange()` (default `null` = the list does not reorder; else `{minGap, maxGap}`), `protected void onMove(int fromRow, int gap)` (default no-op), `protected final void cancelDrag()`.

- [ ] **Step 1: `RowList` drag state.** A press on a draggable row, off its items and off the scrollbar, while `dropRange() != null`, arms a drag (`fromRow`, `pressY`) and defers that row's own `click` to `mouseReleased`. Items (✕) keep clicking on press as today. In `mouseDragged`, the drag starts once `RowDrag.started(pressY, y)`. On release: a started drag computes `gap = RowDrag.gap(contentY, rowH, min, max)` and calls `onMove(fromRow, gap)` unless `gap == fromRow || gap == fromRow + 1`, with no click sound; an unstarted one runs the deferred row click. Releasing outside the list drops nothing.
- [ ] **Step 2: Drawing.** While dragging: the dragged row is drawn in place then covered with `0x60000000` (dimmed); a 1 px line `0xFF000000 | SpeakerSprites.CURRENT` spans `left..right` at the gap's y; no hover highlight, no tooltip. Auto-scroll: with the mouse within one row of the top or bottom inner edge, `scroll` moves one row at most every 100 ms (`net.minecraft.util.Util.getMillis()`, verify it exists), clamped to `[0, maxScroll()]`.
- [ ] **Step 3: Cancel.** `cancelDrag()` clears the state; `QueuePanel.show(...)` calls it when the new state's entry ids differ from the shown ones (Review Focus: no move for a row that changed).
- [ ] **Step 4: `QueuePanel`.** Entry rows other than the current one return `draggable() == true`. `dropRange()` = `{firstGap, endGap}` with `firstGap` = 1 if the first row is the current entry else 0, `endGap` = the separator's row index if present else `rows.size()`; `null` when there are fewer than 2 entries. `onMove(fromRow, gap)` sends `SpeakerStates.send(pos, Command.MOVE, Optional.of(entryId), RowDrag.moveIndex(fromRow, gap, firstGap))` unless that index is -1.
- [ ] **Step 5: Build** — `./gradlew build` (no new block, item or text, so no datagen); expect BUILD SUCCESSFUL with JUnit and GameTests green.
- [ ] **Step 6: In-game check** (user): a 20-entry queue — drag up, down, past the bottom edge (auto-scroll), a played entry above the separator, with shuffle on; ✕ still removes; a plain click does nothing new; a second player sees the order change.
- [ ] **Step 7: Commit** — `git commit -m "Drag queue entries to reorder what is still to play"` (with the SPEC update)
