# Carried Speaker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A portable speaker keeps playing when broken, dropped, held or carried in the inventory, pauses when stored, and its screen opens from the item (right-click in the air, key J).

**Architecture:** Every server table keys on `SpeakerData.id` instead of `SpeakerKey`. A pure `HostTracker` in `core.speaker` decides where each id lives (block, carrier, dropped) and when a lost host expires. `SpeakerHosts` feeds it from events and hands out a `SpeakerHandle` that reads and writes the data wherever it lives. Sessions never notice a host change: the relay asks for the host on every chunk, and a `SpeakerHostPayload` tells clients where the sound is. The client reads that position every tick.

**Tech Stack:** Java 25, Fabric (MC 26.3, Mojang names), Fabric API 0.166.2 (lifecycle events, networking, key mapping), JUnit 5, Fabric GameTests.

**Spec:** [docs/superpowers/specs/2026-11-21-party-speaker-design.md](../specs/2026-11-21-party-speaker-design.md) §3 (PR 6a), §5, §6. §4 (party speaker) is PR 6b and out of scope here.

## Global Constraints

- The speaker is identified by `SpeakerData.id`. `SpeakerKey` is deleted at the end of Task 4.
- No block or chunk scan per tick. A carrier reports its speaker from `Item#inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)`, called by vanilla for the 36 main slots (`Inventory.tick`) and the equipment slots, offhand included (`EntityEquipment.tick`). Each report is one write in memory.
- A carried speaker not reported for **20 ticks** (`HostTracker.LOST_AFTER_TICKS`) is stored: its session pauses (D10). When it comes back into a player inventory, onto the ground or placed, it resumes where it stopped.
- A carrier who disconnects pauses at once. Restarting the server pauses every speaker.
- A speaker destroyed (lava, fire, cactus) or vanished (void, despawn) stops its session, and its id is forgotten.
- Duplicate id: the newcomer gets a new id and keeps a copy of the queue, paused. Only one speaker plays per id.
- A host change during playback keeps the session: same `sessionId`, same clock.
- Listeners: the players tracking the chunk for a block, or tracking the entity for a carrier or a dropped item, plus the carrier itself. The carrier is never the stream's owner here, since the owner already plays locally. The relay radius is unchanged (`Propagation.relayRadius`).
- Who may control a speaker: its carrier, and anyone within interaction range of the host (`isWithinBlockInteractionRange(pos, 1.0)` for a block, `isWithinEntityInteractionRange(entity, 1.0)` for an entity).
- The server looks for a carried stack by id among the carrier's `Inventory.getContainerSize()` slots (41), and only when it handles a command.
- Key `key.ravecraft.open_speaker`, default `InputConstants.KEY_J`, in a new category `KeyMapping.Category.register(RaveCraft.id("ravecraft"))`. Priority order: main hand, offhand, hotbar 0–8, main inventory 9–35. The back slot comes first in 6b.
- Tooltip line, gray, after the base values: `[%s] : ouvrir l'interface (inventaire ou dos)`, filled with `KeyMapping#getTranslatedKeyMessage()`. The line is hidden when `isUnbound()`.
- In 6a a carrier emits omnidirectionally. The portable speaker is `Directivity.OMNI` anyway.
- Every new text goes in both `en_us.json` and `fr_fr.json`. Code, comments and commits are in English, with no Claude attribution.
- `./gradlew build` is green at the end of each task, with the prefix `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`.

## Review Focus

- **Two copies of one speaker in the same inventory** (creative middle-click, dupe glitch). Expect one copy to keep the id and play, while the other gets a new id with the same queue, paused. Pinned by `HostTrackerTest.sameCarrierTwiceInOneTickIsDuplicate` (Task 1) and `CarriedSpeakerGameTest.duplicateInInventoryGetsNewId` (Task 6).
- **A hopper or a player picks up a playing speaker from the ground.** The item entity is discarded with an empty stack. Expect a hopper to pause the speaker and a player to keep it playing, never a stop. Pinned by `CarriedSpeakerGameTest.pickupByHopperPausesNotStops` (Task 6).
- **The carrier dies or throws the speaker.** The dropped entity loads while the carrier reported it on the previous tick. Expect the entity to take the speaker over, with no duplicate and no pause. Pinned by `HostTrackerTest.throwFromCarrierIsMove` (Task 1) and `CarriedSpeakerGameTest.breakKeepsSession` (Task 6).
- **The server stops while a speaker is carried and playing.** Expect the live track position written into the stack, so it resumes from there. Pinned by `CarriedSpeakerGameTest.pauseAllWritesCarriedPosition` (Task 6).
- **A player walks into range of a moving carrier halfway through a track.** Expect the host before or with the first chunk, never a chunk the client cannot place. Pinned by `AudioRelayGameTest.newListenerIsToldHostFirst` (Task 5).

---

## File Structure

| File | Role |
|---|---|
| `src/main/java/com/ravecraft/mod/core/speaker/HostTracker.java` | Pure: id → host, reports, losses, expiry after 20 ticks, duplicates |
| `src/main/java/com/ravecraft/mod/core/speaker/SpeakerKeyTarget.java` | Pure: which inventory slot key J opens |
| `src/main/java/com/ravecraft/mod/world/audio/SpeakerHost.java` | Sealed host: `Block`, `Carrier`, `Dropped` |
| `src/main/java/com/ravecraft/mod/world/audio/SpeakerHandle.java` | Reads and writes a speaker's data wherever it lives; position, level, control rule |
| `src/main/java/com/ravecraft/mod/world/audio/SpeakerHosts.java` | Server registry: events → `HostTracker`, expiry outcomes, parked queues, host notices |
| `src/main/java/com/ravecraft/mod/network/payload/SpeakerHostPayload.java` | S→C: where speaker `id` plays from, with volume and power |
| `src/main/java/com/ravecraft/mod/network/payload/SpeakerClosedPayload.java` | S→C: close the screen of speaker `id` |
| `src/client/java/com/ravecraft/mod/client/audio/ClientSpeakerHosts.java` | Client map id → last `SpeakerHostPayload`, position lookup |
| `src/client/java/com/ravecraft/mod/client/OpenSpeakerKey.java` | Key J registration and handling |
| `src/gametest/java/com/ravecraft/mod/gametest/CarriedSpeakerGameTest.java` | Break, store, resume, duplicate, out-of-range, server stop |
| `src/gametest/java/com/ravecraft/mod/gametest/Speakers.java` | Test helper `idAt(GameTestHelper, BlockPos)` |

Modified: `SpeakerPlayback`, `StreamSessions`, `SpeakerWatchers`, `NowPlaying`, `AudioRelay`, `SpeakerNetwork`, `ModPayloads`, the payloads listed in Tasks 4 and 5, `SpeakerData`, `PortableSpeakerBlock`, `PortableSpeakerBlockEntity`, `PortableSpeakerItem`, `SpeakerScreenOpener`. On the client: `RenderStream`, `AcousticRenderer`, `OcclusionScheduler`, `ReverbScheduler`, `AcousticDebugInfo`, `ReceivedStreams`, `StreamEmitter`, `LocalPlayback`, `ClientRelay`, `NowPlayingToasts`, `SpeakerScreen`, `SpeakerStates`, the five screen widgets, `SpeakerTooltip`, `RaveCraftClient`. Delete: `SpeakerKey`.

---

### Task 1: HostTracker (core)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/speaker/HostTracker.java`
- Test: `src/test/java/com/ravecraft/mod/core/speaker/HostTrackerTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class HostTracker<H> {
      public static final int LOST_AFTER_TICKS = 20;
      public enum Verdict { SAME, MOVED, DUPLICATE }
      public record Expired<H>(UUID id, H lastHost) {}
      /** A host that stays until lost: placed block, item on the ground. */
      public Verdict place(UUID id, H host, long tick);
      /** A host that must report every tick: a carrier. */
      public Verdict report(UUID id, H host, long tick);
      /** {@code host} no longer holds {@code id}; ignored unless it is the current host. Grace starts. */
      public void lose(UUID id, H host, long tick);
      public Optional<H> host(UUID id);          // empty while lost or unknown
      public List<Expired<H>> expire(long tick); // removes and returns what expired
      public void forget(UUID id);
      public void clear();
  }
  ```
  Equality of hosts is `H#equals`.

Rules the tests pin:
- `place` over a live **carrier** host is `MOVED` (the item left the inventory). Over another live placed host it is `DUPLICATE`, and the tracker is unchanged.
- `report` is `DUPLICATE` when the current host is a live placed host, when another carrier reported at `tick` or `tick - 1`, or when the same carrier already reported at `tick`. It is `SAME` for the same carrier reporting once per tick. Otherwise it is `MOVED`.
- A lost id, and any unknown id, is taken by the next `place` or `report` as `MOVED`.
- `expire(tick)` returns a carrier whose last report is `<= tick - LOST_AFTER_TICKS`, and a lost id whose loss is `<= tick - LOST_AFTER_TICKS`. A placed host never expires.

The tracker tells placed hosts from carriers by the method that claimed them, not by `H`.

- [ ] **Step 1: Write the failing tests**

  Each test uses `HostTracker<String>` and fixed UUIDs.
  - `carrierExpiresAfter20Ticks`: report `"A"` at tick 0. `expire(19)` is empty. `expire(20)` is `[Expired(id, "A")]`. `host(id)` is then empty.
  - `carrierReportingEveryTickNeverExpires`: report at ticks 0 to 100. `expire(100)` is empty.
  - `placedNeverExpires`: `place(id, "block", 0)`. `expire(10_000)` is empty.
  - `lostPlacedExpiresAfter20Ticks`: place, then `lose(id, "block", 5)`. `expire(24)` is empty. `expire(25)` holds the id with `lastHost "block"`.
  - `breakThenDropIsMove`: place `"block"`, `lose` at 3, `place(id, "item", 3)` is `MOVED`. `expire(100)` is empty.
  - `pickupIsMove`: place `"item"`, `lose` at 7, `report(id, "P", 8)` is `MOVED`.
  - `throwFromCarrierIsMove`: report `"P"` at 9, `place(id, "item", 10)` is `MOVED`, and `host` is `"item"`.
  - `sameCarrierTwiceInOneTickIsDuplicate`: report `"P"` at 4 is `MOVED`, again at 4 is `DUPLICATE`, at 5 is `SAME`.
  - `otherCarrierWhileFirstLiveIsDuplicate`: report `"P"` at 4, `report(id, "Q", 5)` is `DUPLICATE`. `report(id, "Q", 6)` is still `DUPLICATE` while P keeps reporting at 5 and 6.
  - `secondPlacementIsDuplicate`: place `"b1"`, then `place(id, "b2", 1)` is `DUPLICATE`, and `host` stays `"b1"`.
  - `carrierOverPlacedIsDuplicate`: place `"block"`, `report(id, "P", 1)` is `DUPLICATE`.
  - `staleLoseIsIgnored`: place `"block"`, place-move to `"item"` after a lose, then `lose(id, "block", 9)`. `host` is still `"item"`.

- [ ] **Step 2: Run them to see them fail**

  Run: `./gradlew test --tests '*HostTrackerTest'`
  Expected: FAIL, compilation error `cannot find symbol HostTracker`.

- [ ] **Step 3: Implement `HostTracker<H>`**

  Keep one `HashMap<UUID, Entry>`. An `Entry` holds the host (or null while lost), whether it is placed, the last report tick, the loss tick, and the tick of the previous report. That last field is how a second report in the same tick is detected.

- [ ] **Step 4: Run them to see them pass**

  Run: `./gradlew test --tests '*HostTrackerTest'`
  Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/ravecraft/mod/core/speaker/HostTracker.java src/test/java/com/ravecraft/mod/core/speaker/HostTrackerTest.java
  git commit -m "Add the speaker host tracker"
  ```

---

### Task 2: SpeakerKeyTarget (core)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/speaker/SpeakerKeyTarget.java`
- Test: `src/test/java/com/ravecraft/mod/core/speaker/SpeakerKeyTargetTest.java`

**Interfaces:**
- Produces: `public static OptionalInt SpeakerKeyTarget.choose(int selectedHotbar, IntPredicate isSpeaker)`. It returns a vanilla inventory slot index: the main hand is `selectedHotbar`, the offhand is `40` (`Inventory.SLOT_OFFHAND`, copied as the constant `OFFHAND = 40` because core cannot import it), the hotbar is `0..8` and the main inventory is `9..35`.

- [ ] **Step 1: Write the failing tests**
  - `mainHandFirst`: speakers at 3 (selected), 40 and 0 → `3`.
  - `offhandBeforeHotbar`: selected 2 is empty, speakers at 40 and 0 → `40`.
  - `hotbarInOrder`: speakers at 7 and 5 → `5`.
  - `hotbarBeforeInventory`: speakers at 30 and 8 → `8`.
  - `inventoryInOrder`: speakers at 35 and 12 → `12`.
  - `armorSlotsIgnored`: speaker only at 38 → empty.
  - `noSpeaker`: → empty.

- [ ] **Step 2: Run them to see them fail**

  Run: `./gradlew test --tests '*SpeakerKeyTargetTest'`
  Expected: FAIL, `cannot find symbol SpeakerKeyTarget`.

- [ ] **Step 3: Implement `choose`**

- [ ] **Step 4: Run them to see them pass**

  Run: `./gradlew test --tests '*SpeakerKeyTargetTest'`
  Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

  `git commit -m "Add the open-speaker key target"`, with both files.

---

### Task 3: Hosts, handles and the block host

The registry ships with block hosts only, so nothing changes in game yet. Later tasks rely on it.

**Files:**
- Create: `world/audio/SpeakerHost.java`, `world/audio/SpeakerHandle.java`, `world/audio/SpeakerHosts.java` (under `src/main/java/com/ravecraft/mod/`)
- Modify: `component/SpeakerData.java` (add `withId`), `block/PortableSpeakerBlock.java` (`setPlacedBy`), `blockentity/PortableSpeakerBlockEntity.java` (`setRemoved`), `network/SpeakerNetwork.java` (tick, stop)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/PortableSpeakerGameTest.java` (new tests)

**Interfaces:**
- Consumes: `HostTracker<SpeakerHost>` (Task 1).
- Produces:
  ```java
  public sealed interface SpeakerHost {
      record Block(ResourceKey<Level> dimension, BlockPos pos) implements SpeakerHost {}
      record Carrier(UUID player, ResourceKey<Level> dimension, boolean inHand) implements SpeakerHost {}
      record Dropped(UUID entity, ResourceKey<Level> dimension) implements SpeakerHost {}
  }
  public interface SpeakerHandle {
      UUID id();
      ServerLevel level();
      SpeakerHost host();
      SpeakerData data();
      void setData(SpeakerData data);   // writes where the data lives, then SpeakerWatchers.markDirty(id)
      boolean powered();                // data().settings().powered()
      Vec3 position();                  // block centre, carrier eyes (inHand) or chest, entity position
      boolean canControl(ServerPlayer player);
      /** Players who should hear it: chunk trackers (block) or entity trackers plus the carrier. */
      Collection<ServerPlayer> trackers();
  }
  public final class SpeakerHosts {
      public static Optional<SpeakerHandle> handle(MinecraftServer server, UUID id);
      public static void placed(PortableSpeakerBlockEntity speaker);   // from setPlacedBy and BLOCK_ENTITY_LOAD
      public static void removed(PortableSpeakerBlockEntity speaker);  // from setRemoved
      public static void tick(MinecraftServer server);                 // expiry, then host notices (Task 5)
      public static void clear();
  }
  public SpeakerData SpeakerData.withId(UUID id);
  ```
  The chest position is `player.getY() + player.getBbHeight() * 0.6`. For `Block`, `handle` returns empty when the chunk is not loaded or the block entity is missing.

- [ ] **Step 1: Write the failing GameTests** in `PortableSpeakerGameTest`:
  - `placedSpeakerHasBlockHost`: set a portable speaker block, then call `SpeakerHosts.placed(be)`. `SpeakerHosts.handle(server, be.data().id())` is present, its host is `Block(dim, absPos)`, and `position()` is the block centre.
  - `secondBlockWithSameIdGetsNewId`: two speaker blocks where the second gets `setData(first.data())` before `placed`. The second's id differs from the first's, its queue equals the first's, and `SpeakerPlayback.isPlaying(secondId)` is false.
  - `brokenBlockHostExpires`: `removed(be)`, then `h.runAfterDelay(21, ...)`. `handle` is empty.

- [ ] **Step 2: Run them to see them fail**

  Run: `./gradlew runGameTest`
  Expected: FAIL, compilation error on `SpeakerHosts`.

- [ ] **Step 3: Implement**
  - The handles are package-private records: `BlockHandle(ServerLevel, BlockPos, PortableSpeakerBlockEntity)` here, plus `CarrierHandle` and `DroppedHandle` in Task 6.
  - In `SpeakerHosts`, `placed` calls `tracker.place`. On `DUPLICATE`, it calls `speaker.setData(data.withId(UUID.randomUUID()))` and places again.
  - The tick is `server.getTickCount()`.
  - `tick` runs `expire` and, in this task, only drops what expired. Task 6 adds the outcomes.
  - Register `ServerBlockEntityEvents.BLOCK_ENTITY_LOAD` (speaker block entities only) → `placed`. A chunk reload then re-registers its speakers.
  - `PortableSpeakerBlock.setPlacedBy` calls `placed` after its creative id refresh. The id is already applied from the item at that point.
  - `PortableSpeakerBlockEntity.setRemoved` also calls `SpeakerHosts.removed(this)`. It keeps its direct pause until Task 6, which replaces that pause with the expiry outcome so the dropped item can take the session over.
  - Wire it in `SpeakerNetwork`: `SpeakerHosts.tick(server)` runs first in `END_SERVER_TICK`, and `SpeakerHosts.clear()` runs in `SERVER_STOPPING` after `pauseAll`.
- [ ] **Step 4: Run to see it pass**

  Run: `./gradlew runGameTest`
  Expected: PASS, all GameTests.

- [ ] **Step 5: Commit**

  `git commit -m "Track speaker hosts by id, starting with blocks"`

---

### Task 4: Key the server and the control payloads on the id

Behaviour stays the same. The 95 existing GameTests are migrated and must stay green.

**Files:**
- Modify (server): `world/audio/SpeakerPlayback.java`, `StreamSessions.java`, `SpeakerWatchers.java`, `network/SpeakerNetwork.java`, `blockentity/PortableSpeakerBlockEntity.java`, `block/PortableSpeakerBlock.java`, `block/SpeakerScreenOpener.java`
- Modify (payloads): `SpeakerCommandPayload`, `SpeakerStatePayload`, `QueueAddPayload` and `SpeakerWatchPayload`: replace `BlockPos pos` with `UUID speakerId`, encoded with `UUIDUtil.STREAM_CODEC`.
- Modify (client): `SpeakerScreen`, `SpeakerStates`, `ControlRow`, `VolumeSlider`, `QueuePanel`, `PlayerBand`, `LibraryTree`, `LocalPlayback.add`, `RaveCraftClient`
- Create: `src/gametest/java/com/ravecraft/mod/gametest/Speakers.java`
- Modify: every GameTest class that calls the position API
- Delete: `world/audio/SpeakerKey.java`

**Interfaces:**
- Consumes: `SpeakerHosts.handle`, `SpeakerHandle` (Task 3).
- Produces (server API, each replacing its `BlockPos` twin):
  - `SpeakerPlayback`:
    - `add(ServerPlayer, UUID speakerId, List<NewEntry>)`
    - `command(ServerPlayer, UUID speakerId, Command, Optional<UUID>[, long])`
    - `isPlaying(UUID)`
    - `pauseAt(UUID)`
    - `liveQueue(UUID, SpeakerQueue stored)`
    - `stateOf(SpeakerHandle)`
  - `StreamSessions`:
    - `SessionTable<UUID>`
    - `open(SpeakerHandle, ServerPlayer owner, UUID entryId)`
    - `endAt(MinecraftServer, UUID speakerId)`
  - `SpeakerWatchers`:
    - `watch(ServerPlayer, UUID, boolean)`
    - `markDirty(UUID)`
    - `isDirty(UUID)`
  - `AudioRelay.relayRadius(SpeakerHandle)`: 0 when the speaker is unpowered.
  - Client:
    - `new SpeakerScreen(UUID speakerId, Component title)`
    - `SpeakerScreen#speakerId()`
    - `SpeakerScreenOpener.set(BiConsumer<UUID, Component>)` and `open(UUID, Component)`
    - `SpeakerStates.get/send/forget(UUID, …)`
    - `LocalPlayback.add(UUID speakerId, List<Path>)`
  - GameTests: `Speakers.idAt(GameTestHelper h, BlockPos absPos)` returns the id of the speaker block entity there.

- [ ] **Step 1: Migrate the GameTests to the id API.** This is mechanical: each `SpeakerPlayback.add/command/isPlaying(…, pos, …)` becomes `(…, Speakers.idAt(h, pos), …)`. Likewise for `SpeakerWatchers.watch/isDirty`, `StreamSessions.open/endAt` (now through `SpeakerHosts.handle(...).orElseThrow()`) and `AudioRelay.relayRadius(handle)`. Each test calls `SpeakerHosts.placed(be)` after `setBlock` if the block entity load event has not done it.

- [ ] **Step 2: Run to see the failure**

  Run: `./gradlew runGameTest`
  Expected: FAIL, compilation errors on the new signatures.

- [ ] **Step 3: Rekey the server.**
  - Every private helper of `SpeakerPlayback` takes a `SpeakerHandle` instead of a `PortableSpeakerBlockEntity`.
  - `update` calls `handle.setData`. `powered` calls `handle.powered()`. `speakerInReach` calls `handle.canControl(player)`.
  - The maps `PLAYING` and `HISTORY` are keyed by `UUID`.
  - `StreamSessions.open` refuses an unpowered handle.
  - `SpeakerWatchers.flush` drops a watcher when `handle(id)` is empty or `!canControl(player)`. It then sends that player `SpeakerClosedPayload(id)` (create it now: `record SpeakerClosedPayload(UUID speakerId)`, registered in `ModPayloads`).
  - `PortableSpeakerBlockEntity.togglePower`, `saveAdditional` and `collectImplicitComponents` call the id versions.
  - Block right-click: `SpeakerScreenOpener.open(speaker.data().id(), title)`. The title is the block entity's custom name, or the block name.
  - In this task, `StreamOrderPayload`, `NowPlayingPayload` and `RelayedChunkPayload` still carry the block position, read from `((SpeakerHost.Block) handle.host()).pos()`. Task 5 replaces them.

- [ ] **Step 4: Rekey the client.**
  - The screen and its widgets hold a `UUID speakerId` instead of the `pos`.
  - The screen closes on `SpeakerClosedPayload` for its id: `ClientPlayNetworking.registerGlobalReceiver` in `RaveCraftClient`. This replaces `speakerPresent()`, which is deleted.
  - `blockPowered()` is replaced by `state != null && state.powered()`.
  - `NowPlayingToasts` compares `screen.speakerId()` once Task 5 gives the notice an id; until then it keeps the position.

- [ ] **Step 5: Run to see it pass**

  Run: `./gradlew build`
  Expected: BUILD SUCCESSFUL, JUnit and every GameTest green.

- [ ] **Step 6: Delete `SpeakerKey`, then commit.** `grep -rn SpeakerKey src` must return nothing.

  `git commit -m "Key speaker playback, watchers and control payloads on the speaker id"`

---

### Task 5: Host payload and client positions

**Files:**
- Create: `network/payload/SpeakerHostPayload.java` and `client/audio/ClientSpeakerHosts.java`
- Modify:
  - payloads: `StreamOrderPayload`, `NowPlayingPayload` and `RelayedChunkPayload` drop `dimension` and `pos` and gain `UUID speakerId`;
  - server: `SpeakerHosts` (host notices), `AudioRelay`, `SpeakerPlayback.start`, `NowPlaying`, `ModPayloads`;
  - client: `ClientRelay`, `ReceivedStreams`, `StreamEmitter`, `LocalPlayback`, `RenderStream`, `AcousticRenderer`, `OcclusionScheduler`, `ReverbScheduler`, `AcousticDebugInfo`, `NowPlayingToasts`.
- Test: `src/gametest/java/com/ravecraft/mod/gametest/AudioRelayGameTest.java`

**Interfaces:**
- Consumes: `SpeakerHandle#trackers/position/host` (Task 3).
- Produces:
  ```java
  public record SpeakerHostPayload(UUID speakerId, ResourceKey<Level> dimension, Optional<BlockPos> block,
          int entityId, boolean inHand, int volume, boolean powered) implements CustomPacketPayload
  // block present → block host; otherwise entityId is Entity#getId() of the carrier or the item entity.
  ```
  - `SpeakerHosts.notice(UUID speakerId)`: (re)builds the payload of a playing speaker and sends it to its trackers and the session owner. Called on session start, host change, volume and power. It is a `SessionNotices<SpeakerHostPayload>` keyed by session: `open` on each change.
  - `boolean SpeakerHosts.tellHost(ServerPlayer, int sessionId)`: sends the host to a listener not yet told. It returns whether it sent.
  - `Optional<SpeakerHostPayload> SpeakerHosts.noticeOf(int sessionId)`.
  - Client:
    - `ClientSpeakerHosts.put(SpeakerHostPayload)`
    - `ClientSpeakerHosts.get(UUID)`
    - `@Nullable Vec3 ClientSpeakerHosts.position(UUID, ClientLevel)` returns null when the host is in another dimension or the entity is not loaded;
    - `ClientSpeakerHosts.clear()` on disconnect.

- [ ] **Step 1: Write the failing GameTests** in `AudioRelayGameTest`:
  - `newListenerIsToldHostFirst`: a playing block speaker and a mock listener in range. `SpeakerHosts.tellHost(listener, sessionId)` returns true the first time, and false on the second call. `AudioRelay` calls it before each `RelayedChunkPayload`.
  - `hostNoticeHasBlockAndVolume`: once playing, `SpeakerHosts.noticeOf(sessionId)` holds `block = pos`, `volume` = the speaker's volume and `powered = true`. After a `VOLUME` command it holds the new volume.

- [ ] **Step 2: Run to see them fail**

  Run: `./gradlew runGameTest`
  Expected: FAIL, compilation error on `SpeakerHostPayload` / `tellHost` / `noticeOf`.

- [ ] **Step 3: Server.**
  - `AudioRelay.relay` gets the handle from the session's id. With no handle (lost host, in its grace), it drops the chunk.
  - The radius is taken around `handle.position()`. The candidates are `handle.trackers()`, and `listeners` takes a `Vec3` centre and `ServerLevel`.
  - The relay calls `tellHost` then `NowPlaying.tell` before each send.
  - `SpeakerPlayback.start` calls `SpeakerHosts.notice(id)` after opening the session. The `VOLUME` and `POWER` commands call it too.

- [ ] **Step 4: Client.**
  - `RenderStream` holds `speakerId`. On the client thread, `refresh(level)` writes `volatile double x, y, z`, plus `volume` and `powered`, from `ClientSpeakerHosts`. Facing comes from the block state for a block host and is `(0, 1)` otherwise.
  - When the position is null, `refresh` sets `volume = 0`.
  - Every `stream.pos` read becomes `stream.x/y/z`, and `isPoweredSpeaker` is deleted.
  - `StreamEmitter` drops its `dimension` and `pos`. Before offering a chunk locally, it checks `ClientSpeakerHosts.position(speakerId, level) != null`.
  - `ReceivedStreams.offer` takes `speakerId`.
  - `NowPlayingToasts` matches on `speakerId`.

  Ruling recorded here: the position is refreshed on each client tick (20 Hz), not on each frame. The audio thread reads it once per 100 ms block, so a per-frame read would never be heard.

- [ ] **Step 5: Run to see it pass**

  Run: `./gradlew build`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

  `git commit -m "Send speaker hosts to listeners and place sounds from them"`

---

### Task 6: Carried and dropped speakers

**Files:**
- Modify: `item/PortableSpeakerItem.java` (`inventoryTick`, `onDestroyed`), `world/audio/SpeakerHosts.java`, `world/audio/SpeakerPlayback.java`, `network/SpeakerNetwork.java` (disconnect)
- Create: `src/gametest/java/com/ravecraft/mod/gametest/CarriedSpeakerGameTest.java`, declared in `src/gametest/resources/fabric.mod.json`

**Interfaces:**
- Consumes: Tasks 1, 3, 4, 5.
- Produces:
  - `SpeakerHosts`:
    - `carried(ServerPlayer, ItemStack, boolean inHand)`, called by `inventoryTick` when `owner instanceof ServerPlayer`, with `inHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND`;
    - `dropped(ItemEntity)`, from `ServerEntityEvents.ENTITY_LOAD`;
    - `droppedRemoved(ItemEntity)`, from `ENTITY_UNLOAD`;
    - `destroyed(UUID)`, from `Item#onDestroyed(ItemEntity)`;
    - `disconnected(ServerPlayer)`.
  - `SpeakerPlayback`:
    - `stopAt(UUID)`: ends the session and forgets the history;
    - `detach(UUID, SpeakerQueue lastStored) → SpeakerQueue`: ends the session of a speaker whose host is gone and returns the queue at the live position (`liveQueue`);
    - `resume(SpeakerHandle)`: PLAY at the stored position when powered.
  - `SpeakerHosts` keeps `Map<UUID, SpeakerQueue> lastQueue`, the last queue seen for each tracked id. It is written by `placed`, `carried`, `dropped` and every `handle.setData`. This is what `detach` gets once the host is out of reach (a chest, an unloaded chunk). "Pause" in the table below means `detach`, and the queue it returns is parked.

Outcome when a host expires, decided in `SpeakerHosts.tick`:

| Last host | How it was lost | Outcome |
|---|---|---|
| `Carrier` | no report for 20 ticks (stored) | pause; park the live queue with `resume = true` |
| `Dropped` | unloaded with an **empty** stack (picked up by a hopper or a player) | pause; park with `resume = true` |
| `Dropped` | unloaded with `UNLOADED_TO_CHUNK` | pause; park with `resume = false` |
| `Dropped` | discarded with a non-empty stack (despawn, void) | `stopAt`; `tracker.forget` |
| `Block` | `setRemoved` (break, chunk unload) | pause; park with `resume = false` |

- Parked: `Map<UUID, Parked(SpeakerQueue queue, boolean resume)>`, in memory and cleared on stop. The next `placed`, `carried` or `dropped` of that id writes `queue` into its handle, then calls `resume(handle)` if asked.
- `destroyed(id)`: `stopAt(id)`, then `forget`.
- `disconnected(player)`: for every id whose host is that player, pause through the handle (the stack is still in the inventory), then `forget`.

Handles:
- `CarrierHandle`: finds the stack whose `SPEAKER` id matches among `player.getInventory().getItem(0 .. getContainerSize()-1)`. When it is not found, `handle()` is empty.
- `DroppedHandle`: `entity.getItem().set(SPEAKER, …)`.
- Both resolve the entity with `server.getPlayerList().getPlayer(uuid)` or `level.getEntity(uuid)`.

A stack without `SPEAKER` (freshly crafted) receives `SpeakerData.create()` in `inventoryTick` before reporting. On `DUPLICATE`, the stack gets `withId(UUID.randomUUID())` and reports again.

- [ ] **Step 1: Write the failing GameTests** in `CarriedSpeakerGameTest`. Each one has a mock server player as the owner, a track queued and playing via `onStreamStarted`.
  - `breakKeepsSession`: note the `sessionId`, then `level.destroyBlock(pos, true)`. After 25 ticks, `isPlaying(id)` is true, the session id is unchanged and the host is `Dropped`.
  - `pickupByHopperPausesNotStops`: break the block over a hopper. After 30 ticks, `isPlaying(id)` is false, and the hopper's stack still carries `id`. Then call `SpeakerHosts.carried(player, thatStack, false)` for 2 ticks: `isPlaying(id)` is true again.
  - `storedCarrierPausesThenResumes`: call `carried(player, stack, true)` each tick for 5 ticks, then stop for 21 ticks. `isPlaying` is false and the stored position is greater than 0. Report again: `isPlaying` is true.
  - `duplicateInInventoryGetsNewId`: two stacks with the same `SpeakerData`, reported in one tick. Exactly one keeps the id, and the other's id is new with an equal queue.
  - `farPlayerCannotCommand`: a dropped playing speaker, and a second player 20 blocks away sends `PAUSE`. It is still playing.
  - `carrierCanCommandFromAnywhere`: the carrier sends `PAUSE` and it is paused.
  - `destroyedStops`: `ItemEntity#hurtServer` with lava damage until it is removed. `isPlaying` is false and `handle(id)` is empty.
  - `pauseAllWritesCarriedPosition`: a carried playing speaker, then 40 ticks later `SpeakerPlayback.pauseAll(server)`. The stack's queue position is greater than 0.
  - `disconnectPauses`: a carried playing speaker, then `SpeakerHosts.disconnected(player)`. `isPlaying` is false.

- [ ] **Step 2: Run to see them fail**

  Run: `./gradlew runGameTest`
  Expected: FAIL, compilation error on `SpeakerHosts.carried`.

- [ ] **Step 3: Implement** the item hooks, the event wiring in `SpeakerHosts.init()` (called from `RaveCraft.onInitialize`), the handles, the outcome table and the parked queues.
  - `PortableSpeakerBlockEntity.setRemoved` drops its direct pause. The block row of the table pauses instead.
  - `SpeakerPlayback.pauseAll` pauses every playing id through its handle.
  - `SpeakerNetwork`'s disconnect handler calls `SpeakerHosts.disconnected` before `SpeakerPlayback.onDisconnect`.
  - Each host change (`MOVED`) on a playing id calls `SpeakerHosts.notice(id)`.

- [ ] **Step 4: Run to see it pass**

  Run: `./gradlew build`
  Expected: BUILD SUCCESSFUL, the 9 new GameTests green.

- [ ] **Step 5: Commit**

  `git commit -m "Keep speakers playing when carried or dropped"`

---

### Task 7: Open from the item and key J, tooltip line

**Files:**
- Modify: `item/PortableSpeakerItem.java` (`use`), `client/SpeakerTooltip.java`, `client/RaveCraftClient.java`, `assets/ravecraft/lang/en_us.json`, `fr_fr.json`
- Create: `src/client/java/com/ravecraft/mod/client/OpenSpeakerKey.java`

**Interfaces:**
- Consumes: `SpeakerKeyTarget.choose` (Task 2), `SpeakerScreenOpener.open(UUID, Component)` (Task 4).
- Produces: `OpenSpeakerKey.register()` and `OpenSpeakerKey.key()`.

Texts:

| Key | en_us | fr_fr |
|---|---|---|
| `key.ravecraft.open_speaker` | `Open speaker` | `Ouvrir l'enceinte` |
| `key.category.ravecraft.ravecraft` | `RaveCraft` | `RaveCraft` |
| `tooltip.ravecraft.speaker.open_key` | `[%s]: open the interface (inventory or back)` | `[%s] : ouvrir l'interface (inventaire ou dos)` |

- [ ] **Step 1: Write the failing GameTest** `PortableSpeakerGameTest.useInAirNoLongerTogglesPower`: a mock player holds a speaker with `powered = true`, and `item.use(level, player, MAIN_HAND)` runs on the server. `powered` is still true, and the stack has a `SPEAKER` component.

- [ ] **Step 2: Run to see it fail**

  Run: `./gradlew runGameTest`
  Expected: FAIL, `powered` is false.

- [ ] **Step 3: Implement.**
  - `use`: on the server, add `SpeakerData.create()` if the component is missing. On the client, call `SpeakerScreenOpener.open(data.id(), stack.getHoverName())` when the component is present. Return `InteractionResult.SUCCESS`.
  - `OpenSpeakerKey`: `END_CLIENT_TICK`, `while (key.consumeClick())`. It only acts when `minecraft.screen == null` and a player exists. It uses `choose(inventory.getSelectedSlot(), i -> stack(i).has(SPEAKER))`, then opens that stack's id with its hover name.
  - `SpeakerTooltip`: after the base line, unless `OpenSpeakerKey.key().isUnbound()`, add `gray("tooltip.ravecraft.speaker.open_key", key.getTranslatedKeyMessage())`.

- [ ] **Step 4: Run to see it pass**

  Run: `./gradlew runDatagen && ./gradlew build`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

  `git commit -m "Open the speaker screen from the item and the J key"`

---

### Task 8: SPEC and in-game check

- [ ] **Step 1: Update `docs/SPEC.md`.** On the step 6/6 line, mark 6a as delivered, `à vérifier en jeu`, and say that 6b remains.
- [ ] **Step 2: Run the build.**

  Run: `./gradlew build`
  Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit.** `git commit -m "Note the carried speaker in the spec"`
- [ ] **Step 4: Hand the in-game checklist (spec §6) to the user.** This step is for the user and the agent does not run it.
  - Break a playing speaker, pick it up and walk: the sound follows.
  - Put it in a chest: it pauses. Take it out: it resumes.
  - Press J with it in the inventory.
  - Check the tooltip line, then unbind J and check that the line is gone.
  - Right-click in the air.
  - With two players, the other hears it move.
