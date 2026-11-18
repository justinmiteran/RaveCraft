# Acoustic Reverb (step 4c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Speakers take the colour of the place they resonate in (room reverb measured by rays, rare distinct echoes, a fluid preset when the listener's head is under water or lava, optional underwater chorus).

**Architecture:** All measurement and DSP is pure Java in `core.acoustics.reverb` (rays over the 4b `AcousticGrid`, Eyring RT60, echo search, EAX parameter mapping, chorus delay line, slot assignment, send fades). A client `ReverbScheduler` measures spaces under a voxel budget each tick and publishes results; the audio thread drives 4 EFX auxiliary slots (listener reverb L, speaker spaces S1/S2, echo E) and 2 sends per source, and runs the chorus in the stream's PCM path after the occlusion filter.

**Tech Stack:** Java 25, Fabric 26.3 (Mojang names), LWJGL OpenAL `EXTEfx` 3.4.3, JUnit 5, Fabric GameTest.

**Spec:** [docs/superpowers/specs/2026-11-18-acoustic-reverb-design.md](../specs/2026-11-18-acoustic-reverb-design.md)

## Global Constraints

- `core` is pure Java: no `net.minecraft` import. `src/main` never imports `net.minecraft.client`.
- Only RaveCraft speakers are affected; vanilla sounds are untouched (R5).
- 1 block = 1 m; speed of sound 343 m/s.
- Ray counts per "Qualité de l'occlusion": LOW 16, MEDIUM 32, HIGH 48. Reverb voxel budget per tick: LOW 1 500, MEDIUM 3 000, HIGH 6 000.
- Re-measure: listener when the ear moved ≥ 1 block since its last measure, and at least every 2 s; a speaker space when its speaker enters S1/S2, then every 5 s.
- EFX limits (verified in LWJGL 3.4.3): `AL_EAXREVERB_MAX_DECAY_TIME` 20, `MAX_REFLECTIONS_DELAY` 0.3, `MAX_LATE_REVERB_DELAY` 0.1, `AL_ECHO_MAX_DELAY` 0.207, `AL_ECHO_MAX_DAMPING` 0.99.
- Glides: EAX parameters 1 s (≤ 10 updates/s); send slot change 100 ms down + 100 ms up; chorus in/out 200 ms.
- Server config: new keys get defaults and bounds from spec §5; a file missing any of them is rewritten complete with a `.bak` (existing mechanism of `AcousticSettingsJson`).
- No exception may leave a client tick; failures are logged at most once a minute and the last measure stays.
- Build rule: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"` then `./gradlew build` green before a task is done. Commits in English, imperative, no Claude attribution.

## Review Focus

1. A listener standing inside a solid block or at a chunk edge (rays start in a solid voxel, or walk into unloaded chunks) → the probe must still finish (start voxel ignored, unloaded = air per `WorldAcousticGrid`) and give a sane shape. Test in Task 4.
2. A speaker whose S1/S2 slot is reassigned every tick because two speakers have nearly equal gains → assignment must be stable (an id still in the top 2 keeps its slot). Test in Task 7.
3. EFX present but fewer than 2 aux sends (or slot creation failing) → no crash, echo dropped first, then reverb. Covered by `SendRoute`/renderer guards in Task 8; checked in Review.
4. The server sending a reverb config with extreme values (rayLength 128, decay 20 s, wet gain 1) → every EAX value stays in bounds. Test in Task 6.
5. Head entering and leaving water every few ticks (swimming at the surface) → preset swaps glide, chorus fades without clicks. Chorus test in Task 9; glide via `ReverbParams.toward` in Task 6.

---

### Task 1: Extract the voxel walk from the occlusion tracer

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/occlusion/VoxelWalk.java`
- Modify: `src/main/java/com/ravecraft/mod/core/acoustics/occlusion/OcclusionTracer.java` (`trace`, lines ~90–210)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/VoxelWalkTest.java`

**Interfaces:**
- Produces: `public final class VoxelWalk` (mutable, reusable, not thread-safe):
  - `void start(double x0, double y0, double z0, double x1, double y1, double z1)`
  - `int x()`, `int y()`, `int z()` — current voxel
  - `double length()` — segment length in blocks
  - `double enterT()`, `double exitT()` — fractions (0..1) of the segment where it enters and leaves the current voxel
  - `int entryAxis()` — axis crossed to enter (the dominant axis for the first voxel), `VoxelSample.X/Y/Z`
  - `boolean first()`, `boolean last()`
  - `void step()` — moves to the next voxel (lowest axis first on ties; an axis already at its end coordinate never steps again); illegal after `last()`

- [ ] **Step 1: Write the failing tests** `VoxelWalkTest`:
  - `walksAStraightLineThroughEachVoxel`: start (0.5,0.5,0.5) → (3.5,0.5,0.5): visits x = 0,1,2,3 with y = z = 0; `first()` only on the first, `last()` only on the fourth; `enterT/exitT` = 0/1/6, 1/6/0.5, 0.5/5/6, 5/6/1; `entryAxis()` = X throughout.
  - `diagonalTiesStepTheLowestAxisFirst`: (0.5,0.5,0.5) → (1.5,1.5,0.5) visits (0,0,0), (1,0,0), (1,1,0).
  - `sameVoxelIsFirstAndLast`: (0.2,0.2,0.2) → (0.8,0.8,0.8): one voxel, `first() && last()`, `exitT()` = 1.
- [ ] **Step 2: Run** `./gradlew test --tests '*VoxelWalkTest'` — Expected: FAIL (class missing).
- [ ] **Step 3: Implement `VoxelWalk`** by moving the DDA state (`cell`, `end`, `step`, `tMax`, `tDelta`, `entryAxis`, `t`) out of `OcclusionTracer.trace`, then rewrite `trace` on top of it with identical behaviour (the per-voxel body stays in the tracer).
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS, including every existing `OcclusionTracerTest` and `DetourTest` unchanged.
- [ ] **Step 5: Commit** `git commit -m "Extract the voxel walk from the occlusion tracer"`

### Task 2: Absorption of materials and media

**Files:**
- Modify: `core/acoustics/occlusion/AcousticMaterial.java`, `AcousticMedium.java`, `OcclusionSettings.java` (defaults, `clamped`)
- Modify: `src/main/java/com/ravecraft/mod/config/AcousticSettingsJson.java` (read/write keys + comments)
- Modify: `src/main/java/com/ravecraft/mod/network/payload/AcousticSettingsPayload.java` (`MATERIAL`, `MEDIUM` codecs)
- Test: `OcclusionSettingsTest`, `AcousticSettingsJsonTest`, `AcousticSettingsPayloadTest`

**Interfaces:**
- Produces:
  - `record AcousticMaterial(String name, double lossDb, double highLossDb, double absorption)` with a 3-arg constructor defaulting `absorption` to 0.1.
  - `record AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock, double listenerLossDb, double listenerHighLossDb, double listenerBlur, double surfaceAbsorption)`; the existing 4-arg and 7-arg constructors default `surfaceAbsorption` to 0.05.
  - Defaults: stone 0.02, glass 0.03, metal 0.05, wood 0.10, earth 0.30, snow 0.40, foliage 0.50, wool 0.70; media surfaceAbsorption air 1, water 0.01, lava 0.05, vacuum 1. Bounds 0–1.
  - JSON keys: `"absorption"` on each material, `"surfaceAbsorption"` on each medium; missing → file incomplete.

- [ ] **Step 1: Write failing tests**:
  - `OcclusionSettingsTest.materialsAndMediaAbsorbAsSpecified`: the eight default absorptions and four surfaceAbsorptions above.
  - `OcclusionSettingsTest.absorptionIsBounded`: material absorption 1.5 → 1, medium surfaceAbsorption −0.2 → 0, each with a warning.
  - `AcousticSettingsJsonTest`: round-trip keeps `absorption`/`surfaceAbsorption`; a file without them reads `complete == false` and takes the defaults.
  - `AcousticSettingsPayloadTest.roundTripsOcclusion`: use `new AcousticMaterial("slime", 3, 4, 0.6)` and a medium with `surfaceAbsorption` 0.3.
- [ ] **Step 2: Run** `./gradlew test` — Expected: FAIL (compile errors on the new components).
- [ ] **Step 3: Implement** the record components, defaults, clamping, JSON (one comment line per new key: what it is and its bounds) and the codecs.
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Give acoustic materials and media an absorption"`

### Task 3: Reverb settings

**Files:**
- Create: `core/acoustics/reverb/ReverbSettings.java`, `EchoSettings.java`, `FluidReverb.java`
- Modify: `core/acoustics/AcousticSettings.java` (new last component `ReverbSettings reverb`, `DEFAULT`, `clamped`), every `new AcousticSettings(` site (`AcousticSettingsJson`, `AcousticSettingsPayload`, `AudioRelayGameTest`, `AcousticSettingsJsonTest`, `AcousticSettingsTest`, `PropagationTest`, `AcousticSettingsPayloadTest`)
- Modify: `AcousticSettingsJson` (section `"reverb"`), `AcousticSettingsPayload` (codecs)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/reverb/ReverbSettingsTest.java`, `AcousticSettingsJsonTest`, `AcousticSettingsPayloadTest`

**Interfaces:**
- Produces (all records, lists immutable, values per spec §5):
  - `EchoSettings(double minDistance, double maxDistance, double minRays, double maxAbsorption, double gain, double feedback, double damping)`; `DEFAULT` = (17, 35, 4, 0.2, 0.6, 0.15, 0.5). `minRays` is an int stored as `int`.
  - `FluidReverb(String medium, double decaySeconds, double density, double diffusion, double gain, double decayHfRatio, boolean chorus, double chorusRateHz, double chorusDepthMs)`; water (1.2, 1, 1, 0.4, 0.3, false, 0.4, 1.5), lava (0.6, 1, 1, 0.3, 0.2, false, 0.25, 2).
  - `ReverbSettings(boolean enabled, double rayLength, double maxDecaySeconds, double maxWetGain, double sameSpaceLossDb, EchoSettings echo, List<FluidReverb> media)`; `DEFAULT` = (true, 64, 6, 0.32, 6, EchoSettings.DEFAULT, [water, lava]); `@Nullable FluidReverb fluid(String medium)`; `ReverbSettings clamped(Consumer<String> warn)`; `MAX_MEDIA = 16`.
  - Bounds: rayLength 16–128, maxDecaySeconds 0.5–20, maxWetGain 0–1, sameSpaceLossDb 0–60, minDistance 10–35, maxDistance minDistance–35, minRays 1–16, maxAbsorption 0–1, gain 0–1, feedback 0–0.5, damping 0–0.99; fluid decaySeconds 0.1–20, density/diffusion/gain 0–1, decayHfRatio 0.1–2, chorusRateHz 0.05–5, chorusDepthMs 0–10. Fluid names follow the medium-name pattern of `OcclusionSettings`; invalid or duplicate ones are dropped with a warning.
  - `AcousticSettings.reverb()`.

- [ ] **Step 1: Write failing tests** `ReverbSettingsTest`: `defaultsMatchSpec` (every value above), `valuesAreBounded` (one out-of-range value per bound group, warning text names the key), `maxDistanceNeverBelowMinDistance` (min 30, max 20 → max 30), `fluidLooksUpByMedium` (`fluid("water")` non-null, `fluid("slime")` null). JSON: round-trip of a custom `ReverbSettings` with a third fluid `"slime"`; a file without `"reverb"` is incomplete and takes `DEFAULT`. Payload: round-trip of the same custom settings.
- [ ] **Step 2: Run** `./gradlew test` — Expected: FAIL.
- [ ] **Step 3: Implement** the records, the `"reverb"` JSON section (commented like `"occlusion"`: each key gets one line saying what it does and its bounds; `media` is an open list keyed by medium name), and the codecs (nested `EchoSettings` and `FluidReverb` codecs keep every `StreamCodec.composite` at 10 fields or fewer).
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add server reverb settings"`

### Task 4: Measure a space with rays

**Files:**
- Create: `core/acoustics/reverb/RayDirections.java`, `RayHit.java`, `RoomProbe.java`, `RoomShape.java`
- Modify: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/TestGrid.java` → `public` class with public constructors and builder methods (reverb tests live in another package)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/reverb/RoomProbeTest.java`, `RayDirectionsTest.java`

**Interfaces:**
- Consumes: `VoxelWalk` (Task 1), `AcousticGrid`, `VoxelSample`, `TraceBudget`, absorptions (Task 2).
- Produces:
  - `RayDirections.sphere(int n) -> double[][]` — n unit vectors on a Fibonacci spiral, deterministic.
  - `record RayHit(boolean open, double distance, double absorption)` — open rays have `distance = rayLength`, `absorption = 1`.
  - `RoomProbe(AcousticGrid grid)`: `void start(double x, double y, double z, int rays, double rayLength)`; `boolean run(TraceBudget budget)` (true when every ray is done; resumes where it stopped); `List<RayHit> hits()` (valid once done).
  - `record RoomShape(double openness, double meanFreePath, double absorption, double hitAbsorption)`; `static RoomShape of(List<RayHit> hits, double rayLength)`; `double rt60(double maxDecaySeconds)`.
- Rules (spec §3.1–3.3): the start voxel is never a hit; a ray stops in the first later voxel whose `material != null && fill(entryAxis) >= 0.5` (absorption = material's) or whose own medium is non-null and differs from the start medium (absorption = that medium's `surfaceAbsorption`; impedance ≤ 0 → 1). The start medium is the start voxel's medium, or `grid.defaultMedium()` when null. Distance = `enterT() * length()`. `openness` = open / n; `meanFreePath` = mean distance of non-open hits (= rayLength when none); `absorption` = mean over all rays; `hitAbsorption` = mean over non-open hits (1 when none). `rt60 = clamp(0.161 * l / (-4 * ln(1 - ᾱ)), 0.1, maxDecaySeconds)`, with ᾱ capped at 0.999.

- [ ] **Step 1: Write failing tests**:
  - `RayDirectionsTest.unitAndSpread`: for n = 16, 32, 48 every vector has norm 1 ± 1e-9 and the smallest angle between two is > 10°; two calls give equal arrays.
  - `RoomProbeTest.closedStoneRoomIsClosedAndLive`: `TestGrid().shell(-5,-3,-5,5,3,5, stone)`, probe from (0.5,0.5,0.5), 32 rays, length 64 → openness 0, meanFreePath in [3, 6], `rt60(6)` = 6 (capped).
  - `woolRoomDecaysFast`: same shell in wool → `rt60(6)` < 0.3 and < stone's.
  - `openFieldIsOpen`: only a floor `fill(-40,-1,-40,40,-1,40, stone)` → openness > 0.4.
  - `waterSurfaceStopsARay`: listener in a water box under air: an upward ray (n large enough) hits at the surface with absorption 1 (air's surfaceAbsorption); from air above water, a downward hit has 0.01.
  - `startingInsideASolidStillMeasures` (Review Focus 1): start voxel is stone inside the stone room → same openness and meanFreePath ± 1 as from an air voxel.
  - `resumedProbeEqualsOneShot`: run with `new TraceBudget(50)` repeatedly until done → same `hits()` as one run with `Integer.MAX_VALUE`.
- [ ] **Step 2: Run** `./gradlew test --tests '*reverb*'` — Expected: FAIL.
- [ ] **Step 3: Implement** the four classes.
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Measure a space with rays"`

### Task 5: Find a distinct echo

**Files:**
- Create: `core/acoustics/reverb/Echo.java`, `EchoFinder.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/reverb/EchoFinderTest.java`

**Interfaces:**
- Consumes: `RayHit`, `EchoSettings`.
- Produces: `record Echo(double delaySeconds, double gain)` with `Echo.NONE` (0, 0); `static Echo EchoFinder.find(List<RayHit> hits, EchoSettings settings)`.
- Rules (spec §3.4): candidates = non-open hits with `absorption <= maxAbsorption` and `minDistance <= distance <= maxDistance`. Sort by distance; a group is a maximal run whose distances are within ±15 % of the run's median. Keep the group with most rays (ties: nearest). Fewer than `minRays` → `NONE`. `d` = group median; `delay = 2 d / 343`; `gain = clamp(gain * (1 - α̅group) * (count / n) * (17 / d), 0, 1)` where n = hits.size().

- [ ] **Step 1: Write failing tests**: from synthetic hit lists of 32 rays —
  - `stoneWallAt25BlocksEchoes`: 6 hits at 24.5–25.5 (stone 0.02), rest open → delay = 2·25/343 ± 0.003, gain > 0.
  - `wallAt50BlocksDoesNotEcho`: same at 50 → `NONE`.
  - `woolWallDoesNotEcho`: absorption 0.7 → `NONE`.
  - `tooFewRaysDoNotEcho`: 3 hits → `NONE`.
  - `largerGroupWins`: 4 hits at 20 and 6 at 30 → delay of 30.
  - Integration: `RoomProbe` in a `TestGrid` with a stone wall plane at x = 25 and a stone floor → `find` gives delay ≈ 2·24.5/343 (hits are measured at the wall's near face).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Find distinct echoes off far walls"`

### Task 6: Map a space to EAX parameters

**Files:**
- Create: `core/acoustics/reverb/ReverbParams.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/reverb/ReverbParamsTest.java`

**Interfaces:**
- Consumes: `RoomShape`, `ReverbSettings`, `FluidReverb`.
- Produces: `record ReverbParams(double decayTime, double decayHfRatio, double gain, double reflectionsGain, double reflectionsDelay, double lateReverbDelay, double density, double diffusion, double airAbsorptionGainHf)`:
  - `static final ReverbParams SILENT` — gain 0 and reflectionsGain 0, other fields at EAX defaults (decay 1.49, hf 0.83, delays 0.007/0.011, density 1, diffusion 1, air 0.994).
  - `static ReverbParams of(RoomShape shape, ReverbSettings settings)` — table of spec §3.5: `decayTime = shape.rt60(maxDecaySeconds)`, `decayHfRatio = clamp(1 - 0.6 * hitAbsorption, 0.1, 1)`, `gain = maxWetGain * (1 - openness)^1.5`, `reflectionsGain = 0.5 * gain`, `reflectionsDelay = min(0.3, l / 343)`, `lateReverbDelay = min(0.1, l / 343)`, `density = clamp(8 / l, 0, 1)`, `diffusion = 0.5 + 0.5 * (1 - openness)`, `airAbsorptionGainHf = 0.994`.
  - `static ReverbParams of(FluidReverb fluid)` — decay/density/diffusion/gain/hfRatio from the preset, `reflectionsGain = 0.5 * gain`, delays 0.005 / 0.01, air 0.994.
  - `ReverbParams toward(ReverbParams target, double fraction)` — field-wise linear interpolation, fraction clamped to 0..1.
  - Every result is within EAX bounds (decay 0.1–20, hf 0.1–2, gains 0–1 for late gain and 0–3.16 for reflections, delays per Global Constraints, density/diffusion 0–1).

- [ ] **Step 1: Write failing tests**: `stoneRoomIsLiveAndWoolRoomIsDull` (from Task 4 shapes: stone decay > wool decay, stone hf > wool hf), `openAirIsDry` (openness 1 → gain 0), `smallRoomIsDenserThanBigRoom`, `extremeSettingsStayInEaxBounds` (Review Focus 4: rayLength 128, maxDecaySeconds 20, maxWetGain 1, shapes with l = 0.5 and l = 128, openness 0 and 1), `waterPresetUsesItsValues`, `towardInterpolatesAndClamps` (fraction 0.5 → midpoints, 2 → target).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Map measured spaces to EAX reverb parameters"`

### Task 7: Speaker space slots and send fades

**Files:**
- Create: `core/acoustics/reverb/SpaceSlots.java`, `SendRoute.java`
- Test: `SpaceSlotsTest.java`, `SendRouteTest.java` (same test package)

**Interfaces:**
- Produces:
  - `SpaceSlots.SLOTS = 2`; `static Integer[] assign(Map<Integer, Double> outsideGains, Integer[] previous)` — returns a new array of length 2 (entries may be null): the two loudest session ids with gain > 0; an id already in `previous` and still in the top 2 keeps its index; new ids fill free indexes in loudness order.
  - `static boolean sameSpace(boolean occlusionKnown, double occlusionLossDb, double sameSpaceLossDb)` — true when unknown or `lossDb < sameSpaceLossDb`.
  - `SendRoute` (per stream, audio thread): `static final int LISTENER = 0` (slot index L; 1 = S1, 2 = S2); `int slot()`, `double gain()`; `void step(int targetSlot, double dtMs)` — if `targetSlot == slot()`, gain rises to 1 at 1/100 ms; otherwise gain falls at 1/100 ms and, once at 0, `slot` becomes `targetSlot`. Starts at slot `LISTENER`, gain 1.

- [ ] **Step 1: Write failing tests**: `SpaceSlotsTest` — `picksTheTwoLoudest`, `keepsAnIdInItsSlot` (previous [7, 9], gains {9: 0.5, 3: 0.45, 7: 0.4} → 7 leaves the top 2, 9 keeps index 1, 3 takes the freed index 0 → [3, 9]), `nearlyEqualGainsDoNotSwapSlots` (Review Focus 2: previous [1, 2], gains {1: 0.300, 2: 0.301} → [1, 2]), `silentSpeakersGetNoSlot`, `sameSpaceBelowThresholdOrUnknown`. `SendRouteTest` — `staysPutAtFullGain`, `fadesOutSwitchesAndFadesIn` (target 1 from slot 0: after 50 ms gain 0.5 slot 0; after 100 ms gain 0 slot 1; after 200 ms gain 1 slot 1), `changingTargetMidFadeReturnsSmoothly`.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Assign speaker spaces to reverb slots with smooth sends"`

### Task 8: EFX slots and sends in the renderer

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/client/audio/render/Efx.java`, `AlSource.java`, `AcousticRenderer.java`, `RenderStream.java`

**Interfaces:**
- Consumes: `ReverbParams`, `Echo`, `EchoSettings`, `SendRoute` (Tasks 5–7).
- Produces (client, package-private unless noted):
  - `Efx.maxSends() -> int` (`ALC_MAX_AUXILIARY_SENDS` on the current device, 0 without EFX).
  - `record Efx.Slot(int slot, int effect, boolean eax)`; `Efx.createReverbSlot() -> @Nullable Slot` (`AL_EFFECT_EAXREVERB`, falling back to `AL_EFFECT_REVERB` when the type does not take; null if a slot or effect cannot be generated); `Efx.createEchoSlot() -> @Nullable Slot`; `Efx.applyReverb(Slot, ReverbParams)` (with plain reverb: same fields without the EAX-only ones) then re-attach the effect (`AL_EFFECTSLOT_EFFECT`); `Efx.applyEcho(Slot, Echo, EchoSettings)` (`AL_ECHO_DELAY = min(0.207, delay)`, `LRDELAY` 0, `DAMPING = damping`, `FEEDBACK = feedback`, `SPREAD` −1, slot gain = echo gain); `Efx.deleteSlot(Slot)`.
  - `AlSource.send(int index, int slotId, float gain)` — keeps one low-pass filter per send used only for its `AL_LOWPASS_GAIN`; `slotId` 0 detaches (`AL_EFFECTSLOT_NULL`); skips the AL call when nothing changed by > 1e-3.
  - `RenderStream`: `volatile int reverbSlot` (0 = L, 1 = S1, 2 = S2; written by the scheduler), `final SendRoute route = new SendRoute()`.
  - `AcousticRenderer` publication points (static, volatile, written by `ReverbScheduler`): `static void reverbTargets(ReverbParams listener, ReverbParams s1, ReverbParams s2, Echo echo, EchoSettings echoSettings)`.
- Behaviour:
  - `Worker.run()` creates L, S1, S2 (reverb) and E (echo) after `Efx.detect()` when EFX is present and `maxSends() >= 1`; E only when `maxSends() >= 2` (Review Focus 3). Missing slots are logged once and skipped. `releaseAll()` detaches every send and deletes the slots.
  - Each step: when reverb is disabled (`efxWanted == false` or `settings.reverb().enabled() == false`) every send goes to 0 and slot targets become `SILENT`/`NONE`. Otherwise, at most every 100 ms, each slot's current params move `toward` its target by `elapsed / 1000 ms` and are applied; the echo is applied when it changes.
  - Per stream with a source: `route.step(stream.reverbSlot, dt)`; send 0 → slot of `route.slot()` with gain `route.gain()`; send 1 → E with gain 1.
- Manual check (no unit test: OpenAL calls): covered by the in-game list of Task 11.

- [ ] **Step 1: Implement** the `Efx` and `AlSource` additions, verifying each `EXTEfx` name used exists in `lwjgl-openal-3.4.3.jar` (`javap -constants -cp <jar> org.lwjgl.openal.EXTEfx`).
- [ ] **Step 2: Wire** slots, glides and sends into `AcousticRenderer`/`RenderStream` as described.
- [ ] **Step 3: Run** `./gradlew build` — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 4: Commit** `git commit -m "Drive EFX reverb and echo slots from the speaker renderer"`

### Task 9: Underwater chorus in the stream

**Files:**
- Create: `core/acoustics/reverb/Chorus.java`
- Modify: `RenderStream.java` (`nextBlock` runs the chorus after `occlusionFilter`), `AcousticRenderer.java` (passes the head chorus)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/reverb/ChorusTest.java`

**Interfaces:**
- Produces: `Chorus(double sampleRate)`; `void process(float[] samples, int count, @Nullable FluidReverb fluid)` — active when `fluid != null && fluid.chorus()`; output mixes dry and modulated with a mix that glides 0 ↔ 1 over 200 ms; the modulated tap reads a delay line at `8 ms + depthMs * sin(2π · rate · t)` with linear interpolation; the delay line is fed even when inactive, so switching on has no gap. `AcousticRenderer.headChorus(@Nullable FluidReverb fluid)` (static volatile, written by the scheduler); `RenderStream.nextBlock(float[] scratch, @Nullable ByteBuffer out, double highLossDb, @Nullable FluidReverb chorus)`; `RenderStream.chorus` field. Disabled when `efxWanted == false` or reverb is disabled (pass null).

- [ ] **Step 1: Write failing tests**: `offLeavesTheSignalUntouched` (null and `chorus=false` → identical array), `onDelaysByAtMostBasePlusDepth` (impulse → output energy only between 8 − depth and 8 + depth ms once fully on), `pitchWobbles` (1 kHz sine, depth 1.5 ms, rate 0.4 Hz: zero-crossing spacing varies by > 0.1 % across 3 s), `switchingOnAndOffDoesNotClick` (Review Focus 5: 200 Hz sine over blocks off/on/off/on, max sample step ≤ 1.5 × the input's).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement** `Chorus` and wire it.
- [ ] **Step 4: Run** `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add an optional underwater chorus to speaker streams"`

### Task 10: Reverb scheduler

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/audio/render/ReverbScheduler.java`
- Modify: `client/options/OcclusionQuality.java` (`reverbRays()`, `reverbVoxelsPerTick()`), `client/audio/occlusion/WorldAcousticGrid.java` (`@Nullable AcousticMedium headMedium(Vec3 eye)`, used by `perceptionAt`), `RaveCraftClient.java` (register after `OcclusionScheduler`)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/RoomProbeGameTest.java` (+ entry in `src/gametest/resources/fabric.mod.json`)

**Interfaces:**
- Consumes: `RoomProbe`, `RoomShape`, `EchoFinder`, `ReverbParams`, `SpaceSlots`, `AcousticRenderer.reverbTargets`, `AcousticRenderer.headChorus`, `RenderStream.reverbSlot`, the scheduler's `WorldAcousticGrid` (exposed by `OcclusionScheduler` as `static @Nullable WorldAcousticGrid grid()`).
- Behaviour (spec §6), each END_CLIENT_TICK inside a try/catch logging at most once a minute:
  - Head medium with a `FluidReverb` → listener target `ReverbParams.of(fluid)`, echo `NONE`, `headChorus(fluid)`; otherwise `headChorus(null)` and the listener probe drives L and E.
  - Listener probe: restart when the ear moved ≥ 1 block from the last start or 40 ticks passed; on completion publish `ReverbParams.of(RoomShape.of(...))` and `EchoFinder.find(...)`.
  - Outside gains: audible streams with `!SpaceSlots.sameSpace(...)`, keyed by session, valued by `heardGain`; `assign` → set `reverbSlot` (1 or 2 for assigned, else 0) on every stream; a slot whose session changed restarts its probe at the speaker centre; each slot re-probes every 100 ticks; an empty slot targets `SILENT`.
  - One `TraceBudget(quality.reverbVoxelsPerTick())` per tick shared in the order listener, S1, S2; rays = `quality.reverbRays()`; ray length = `settings.reverb().rayLength()`.
  - No level, reverb disabled, or occlusion grid missing → targets `SILENT`/`NONE`, `headChorus(null)`, all `reverbSlot = 0`.
- GameTest `RoomProbeGameTest.stoneRoomIsClosed`: builds a 7×5×7 hollow stone shell in the test structure, probes from its centre through a test-local `AcousticGrid` over the server level (`BlockAcoustics.sample(level.getBlockState(pos), media, dimension, out)` with `MediumResolver(OcclusionSettings.DEFAULT)`, dimension `minecraft:overworld`), 32 rays, length 64 → openness 0 and `rt60(6)` > 1.

- [ ] **Step 1: Write the GameTest**, declare it in `fabric.mod.json`, run `./gradlew runGameTest` — Expected: PASS (probe already exists; this pins the real-world grid path).
- [ ] **Step 2: Implement** `OcclusionQuality` values (LOW 16 / 1 500, MEDIUM 32 / 3 000, HIGH 48 / 6 000), `headMedium`, `OcclusionScheduler.grid()`, and `ReverbScheduler`.
- [ ] **Step 3: Run** `./gradlew build` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -m "Schedule room measurements for the listener and speaker spaces"`

### Task 11: Documentation and final checks

**Files:**
- Modify: `docs/SPEC.md` (step 4c line: what was delivered, "(à vérifier en jeu)"), spec §8 checklist kept.

- [ ] **Step 1:** Update the step 4c line of `docs/SPEC.md` with the delivered behaviour (config section `"reverb"`, absorptions, quality option sets rays and budget).
- [ ] **Step 2: Run** `./gradlew runDatagen && ./gradlew build` — Expected: BUILD SUCCESSFUL; `git status` shows no change under `src/main/generated` other than line endings (do not commit those).
- [ ] **Step 3: Commit** `git commit -m "Document the acoustic reverb step"`
- [ ] **Step 4:** In-game checklist for the user (spec §8), after deleting `run/config/ravecraft-server.json5`.
