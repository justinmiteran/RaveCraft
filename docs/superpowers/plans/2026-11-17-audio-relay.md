# Audio Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a player plays music on a placed speaker, every player within range hears it. The music is streamed from that player's PC, compressed in Opus and relayed by the Minecraft server.

**Architecture:**
- **Emitter (client of the player who presses Play).** It decodes the file with the step 2 decoders, downmixes to mono and resamples to 48 kHz. It then encodes 20 ms Opus frames, groups them by 5 into `AudioChunk` packets (~100 ms) and sends them at most 2.5 s ahead of real time. It also feeds its own frames to its local receiver (loopback), so everyone, the emitter included, hears the music through the same receiver path.
- **Server.** It keeps one stream session per speaker and checks that each chunk comes from the session owner and stays under the rate limit. It forwards each chunk to the players that track the speaker's chunk and are within 24 blocks. That player set is vanilla's chunk-tracking set, maintained on events: no per-tick scan.
- **Receiver (each client).** It creates a stream lazily on the first relayed chunk. A jitter buffer of about 1 s feeds Opus decoding, with loss concealment, into a vanilla `AudioStream` that always returns 100 ms blocks. The speaker's sound instance from step 2 plays it.

All codec, buffering, pacing, rate-limit and session-table logic is in `core`, in pure Java tested with JUnit.

**Tech Stack:**
- Fabric 26.3 with Mojang names.
- Fabric Networking API (`PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`, `ServerPlayNetworking`, `ClientPlayNetworking`, `PlayerLookup.tracking`).
- Concentus `io.github.jaredmdobson:concentus:1.0.2`, a pure-Java Opus under BSD-3-Clause, bundled jar-in-jar.

**Spec:** [docs/superpowers/specs/2026-11-17-audio-core-design.md](../specs/2026-11-17-audio-core-design.md), sections 3, 6.3, 7 and 8, step 3. This plan covers step **3a** (`feat/audio-relay`). Step **3b** (`feat/shared-queue`) adds the server-side shared queue, `StreamOrder` driven by the server, absent owners and unreadable files (D10–D12).

## Decisions taken for this step (user-approved 2026-11-17)

| # | Decision |
|---|---|
| R1 | Step 3 is split in two branches. **3a** (this plan) covers Opus, packets, relay and receiver; the player who presses Play streams their own local queue. **3b** adds the shared queue and the server playback manager. |
| R2 | Opus through **Concentus 1.0.2** (pure Java, BSD-3-Clause, no natives). The licence text ships inside the mod jar. |
| R3 | The stream is **mono, 48 kHz, ~64 kb/s**: OpenAL only spatialises mono. This changes the spec's "stereo ~128 kb/s"; update the spec in Task 1. |
| R4 | The branch `feat/audio-relay` is stacked on `feat/local-playback` and rebased onto `main` once PR #3 is merged. |

## Global Constraints

- Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`, Java 25. Mojang names only.
- `src/main` never imports `net.minecraft.client`. `core` never imports Minecraft or Fabric (Concentus is allowed in `core`).
- Stream format: 48 000 Hz, mono, 16-bit signed. Opus frame of 20 ms (960 samples), 5 frames per chunk, bitrate 64 000, complexity 5, each encoded frame ≤ 380 bytes.
- Limits are capped before sending and checked on receipt:
  - chunk ≤ 5 frames, frame ≤ 380 bytes (so a chunk stays ≤ 2 KB);
  - sender rate ≤ 200 kb/s, enforced by a token bucket of 25 000 B/s with a 40 000 B capacity. Packets over the limit are dropped.
- No file path or file name is sent over the network (track titles arrive in 3b).
- Emitter lead over real time: 2 500 ms. Receiver jitter target: 1 s (50 frames). Frames more than 5 s (250 frames) ahead of playout trigger a resync.
- Relay range: 24 blocks from the speaker centre (16 audible + 8 margin, so players walking in already have audio buffered).
- Decoding, encoding and file IO never run on the render thread or the server thread. Networking handlers run on the main thread (Fabric default).
- Translations in `en_us.json` **and** `fr_fr.json` for any new text. Code, comments and commits are in English, with no Claude attribution.
- `./gradlew build` must be green before every commit that closes a task.

## Review Focus

1. **A player walks into range mid-track.** They hear the music after ≤ ~1 s of buffering, with no error. The receiver is created by the first relayed chunk, with no prior "start" packet. *(Task 8, `ReceivedStreamsTest` lazy creation; Task 7 GameTest `relayTargetsOnlyPlayersInRange`.)*
2. **A second player presses Play on a speaker that is already streaming.** The first owner's emitter stops, its listeners get `SessionEnd`, and no listener hears two tracks at once. *(Task 5 `openReplacesExistingSession`; Task 7 GameTest `secondOpenReplacesFirstOwner`.)*
3. **The owner breaks the speaker, turns it off, or disconnects; or the chunk unloads.** Every listener's sound stops within one relay round trip; no stuck sound remains. *(Task 7 GameTests `breakingSpeakerEndsSession`, `poweringOffEndsSession`; disconnect handled in Task 7.)*
4. **A 44.1 kHz stereo MP3 or FLAC is streamed.** Pitch and speed stay correct. The resampler keeps frequency, and the pacer keeps real-time speed. *(Task 2 `resamplingKeepsFrequency`; Task 4 `pacerHoldsLead`.)*
5. **A network hiccup or a slow listener.** A short silence, then the receiver catches up from the newest frames instead of playing seconds of concealment. The vanilla channel never starves and ends the sound. *(Task 3 `underrunReturnsToPrebufferingAndSkipsGap`; Task 6 `playoutAlwaysReturnsFullBlock`.)*

---

## File Structure

```
src/main/java/com/ravecraft/mod/
  core/audio/opus/OpusMonoEncoder.java      Concentus wrapper: 960 samples -> ≤380 B frame
  core/audio/opus/OpusMonoDecoder.java      frame (or null = lost) -> 960 samples
  core/audio/stream/StreamFormat.java       constants: rate, frame size, frames per chunk, lead, jitter, caps
  core/audio/stream/LinearResampler.java    streaming mono resampler any rate -> 48 kHz
  core/audio/stream/MonoFramer.java         accumulates mono samples into 960-sample frames
  core/audio/stream/JitterBuffer.java       reorder, dedup, prebuffer, loss, resync
  core/audio/stream/StreamPlayout.java      JitterBuffer + decoder -> always one 100 ms PCM block
  core/audio/stream/EmitterPacer.java       how long to wait before sending the next chunk
  core/audio/stream/TokenBucket.java        per-sender rate limiter
  core/audio/stream/SessionTable.java       speaker key -> session(id, owner), generic in the key
  network/payload/*.java                    StreamOpen, StreamOrder, AudioChunk, RelayedChunk, StreamClose, SessionEnd
  network/ModPayloads.java                  registers payload types (common)
  network/AudioRelay.java                   server receivers, validation, forwarding
  world/audio/StreamSessions.java           server singleton around SessionTable<SpeakerKey>, end hooks
src/client/java/com/ravecraft/mod/client/audio/
  stream/ReceivedStreams.java               sessionId -> StreamPlayout + sound instance, timeout
  stream/NetworkAudioStream.java            AudioStream over StreamPlayout
  stream/StreamEmitter.java                 decode -> mono -> 48k -> Opus -> chunks, paced, on its own thread
  stream/OggPcmDecoder.java                 PcmDecoder over vanilla JOrbisAudioStream
  stream/ClientRelay.java                   client receivers for StreamOrder / RelayedChunk / SessionEnd
  SpeakerSoundInstance.java                 (modified) plays any AudioStream supplier
  LocalPlayback.java                        (modified) queue driven by StreamOrder instead of local sounds
```

---

### Task 1: Concentus dependency and Opus mono codec

**Files:**
- Modify: `build.gradle` (add `implementation` + `include` of `io.github.jaredmdobson:concentus:1.0.2`)
- Create: `src/main/resources/licenses/concentus-LICENSE.txt` (full text from https://raw.githubusercontent.com/lostromb/concentus/master/LICENSE)
- Modify: `LICENSE-THIRD-PARTY.md` (row: Concentus, 1.0.2, Opus encode/decode, BSD-3-Clause, https://github.com/jaredmdobson/concentus; note that the licence text ships at `licenses/concentus-LICENSE.txt` in the jar)
- Modify: `docs/superpowers/specs/2026-11-17-audio-core-design.md`. Apply R1 (step 3 split into 3a and 3b in section 10). Apply R3 (mono 48 kHz ~64 kb/s in the Emitter row, section 3.2, and in section 8).
- Create: `core/audio/stream/StreamFormat.java`, `core/audio/opus/OpusMonoEncoder.java`, `core/audio/opus/OpusMonoDecoder.java`
- Test: `src/test/java/com/ravecraft/mod/core/audio/opus/OpusMonoCodecTest.java`

**Interfaces:**
- Produces:
  - `final class StreamFormat`: `SAMPLE_RATE = 48_000`, `FRAME_SAMPLES = 960`, `FRAME_MS = 20`, `FRAMES_PER_CHUNK = 5`, `CHUNK_SAMPLES = 4_800`, `BITRATE = 64_000`, `COMPLEXITY = 5`, `MAX_FRAME_BYTES = 380`, `LEAD_MS = 2_500`, `JITTER_TARGET_FRAMES = 50`, `MAX_AHEAD_FRAMES = 250`, `RATE_BYTES_PER_SECOND = 25_000`, `RATE_BURST_BYTES = 40_000`, `RELAY_RANGE = 24.0`.
  - `OpusMonoEncoder()` and `byte[] encode(short[] frame)`. It requires `frame.length == FRAME_SAMPLES` and returns ≤ `MAX_FRAME_BYTES` bytes. It throws `IllegalStateException` wrapping `OpusException`.
  - `OpusMonoDecoder()` and `short[] decode(byte @Nullable [] frame)`. It returns `FRAME_SAMPLES` samples; `null` means a lost frame and runs concealment. A corrupt frame gives silence, not an exception.

- [ ] **Step 1: Write failing tests** in `OpusMonoCodecTest`:
  - `encodedFrameFitsCap`: encode 50 frames of full-scale white noise (seeded `Random(1)`); each result has length in 1..380.
  - `roundTripKeepsTone`: encode then decode 50 frames of a 1 kHz sine at amplitude 10 000. Skip the first 5 frames. The decoded RMS is within ±30 % of the input RMS (≈7 071), and the zero-crossing count over frames 5..49 is within ±5 % of 2 × 1000 × 0.9 s = 1 800.
  - `lostFrameConcealsWithoutThrowing`: after 10 sine frames, `decode(null)` returns 960 samples.
  - `corruptFrameGivesSilence`: `decode(new byte[]{(byte) 0xFF, 1, 2})` returns 960 samples, all 0, or at least no exception.
  - `wrongFrameSizeRejected`: `encode(new short[100])` throws `IllegalArgumentException`.
- [ ] **Step 2: Run** `./gradlew test --tests '*OpusMonoCodecTest'`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement.**
  - Encoder: `new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_AUDIO)`, `setBitrate(BITRATE)`, `setComplexity(COMPLEXITY)`, `setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC)`, then `encode(short[],0,960,out,0,MAX_FRAME_BYTES)`.
  - Decoder: `new OpusDecoder(48000, 1)`, `decode(data, 0, len, pcm, 0, 960, false)`; for a `null` frame, pass `null, 0, 0`.
  - Add the dependency (`implementation` + `include`) and the licence resource.
- [ ] **Step 4: Run** the same command. Expected: PASS. Then run `./gradlew build` and check that `build/libs/*.jar` contains `META-INF/jars/concentus-1.0.2.jar` and `licenses/concentus-LICENSE.txt`.
- [ ] **Step 5: Commit** "Add Concentus Opus mono codec".

### Task 2: Resampler and framer

**Files:** Create `core/audio/stream/LinearResampler.java` and `core/audio/stream/MonoFramer.java`. Test: `core/audio/stream/LinearResamplerTest.java` and `MonoFramerTest.java`.

**Interfaces:**
- Consumes: `StreamFormat.SAMPLE_RATE`, `FRAME_SAMPLES`.
- Produces:
  - `LinearResampler(int fromRate)` resamples to 48 kHz and keeps its fractional position and last sample across calls. Method: `short[] process(short[] in, int length)`. When `fromRate == 48000` it returns a copy.
  - `MonoFramer()` with `void push(short[] samples, int length)`, `@Nullable short[] poll()` (a full 960-sample frame or null) and `@Nullable short[] flush()` (the remainder padded with zeros, null if empty).
  - Helper `static short[] Pcm16.toShorts(byte[] le, int length)` added to the existing `core/audio/Pcm16.java`.

- [ ] **Step 1: Write failing tests.**
  - `resamplingKeepsFrequency`: a 1 kHz sine at 44 100 Hz, 1 s, fed in 7 uneven blocks (sizes 1000, 4410, 3, 9000, …). The output length is 48 000 ± 2 and its zero-crossing count is 2 000 ± 4.
  - `sameRateIsIdentity`: the output equals the input.
  - `upsampleFrom8k`: 8 000 samples in gives 48 000 ± 2 out.
  - `framerSplitsAndPads`: push 2 000 samples. `poll` returns two frames, then null. `flush` returns 960 samples whose last 880 are 0.
  - `toShortsLittleEndian`: `{0x01, 0x02}` gives `{0x0201}`.
- [ ] **Step 2: Run** `./gradlew test --tests '*LinearResamplerTest' --tests '*MonoFramerTest' --tests '*Pcm16Test'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use linear interpolation between consecutive input samples. The step is `fromRate / 48000.0`, and the position carries over between calls, so the block boundaries in `resamplingKeepsFrequency` leave no clicks or drift.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** "Add mono resampler and Opus framer".

### Task 3: Jitter buffer

**Files:** Create `core/audio/stream/JitterBuffer.java`. Test: `JitterBufferTest.java`.

**Interfaces:**
- Produces:
  - `JitterBuffer(int targetFrames, int maxAheadFrames)`.
  - `void offer(long firstFrame, List<byte[]> frames)`. Frame `i` has index `firstFrame + i`, and the chunk `seq` maps to `firstFrame = seq * FRAMES_PER_CHUNK`.
  - `Slot poll()`, with `sealed interface Slot` and the records `Audio(byte[] frame)`, `Lost()` and `Waiting()`.
  - `int bufferedFrames()`.
- Rules:
  1. A frame below the playout cursor (too old) or already present (duplicate) is ignored.
  2. A frame more than `maxAheadFrames` past the cursor while playing means a resync: clear and prebuffer from the newest frames.
  3. While **prebuffering**, `poll` returns `Waiting`. Playout starts once the span from the lowest to the highest buffered index is at least `targetFrames`; the cursor is then set to the **lowest buffered index**.
  4. While **playing**, `poll` returns `Audio` when the cursor frame is present. It returns `Lost` when the frame is missing but a later frame exists. When the buffer is empty, it switches back to prebuffering and returns `Waiting`. Every non-`Waiting` poll advances the cursor.
  5. At most `maxAheadFrames + targetFrames` frames are kept in memory.

- [ ] **Step 1: Write failing tests** (target 10, maxAhead 50, frames are `new byte[]{(byte) index}`):
  - `prebuffersUntilTarget`: offering chunks 0 and 1 (frames 0–9) gives `Waiting` on the first poll, then `Audio(0)` once the span reaches 10 (offer frames 0..10).
  - `reordersOutOfOrderChunks`: offering seq 2, 0, 1 gives Audio 0..14 in order.
  - `dropsDuplicatesAndTooOld`: offering seq 0 twice, then seq 0 again after polling 5, does not change the order or the count.
  - `missingFrameIsLost`: frames 0–4 and 10–14 present give Audio 0..4, then Lost ×5, then Audio 10..14.
  - `underrunReturnsToPrebufferingAndSkipsGap`: play frames 0..14 until empty (Waiting), then offer frames 100..114. The next non-Waiting poll is `Audio(100)`, not 85 `Lost` slots.
  - `farAheadFrameResyncs`: while playing at cursor 5, offering frame 500 leads to `Audio(500)` after prebuffering, and frames 6..14 are discarded.
- [ ] **Step 2: Run** `./gradlew test --tests '*JitterBufferTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** with a `TreeMap<Long, byte[]>`, a `long cursor` and a `boolean playing`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** "Add jitter buffer for relayed audio".

### Task 4: Emitter pacer and token bucket

**Files:** Create `core/audio/stream/EmitterPacer.java` and `TokenBucket.java`. Tests: `EmitterPacerTest.java` and `TokenBucketTest.java`.

**Interfaces:**
- Produces:
  - `EmitterPacer(long leadMillis)` with:
    - `long delayBeforeNextMillis(long elapsedMillis, long sentAudioMillis)`, which returns `max(0, sentAudioMillis - elapsedMillis - leadMillis)`;
    - `void reset()`.

    Time is passed in, never read inside: the emitter supplies `System.nanoTime()`.
  - `TokenBucket(long ratePerSecond, long capacity)` with `boolean tryConsume(long amount, long nowNanos)`. It starts full, refills continuously, and never exceeds `capacity`.

- [ ] **Step 1: Write failing tests.**
  - `pacerAllowsInitialLead`: with lead 2 500, elapsed 0 and sent 0..2 500, the delay is 0; with sent 2 600, the delay is 100.
  - `pacerHoldsLead`: simulate a send loop of 100 ms chunks that sleeps the returned delay (a fake clock). After 60 simulated seconds, sent − elapsed lies in [2 400, 2 600].
  - `bucketAllowsBurstThenRate`: with `TokenBucket(25_000, 40_000)` at t=0, consuming 40 000 succeeds and 1 more fails. At t=+1 s, 25 000 succeeds and 1 more fails.
  - `bucketCapsRefill`: after 10 s idle, at most 40 000 can be consumed.
- [ ] **Step 2: Run** `./gradlew test --tests '*EmitterPacerTest' --tests '*TokenBucketTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** as specified.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** "Add emitter pacing and rate limiting".

### Task 5: Session table

**Files:** Create `core/audio/stream/SessionTable.java`. Test: `SessionTableTest.java`.

**Interfaces:**
- Produces:
  - `SessionTable<K>` with `record Session<K>(int id, K key, UUID owner)` and `record Opened<K>(Session<K> session, Optional<Session<K>> replaced)`.
  - Methods:
    - `Opened<K> open(K key, UUID owner)`: ids are positive and increase, never reused within a table;
    - `Optional<Session<K>> byId(int id)`;
    - `Optional<Session<K>> byKey(K key)`;
    - `Optional<Session<K>> endById(int id)`;
    - `Optional<Session<K>> endByKey(K key)`;
    - `List<Session<K>> endByOwner(UUID owner)`;
    - `List<Session<K>> endAll()`.

- [ ] **Step 1: Write failing tests:**
  - `openCreatesSessionWithFreshId`;
  - `openReplacesExistingSession`: a second open on the same key returns `replaced` = the first, and `byId(first.id)` is empty;
  - `endByOwnerEndsOnlyTheirs`;
  - `endByIdOnUnknownIsEmpty`;
  - `idsNotReused`: open, end, open again gives a different id.
- [ ] **Step 2: Run** `./gradlew test --tests '*SessionTableTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** with two `HashMap`s (id → session, key → session).
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** "Add stream session table".

### Task 6: Stream playout

**Files:** Create `core/audio/stream/StreamPlayout.java`. Test: `StreamPlayoutTest.java`.

**Interfaces:**
- Consumes: `JitterBuffer` (Task 3), `OpusMonoDecoder` (Task 1), `StreamFormat`.
- Produces:
  - `StreamPlayout()`.
  - `synchronized void offer(int seq, List<byte[]> frames)`.
  - `synchronized byte[] nextBlock()`, which always returns exactly `CHUNK_SAMPLES * 2` bytes of 16-bit LE PCM. Each `Audio` slot is decoded, each `Lost` slot goes through `decode(null)`, and each `Waiting` slot gives silence.
  - `synchronized boolean idleFor(long millis, long nowMillis)`: true when no offer arrived during the last `millis`.
  - Called from the network thread (offer) and from the sound engine thread (nextBlock), hence `synchronized`.

- [ ] **Step 1: Write failing tests.**
  - `playoutAlwaysReturnsFullBlock`: on an empty playout, `nextBlock().length == 9_600`, all zeros.
  - `playsEncodedToneAfterPrebuffer`: offer 12 chunks of encoded 1 kHz sine (seq 0..11). After the prebuffer, the blocks have RMS > 3 000.
  - `idleDetection`.
- [ ] **Step 2: Run** `./gradlew test --tests '*StreamPlayoutTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** "Add stream playout over jitter buffer".

### Task 7: Payloads, server sessions and relay

**Files:**
- Create in `network/payload/`. Each payload is a record implementing `CustomPacketPayload`, with `TYPE` and `StreamCodec<ByteBuf, T> CODEC`:
  - `StreamOpenPayload(BlockPos pos)`: C→S;
  - `StreamOrderPayload(int sessionId, BlockPos pos, boolean start)`: S→C to the owner. `start = true` grants the session; `false` stops it (replaced, speaker gone or off);
  - `AudioChunkPayload(int sessionId, int seq, List<byte[]> frames)`: C→S. The codec is `ByteBufCodecs.byteArray(MAX_FRAME_BYTES).apply(ByteBufCodecs.list(FRAMES_PER_CHUNK))`;
  - `RelayedChunkPayload(int sessionId, BlockPos pos, int seq, List<byte[]> frames)`: S→C, same frame codec;
  - `StreamClosePayload(int sessionId)`: C→S;
  - `SessionEndPayload(int sessionId)`: S→C.
- Create:
  - `network/ModPayloads.java` with `init()`: registers the C→S payloads with `serverboundPlay()` and the S→C ones with `clientboundPlay()`. Call it from `RaveCraft.onInitialize`.
  - `world/audio/StreamSessions.java`.
  - `network/AudioRelay.java`.
- Modify:
  - `PortableSpeakerBlockEntity`: `setRemoved()` (server side) ends the session at its position. This covers breaking and chunk unload. `togglePower()` ends it when turning off.
  - `RaveCraft.java`: calls `ModPayloads.init()` and `AudioRelay.init()`.
  - `src/gametest/.../PortableSpeakerGameTest.java`, or a new `AudioRelayGameTest` declared in the gametest `fabric.mod.json`.
- Test: `src/test/.../network/payload/AudioChunkPayloadTest.java`, which round-trips the codec through `Unpooled.buffer()`. If it fails at class init because the Minecraft bootstrap is missing, move the test into the GameTest class.

**Interfaces:**
- Consumes: `SessionTable` (Task 5), `TokenBucket` (Task 4), `StreamFormat`.
- Produces:
  - `record SpeakerKey(ResourceKey<Level> dimension, BlockPos pos)`.
  - `StreamSessions` (static, server thread only):
    - `Optional<Session<SpeakerKey>> open(ServerPlayer player, BlockPos pos)`: validates, notifies the replaced owner and its listeners, then sends `StreamOrder(start=true)` to the new owner;
    - `void endAt(ServerLevel level, BlockPos pos)`;
    - `void endById(int id, ServerPlayer requester)`: owner only;
    - `void endByOwner(UUID owner)`;
    - `void clear()`.

    Each end sends `StreamOrder(start=false)` to the owner if online, and `SessionEnd` to the current listeners.
  - `AudioRelay.listeners(ServerLevel level, BlockPos pos, UUID owner, Collection<ServerPlayer> candidates) → List<ServerPlayer>` keeps the candidates within `RELAY_RANGE` of the block centre, in the same dimension, other than the owner. Production passes `PlayerLookup.tracking(level, pos)`.
  - Validation for `open`:
    - the block is `PORTABLE_SPEAKER` with `POWERED = true`;
    - `player.isWithinBlockInteractionRange(pos, 1.0)`.

    A rejected open sends nothing.
  - On `AudioChunk`:
    - drop it if the session is unknown or the sender is not the owner;
    - drop it if `TokenBucket.tryConsume(total frame bytes, System.nanoTime())` fails. There is one bucket per player UUID, removed on disconnect;
    - otherwise send `RelayedChunk` to each listener.
  - Registers `ServerPlayConnectionEvents.DISCONNECT` → `endByOwner`, and `ServerLifecycleEvents.SERVER_STOPPING` → `clear`.

- [ ] **Step 1: Write failing tests.**
  - JUnit `AudioChunkPayloadTest`:
    - round trip of 5 frames;
    - encoding 6 frames throws (the cap is enforced on send);
    - decoding a buffer with a 381-byte frame throws.
  - GameTests (mock players via `helper.makeMockPlayer(GameType.SURVIVAL)`, speaker powered with the existing `powerOn` helper):
    - `openInRangeCreatesSession`;
    - `openOnUnpoweredSpeakerRejected`;
    - `openOutOfReachRejected`: the player is moved 10 blocks away;
    - `secondOpenReplacesFirstOwner`;
    - `breakingSpeakerEndsSession`: `helper.destroyBlock(pos)` leaves `byKey` empty;
    - `poweringOffEndsSession`;
    - `relayTargetsOnlyPlayersInRange`: players at 5 and 30 blocks plus the owner give only the 5-block player.
- [ ] **Step 2: Run** `./gradlew test --tests '*AudioChunkPayloadTest'` and `./gradlew runGameTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Expose `StreamSessions.table()` (package-private or `@VisibleForTesting`) so GameTests can assert on `byKey`.
- [ ] **Step 4: Run** `./gradlew build`. Expected: PASS (all JUnit and GameTests).
- [ ] **Step 5: Commit** "Relay speaker audio chunks through the server".

### Task 8: Client receiver

**Files:**
- Create `client/audio/stream/NetworkAudioStream.java`, `ReceivedStreams.java` and `ClientRelay.java`.
- Modify `client/audio/SpeakerSoundInstance.java`:
  - constructor `SpeakerSoundInstance(BlockPos pos, Supplier<AudioStream> stream, BooleanSupplier alive)`;
  - `getAudioStream` returns `CompletableFuture.completedFuture(stream.get())`; file opening moves to the emitter;
  - `tick` also stops when `!alive.getAsBoolean()`.
- Modify `RaveCraftClient` to call `ClientRelay.init()`.
- Tests: core behaviour is already covered by Task 6. The client classes have no JUnit test; they are covered by the in-game check in Task 10.

**Interfaces:**
- Consumes: `StreamPlayout` (Task 6), the payloads (Task 7).
- Produces:
  - `NetworkAudioStream(StreamPlayout)`:
    - `getFormat()` is `new AudioFormat(48000, 16, 1, true, false)`;
    - `read(int ignored)` returns a direct buffer of `playout.nextBlock()` (100 ms). Vanilla queues 4 buffers, so latency stays ~400 ms after the jitter buffer.
  - `ReceivedStreams` (client thread):
    - `void offer(int sessionId, BlockPos pos, int seq, List<byte[]> frames)`: creates the playout and plays a new `SpeakerSoundInstance` on first sight, then offers;
    - `void end(int sessionId)`;
    - `void endAll()` (on DISCONNECT);
    - `tick`: ends streams idle for 5 000 ms.
  - `ClientRelay.init()` registers the `RelayedChunk` → `ReceivedStreams.offer` and `SessionEnd` → `ReceivedStreams.end` receivers, plus `StreamOrder` → `LocalPlayback.onOrder` (Task 9).

- [ ] **Step 1: Implement** the three classes and the `SpeakerSoundInstance` change. Temporarily keep `LocalPlayback` compiling by passing a file-backed `AudioStream` supplier; Task 9 replaces it.
- [ ] **Step 2: Run** `./gradlew build`. Expected: PASS.
- [ ] **Step 3: Commit** "Play relayed audio streams on the client".

### Task 9: Client emitter and order-driven local playback

**Files:**
- Create:
  - `client/audio/stream/OggPcmDecoder.java`: `PcmDecoder` over `JOrbisAudioStream`. It reads `ByteBuffer`s and copies them into the caller's array.
  - `client/audio/stream/StreamEmitter.java`.
- Modify:
  - `client/audio/LocalPlayback.java`.
  - `client/screen/SpeakerBrowserScreen.java`: only if `nowPlaying` changes type; keep `Optional<Path>`.
- Delete: `client/audio/MonoPcmAudioStream.java` and `MonoOggAudioStream.java` once unused.

**Interfaces:**
- Consumes: `Decoders.open` (step 2), `Pcm16.downmixToMono` / `toShorts`, `LinearResampler`, `MonoFramer`, `OpusMonoEncoder`, `EmitterPacer`, `StreamFormat`, `ReceivedStreams.offer` (loopback), and the payloads.
- Produces:
  - `StreamEmitter(int sessionId, BlockPos pos, Path track, Runnable onFinished, Consumer<Throwable> onFailed)`, with `start()` (a daemon thread named `RaveCraft emitter #<id>`) and `cancel()`.
  - The emitter loop:
    1. open the decoder, or `OggPcmDecoder` for ogg/oga;
    2. read a block, downmix it, resample it and push it to the framer;
    3. for each 5 encoded frames, wait `pacer.delayBeforeNextMillis` (sleep, interruptible by `cancel`);
    4. hand off to the client thread with `Minecraft.getInstance().execute`. There, `ClientPlayNetworking.send(new AudioChunkPayload(...))` runs and `ReceivedStreams.offer(sessionId, pos, seq, frames)` provides the loopback;
    5. at EOF, flush the framer, send the last chunk, then call `onFinished` on the client thread.

    If the file fails to open or decode, log `"Cannot play {}: {}"` with the file name and call `onFailed`.
  - `LocalPlayback`. The public API `play`, `stop`, `stopAll` and `nowPlaying` is unchanged.
    - `play(pos, tracks)` stores the queue and sends `StreamOpenPayload(pos)` for the first track.
    - `onOrder(StreamOrderPayload)`:
      - `start` with a pending queue at `pos` starts a `StreamEmitter` for the current track;
      - `!start` cancels the emitter and clears that speaker's queue.
    - When an emitter finishes or fails, `StreamClosePayload(sessionId)` is sent, followed by the next track's `StreamOpenPayload`; when the queue is empty, the session is removed.
    - `stop(pos)` cancels and sends `StreamClose`.
    - DISCONNECT calls `stopAll()` without sending.

- [ ] **Step 1: Implement** the classes above, then delete the two now-unused stream classes.
- [ ] **Step 2: Run** `./gradlew build`. Expected: PASS.
- [ ] **Step 3: Check in `runClient`, in single player.**
  - Play an MP3 at 44.1 kHz, a FLAC, a WAV and an OGG: each plays at the right pitch, spatialised.
  - Stop works. Breaking the speaker and turning it off stop the sound within about a second.
  - A folder plays track after track.
- [ ] **Step 4: Commit** "Stream local tracks through the relay".

### Task 10: Docs and multiplayer check

**Files:** Modify `docs/SPEC.md` (step 3a line, marked "vérifié en jeu" once checked) and the PR description (the template, in French).

- [ ] **Step 1: Run** `./gradlew runDatagen && ./gradlew build`. Expected: green, with no generated changes.
- [ ] **Step 2: Hand over the multiplayer check to the user.** The setup is a local dedicated server (`runServer`, `online-mode=false`, EULA accepted by the user) and two `runClient` instances with different usernames. Things to check:
  - B hears what A plays and stops hearing it when walking beyond ~16 blocks; it resumes when coming back.
  - B pressing Play on the same speaker replaces A's track.
  - A disconnecting stops the sound for B.
  - A track at 44.1 kHz plays at the right speed for B.
- [ ] **Step 3: Update** `docs/SPEC.md`, then commit "Update spec progress for audio relay".
