# Acoustic Render (step 4a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the vanilla `SpeakerSoundInstance` output with RaveCraft's own OpenAL+EFX renderer: realistic distance law, device profile, directivity, air absorption, bass shelf, source limit, relay radius following the profile range, server acoustic settings file and three player options in Music & Sounds.

**Architecture:** All acoustics math lives in `core.acoustics` (pure Java, JUnit). The server loads `config/ravecraft-server.json`, relays chunks within `range × volume + 8` blocks and sends the settings on join. The client feeds every received stream into `AcousticRenderer`, which owns a pool of OpenAL streaming sources on a dedicated thread, picks the N loudest streams, keeps the others virtual, and follows the vanilla sound engine's lifecycle through a `SoundEngine` mixin.

**Tech Stack:** Java 25, Fabric 0.166.2+26.3, Mojang names, LWJGL OpenAL 3.4.3 (`AL10`, `ALC10`, `EXTEfx`, shipped with Minecraft), Gson, JUnit 5, Fabric GameTest.

**Spec:** [docs/superpowers/specs/2026-11-17-acoustic-engine-design.md](../specs/2026-11-17-acoustic-engine-design.md) (parent: `2026-11-17-audio-core-design.md` §4).

## Global Constraints

- Prefix every Gradle command with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`.
- `core` is pure Java (no `net.minecraft` import). `src/main` never imports `net.minecraft.client` or `org.lwjgl.openal`.
- Code, comments and commits in English; user-facing strings in `en_us.json` **and** `fr_fr.json`.
- No `Co-Authored-By` / "Generated with Claude Code" lines. Never `git add art/proposals/`. Never push without the user's OK.
- Branch `feat/acoustic-render`, created from `docs/acoustic-engine-spec` (the spec and this plan travel in the same PR).
- Portable profile: range 32, bass shelf −6 dB at 150 Hz, omnidirectional. Defaults: `rolloff` 1, `fadeFraction` 0.2, `airAbsorptionDbPerBlock` 0.05. Relay margin 8 blocks. Player options: volume 0–100 % (default 100 %), simultaneous speakers 2–32 (default 8), acoustic effects on/off (default on).
- Every task ends with `./gradlew build` green.

## Decisions taken while planning (spec §8 "points à vérifier", checked in the 26.3 sources)

- **OpenAL thread:** `Library.init` calls `alcMakeContextCurrent` (process-wide) and vanilla already issues AL calls from both the render thread and the `Sound engine` thread, so a dedicated RaveCraft thread may call AL directly.
- **Reload hook:** `SoundEngine.reload()` = `destroy()` + `loadLibrary()`; `destroy()` and `emergencyShutdown()` call `library.cleanup()`, which destroys the context. Mixin: stop the renderer at `destroy` HEAD and `emergencyShutdown` HEAD, start it at `loadLibrary` TAIL when `loaded` is true.
- **Pause:** follow vanilla — `SoundEngine.pauseAllExcept` HEAD → renderer pause, `SoundEngine.resume` HEAD → renderer resume.
- **Listener:** vanilla sets the global AL listener (`updateSource(Camera)` → `Listener.setTransform`); our sources share it. `updateSource` TAIL publishes `camera.position()` to the renderer for gain computation.
- **Source budget:** vanilla reserves `ALC_MONO_SOURCES` in counting pools but only generates sources on demand. If `alGenSources` fails, the pool keeps the sources it got and logs one warning.
- **EFX:** detect with `ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext()), "ALC_EXT_EFX")`.
- **Options storage:** `Options.processOptions(FieldAccess)` is private and `FieldAccess`/`OptionAccess` are private interfaces. A class tweaker (`classTweaker v1 official`, same format as Fabric API's) makes `net/minecraft/client/Options$FieldAccess` and `net/minecraft/client/Options$OptionAccess` accessible; the mixin injects at `processOptions` TAIL and calls `access.process(name, option)`.
- **Screen:** inject at `SoundOptionsScreen.addOptions()` TAIL. `list` is `protected @Nullable OptionsList list` in `OptionsSubScreen`, so declare `@Mixin(SoundOptionsScreen.class) abstract class SoundOptionsScreenMixin extends OptionsSubScreen` (with the matching constructor) and use `this.list` directly.
- **Player volume:** `options.getFinalSoundSourceVolume(SoundSource.RECORDS)` already multiplies by master.
- **Spec deviation:** the GameTest "settings are sent on join" is not feasible (GameTest mock players have no connection, `Net.send` skips them). Covered instead by the payload codec JUnit test and the in-game check.

## Review Focus

1. Sound engine reload (F3+T, device change) while streams play: sources must be deleted **before** the context is destroyed, and streams must resume without restarting the track — no AL error spam, no crash on a dead context.
2. Stream promoted from virtual to real: it must start at the current playout position (virtual streams kept consuming), not replay stale audio or burst.
3. Lowering "Simultaneous speakers" below the active count while playing: extra sources released at once, no leaked AL sources/buffers (count must stay ≤ option).
4. Volume 0 on a speaker: no listener receives the stream (relay radius 0 + 8 margin must not leak — radius is 0 when volume is 0) and the client never gives it a source.
5. Owner's loopback stream (own speaker) goes through the same path: the owner hears their own speaker with the same distance law.

Tests pinning these: Task 3 `selectorNeverGivesSourceToSilentStream`, `selectorShrinksWhenMaxDrops`; Task 3 `pacerCatchesUpAfterStall`; Task 5 `relayRadiusIsZeroAtVolumeZero`; items 1, 2 and 5 are also in-game checks of Task 8.

---

### Task 1: Propagation and directivity

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/Directivity.java`, `DeviceProfile.java`, `Propagation.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/PropagationTest.java`, `DirectivityTest.java`

**Interfaces:**
- Produces:
  - `record Directivity(double halfAngleDeg, double rearGainDb)`; `static final Directivity OMNI = new Directivity(180, 0)`; `double gain(double cosAngle)`.
  - `record DeviceProfile(double range, double bassCutDb, double bassCutHz, Directivity directivity)`; `static final DeviceProfile PORTABLE = new DeviceProfile(32, -6, 150, Directivity.OMNI)`.
  - `final class Propagation` (static): `double RELAY_MARGIN = 8.0`; `double range(DeviceProfile p, double volume)`; `double distanceGain(double rolloff, double distance)`; `double fade(double distance, double range, double fadeFraction)`; `double gain(double rolloff, double fadeFraction, DeviceProfile p, double volume, double distance, double cosAngle)`; `double highFrequencyGain(double dbPerBlock, double distance)`; `double relayRadius(DeviceProfile p, double volume)`.

- [ ] **Step 1: Create the branch and write the failing tests**

```bash
git switch docs/acoustic-engine-spec && git switch -c feat/acoustic-render
```

`PropagationTest` (delta 1e-9 unless stated):
- `inverseDistanceLaw`: `distanceGain(1, 2) == 0.5`, `distanceGain(1, 4) == 0.25`.
- `fullGainInsideOneBlock`: `distanceGain(1, 0.3) == 1`, `distanceGain(2, 0.0) == 1`.
- `rolloffExponent`: `distanceGain(0.5, 4) == 0.5`, `distanceGain(2, 2) == 0.25`.
- `rangeScalesWithVolume`: `range(PORTABLE, 1) == 32`, `range(PORTABLE, 0.5) == 16`, `range(PORTABLE, 0) == 0`.
- `fadeStartsAtEightyPercent`: `fade(25.6, 32, 0.2) == 1`, `fade(20, 32, 0.2) == 1`, `fade(28.8, 32, 0.2) == 0.5` (1e-6), `fade(32, 32, 0.2) == 0`, `fade(40, 32, 0.2) == 0`.
- `gainZeroBeyondRangeAndAtVolumeZero`: `gain(1, 0.2, PORTABLE, 1, 33, 1) == 0`; `gain(1, 0.2, PORTABLE, 0, 0.5, 1) == 0`.
- `gainCombinesVolumeAndDistance`: `gain(1, 0.2, PORTABLE, 0.5, 4, 1) == 0.125` (range 16, 4 < 12.8 so no fade).
- `airAbsorption`: `highFrequencyGain(0.05, 0) == 1`; `20*log10(highFrequencyGain(0.05, 32)) == -1.6` (1e-9); `highFrequencyGain(1, 1000) == 0.05` (floor).
- `relayRadius`: `relayRadius(PORTABLE, 1) == 40`, `relayRadius(PORTABLE, 0.5) == 24`, `relayRadiusIsZeroAtVolumeZero`: `relayRadius(PORTABLE, 0) == 0`.

`DirectivityTest`:
- `omniIsFlat`: `OMNI.gain(1) == 1`, `OMNI.gain(0) == 1`, `OMNI.gain(-1) == 1`.
- `coneFullInsideHalfAngle`: `new Directivity(45, -12).gain(cos(30°)) == 1`.
- `coneRearFloor`: `20*log10(new Directivity(45, -12).gain(-1)) == -12` (1e-9).
- `coneDecreasesOffAxis`: gains at 60°, 90°, 135° strictly decreasing and all in (rear, 1).

- [ ] **Step 2: Run the tests — expect compilation failure**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.*"` → Expected: FAIL (classes missing).

- [ ] **Step 3: Implement**

- `distanceGain = 1 / pow(max(d, 1), rolloff)`.
- `fade`: `start = (1 - f) * range`; `d <= start → 1`; `d >= range → 0`; else `t = (d - start) / (f * range)`, `0.5 * (1 + cos(PI * t))`. `range <= 0 → 0`.
- `gain = volume * distanceGain * fade(d, range(p, volume), f) * p.directivity().gain(cosAngle)`.
- `highFrequencyGain = max(0.05, pow(10, -dbPerBlock * d / 20))`.
- `relayRadius = volume <= 0 ? 0 : range(p, volume) + RELAY_MARGIN`.
- `Directivity.gain`: `angle = toDegrees(acos(clamp(cos, -1, 1)))`; `angle <= half → 1`; else `t = (angle - half) / (180 - half)`, `db = rearGainDb * (1 - cos(PI * t)) / 2`, return `pow(10, db / 20)`.

- [ ] **Step 4: Run the tests** → Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Add acoustic propagation and directivity model"`

---

### Task 2: Bass shelf equalizer

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/Equalizer.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/EqualizerTest.java`

**Interfaces:**
- Produces: `final class Equalizer`: `Equalizer(double sampleRate, double shelfHz, double gainDb)`; `void process(float[] samples, int count)` (in place, mono, keeps state between calls, output clamped to [−1, 1]).

- [ ] **Step 1: Write the failing tests** (helper: RMS in dB of a sine of amplitude 0.5 at 48 kHz, 1 s, measured on the last 0.5 s)
- `bassCutAt60Hz`: `new Equalizer(48000, 150, -6)` on 60 Hz → level change in [−6.5, −4.5] dB.
- `midUntouchedAt1kHz`: same EQ on 1 kHz → change within ±0.5 dB.
- `zeroGainIsIdentity`: `new Equalizer(48000, 150, 0)` → every output sample equals input (1e-6).
- `stateCarriesAcrossBlocks`: processing 4800 samples in one call vs in 48 blocks of 100 gives identical output (1e-6).
- `outputClamped`: a full-scale square wave (±1) through `new Equalizer(48000, 150, 6)` → all samples in [−1, 1].

- [ ] **Step 2: Run** `./gradlew test --tests "*EqualizerTest"` → Expected: FAIL (class missing).

- [ ] **Step 3: Implement** an RBJ low-shelf biquad, Q = 0.707, transposed direct form II in `double`:

```
A = 10^(gainDb/40); w0 = 2π·shelfHz/sampleRate; c = cos w0; α = sin w0 / (2·0.707); k = 2·√A·α
b0 = A((A+1) − (A−1)c + k)   b1 = 2A((A−1) − (A+1)c)   b2 = A((A+1) − (A−1)c − k)
a0 = (A+1) + (A−1)c + k      a1 = −2((A−1) + (A+1)c)   a2 = (A+1) + (A−1)c − k
```
Normalize by `a0`. If `gainDb == 0`, `process` returns without touching the samples.

- [ ] **Step 4: Run** → Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Add bass shelf equalizer"`

---

### Task 3: Source selection and virtual pacing

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/SourceSelector.java`, `VirtualPacer.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/SourceSelectorTest.java`, `VirtualPacerTest.java`

**Interfaces:**
- Produces:
  - `SourceSelector` (static): `double HYSTERESIS = Math.pow(10, 1 / 20.0)`; `Set<Integer> select(Map<Integer, Double> gains, Set<Integer> active, int max)` — session ids that get a source.
  - `VirtualPacer`: `VirtualPacer(long startMs, long blockMs)`; `int due(long nowMs)` — whole blocks elapsed since the last call (remainder carried), at most `MAX_CATCH_UP = 10` per call; `void reset(long nowMs)`.

- [ ] **Step 1: Write the failing tests**

`SourceSelectorTest`:
- `keepsLoudest`: gains `{1:0.1, 2:0.5, 3:0.3}`, active `{}`, max 2 → `{2, 3}`.
- `tieBrokenByLowestId`: `{5:0.2, 4:0.2}`, max 1 → `{4}`.
- `activeStreamKeepsSourceWithinOneDecibel`: `{1:0.50, 2:0.55}`, active `{1}`, max 1 → `{1}` (0.55 < 0.5·1.122).
- `louderByMoreThanOneDecibelTakesOver`: `{1:0.50, 2:0.60}`, active `{1}`, max 1 → `{2}`.
- `selectorShrinksWhenMaxDrops`: 5 streams all active, max 2 → result size 2, the 2 loudest.
- `selectorNeverGivesSourceToSilentStream`: `{1:0.0, 2:0.3}`, max 8 → `{2}`.

`VirtualPacerTest` (blockMs 100):
- `countsWholeBlocks`: start 0 → `due(250) == 2`, `due(300) == 1`, `due(350) == 0`.
- `pacerCatchesUpAfterStall`: start 0 → `due(5000) == 10` (capped), then `due(5000) == 0`.
- `resetRestartsClock`: start 0, `reset(1000)`, `due(1150) == 1`.

- [ ] **Step 2: Run** `./gradlew test --tests "com.ravecraft.mod.core.acoustics.*"` → Expected: FAIL.

- [ ] **Step 3: Implement** `select`: score = `gain × (active.contains(id) ? HYSTERESIS : 1)`; drop gain ≤ 0; sort by score desc then id asc; take `max`. `due`: after a capped call, drop the extra backlog (move the clock to `now`).

- [ ] **Step 4: Run** → Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Add source selection and virtual stream pacing"`

---

### Task 4: Acoustic settings — file, payload, server load, client copy

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/AcousticSettings.java`
- Create: `src/main/java/com/ravecraft/mod/config/AcousticSettingsJson.java`, `src/main/java/com/ravecraft/mod/config/AcousticConfig.java`
- Create: `src/main/java/com/ravecraft/mod/network/payload/AcousticSettingsPayload.java`
- Create: `src/client/java/com/ravecraft/mod/client/audio/render/AcousticSettingsClient.java`
- Modify: `src/main/java/com/ravecraft/mod/network/ModPayloads.java` (register clientbound), `src/main/java/com/ravecraft/mod/RaveCraft.java` (`AcousticConfig.init()`), the client initializer (`AcousticSettingsClient.register()`)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/AcousticSettingsTest.java`, `src/test/java/com/ravecraft/mod/config/AcousticSettingsJsonTest.java`, `src/test/java/com/ravecraft/mod/network/payload/AcousticSettingsPayloadTest.java`

**Interfaces:**
- Consumes: `DeviceProfile`, `Directivity` (Task 1).
- Produces:
  - `record AcousticSettings(double rolloff, double fadeFraction, double airAbsorptionDbPerBlock, DeviceProfile portable)`; `static final AcousticSettings DEFAULT` (1, 0.2, 0.05, `PORTABLE`); `AcousticSettings clamped(Consumer<String> warn)` — bounds `rolloff` [0.25, 3], `fadeFraction` [0.05, 0.5], `airAbsorptionDbPerBlock` [0, 1], `range` [4, 128], `bassCutDb` [−24, 0], `bassCutHz` [40, 500]; one warning per clamped field naming it.
  - `AcousticSettingsJson`: `static AcousticSettings read(Reader in, Consumer<String> warn)` (missing field → default; unparsable → `DEFAULT` + warning; result is `clamped`), `static void write(AcousticSettings s, Writer out)`. Shape: `{"rolloff":1.0,"fadeFraction":0.2,"airAbsorptionDbPerBlock":0.05,"profiles":{"portable":{"range":32.0,"bassCutDb":-6.0,"bassCutHz":150.0}}}`.
  - `AcousticConfig` (server): `static void init()` — on `ServerLifecycleEvents.SERVER_STARTING` load `FabricLoader.getInstance().getConfigDir().resolve("ravecraft-server.json")` (write defaults if missing), on `ServerPlayConnectionEvents.JOIN` `Net.send(player, new AcousticSettingsPayload(get()))`; `static AcousticSettings get()`; `static void set(AcousticSettings s)` (tests).
  - `record AcousticSettingsPayload(AcousticSettings settings)`, id `ravecraft:acoustic_settings`, `CODEC` of the six doubles (directivity is not sent: always `OMNI` for the portable).
  - `AcousticSettingsClient`: `static void register()` (receiver + reset to `DEFAULT` on `DISCONNECT`), `static AcousticSettings get()` (volatile, read from the audio thread).

- [ ] **Step 1: Write the failing tests**
- `AcousticSettingsTest.defaultsUntouchedByClamp` (`DEFAULT.clamped(w) == DEFAULT`, no warning); `clampsEveryField` (rolloff 10 → 3, fade 0 → 0.05, absorption −1 → 0, range 1000 → 128, bassCutDb 5 → 0, bassCutHz 1 → 40; 6 warnings).
- `AcousticSettingsJsonTest.roundTrip` (write `DEFAULT`, read back equal); `missingFieldsUseDefaults` (`{"rolloff":0.5}` → rolloff 0.5, rest default); `corruptFileGivesDefaults` (`"{not json"` → `DEFAULT`, one warning); `outOfRangeIsClamped` (`{"profiles":{"portable":{"range":999}}}` → range 128).
- `AcousticSettingsPayloadTest.codecRoundTrip` — same pattern as `QueuePayloadsTest` with a non-default value in each field.

- [ ] **Step 2: Run** `./gradlew test` → Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** the classes above; translate the unknown/invalid cases as specified; log warnings with the mod logger prefixed `ravecraft-server.json:`.
- [ ] **Step 4: Run** `./gradlew build` → Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add server acoustic settings file and sync"`

---

### Task 5: Relay radius follows the profile range

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/network/AudioRelay.java:69-85`, `src/main/java/com/ravecraft/mod/core/audio/stream/StreamFormat.java:26-27` (delete `RELAY_RANGE`)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/AudioRelayGameTest.java`

**Interfaces:**
- Consumes: `Propagation.relayRadius`, `AcousticConfig.get()`.
- Produces: `AudioRelay.listeners(ServerLevel level, BlockPos pos, UUID owner, Collection<ServerPlayer> candidates, double radius)`; `static double relayRadius(ServerLevel level, BlockPos pos)` = `Propagation.relayRadius(AcousticConfig.get().portable(), volume / 100.0)` where volume comes from the `PortableSpeakerBlockEntity` at `pos` (0 when absent or unpowered).

- [ ] **Step 1: Write/adjust the failing GameTests** in `AudioRelayGameTest`:
- Update `relayTargetsOnlyPlayersInRange` to pass `AudioRelay.relayRadius(level, pos)` with the speaker at volume 100 and players at 5 (in) and 45 (out).
- `fullVolumeReachesThirtyBlocks`: speaker volume 100, player at 30 → listed.
- `halfVolumeDoesNotReachThirtyBlocks`: volume 50, player at 30 → not listed.
- `volumeChangeAppliesAtOnce`: volume 50 → not listed; set volume 100 on the block entity → listed.
- `serverRangeSettingApplies`: `AcousticConfig.set(` DEFAULT with portable range 64 `)`, volume 50, player at 30 → listed; restore `DEFAULT` in a `finally`.
- `relayRadiusIsZeroAtVolumeZero`: volume 0 → `relayRadius == 0`, player at 1 → not listed.

(Players farther than the test structure: place the speaker at one end of a structure long enough, or use `h.absolutePos` with mock players positioned by `setPos` — follow the existing `playerAt` helper, extending it if it caps the distance.)

- [ ] **Step 2: Run** `./gradlew runGameTest` → Expected: FAIL (signature).
- [ ] **Step 3: Implement** the radius; `relay` computes it once per chunk.
- [ ] **Step 4: Run** `./gradlew build` → Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Scale relay radius with speaker volume and profile range"`

---

### Task 6: Player options in Music & Sounds

**Files:**
- Create: `src/main/resources/ravecraft.classtweaker`, `src/client/java/com/ravecraft/mod/client/options/RaveCraftOptions.java`, `src/client/java/com/ravecraft/mod/client/mixin/OptionsMixin.java`, `src/client/java/com/ravecraft/mod/client/mixin/SoundOptionsScreenMixin.java`
- Modify: `src/main/resources/fabric.mod.json` (`"accessWidener": "ravecraft.classtweaker"`), `build.gradle` (`loom { accessWidenerPath = file("src/main/resources/ravecraft.classtweaker") }`), `src/client/resources/ravecraft.client.mixins.json`, `assets/ravecraft/lang/en_us.json`, `fr_fr.json`

**Interfaces:**
- Produces: `RaveCraftOptions` (static): `OptionInstance<Double> volume()` (UnitDouble, default 1.0, label "RaveCraft: 100%" via `options.percent_value`), `OptionInstance<Integer> maxSources()` (`IntRange(2, 32)`, default 8, `Options::genericValueLabel`), `OptionInstance<Boolean> acousticEffects()` (`createBoolean`, default true); `void process(Options.FieldAccess access)` → `access.process("ravecraft_volume", volume)`, `"ravecraft_maxSources"`, `"ravecraft_efx"`.
- Class tweaker content:
```
classTweaker	v1	official

accessible	class	net/minecraft/client/Options$FieldAccess
accessible	class	net/minecraft/client/Options$OptionAccess
```
- Lang keys: `options.ravecraft.volume` "RaveCraft" / "RaveCraft"; `options.ravecraft.maxSources` "Simultaneous speakers" / "Enceintes simultanées"; `options.ravecraft.acousticEffects` "Acoustic effects" / "Effets acoustiques"; tooltip `options.ravecraft.acousticEffects.tooltip` "Air absorption filter. Turn off if your audio driver has trouble." / "Filtre d'absorption de l'air. À désactiver si ton pilote audio pose problème."

- [ ] **Step 1:** Add the class tweaker, the Loom and `fabric.mod.json` entries; run `./gradlew build` → Expected: PASS (if Loom rejects the class tweaker format, ledger a ruling and fall back to the `accessWidener\tv2\tnamed` header with the same two lines).
- [ ] **Step 2:** Implement `RaveCraftOptions`, `OptionsMixin` (`@Inject(method = "processOptions", at = @At("TAIL"))`), `SoundOptionsScreenMixin` (`addOptions` TAIL: `this.list.addSmall(volume, maxSources)` then `this.list.addSmall(acousticEffects)`), register both mixins, add translations.
- [ ] **Step 3:** `./gradlew build` → Expected: PASS. Then `./gradlew runClient`: the three rows appear at the bottom of Options › Music & Sounds; change them, quit, relaunch → values kept; `run/options.txt` contains `ravecraft_volume`, `ravecraft_maxSources`, `ravecraft_efx`.
- [ ] **Step 4: Commit** — `git commit -m "Add RaveCraft sound options"`

---

### Task 7: OpenAL renderer replaces the vanilla sound instance

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/audio/render/AlSource.java`, `Efx.java`, `RenderStream.java`, `AcousticRenderer.java`, `src/client/java/com/ravecraft/mod/client/mixin/SoundEngineMixin.java`
- Modify: `src/client/java/com/ravecraft/mod/client/audio/stream/ReceivedStreams.java`, `ravecraft.client.mixins.json`, the client initializer (`AcousticRenderer.register()`)
- Delete: `src/client/java/com/ravecraft/mod/client/audio/SpeakerSoundInstance.java`, `src/client/java/com/ravecraft/mod/client/audio/stream/NetworkAudioStream.java` (move `isPoweredSpeaker` to `RenderStream`)

**Interfaces:**
- Consumes: Tasks 1–4, 6 (`Propagation`, `Equalizer`, `SourceSelector`, `VirtualPacer`, `AcousticSettingsClient.get()`, `RaveCraftOptions`), `StreamPlayout.nextBlock()` (100 ms, 16-bit LE mono 48 kHz), `StreamPlayout.TAIL_BLOCKS` (4).
- Produces:
  - `Efx`: `static boolean detect()`; `static int createLowpass()`; `static void setHighGain(int filter, float gainHf)`; `static void delete(int filter)`.
  - `AlSource`: `static AlSource create(boolean efx)` (null when `alGenSources` fails); `void reset()` (stop, unqueue, rewind); `boolean wantsBlock()` (fewer than 4 queued after unqueuing processed); `void queue(ByteBuffer pcm)`; `void place(double x, double y, double z)`; `void gain(float g)`; `void highGain(float g)`; `void pause()`; `void resume()`; `void delete()`. Each source: `AL_SOURCE_RELATIVE` false, `AL_ROLLOFF_FACTOR` 0 (no OpenAL attenuation), `AL_DISTANCE_MODEL` `AL_NONE` per source.
  - `RenderStream`: session id, `BlockPos pos`, `StreamPlayout playout`, `Equalizer eq`, `VirtualPacer pacer`, `volatile` snapshot fields `volume` (0–1), `powered`, `facing` (unit vector) set on the client thread; `ByteBuffer nextProcessedBlock()` (playout → float → `eq` → 16-bit direct buffer).
  - `AcousticRenderer` (static facade): `register()`; `attach(int sessionId, BlockPos pos, StreamPlayout playout)`; `detach(int sessionId)`; `detachAll()`; mixin hooks `onEngineStarted()`, `onEngineStopping()`, `onPause()`, `onResume()`, `onListenerMoved(Vec3 camera)`.

- [ ] **Step 1:** Implement `Efx`, `AlSource` and `RenderStream`; `./gradlew build` → PASS (no behaviour change yet).
- [ ] **Step 2:** Implement `AcousticRenderer`:
  - Client thread, `END_CLIENT_TICK`: refresh each `RenderStream` snapshot from the client level (`PortableSpeakerBlockEntity` volume / 100, powered, `FACING`), plus `playerVolume = options.getFinalSoundSourceVolume(RECORDS) × RaveCraftOptions.volume`, `maxSources`, `efxWanted`.
  - Thread `RaveCraft audio` (daemon), loop every 10 ms while started and not paused:
    1. gains: `Propagation.gain(settings.rolloff(), settings.fadeFraction(), settings.portable(), volume, distance(camera, centre of pos), cosAngle(facing, camera − centre))`, 0 when unpowered;
    2. `SourceSelector.select(gains, activeIds, maxSources)`; release sources of demoted streams (`reset`, back to the free list, `pacer.reset(now)`); give free sources to promoted streams (create lazily up to `maxSources`, delete free sources above `maxSources`);
    3. active streams: while `wantsBlock()` queue `nextProcessedBlock()`, play if stopped; `place`, `gain(gain × playerVolume)`, `highGain(efx ? highFrequencyGain : 1)`;
    4. virtual streams: pull and drop `pacer.due(now)` blocks through `nextProcessedBlock()` (keeps the EQ state and the playout position);
    5. a stream whose `ReceivedStreams` entry died is detached (source released).
  - `onEngineStopping`: stop and join the thread, delete every source and filter (streams stay attached). `onEngineStarted`: `Efx.detect()` (warn once if absent), restart the thread. `onPause`/`onResume`: pause/resume every source and stop/restart pumping (virtual pacers reset on resume).
  - Settings change (new `AcousticSettingsClient` value): rebuild each stream's `Equalizer`.
- [ ] **Step 3:** `SoundEngineMixin`: `destroy` HEAD and `emergencyShutdown` HEAD → `onEngineStopping()`; `loadLibrary` TAIL → `onEngineStarted()` if `@Shadow private boolean loaded`; `pauseAllExcept` HEAD → `onPause()`; `resume` HEAD → `onResume()`; `updateSource(Camera)` TAIL → `onListenerMoved(camera.position())`.
- [ ] **Step 4:** `ReceivedStreams`: replace `ensurePlaying()` by `AcousticRenderer.attach(...)` on first chunk; detach when the stream stops, drains or expires; `endAll` → `detachAll()`. Delete `SpeakerSoundInstance` and `NetworkAudioStream`.
- [ ] **Step 5:** `./gradlew build` → Expected: PASS (all JUnit + GameTests).
- [ ] **Step 6: Commit** — `git commit -m "Render speaker streams through dedicated OpenAL sources"`

---

### Task 8: In-game check and docs

- [ ] **Step 1:** `./gradlew runDatagen` → Expected: no change under `src/main/generated` (no content changed). `./gradlew build` → PASS.
- [ ] **Step 2:** In-game (user, dedicated server + 2 clients as in 3b), record results for the PR:
  - level falls smoothly to silence near 32 blocks at 100 %, near 16 at 50 %; left/right follows the head;
  - 9 speakers: only the 8 loudest sound; walking around swaps them without audible gaps; "Simultaneous speakers" = 2 cuts to 2 at once;
  - the three options act live and survive a restart;
  - F3+T while playing: sound comes back without restarting the track; solo pause pauses the sound;
  - the owner hears their own speaker with the same law;
  - `rolloff` 0.5 in `config/ravecraft-server.json` → softer decay after a server restart.
- [ ] **Step 3:** Update `docs/SPEC.md` (step 4a done) and commit — `git commit -m "Update spec progress for acoustic render"`.
