# Shared Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each placed speaker holds a shared, server-side queue. Any player in reach adds tracks to it, and every track is streamed from the PC of the player who added it. Tracks of absent players are skipped but kept. Unreadable files are removed, and only their owner gets a toast.

**Architecture:**
- **Queue model.** The queue is a pure-Java immutable value (`core.queue`), and `SpeakerData` persists it in the block entity and the item component.
- **Playback manager.** The server-side `SpeakerPlayback` (`world.audio`) is the only place that opens stream sessions. It sends `StreamOrder(start, sessionId, entryId, positionMs)` to the owner of the chosen track and advances on `StreamClose`, `StreamResult`, commands and disconnects.
- **Owner's client.** It resolves `entryId` to a file through a local `TrackTable`, which is never sent. It then streams from `positionMs` with the 3a emitter.
- **Watchers.** Players with the screen open register through `SpeakerWatch` and receive `SpeakerState`, at most once per tick.

**Tech Stack:** Fabric 26.3 (Mojang names), Fabric Networking API, DFU codecs, Gson (client), Fabric lifecycle and connection events.

**Spec:** [docs/superpowers/specs/2026-11-17-shared-queue-design.md](../specs/2026-11-17-shared-queue-design.md). The parent spec is [2026-11-17-audio-core-design.md](../specs/2026-11-17-audio-core-design.md), D10–D12.

## Global Constraints

- **Queue limits.** At most 200 entries, a title is at most 128 characters, and an owner name at most 16. `QueueAdd` carries at most 50 entries per packet, and a player may send at most 4 packets per second. The track table holds at most 5 000 entries.
- **State updates.** `SpeakerState` is sent at most once per tick per speaker, only to watchers, and only while they are within interaction range.
- **No file path ever leaves the client.** Only `entryId` (UUID) and the title (file name without extension) are sent.
- **Commands.** Every command needs `player.isWithinBlockInteractionRange(pos, 1.0)` and a placed speaker. An unknown `entryId` is ignored.
- **`StreamResult` and `StreamClose`** are accepted only from the session owner.
- **Pausing.** `playing` is never persisted. Every speaker loads paused.
- **Code boundaries.** `core` stays pure Java: no Minecraft imports, no jspecify. `src/main` never imports `net.minecraft.client`.
- **Lists.** Cap every list **before** sending, then check it again when decoding (`ByteBufCodecs.list(max)` throws past `max`).
- **Language.** UI text goes into both `en_us.json` and `fr_fr.json`. Code, comments and commits are in English.
- **Commits.** No Claude attribution in any commit. Never use `git add` on paths that include `art/proposals/`.
- **Java.** Every Gradle command is prefixed with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`.

## Review Focus

1. **The owner closes the screen, walks away or changes dimension mid-track.** Streaming continues. Only `SpeakerWatchers` checks reach, and the playback path never does. Test: `ownerOutOfReachKeepsStreaming` (Task 5).
2. **The last playable track ends while only absent owners' tracks remain.** The speaker pauses and keeps those entries. When their owner rejoins, nothing auto-starts. Tests: `onlyAbsentLeftPauses` (Task 1) and `rejoinDoesNotResume` (Task 5).
3. **A stale `StreamClose` or `StreamResult` arrives after a pause, next or clear.** The old session id no longer matches the playing session, so it must not advance or remove the new current entry. Test: `staleCloseIgnored` (Task 5).
4. **The current entry is removed or cleared while its owner is streaming.** The owner gets a stop order and listeners stop at once, so no orphan emitter keeps sending. Test: `removeCurrentStopsOwnerAndAdvances` (Task 5).
5. **A speaker with a queue is broken while playing.** The item keeps the queue and the paused position, and placing it again restores both, paused. Test: `breakWhilePlayingKeepsQueueInItem` (Task 5).

---

### Task 1: Queue model (`core.queue`)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/queue/QueueEntry.java`, `SpeakerQueue.java`, `PlaybackClock.java`
- Test: `src/test/java/com/ravecraft/mod/core/queue/SpeakerQueueTest.java`, `PlaybackClockTest.java`

**Interfaces (Produces):**
- `record QueueEntry(UUID entryId, UUID owner, String ownerName, String title)`. The compact constructor truncates `ownerName` to `MAX_OWNER_NAME = 16` and `title` to `MAX_TITLE = 128`.
- `record SpeakerQueue(List<QueueEntry> entries, Optional<UUID> current, long positionMs)`. It is immutable (`List.copyOf`), and `MAX_ENTRIES = 200`, `EMPTY`.
  - `SpeakerQueue add(List<QueueEntry> added)` appends in order. It skips ids already present and stops at 200. It never changes `current`.
  - `Optional<QueueEntry> nextPlayable(Predicate<UUID> ownerOnline)` returns the current entry if its owner is online. Otherwise it returns the first online entry after the current one, wrapping round to the current. With no current, it scans from the start.
  - `SpeakerQueue withCurrent(UUID entryId, long positionMs)`.
  - `SpeakerQueue withPosition(long positionMs)`.
  - `SpeakerQueue remove(UUID entryId)`. Removing the current entry makes `current` the entry that followed it, wrapping to the first, or empty if the queue is now empty, and resets `positionMs` to 0. Removing any other entry keeps `current` and the position.
  - `SpeakerQueue clear()` returns `EMPTY`.
  - `Optional<QueueEntry> entry(UUID entryId)`.
- `record PlaybackClock(long startPositionMs, long startedAtMs)` with `long positionAt(long nowMs)`, which returns `startPositionMs + max(0, nowMs - startedAtMs)`.

- [ ] **Step 1: Write the failing tests** in `SpeakerQueueTest`.
  - `addAppendsInOrder`.
  - `addStopsAt200`: add 210 entries; `entries().size() == 200` and the last kept is entry #199.
  - `addSkipsDuplicateId`.
  - `addKeepsCurrent`.
  - `nextPlayableReturnsCurrentWhenOnline`.
  - `nextPlayableSkipsAbsent`: in A(off) B(on) with current A, the result is B.
  - `nextPlayableWraps`: in A(on) B(off) C(off) with current B, the result is A.
  - `onlyAbsentLeftPauses`: when every owner is offline, the result is empty.
  - `nextPlayableWithoutCurrentScansFromStart`.
  - `removeCurrentMovesToFollowing`: removing B from A B C (current B, position 5000) gives current C and position 0.
  - `removeLastCurrentWrapsToFirst`.
  - `removeOtherKeepsCurrentAndPosition`.
  - `removeUnknownIsNoOp`.
  - `clearEmpties`.
  - `titleAndOwnerNameAreTruncated`: lengths 128 and 16.

  In `PlaybackClockTest`: `positionAddsElapsed` (start 1000, started 50 000, now 53 000 gives 4000) and `positionNeverGoesBackwards` (a `now` before the start gives the start position).
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew test --tests 'com.ravecraft.mod.core.queue.*'`. Expected: FAIL, because the classes do not compile yet.
- [ ] **Step 3: Implement the three classes** per the Interfaces block.
- [ ] **Step 4: Run them to verify they pass.** Same command. Expected: PASS, 17 tests.
- [ ] **Step 5: Commit.** `git add src/main/java/com/ravecraft/mod/core/queue src/test/java/com/ravecraft/mod/core/queue && git commit -m "Add shared speaker queue model"`

### Task 2: Track table (core logic and client store)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/queue/TrackTable.java`
- Create: `src/client/java/com/ravecraft/mod/client/audio/TrackTableStore.java`
- Test: `src/test/java/com/ravecraft/mod/core/queue/TrackTableTest.java`

**Interfaces (Produces):**
- `final class TrackTable` with `MAX_ENTRIES = 5000`, backed by an insertion-ordered `LinkedHashMap<UUID, String>`.
  - `void put(UUID entryId, String path)` evicts the oldest entry past the cap.
  - `Optional<String> get(UUID)`.
  - `Map<UUID, String> snapshot()`, an ordered copy.
  - `static TrackTable of(Map<UUID, String> ordered)`, capped.
- `final class TrackTableStore` (client, static). It loads `FabricLoader.getInstance().getConfigDir().resolve("ravecraft-tracks.json")` lazily on first use. The file holds a JSON array of `{"id": "...", "path": "..."}`, read and written with Gson.
  - `static void register(Map<UUID, Path> added)` puts the entries, then saves synchronously.
  - `static Optional<Path> resolve(UUID entryId)`.
  - A corrupt or missing file starts an empty table and logs a warning. It never throws.

- [ ] **Step 1: Write the failing tests** in `TrackTableTest`:
  - `putThenGet`;
  - `evictsOldestPast5000` (put 5 001; the first is gone, the last is present);
  - `ofKeepsOrderAndCaps`;
  - `snapshotRoundTripsThroughOf`.
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew test --tests 'com.ravecraft.mod.core.queue.TrackTableTest'`. Expected: FAIL, because the class does not compile yet.
- [ ] **Step 3: Implement `TrackTable` and then `TrackTableStore`.** The store has no unit test; it is covered by the in-game check in Task 8.
- [ ] **Step 4: Run the tests.** Expected: PASS. Then run `./gradlew compileClientJava`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit.** Message: "Add local track table".

### Task 3: Persist the queue in `SpeakerData`

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/component/SpeakerData.java`
- Create: `src/main/java/com/ravecraft/mod/component/QueueCodecs.java`
- Modify: `src/main/java/com/ravecraft/mod/blockentity/PortableSpeakerBlockEntity.java` (`getUpdateTag`)
- Modify: every `new SpeakerData(…)` call site. Find them with `grep -rn "new SpeakerData(" src`; this includes `PortableSpeakerGameTest:50`.
- Test: `src/test/java/com/ravecraft/mod/component/SpeakerDataTest.java`

**Interfaces:**
- Consumes `SpeakerQueue` and `QueueEntry` (Task 1).
- Produces:
  - `record SpeakerData(UUID id, SpeakerSettings settings, SpeakerQueue queue)`;
  - `SpeakerData withQueue(SpeakerQueue)`;
  - `create()` uses `SpeakerQueue.EMPTY`;
  - `withSettings` keeps the queue.
- `QueueCodecs`:
  - `Codec<QueueEntry> ENTRY` with fields `id`, `owner`, `owner_name`, `title`;
  - `Codec<SpeakerQueue> QUEUE` with fields `entries` (list), optional `current`, and `position_ms` (long, default 0). Truncate to 200 on decode; never fail on length.
  - `StreamCodec<ByteBuf, SpeakerQueue> QUEUE_STREAM` uses `ByteBufCodecs.list(SpeakerQueue.MAX_ENTRIES)`, `stringUtf8(128)` for titles and `stringUtf8(16)` for names.
- The `SpeakerData.CODEC` field `queue` is declared as `QueueCodecs.QUEUE.optionalFieldOf("queue", SpeakerQueue.EMPTY)`. `STREAM_CODEC` adds `QUEUE_STREAM`, so creative pick and copy keep the queue.
- `getUpdateTag` must not send the queue to every tracking client (spec Q4). It returns `saveCustomOnly(registries)`, then overwrites the `"speaker"` key with `data.withQueue(SpeakerQueue.EMPTY)` encoded by `SpeakerData.CODEC` with `registries.createSerializationContext(NbtOps.INSTANCE)`. Before using `CompoundTag.store(String, Codec, T)`, check it exists in 26.3; otherwise use `put` with the encoded `Tag`.
  - Clients load that copy with an empty queue. This is harmless, because the client never reads the queue from the block entity.

- [ ] **Step 1: Write the failing tests** in `SpeakerDataTest`, using `JsonOps.INSTANCE`:
  - `decodesWithoutQueueField`: JSON `{"id":…,"powered":true,"volume":40}` decodes to `queue == SpeakerQueue.EMPTY`;
  - `queueRoundTripsThroughCodec` (2 entries, a current entry and position 12 345);
  - `queueRoundTripsThroughStreamCodec` (`Unpooled.buffer()`);
  - `decodeTruncatesQueueOver200`.
- [ ] **Step 2: Run it to verify it fails.** Run `./gradlew test --tests 'com.ravecraft.mod.component.SpeakerDataTest'`. Expected: FAIL to compile.
- [ ] **Step 3: Implement** `QueueCodecs`, the new `SpeakerData` and `getUpdateTag`. Then fix the call sites.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS, with all existing JUnit tests still green.
- [ ] **Step 5: Commit.** Message: "Persist speaker queue in speaker data".

### Task 4: Payloads (3b set, `StreamOpen` removed)

**Files:**
- Create in `src/main/java/com/ravecraft/mod/network/payload/`: `QueueAddPayload`, `SpeakerCommandPayload`, `SpeakerWatchPayload`, `SpeakerStatePayload`, `StreamResultPayload`, `TrackRemovedPayload`.
- Modify: `StreamOrderPayload`.
- Delete: `StreamOpenPayload`.
- Modify: `network/ModPayloads.java`.
- Test: `src/test/java/com/ravecraft/mod/network/payload/QueuePayloadsTest.java`

**Interfaces (Produces).** All payloads are records with `TYPE` and `CODEC` (`StreamCodec<ByteBuf, …>`), like `AudioChunkPayload`.
- `QueueAddPayload(BlockPos pos, List<NewEntry> entries)` with `record NewEntry(UUID entryId, String title)` and `MAX_ENTRIES = 50`. The title uses `stringUtf8(128)`.
- `SpeakerCommandPayload(BlockPos pos, Command command, Optional<UUID> entryId)` with `enum Command { PLAY, PAUSE, NEXT, REMOVE, CLEAR }`. Encode the enum through `ByteBufCodecs.idMapper(i -> Command.values()[i], Command::ordinal)`, or an equivalent that throws `DecoderException` on an out-of-range id. Encode `entryId` with `ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC)`.
- `SpeakerWatchPayload(BlockPos pos, boolean open)`.
- `SpeakerStatePayload(BlockPos pos, boolean playing, Optional<UUID> current, long positionMs, List<StateEntry> entries)` with `record StateEntry(UUID entryId, String ownerName, String title, boolean ownerOnline)` and `MAX_ENTRIES = 200`.
- `StreamOrderPayload(int sessionId, BlockPos pos, boolean start, UUID entryId, long positionMs)`. It drops `request`, `NO_SESSION` and `NO_REQUEST`, and encodes the position as `VAR_LONG`.
- `StreamResultPayload(int sessionId)`.
- `TrackRemovedPayload(String title)`, with the title capped at 128.
- `ModPayloads` registers:
  - serverbound: `QueueAdd`, `SpeakerCommand`, `SpeakerWatch`, `StreamResult`, `AudioChunk`, `StreamClose`;
  - clientbound: `StreamOrder`, `RelayedChunk`, `SessionEnd`, `SpeakerState`, `TrackRemoved`.

- [ ] **Step 1: Write the failing tests** in `QueuePayloadsTest`:
  - a round trip for each new payload and for `StreamOrderPayload`;
  - `queueAddOver50Throws` (encoding 51 throws);
  - `stateOver200Throws`;
  - `commandWithBadOrdinalThrowsOnDecode` (write the pos, then var-int 9);
  - `titleOver128Throws`.
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew test --tests 'com.ravecraft.mod.network.payload.QueuePayloadsTest'`. Expected: FAIL to compile.
- [ ] **Step 3: Implement the payloads and update `ModPayloads`.** Delete `StreamOpenPayload`. Compilation now breaks in `AudioRelay`, `StreamSessions`, `LocalPlayback` and `AudioRelayGameTest`. Fix only enough to compile:
  - remove the `StreamOpen` receiver;
  - stub `LocalPlayback.play` to do nothing;
  - mark the 3a open/renew GameTests for removal in Task 5.

  Tasks 5 and 6 rewrite these classes.
- [ ] **Step 4: Run** `./gradlew test compileClientJava compileGametestJava`. Expected: PASS and BUILD SUCCESSFUL. Check the gametest source-set task name with `./gradlew tasks --all | grep -i gametest`.
- [ ] **Step 5: Commit.** Message: "Add shared queue payloads".

### Task 5: Server playback manager and watchers

**Files:**
- Create: `src/main/java/com/ravecraft/mod/world/audio/SpeakerPlayback.java`, `SpeakerWatchers.java`
- Create: `src/main/java/com/ravecraft/mod/network/SpeakerNetwork.java`, which handles receivers and the `QueueAdd` rate limit.
- Modify:
  - `world/audio/StreamSessions.java`: new `open`, no reach check, no orders;
  - `network/AudioRelay.java`: `StreamClose` goes to `SpeakerPlayback`, and disconnect handling moves;
  - `blockentity/PortableSpeakerBlockEntity.java`: `togglePower` and `setRemoved` call `SpeakerPlayback.pauseAt`;
  - `RaveCraft.java`: calls `SpeakerNetwork.init()`.
- Test: rewrite `src/gametest/java/com/ravecraft/mod/gametest/AudioRelayGameTest.java` to remove the `open` tests that no longer apply, and keep the relay and end tests on the new `open`. Create `SharedQueueGameTest.java` and declare it in `src/gametest/resources/fabric.mod.json`.

**Interfaces:**
- Consumes:
  - Task 1: `SpeakerQueue`, `QueueEntry`, `PlaybackClock`;
  - Task 3: `SpeakerData.withQueue`;
  - Task 4: payloads;
  - 3a: `TokenBucket(rate, capacity, nowNanos).tryConsume(amount, nowNanos)`, `Net.send`, `SessionTable`.
- Produces:
  - `StreamSessions`:
    - `Optional<Session<SpeakerKey>> open(ServerLevel level, BlockPos pos, ServerPlayer owner)` requires a loaded, powered speaker. It ends any previous session at that key with a hard stop, and sends nothing to the owner;
    - `close(int id, ServerPlayer requester, boolean finished)` now returns `Optional<Session<SpeakerKey>>`, the closed session, or empty if the requester was not its owner;
    - `endAt` and `endByOwner` keep sending the owner a stop order through `SpeakerPlayback.stopOrder(session)`.
  - `SpeakerPlayback` is static and server-thread only. Its state is `Map<SpeakerKey, Playing>`, with `record Playing(int sessionId, UUID entryId, PlaybackClock clock)`. *Playing* means "the key is present".
    - `void add(ServerPlayer p, BlockPos pos, List<QueueAddPayload.NewEntry> added)`.
    - `void command(ServerPlayer p, BlockPos pos, Command c, Optional<UUID> entryId)`.
    - `void onStreamClose(ServerPlayer p, int sessionId, boolean finished)`.
    - `void onStreamResult(ServerPlayer p, int sessionId)`.
    - `void pauseAt(ServerLevel level, BlockPos pos)` stores the estimated position and ends the session. It is used by power off, break or unload, and server stop.
    - `void onJoin(ServerPlayer p)`, `void onDisconnect(MinecraftServer s, UUID player)`, `void pauseAll(MinecraftServer s)` and `boolean isPlaying(ServerLevel, BlockPos)`.
    - `SpeakerStatePayload stateOf(ServerLevel, BlockPos)` (empty queue if no speaker).
    - Positions use `Util.getMillis()`. *Online* means `server.getPlayerList().getPlayer(owner) != null`.
  - `SpeakerWatchers` holds `Map<SpeakerKey, Set<UUID>>` and a dirty set.
    - `watch(ServerPlayer, BlockPos, boolean open)`: opening needs reach and marks the key dirty.
    - `markDirty(ServerLevel, BlockPos)`.
    - `markAllDirty()` (join and leave).
    - `flush(MinecraftServer)`, registered on `ServerTickEvents.END_SERVER_TICK`. It sends `stateOf` once per dirty key to each watcher who is online, in the same dimension and within reach, and drops the others.
    - `forget(UUID)`.
  - `SpeakerNetwork.init()` registers the 4 serverbound receivers and these events:
    - `ServerPlayConnectionEvents.JOIN` calls `onJoin`;
    - `DISCONNECT` calls `onDisconnect` and `SpeakerWatchers.forget`;
    - `ServerLifecycleEvents.SERVER_STOPPING` calls `pauseAll` before the world save.

    `QueueAdd` goes through a per-player `TokenBucket(4, 4, …)` (cost 1). Packets over the limit are dropped.

**Behaviour (spec §4).** Every mutation writes the queue back through `be.setData(be.data().withQueue(q))`, then calls `SpeakerWatchers.markDirty`.
- **`start(level, pos, be)`.**
  - It picks `q.nextPlayable(online)`.
  - If nothing is playable, it pauses: removes the `Playing`, keeps `current` and the position.
  - Otherwise, the start position is `q.positionMs()` if the pick is the current entry, else 0. It opens a session for the owner, sets `withCurrent(pick, startPos)`, puts the `Playing`, and sends the owner `StreamOrder(start=true, id, pos, entryId, startPos)`.
- **`PLAY`.** Ignored if the speaker is unpowered or already playing.
- **`PAUSE`.** Stores `clock.positionAt(now)`, then ends the session with a hard stop.
- **`NEXT`.** The same as removing the current entry: hard stop, `remove(current)`, then `start` if it was playing.
- **`REMOVE`.** Removing the current entry behaves like `NEXT`. Removing any other entry is just `remove`.
- **`CLEAR`.** Hard stop, then `clear()`.
- **`onStreamClose(finished=true)`**, accepted only if `sessionId` equals `Playing.sessionId`. Listeners drain (3a), the entry is removed, then `start`.
  - A `finished=false` close from the owner is treated like a failed result.
- **`onStreamResult`** is accepted under the same condition. It removes the entry, sends the owner `TrackRemoved(title)`, then calls `start`.
- **`onDisconnect`.** For each playing key whose current owner left: end the session, then `start`, which picks the next playable entry.
- **`onJoin`.** Calls `markAllDirty` and never resumes playback.
- **Stale ids are ignored.** Any close or result whose `sessionId` does not match the key's `Playing` is ignored.
- **Silent owner** (spec §4.3, a client that ignores the order). `Playing` gains `boolean heard`, and `AudioRelay` calls `SpeakerPlayback.onChunk(int sessionId)` on each accepted chunk. On `END_SERVER_TICK`, iterating only the `Playing` map (no world scan), a session not `heard` `START_TIMEOUT_MS = 10_000` after it opened is handled like `onStreamResult`.
- **Chunk unload.** `setRemoved` stores the position in the block entity, but the chunk may already be saved. Losing that last position on unload only is accepted; the queue itself is persisted on every mutation.

**Test players.** Players that must count as online are created with `h.makeMockServerPlayerInLevel()`. Before relying on it, check that it exists in 26.3 and adds the player to the `PlayerList`. Offline owners are plain random UUIDs in a `QueueEntry`.

- [ ] **Step 1: Write the failing GameTests** in `SharedQueueGameTest`:
  - `addInReachAppends`;
  - `addOutOfReachIgnored`;
  - `playStartsSessionForEntryOwner` (the session owner is the entry's owner, not the player who pressed Play);
  - `absentOwnerSkippedAndKept`;
  - `onlyAbsentEntriesPause` (Play does nothing; the entries remain);
  - `streamResultRemovesEntryAndAdvances`;
  - `finishedCloseRemovesEntryAndAdvances`;
  - `staleCloseIgnored` (pause, then the old session id closes; the queue is unchanged);
  - `nonOwnerResultIgnored`;
  - `removeCurrentStopsOwnerAndAdvances`;
  - `clearEmptiesAndStops`;
  - `powerOffPausesAndStoresPosition`: play, then `runAfterDelay(10)`, then toggle power; expect not playing, `current` kept and `positionMs > 0`;
  - `breakWhilePlayingKeepsQueueInItem`: break with drop, then read `ModDataComponents.SPEAKER` from the dropped item entity; the queue has 2 entries and `positionMs > 0`;
  - `ownerOutOfReachKeepsStreaming` (move the owner 100 blocks away; the session stays);
  - `rejoinDoesNotResume`: an owner leaves through `onDisconnect`, then `onJoin`; still paused;
  - `commandOnUnpoweredSpeakerDoesNotPlay`, `silentOwnerTimesOutAndEntryRemoved` (`@GameTest(maxTicks = 300)`, assert after `runAfterDelay(220)`).

  In `AudioRelayGameTest`:
  - port the session/relay/break/power-off tests to `StreamSessions.open(level, pos, player)`;
  - delete `openOutOfReachRejected`, `secondOpenReplacesFirstOwner` and `ownerRenewsSessionOutOfReach`.
- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew runGameTest`. Expected: FAIL to compile, because `SpeakerPlayback` does not exist yet.
- [ ] **Step 3: Implement** `StreamSessions` changes, `SpeakerPlayback`, `SpeakerWatchers`, `SpeakerNetwork`, the block entity hooks, the `AudioRelay` rewiring and registration.
- [ ] **Step 4: Run** `./gradlew runGameTest`. Expected: every GameTest passes. Then run `./gradlew test`. Expected: PASS.
- [ ] **Step 5: Commit.** Message: "Drive speaker playback from a server-side queue".

### Task 6: Client executor (emitter start position, playback, toast)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/audio/stream/FrameSkipper.java`
- Test: `src/test/java/com/ravecraft/mod/core/audio/stream/FrameSkipperTest.java`
- Modify:
  - `client/audio/stream/StreamEmitter.java`: new `long startMs` constructor parameter;
  - `client/audio/LocalPlayback.java`: rewrite;
  - `client/audio/stream/ClientRelay.java`: `StreamOrder` and `TrackRemoved` receivers;
  - `assets/ravecraft/lang/en_us.json` and `fr_fr.json`.

**Interfaces:**
- Consumes:
  - Task 2: `TrackTableStore.resolve`, `TrackTableStore.register`;
  - Task 4: `StreamOrderPayload`, `StreamResultPayload`, `StreamClosePayload`, `QueueAddPayload`, `TrackRemovedPayload`.
- Produces:
  - `final class FrameSkipper(long startMs)`, whose `boolean skip()` returns true for the first `ceil(startMs / StreamFormat.FRAME_MS)` calls and false afterwards;
  - `StreamEmitter(int sessionId, BlockPos pos, Path track, long startMs, Runnable onFinished, Consumer<Exception> onFailed)`. A frame polled from the framer while `skipper.skip()` is true is dropped before encoding. The pacer and `seq` count only sent chunks;
  - `LocalPlayback`:
    - `static void add(BlockPos pos, List<Path> tracks)` takes at most 200 tracks. It gives each a fresh random UUID, registers them in `TrackTableStore`, and builds `NewEntry(id, title)`, where `title` is the file name without its extension, truncated to 128. It splits the entries into batches of 50 and sends one batch every 5 client ticks (4/s) through a pending deque drained on `END_CLIENT_TICK`. Nothing is sent if `!ClientPlayNetworking.canSend(QueueAddPayload.TYPE)`;
    - `static void onOrder(StreamOrderPayload)`;
    - `static void stopAll()`.
- Translation keys:
  - `toast.ravecraft.track_removed.title`: "Track removed" / "Morceau retiré";
  - `toast.ravecraft.track_removed.message`: "%s could not be read" / "%s est introuvable ou illisible".

**Behaviour:**
- **`onOrder(start)`.** If the entry resolves to a regular readable file (`Files.isReadable`), it starts an emitter keyed by `sessionId` in `Map<Integer, StreamEmitter>`. When the emitter finishes, it sends `StreamClose(id, true)`; when it fails, it sends `StreamResult(id)`. Either way it removes itself from the map. If the entry does not resolve, it sends `StreamResult(id)` at once.
- **`onOrder(stop)`.** Cancels that emitter and calls `ReceivedStreams.end(id, false)`.
- **Toast.** `TrackRemoved` calls `SystemToast.add(minecraft.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message)`. Check that `Minecraft#getToastManager` exists in 26.3. `SystemToast.add(ToastManager, SystemToastId, Component, @Nullable Component)` is verified.
- **Removed.** The 3a request numbers, session renewal and the `nowPlaying` / `play` / `stop(pos)` API all go.

- [ ] **Step 1: Write the failing tests** in `FrameSkipperTest`:
  - `zeroSkipsNothing`;
  - `skipsExactFrameCount` (1000 ms gives 50 skips, then false);
  - `roundsPartialFrameUp` (1010 ms gives 51).
- [ ] **Step 2: Run it to verify it fails.** Run `./gradlew test --tests '*FrameSkipperTest'`. Expected: FAIL to compile.
- [ ] **Step 3: Implement** `FrameSkipper`, the emitter parameter, the `LocalPlayback` rewrite, the receivers and the translations.
- [ ] **Step 4: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL, all JUnit tests and GameTests green. The screen still calls the stubbed API, which Task 7 replaces; `build` must compile.
- [ ] **Step 5: Commit.** Message: "Stream queued tracks on server order".

### Task 7: Speaker screen (mockups first)

**Files:**
- Create: `art/proposals/screen/` mockups. This folder stays **uncommitted**.
- Create: `src/client/java/com/ravecraft/mod/client/screen/SpeakerStates.java`, a client cache of the latest `SpeakerStatePayload` per `BlockPos`, cleared on disconnect.
- Modify: `src/client/java/com/ravecraft/mod/client/screen/SpeakerBrowserScreen.java` and `ClientRelay.java` (the `SpeakerState` receiver).
- Modify: both lang files.

**Interfaces:**
- Consumes:
  - Task 4: `SpeakerCommandPayload`, `SpeakerWatchPayload`, `SpeakerStatePayload`;
  - Task 6: `LocalPlayback.add`.
- Produces: `SpeakerStates.get(BlockPos) → Optional<SpeakerStatePayload>` and `SpeakerStates.put(SpeakerStatePayload)`.

- [ ] **Step 1: Mockups.** Render 2–3 layout mockups of the spec layout as PNG (Pillow script in the scratchpad): browser on the left with "Add", queue on the right with greyed absent entries and a remove button per row, player at the bottom with play/pause, next and clear. Send them to the user with SendUserFile. **Stop and wait for the user's choice.** This is a decision only the user makes.
- [ ] **Step 2: Implement the chosen layout.**
  - `init` sends `SpeakerWatch(pos, true)`, and `removed()` sends `false`.
  - The queue list renders from `SpeakerStates.get(pos)`. Entries whose owner is absent use the grey colour `0xFF808080`, and the current entry is highlighted.
  - "Add" calls `LocalPlayback.add(pos, …)`. A selected folder or playlist goes through the existing `runAsync(MusicLibrary.tracksOf…)`. With nothing selected, it adds every listed track.
  - The buttons send `SpeakerCommandPayload`. The play/pause label follows `playing`.
- [ ] **Step 3: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit.** Run `git add src` and the lang files only, with the message "Show the shared queue in the speaker screen".

### Task 8: Datagen, docs, full build, in-game check

- [ ] **Step 1: Run** `./gradlew runDatagen && ./gradlew build`. Expected: BUILD SUCCESSFUL and no unexpected diff in `src/main/generated`.
- [ ] **Step 2: Update `docs/SPEC.md`.** Add a line for step 3b. In the parent spec, mark D10–D12 as implemented.
- [ ] **Step 3: Commit.** Message: "Update spec progress for shared queue".
- [ ] **Step 4: In-game check (user).** The user follows the spec's §6 "En jeu" list on a local dedicated server with two clients:
  - tracks from both players play one after the other;
  - A disconnects (A's tracks are skipped and greyed), then rejoins (they are played again);
  - a renamed file is removed and only its owner gets the toast;
  - pause and resume restart from the right place;
  - the queue is restored, paused, after a server restart.

  Do not push or open a PR without the user's explicit OK.
