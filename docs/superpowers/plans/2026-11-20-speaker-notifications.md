# Speaker Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Now playing" toast for the speaker this player hears loudest, and a *RaveCraft notifications* option (all / errors only / none).

**Architecture:** The server tells each player what a session plays, once, with a small `NowPlayingPayload` (to the streaming owner at start, to each listener just before its first relayed chunk). The client joins those notices with the gains `AcousticRenderer` already computes, and a pure `core` class picks the followed source (3 s rule) and says when to announce. The option gates both this toast and the existing "track removed" toast.

**Tech Stack:** Fabric 26.3 (Mojang names), Java 25, JUnit 5, Fabric GameTests.

**Spec:** [docs/superpowers/specs/2026-11-20-speaker-ui-5b-design.md](../specs/2026-11-20-speaker-ui-5b-design.md), section 4 (PR 2).

**Branch:** `feat/speaker-notifications`, from `main` once PR 1 (`feat/queue-reorder`, which carries the spec) is merged.

**Ruling against the spec (user decision, 2026-11-20):** spec §6 says nothing is sent over the network apart from `MOVE`. But listeners only receive audio (session id, position), and the speaker's queue only goes to players with its screen open. So the toast could not show the title or "added by X". The user chose a small S→C payload sent once per session and player. Update spec §4 and §6 in Task 5.

## Global Constraints

- `src/main` never imports `net.minecraft.client`; `core` is pure Java (no Minecraft types).
- Network strings are capped before sending: `QueueEntry` already truncates `title` to `QueueEntry.MAX_TITLE` (128) and `ownerName` to `QueueEntry.MAX_OWNER_NAME`; the codec uses those same limits.
- No per-tick scans: the client checks once a second (every 20 client ticks); the server sends the notice from events (session start, first relayed chunk), never from a scan.
- Follow rule: the loudest audible speaker becomes followed at once the first time, otherwise after being loudest for **3 s** (`FOLLOW_DELAY_MS = 3000`).
- The same entry on the same speaker is never announced twice in a row. No toast while that speaker's screen is open (it counts as announced).
- Toast: vanilla `SystemToast`, one instance updated in place (`SystemToast.addOrUpdate` with one `SystemToastId` of our own). Title "Now playing" / « En lecture » ; message "%s — added by %s" / « %s — ajouté par %s ».
- Option *RaveCraft notifications* in Options › Music & Sounds, saved in `options.txt` as `ravecraft_notifications`: `ALL` (default), `ERRORS` (only the existing "track removed" toast), `NONE`.
- Translations in `en_us.json` **and** `fr_fr.json`. No block, item or recipe: no datagen.

## Review Focus

- A player walking into range mid-track still gets the notice (first chunk relayed to *them*), and only once per session. Pinned by `SessionNoticesTest.eachPlayerOnce`.
- A session ending by any path (stop, owner leaving, speaker broken, server stop) drops its notice: no leak, no stale title for a reused id. Pinned by `NowPlayingGameTest.noticeDroppedWhenSessionEnds`.
- Repeat-one or pause/resume opens a new session for the same entry: no second toast. Pinned by `FollowedSourceTest.sameEntryNotAnnouncedTwice`.
- Opening then closing the screen of the playing speaker: no toast for the track already seen. Pinned by `FollowedSourceTest.openScreenCountsAsAnnounced`.
- A brief louder burst from another speaker (under 3 s) does not steal the follow. Pinned by `FollowedSourceTest.briefPeakDoesNotSwitch`.

---

### Task 1: `core.audio.stream.SessionNotices`

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/audio/stream/SessionNotices.java`
- Test: `src/test/java/com/ravecraft/mod/core/audio/stream/SessionNoticesTest.java`

**Interfaces:**
- Produces: `public final class SessionNotices<T>` (not thread-safe, server thread):
  - `void open(int sessionId, T notice)`: replaces any previous notice and forgets who was told.
  - `Optional<T> firstFor(int sessionId, UUID player)`: the notice the first time `player` asks for that session, empty afterwards or when there is none.
  - `Optional<T> get(int sessionId)`
  - `void end(int sessionId)`
  - `void clear()`

- [ ] **Step 1: Write the failing tests**

```java
@Test void eachPlayerOnce()        // open(1,"a"); firstFor(1,p) == Optional.of("a"); firstFor(1,p) empty; firstFor(1,q) == Optional.of("a")
@Test void unknownSessionEmpty()   // firstFor(9,p) empty; get(9) empty
@Test void reopenTellsAgain()      // open(1,"a"); firstFor(1,p); open(1,"b"); firstFor(1,p) == Optional.of("b")
@Test void endAndClearForget()     // open(1,"a"); open(2,"b"); end(1) → get(1) empty, get(2) present; clear() → get(2) empty
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew test --tests "com.ravecraft.mod.core.audio.stream.SessionNoticesTest"` (cannot find symbol)
- [ ] **Step 3: Implement** (`HashMap<Integer, T>` + `HashMap<Integer, Set<UUID>>`)
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `git commit -m "Remember which players heard about each session"`

---

### Task 2: `NowPlayingPayload` and the server sending it

**Files:**
- Create: `src/main/java/com/ravecraft/mod/network/payload/NowPlayingPayload.java`
- Create: `src/main/java/com/ravecraft/mod/world/audio/NowPlaying.java`
- Modify: `src/main/java/com/ravecraft/mod/network/ModPayloads.java` (register clientbound, like `TrackRemovedPayload`)
- Modify: `src/main/java/com/ravecraft/mod/world/audio/SpeakerPlayback.java` (`start`, next to the `StreamOrderPayload` send)
- Modify: `src/main/java/com/ravecraft/mod/network/AudioRelay.java` (`relay`, inside the listener loop, before `Net.send(listener, relayed)`)
- Modify: `src/main/java/com/ravecraft/mod/world/audio/StreamSessions.java` (`notifyEnded` and `clear`)
- Test: `src/test/java/com/ravecraft/mod/network/payload/SpeakerPayloadCodecTest.java`, `src/gametest/java/com/ravecraft/mod/gametest/NowPlayingGameTest.java` (declare it in `src/gametest/resources/fabric.mod.json`)

**Interfaces:**
- Consumes: `SessionNotices<T>` (Task 1).
- Produces:
  - `record NowPlayingPayload(int sessionId, BlockPos pos, UUID entryId, String title, String ownerName)`, S→C, `TYPE = RaveCraft.id("now_playing")`. The codec uses `ByteBufCodecs.stringUtf8(QueueEntry.MAX_TITLE)` and `stringUtf8(QueueEntry.MAX_OWNER_NAME)`.
  - `NowPlaying` (server thread): `static void start(ServerPlayer owner, int sessionId, BlockPos pos, QueueEntry entry)` opens the notice and sends it to the owner; `static void tell(ServerPlayer listener, int sessionId)` sends it if that listener was not told yet; `static Optional<NowPlayingPayload> of(int sessionId)` for tests; `static void end(int sessionId)`; `static void clear()`.

- [ ] **Step 1: Write the failing tests**

```java
// SpeakerPayloadCodecTest
@Test void nowPlayingRoundTrips()  // encode/decode a NowPlayingPayload(7, POS, id, "Artist — Title", "Steve") → equals
// NowPlayingGameTest (reuse SharedQueueGameTest's static helpers: poweredSpeaker, online, newEntries, finish)
@GameTest void noticeOpenedOnPlay(GameTestHelper h)
// add 1 entry, PLAY → NowPlaying.of(current session id) has title "track 0", ownerName == player's name, pos == speaker
@GameTest void noticeDroppedWhenSessionEnds(GameTestHelper h)
// PLAY, remember the session id, CLEAR → NowPlaying.of(id) empty
```
Get the session id with `StreamSessions.table()` by the speaker's key, as the existing GameTests do (see `session(h, pos)` in `SharedQueueGameTest`).

- [ ] **Step 2: Run, expect FAIL** — `./gradlew test --tests "*SpeakerPayloadCodecTest"` and `./gradlew runGameTest` (cannot find symbol)
- [ ] **Step 3: Implement.** `NowPlaying` holds one `SessionNotices<NowPlayingPayload>`. In `SpeakerPlayback.start`, call `NowPlaying.start(owner, session.get().id(), pos, entry)` right after the `StreamOrderPayload` send. In `AudioRelay.relay`, call `NowPlaying.tell(listener, chunk.sessionId())` before relaying each chunk. In `StreamSessions.notifyEnded`, call `NowPlaying.end(session.id())` first. In `StreamSessions.clear`, call `NowPlaying.clear()`.
- [ ] **Step 4: Run, expect PASS** — the codec test, then `./gradlew runGameTest` (all required tests pass)
- [ ] **Step 5: Commit** — `git commit -m "Tell players what each speaker session plays"`

---

### Task 3: `core.notify.FollowedSource`

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/notify/FollowedSource.java`
- Test: `src/test/java/com/ravecraft/mod/core/notify/FollowedSourceTest.java`

**Interfaces:**
- Produces:
  - `public static final long FOLLOW_DELAY_MS = 3000`
  - `public record Heard(long speaker, UUID entryId, double gain)`: `speaker` is any stable key (the client passes `BlockPos.asLong()`).
  - `public Optional<Heard> update(long nowMs, List<Heard> heard, OptionalLong openSpeaker)`: the entry to announce now, or empty.

Rules (the tests pin them):
- Audible means `gain > 0`. The loudest is the highest gain; ties go to the smaller `speaker` key.
- When the loudest speaker changes, it becomes the candidate from `nowMs`. With no audible speaker, there is no candidate.
- With no followed speaker yet, the candidate is followed at once. Otherwise the candidate replaces the followed speaker once it has been loudest for `FOLLOW_DELAY_MS`.
- If the followed speaker is audible and its `(speaker, entryId)` differs from the last one announced, that pair becomes the last announced. It is returned, unless `openSpeaker` is that speaker.

- [ ] **Step 1: Write the failing tests** (A = 1L, B = 2L; e1, e2 random UUIDs)

```java
@Test void firstLoudestAnnouncedAtOnce()     // update(0,[A e1 .5],none) == Heard(A,e1,.5)
@Test void sameEntryNotAnnouncedTwice()      // then update(1000,[A e1 .5]) empty; update(2000,[A e1 .4]) empty
@Test void newTrackOnFollowedAnnounced()     // after A e1: update(1000,[A e2 .5]) → A e2
@Test void louderSpeakerFollowedAfterDelay() // after A e1 at 0: [A e1 .2, B e2 .6] at 1000 → empty; at 3999 → empty; at 4000 → B e2
@Test void briefPeakDoesNotSwitch()          // after A e1 at 0: B louder at 1000, A louder at 2000, B louder at 3000; at 5999 empty; at 6000 → B e2
@Test void openScreenCountsAsAnnounced()     // update(0,[A e1],OptionalLong.of(A)) empty; update(1000,[A e1],none) empty; [A e2] none → A e2
@Test void silentSpeakersIgnored()           // update(0,[A e1 0.0],none) empty; update(1000,[B e2 .3],none) → B e2 at once (nothing followed yet)
@Test void returningToEarlierSpeakerAnnouncesAgain() // A e1 at 0; B e2 louder from 1000 → announced at 4000; A louder from 5000 → A e1 at 8000
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew test --tests "com.ravecraft.mod.core.notify.FollowedSourceTest"`
- [ ] **Step 3: Implement `FollowedSource`** (final class, mutable, single-threaded)
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `git commit -m "Pick the speaker heard loudest to announce its tracks"`

---

### Task 4: The *RaveCraft notifications* option

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/options/NotificationLevel.java`
- Modify: `src/client/java/com/ravecraft/mod/client/options/RaveCraftOptions.java`, `src/client/java/com/ravecraft/mod/client/mixin/SoundOptionsScreenMixin.java`, `src/client/java/com/ravecraft/mod/client/audio/stream/ClientRelay.java`
- Modify: `src/main/resources/assets/ravecraft/lang/en_us.json`, `fr_fr.json`

**Interfaces:**
- Produces: `enum NotificationLevel implements StringRepresentable { ALL, ERRORS, NONE }`, built like `OcclusionQuality` (codec `StringRepresentable.fromEnum`, caption key `options.ravecraft.notifications.<all|errors|none>`), with `boolean nowPlaying()` (ALL only) and `boolean errors()` (ALL and ERRORS). `RaveCraftOptions.notifications()`: `OptionInstance<NotificationLevel>`, key `options.ravecraft.notifications`, tooltip `options.ravecraft.notifications.tooltip`, default `ALL`, processed as `ravecraft_notifications`.

- [ ] **Step 1: Add the enum, the option and its `process` line**; add the row with `this.list.addSmall(RaveCraftOptions.notifications())` after the existing rows.
- [ ] **Step 2: Gate the "track removed" toast** in `ClientRelay` on `RaveCraftOptions.notifications().get().errors()`.
- [ ] **Step 3: Translations**:

| Key | en_us | fr_fr |
|---|---|---|
| `options.ravecraft.notifications` | RaveCraft Notifications | Notifications RaveCraft |
| `options.ravecraft.notifications.tooltip` | Toasts for the track playing on the speaker you hear loudest, and for errors. | Toasts du morceau joué par l'enceinte que vous entendez le plus fort, et des erreurs. |
| `options.ravecraft.notifications.all` | All | Toutes |
| `options.ravecraft.notifications.errors` | Errors Only | Erreurs seulement |
| `options.ravecraft.notifications.none` | None | Aucune |

- [ ] **Step 4: Build** — `./gradlew build`; expect BUILD SUCCESSFUL
- [ ] **Step 5: Commit** — `git commit -m "Add the RaveCraft notifications option"`

---

### Task 5: Toast on the client, SPEC

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/audio/stream/NowPlayingToasts.java`
- Modify: `src/client/java/com/ravecraft/mod/client/audio/render/AcousticRenderer.java` (public `heard()`), `src/client/java/com/ravecraft/mod/client/screen/SpeakerScreen.java` (public `pos()`), `src/client/java/com/ravecraft/mod/client/audio/stream/ClientRelay.java` (receivers), lang files, `docs/SPEC.md`, the spec (§4 and §6)

**Interfaces:**
- Consumes: `NowPlayingPayload` (Task 2), `FollowedSource` (Task 3), `RaveCraftOptions.notifications()` (Task 4).
- Produces:
  - `AcousticRenderer.heard()`: `Map<Integer, Double>`, session id → `heardGain` of each attached stream (client thread, a copy).
  - `SpeakerScreen.pos()`.
  - `NowPlayingToasts`: `register()` (tick + disconnect), `onNotice(NowPlayingPayload)`, `onSessionEnd(int sessionId)`.

- [ ] **Step 1: `NowPlayingToasts`.**
  - Notices are kept by session id in an insertion-ordered map capped at 64 (oldest out), dropped on `SessionEndPayload` and cleared on disconnect, together with a fresh `FollowedSource`.
  - Every 20 client ticks with a level loaded, and only if `notifications().get().nowPlaying()`: build `Heard(pos.asLong(), entryId, gain)` for each session present in both `heard()` and the notices. `openSpeaker` is the `SpeakerScreen` on screen, if any. Call `update(Util.getMillis(), …)`.
  - On an announcement: `SystemToast.addOrUpdate(toastManager, NOW_PLAYING, title, message)`, with `NOW_PLAYING = new SystemToast.SystemToastId()`.
- [ ] **Step 2: Wire** `NowPlayingPayload` → `onNotice`. In the existing `SessionEndPayload` receiver, also call `onSessionEnd`. Call `register()` from `ClientRelay.init`.
- [ ] **Step 3: Translations**: `toast.ravecraft.now_playing.title` = "Now Playing" / « En lecture » ; `toast.ravecraft.now_playing.message` = "%s — added by %s" / « %s — ajouté par %s ».
- [ ] **Step 4: Docs.** Spec §4: the server tells each player what a session plays (`NowPlayingPayload`, once per session and player). §6: replace "rien n'est envoyé en plus sur le réseau (sauf `MOVE`)" with that payload and `MOVE`. SPEC.md 5b line: PR 2 delivered, "à vérifier en jeu".
- [ ] **Step 5: Build** — `./gradlew build`; expect BUILD SUCCESSFUL with JUnit and GameTests green.
- [ ] **Step 6: In-game check (user):** two speakers playing; walk from one to the other (toast after about 3 s); a track change on the followed speaker; your own track; screen open (no toast); the option set to errors only, then none; a second player walking into range mid-track.
- [ ] **Step 7: Commit** — `git commit -m "Announce the track playing on the speaker heard loudest"`
