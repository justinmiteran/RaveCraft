# Acoustic Occlusion (step 4b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Muffle speaker sound through blocks and media: configurable acoustic materials (by tag, else footstep sound) weighted by block shape, configurable media (air, water, lava, vacuum…) whose interface loss comes from their impedances, detours through nearby openings, all applied by the 4a renderer through EFX gain and high-frequency gain.

**Architecture:** The model and the tracer live in `core.acoustics.occlusion` (pure Java, JUnit on in-memory grids). `world.acoustics` maps real block states and fluids to materials and media (GameTests). On the client, `OcclusionScheduler` traces a fifth of the audible streams each tick within a voxel budget and publishes two volatile losses into `RenderStream`; the audio thread smooths and applies them. The server only adds an `"occlusion"` section to `ravecraft-server.json5` and to the settings payload.

**Tech Stack:** Java 25, Fabric 0.166.2+26.3 (Mojang names), Fabric datagen (`FabricTagsProvider.BlockTagsProvider` / `FluidTagsProvider`), `CommonLifecycleEvents.TAGS_LOADED`, Gson (lenient), LWJGL OpenAL EFX (from 4a), JUnit 5, Fabric GameTest.

**Spec:** [docs/superpowers/specs/2026-11-17-acoustic-occlusion-design.md](../specs/2026-11-17-acoustic-occlusion-design.md) (parent: `2026-11-17-acoustic-engine-design.md`).

## Global Constraints

- Prefix every Gradle command with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`.
- `core` is pure Java (no `net.minecraft` import). `src/main` never imports `net.minecraft.client` or `org.lwjgl`.
- Code, comments and commits in English; user-facing strings in `en_us.json` **and** `fr_fr.json`.
- No `Co-Authored-By` / "Generated with Claude Code" lines. Never `git add art/proposals/`. Never push without the user's OK.
- Branch `feat/acoustic-occlusion`, cut from `feat/acoustic-render` (PR #7 not merged yet).
- Default materials (lossDb / highLossDb): foliage 2/4, glass 5/6, wood 8/10, snow 10/14, earth 12/12, stone 15/15, metal 18/12, wool 20/20.
- Default media (impedance / lossDbPerBlock / highLossDbPerBlock): air 415/0/0.05, water 1 480 000/0/0.02, lava 6 000 000/3/6, vacuum 0/0/0.
- Defaults: `enabled` true, `maxLossDb` 60, `interfaceScale` 0.67, `interfaceHighRatio` 0.5, `detours` true, `bendLossDb` 6, `bendHighLossDb` 10, `dimensions` empty.
- Bounds: `maxLossDb` 20–120; `impedance` 0–100 000 000; every other dB value 0–40; `interfaceScale` 0–1; `interfaceHighRatio` 0–2. Caps: 16 media, 32 materials, 32 dimensions. Names `[a-z0-9_]{1,32}`; dimension ids `namespace:path` (`[a-z0-9_.-]+:[a-z0-9_./-]+`), ≤ 64 chars.
- **Loss convention (everywhere in code):** `lossDb` is the broadband loss; `highLossDb` is the **extra** treble loss on top of it (what EFX `AL_LOWPASS_GAINHF` applies on top of `AL_LOWPASS_GAIN`). "1 stone wall → 15 dB, treble 30" in the spec is `lossDb 15, highLossDb 15`.
- Occlusion quality budget (voxels per tick): low 5 000, medium 20 000 (default), high 60 000.
- Every task ends with `./gradlew build` green.

## Decisions taken while planning (spec §10, checked in the 26.3 sources)

- **`SoundType` constants** exist as listed in Task 4's table (checked in `net/minecraft/world/level/block/SoundType.java`). Leaves use `SoundType.GRASS`/`AZALEA_LEAVES`/`CHERRY_LEAVES`/`POPLAR_LEAVES`; hence the tag-first rule.
- **Shapes:** `BlockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)` (cached by vanilla per state) and `VoxelShape.toAabbs()`. States with `hasOffsetFunction()` (bamboo…) are measured at the origin: their offset does not change volume or sealing much.
- **Tags:** `BlockState`/`FluidState` implement `TypedInstance`, so `state.is(TagKey<Block>)` and `fluidState.is(TagKey<Fluid>)`. `BlockTags.LEAVES`, `WOOL`, `WOOL_CARPETS`, `PLANKS`, `LOGS`; `FluidTags.WATER`, `LAVA`. Fluid tags are generated with `FabricTagsProvider.FluidTagsProvider`.
- **Tag reload:** `CommonLifecycleEvents.TAGS_LOADED` (`(registries, client) -> …`, fired on the client when tags arrive and on the server after `/reload`).
- **Camera:** `Camera.position()`; the listener position is already published by 4a (`AcousticRenderer.onListenerMoved`). Dimension: `Level.dimension()`.
- **Network:** `StreamCodec.composite` goes up to 14 fields; `ByteBufCodecs.list(max)` and `stringUtf8(max)` exist. Lists are capped by `OcclusionSettings.clamped` before any send.
- **Option:** `new OptionInstance.Enum<>(List.of(OcclusionQuality.values()), codec)` for the quality cycle button.
- **Spec deviation — sealing threshold:** the spec says a sealing panel is ≥ 3/16 thick, but glass panes are 2/16 thick and the spec names "vitre de face" as sealing. Use **≥ 2/16**; carpets (1/16) still count by volume. Task 7 updates the spec.
- **Spec deviation — datapack override test:** a GameTest cannot load an extra datapack tag. Tag precedence is proven by leaves (sound `GRASS` → earth by footstep, but `foliage` through the generated tag).
- **`AcousticSettings`:** the record component `airAbsorptionDbPerBlock` is replaced by a last component `OcclusionSettings occlusion`; a method `airAbsorptionDbPerBlock()` returning `occlusion.air().highLossDbPerBlock()` keeps the renderer and `Propagation` callers unchanged.
- **Base materials** removed from the config come back with their defaults (spec §4.1), so `clamped` always re-adds them; the `air` medium likewise.

## Review Focus

1. Rays exactly along a block edge or through a corner (integer coordinates, listener standing at x.0): the DDA must neither skip a wall nor count a block twice — tests `rayAlongGridLineCountsWallOnce`, `diagonalThroughCornerGrazesOnly` (Task 2).
2. Listener inside a solid block (spectator, camera clipping) or speaker fully buried: endpoint voxels never add material loss, so the result is the walls in between, not a guaranteed maximum — test `endpointsInsideSolidAddNothing` (Task 2).
3. Old `ravecraft-server.json5` from 4a (no `"occlusion"`, has `airAbsorptionDbPerBlock` 0.1): migrated, rewritten with a `.bak`, value kept — test `oldFileMigratesAndAsksRewrite` (Task 5) and `rewritesIncompleteFileWithBackup` (Task 5).
4. Config with 40 materials and a 60-character name: no disconnect when a player joins (list codecs throw past their max) — tests `capsListsInFileOrder` (Task 1) and `encodesClampedSettingsAtCaps` (Task 5).
5. Budget runs out mid-stream: the stream keeps its last value and goes first next tick, no exception and no zero-loss flash — tests `budgetExhaustedReturnsNull` (Task 3) and `rotaPutsUnfinishedFirst` (Task 6).

---

### Task 1: Occlusion model types

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/occlusion/AcousticMaterial.java`, `AcousticMedium.java`, `OcclusionSettings.java`, `Interfaces.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/InterfacesTest.java`, `OcclusionSettingsTest.java`

**Interfaces:**
- Produces:
  - `record AcousticMaterial(String name, double lossDb, double highLossDb)`
  - `record AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock)`
  - `record OcclusionSettings(boolean enabled, double maxLossDb, List<AcousticMedium> media, double interfaceScale, double interfaceHighRatio, Map<String, String> dimensions, List<AcousticMaterial> materials, boolean detours, double bendLossDb, double bendHighLossDb)` with `static final OcclusionSettings DEFAULT`, `static final List<String> BASE_MATERIALS` (the 8 names, table order), constants `MAX_MEDIA = 16`, `MAX_MATERIALS = 32`, `MAX_DIMENSIONS = 32`, `MAX_NAME = 32`, `MAX_DIMENSION_ID = 64`, `OcclusionSettings clamped(Consumer<String> warn)`, `AcousticMedium air()`, `@Nullable AcousticMedium medium(String name)`, `AcousticMedium dimensionMedium(String dimensionId)` (mapped medium, else air). Lists are immutable and in file order; `dimensions` is a `LinkedHashMap` copy.
  - `final class Interfaces`: `static double lossDb(AcousticMedium a, AcousticMedium b, double interfaceScale)` (0 when same impedance, `Double.POSITIVE_INFINITY` when the transmission is 0) and `static double highLossDb(double lossDb, double interfaceHighRatio)` = `lossDb × ratio`.

- [ ] **Step 1: Write the failing tests**

`InterfacesTest`:
- `airToWaterIsAboutTwentyDb`: `lossDb(air, water, 0.67)` within 0.3 of 19.8; `highLossDb(that, 0.5)` within 0.2 of 9.9.
- `realScaleIsPhysics`: `lossDb(air, water, 1)` within 0.2 of 29.5.
- `airToLavaIsAboutTwentyFourDb`: within 0.3 of 23.8.
- `symmetric`: `lossDb(a, b) == lossDb(b, a)`.
- `sameImpedanceLosesNothing`: `lossDb(water, water, 0.67) == 0`.
- `vacuumIsMute`: `lossDb(air, vacuum, 0.67)` is `+∞`; `lossDb(vacuum, vacuum, 0.67)` is `+∞` too (no medium carries sound with Z = 0).

`OcclusionSettingsTest`:
- `defaultsMatchSpec`: 4 media in order air, water, lava, vacuum and 8 materials in `BASE_MATERIALS` order with the Global Constraints values; scalars as listed.
- `clampsEveryBound`: one out-of-range value per field (`maxLossDb` 500 → 120, `interfaceScale` −1 → 0, `interfaceHighRatio` 9 → 2, `bendLossDb` −3 → 0, material `lossDb` 99 → 40, medium `impedance` −5 → 0, NaN → minimum); one warning per change.
- `dropsInvalidNames`: material `"Stone!"`, medium `""`, dimension `"moon"` (no namespace) and dimension `"ad_astra:moon"` → `"lava2"` (unknown medium) are dropped, each with a warning.
- `capsListsInFileOrder`: the 8 base materials followed by 40 custom ones `m0…m39` → exactly 32 entries: the 8 base ones then `m0…m23` (file order); one warning. 40 custom materials **without** the base ones → the base ones are re-added by replacing the last custom entries, size still 32. Same for 20 media (16 kept, `air` guaranteed) and 40 dimensions (32 kept).
- `restoresAirAndBaseMaterials`: settings without `air` and without `wool` → both present with default values.
- `dimensionMediumFallsBackToAir`: `dimensionMedium("minecraft:overworld")` is `air`; with `{"ad_astra:moon": "vacuum"}`, `dimensionMedium("ad_astra:moon")` is vacuum.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.*"`
Expected: FAIL, compilation errors (classes missing).

- [ ] **Step 3: Implement the four classes**

`Interfaces.lossDb`: `T = 4·Z₁·Z₂ / (Z₁+Z₂)²`; if `Z₁+Z₂ == 0` or `T == 0` → `+∞`; else `−10·log10(T) × interfaceScale`. `clamped`: bound scalars like `AcousticSettings.bound`, filter names, dedupe by name (first wins), cap, re-add `air` and base materials. Warning texts name the key path (`occlusion.materials.stone.lossDb`).

- [ ] **Step 4: Run them and watch them pass**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.*"`
Expected: PASS.

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL.
```bash
git add src/main/java/com/ravecraft/mod/core/acoustics/occlusion src/test/java/com/ravecraft/mod/core/acoustics/occlusion
git commit -m "Add acoustic materials, media and occlusion settings"
```

### Task 2: Straight-line tracer

**Files:**
- Create: `core/acoustics/occlusion/AcousticGrid.java`, `VoxelSample.java`, `TraceBudget.java`, `PathLoss.java`, `OcclusionTracer.java` (all under `src/main/java/com/ravecraft/mod/`)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/TestGrid.java` (in-memory grid helper), `OcclusionTracerTest.java`

**Interfaces:**
- Consumes: Task 1 types, `Interfaces`.
- Produces:
  - `final class VoxelSample { @Nullable AcousticMaterial material; double fillX, fillY, fillZ; AcousticMedium medium; double fill(int axis) }` (axis 0 = X, 1 = Y, 2 = Z; mutable, reused by the tracer).
  - `interface AcousticGrid { void sample(int x, int y, int z, VoxelSample out); }`
  - `final class TraceBudget { TraceBudget(int voxels); boolean take(); boolean exhausted(); int used(); }` — `take()` consumes one voxel, returns false once empty.
  - `record PathLoss(double lossDb, double highLossDb, double length)` with `boolean mute()` (= `lossDb` infinite).
  - `final class OcclusionTracer { OcclusionTracer(AcousticGrid grid, OcclusionSettings settings); @Nullable PathLoss trace(double x0, double y0, double z0, double x1, double y1, double z1, TraceBudget budget); }` — `null` when the budget ran out.

- [ ] **Step 1: Write the failing tests**

`TestGrid implements AcousticGrid`: air everywhere by default; `solid(x, y, z, material)` (fills 1), `slab(x, y, z, material)` (fillY 0.5, fillX/fillZ 0.5), `fluid(x, y, z, medium)`; `wall(x, material)` fills the plane x = const for y, z in [−8, 8].

`OcclusionTracerTest` (listener at (0.5, 0.5, 0.5), source at (6.5, 0.5, 0.5) unless said):
- `emptyGridLosesNothing`: loss 0, highLoss 0, length 6.
- `oneStoneWall`: `wall(3, stone)` → lossDb 15, highLossDb 15 (±1e-9).
- `fiveWallsCappedAtMax`: walls at x = 1…5 → lossDb 60, highLossDb 60 (`highLossDb` is capped at `maxLossDb` too).
- `slabCountsHalfAlongY`: listener (0.5, 0.5, 0.5), source (0.5, 6.5, 0.5), `slab(0, 3, 0, stone)` → lossDb 7.5.
- `diagonalThroughCornerGrazesOnly`: stone at (1, 1, 0); ray from (0.5, 0.5, 0.5) to (2.4, 2.6, 0.5) clips that voxel's corner over length ℓ < 0.2 → lossDb = 15 × ℓ (compare with the exact segment length computed in the test).
- `rayAlongGridLineCountsWallOnce`: listener (0, 0.5, 0.5), source (6, 0.5, 0.5), `wall(3, stone)` → lossDb 15.
- `endpointsInsideSolidAddNothing`: stone at the listener's and the source's voxels, no wall between → lossDb 0.
- `airToWaterInterface`: water for x ≥ 3 → lossDb within 0.3 of 19.8, highLossDb within 0.3 of 9.9 + 0.02 × 3.5.
- `bothUnderwaterNoInterface`: water everywhere → lossDb 0, highLossDb within 1e-6 of 0.02 × 6.
- `airPathLossIsLeftToPropagation`: empty grid → highLossDb 0 (air absorption is applied by `Propagation`).
- `waterloggedSlabIsMaterialAndMedium`: listener and source in water, `slab` at x = 3 also water → lossDb 7.5 + 0, no interface.
- `vacuumAnywhereIsMute`: vacuum at x = 3 → `mute()`; vacuum at the listener's voxel → `mute()`.
- `budgetStopsTrace`: `new TraceBudget(3)` on the 6-block path → returns `null`, `exhausted()` true.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.OcclusionTracerTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement `OcclusionTracer.trace`**

Amanatides–Woo voxel walk from the start voxel to the end voxel; for each voxel compute `ℓ` = parametric length inside it × segment length and the entry axis (the axis whose boundary was crossed; for the start voxel, the dominant axis of the direction). One `budget.take()` per voxel visited. Per voxel, using one `VoxelSample`:
- skip material on the first and last voxel; else `loss += m.lossDb × fill(axis) × min(1, ℓ)`, same for `highLossDb`;
- medium: if not the `air` medium, `loss += lossDbPerBlock × ℓ`, `high += highLossDbPerBlock × ℓ`;
- if the medium differs from the previous voxel's (a voxel whose sample medium equals the current one counts as no change; a solid voxel reports the fluid it contains or its position's medium as given by the grid) add `Interfaces.lossDb` and its `highLossDb`;
- any medium with `impedance == 0` on the path, endpoints included → return a mute `PathLoss`.
Cap `lossDb` and `highLossDb` at `maxLossDb` (mute stays infinite). A ray along a grid line: break ties between axes deterministically (lowest axis index first) and let a zero-length voxel (`ℓ < 1e-9`) add nothing.

- [ ] **Step 4: Run them and watch them pass**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.OcclusionTracerTest"`
Expected: PASS.

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL.
```bash
git add src/main/java/com/ravecraft/mod/core/acoustics/occlusion src/test/java/com/ravecraft/mod/core/acoustics/occlusion
git commit -m "Trace straight-line sound loss through materials and media"
```

### Task 3: Detours and combined occlusion

**Files:**
- Modify: `core/acoustics/occlusion/OcclusionTracer.java`
- Create: `core/acoustics/occlusion/Occlusion.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/DetourTest.java`

**Interfaces:**
- Consumes: Task 2 `trace`, `TraceBudget`, `TestGrid`.
- Produces:
  - `record Occlusion(double lossDb, double highLossDb)` with `static final Occlusion NONE` (0, 0) and `static final Occlusion MUTE` (∞, 0).
  - `OcclusionTracer.@Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz, double rolloff, double maxPathLength, TraceBudget budget)` — `null` when the budget ran out; `Occlusion.NONE` when `enabled` is false (no voxel visited).

- [ ] **Step 1: Write the failing tests**

Room fixture: stone shell enclosing x, z ∈ [0, 6], y ∈ [0, 4] (walls at x = −1 / 7, z = −1 / 7, floor y = −1, ceiling y = 5); source at (3.5, 1.5, 3.5) inside; listener outside at (12.5, 1.5, 3.5).
- `closedRoomIsDirectLoss`: no opening → lossDb 15 (one wall on the straight line), highLossDb 15.
- `openDoorLetsSoundAround`: 1×2 opening in the wall x = 7 at z = 1, y = 0..1 (not on the straight line, which crosses z = 3) → lossDb < 10 and highLossDb > 0 (bend penalty on the treble).
- `twoOpeningsLouderThanOne`: add a second opening at z = 5 → lossDb lower than with one opening.
- `lCorridorNeedsTwoBends`: source and listener in two rooms joined only by an L-shaped corridor (no single waypoint sees both) → lossDb at least 8 dB below the direct loss.
- `detourLongerThanRangeIgnored`: `openDoorLetsSoundAround` fixture with `maxPathLength` 9.5 (direct 9) → same loss as `closedRoomIsDirectLoss`.
- `clearPathTriesNoDetour`: empty grid → `Occlusion` 0/0 and `budget.used()` equals the direct trace's voxel count (7 on the 6-block path).
- `detoursDisabledUsesDirectOnly`: `detours = false`, open-door fixture → 15/15.
- `disabledVisitsNothing`: `enabled = false` → `NONE`, `budget.used()` 0.
- `mutedListenerStaysMute`: vacuum at the listener → `MUTE`, even with an opening.
- `budgetExhaustedReturnsNull`: open-door fixture, `new TraceBudget(20)` → `null`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.DetourTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement `occlusion(...)`**

```
direct = trace(L, S); null → return null
if !detours or direct.lossDb < 3 → result from direct only
candidates(anchor, other): for the 26 unit directions d (normalised) and r in {3, 6}:
    P = centre of the voxel containing anchor + d·r
    keep if sample(P) has no material, same medium as anchor's voxel,
         and trace(anchor, P).lossDb < 3 (not mute)
    keep the 6 closest to `other`
nearL = candidates(L, S), nearS = candidates(S, L)
paths: S→P→L for P in nearL ∪ nearS; S→Q→P→L for Q in nearS, P in nearL
path cost: Σ segment losses (each segment traced; null → return null; mute → skip path)
         + 20·log10(length / directDistance)·rolloff (directDistance > 0)
         + per bend: bendLossDb·angle/90 (broadband), bendHighLossDb·angle/90 (treble),
           angle = deviation in degrees between the incoming and outgoing directions
skip paths with length > maxPathLength
combine direct + the 3 detours with the lowest broadband loss:
    L = −10·log10 Σ 10^(−lossᵢ/10)
    T = −10·log10 Σ 10^(−(lossᵢ + highᵢ)/10)       (total treble loss per path)
    result = (min(L, maxLossDb), clamp(T − L, 0, maxLossDb)); all mute → MUTE
```
Reuse trace results of `anchor → P` segments within one call (a `HashMap` keyed by the waypoint's voxel).

- [ ] **Step 4: Run them and watch them pass**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.*"`
Expected: PASS (Task 1–3 tests).

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL.
```bash
git add src/main/java/com/ravecraft/mod/core/acoustics/occlusion src/test/java/com/ravecraft/mod/core/acoustics/occlusion
git commit -m "Let sound detour through nearby openings"
```

### Task 4: Block materials and media in the world

**Files:**
- Create: `src/main/java/com/ravecraft/mod/world/acoustics/AcousticTags.java`, `BlockAcoustics.java`, `MediumResolver.java`
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftFluidTagProvider.java`
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftBlockTagProvider.java`, `RaveCraftDataGenerator.java`
- Test: `src/gametest/java/com/ravecraft/mod/gametest/BlockAcousticsGameTest.java`; register it in `src/gametest/resources/fabric.mod.json`

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
  - `AcousticTags`: `static TagKey<Block> material(String name)` → `ravecraft:acoustic/<name>`; `static TagKey<Fluid> medium(String name)` → `ravecraft:medium/<name>` (cached per name).
  - `BlockAcoustics`: `BlockAcoustics(OcclusionSettings settings)`; `@Nullable AcousticMaterial material(BlockState state)`; `double fill(BlockState state, Direction.Axis axis)`; `void sample(BlockState state, AcousticMedium medium, VoxelSample out)` (fills material, the three fills and the given medium); `void clearCache()`. Cache: `ConcurrentHashMap<BlockState, Entry>` (not thread-confined: GameTests and the client thread).
  - `MediumResolver`: `MediumResolver(OcclusionSettings settings)`; `AcousticMedium medium(FluidState fluid, String dimensionId)` — the first medium (settings order) whose fluid tag contains a non-empty fluid, else `settings.dimensionMedium(dimensionId)`.

- [ ] **Step 1: Write the failing GameTests**

`BlockAcousticsGameTest` (each builds `new BlockAcoustics(OcclusionSettings.DEFAULT)` and reads `Blocks.X.defaultBlockState()`; no world placement needed except where noted):
- `stoneIsStone`, `planksAreWood`, `glassIsGlass`, `ironBlockIsMetal`, `whiteWoolIsWool`, `sandIsEarth`, `snowBlockIsSnow`.
- `leavesAreFoliageDespiteGrassSound`: `Blocks.OAK_LEAVES` → `foliage` (its `SoundType` is `GRASS`, which maps to `earth`).
- `unknownSolidFallsBackToStone`: `Blocks.SHROOMLIGHT` (sound `SHROOMLIGHT`, absent from the table) → `stone`.
- `noCollisionNoMaterial`: `Blocks.SHORT_GRASS`, `Blocks.TORCH`, `Blocks.AIR` → `null`.
- `bottomSlabFillsHalf`: `Blocks.STONE_SLAB` default (bottom) → fill Y 0.5, fill X 0.5.
- `closedDoorSealsItsAxis`: `Blocks.OAK_DOOR` facing north, closed, lower half → fill Z 1.0; open → fill Z < 0.25.
- `glassPaneSealsFaceOn`: pane connected east and west → fill Z 1.0.
- `carpetCountsByVolume`: `Blocks.WHITE_CARPET` → fill Y 1/16 (±1e-6).
- `waterloggedSlabIsWoodInWater`: place a waterlogged `OAK_SLAB` with `h.setBlock`; material `wood`, `MediumResolver.medium(state.getFluidState(), dim)` = `water`.
- `lavaIsLava`: `Blocks.LAVA.defaultBlockState().getFluidState()` → `lava`; empty fluid in `"minecraft:overworld"` → `air`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew runGameTest`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`material`: first tag hit in `settings.materials()` order; else the footstep table below (only if the named material exists in the settings, which it always does for base names); else `stone` if the collision shape is not empty; else `null`. Fill per axis from `getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()`: `1` if one box spans the full [0, 1] range on both other axes and is ≥ 2/16 thick on this axis; else the summed box volume, capped at 1.

| Material | `SoundType` constants |
|---|---|
| wood | WOOD, NETHER_WOOD, BAMBOO_WOOD, CHERRY_WOOD, LADDER, SCAFFOLDING, BAMBOO, STEM, CHISELED_BOOKSHELF, SHELF, HANGING_SIGN, NETHER_WOOD_HANGING_SIGN, BAMBOO_WOOD_HANGING_SIGN, CHERRY_WOOD_HANGING_SIGN, CREAKING_HEART, MANGROVE_ROOTS |
| glass | GLASS, AMETHYST, AMETHYST_CLUSTER, SMALL_AMETHYST_BUD, MEDIUM_AMETHYST_BUD, LARGE_AMETHYST_BUD |
| metal | METAL, IRON, ANVIL, LANTERN, CHAIN, NETHERITE_BLOCK, COPPER, COPPER_BULB, COPPER_GRATE, COPPER_GOLEM_STATUE, HEAVY_CORE |
| earth | GRAVEL, SAND, SUSPICIOUS_SAND, SUSPICIOUS_GRAVEL, GRASS, ROOTED_DIRT, MUD, MUDDY_MANGROVE_ROOTS, SOUL_SAND, SOUL_SOIL, MOSS, NYLIUM, SLIME_BLOCK, HONEY_BLOCK, RESIN, SCULK |
| snow | SNOW, POWDER_SNOW |
| wool | WOOL, SPONGE, WET_SPONGE, STRAW_BED |
| foliage | AZALEA_LEAVES, CHERRY_LEAVES, POPLAR_LEAVES, AZALEA, FLOWERING_AZALEA, VINE, CAVE_VINES, WART_BLOCK, BIG_DRIPLEAF |
| stone | everything else with a collision shape |

Datagen: block tags `ravecraft:acoustic/<name>` for all 8 base names (`foliage` ← `#minecraft:leaves`; `wool` ← `#minecraft:wool`, `#minecraft:wool_carpets`; `wood` ← `#minecraft:planks`, `#minecraft:logs`; the five others empty). Fluid tags `ravecraft:medium/water` ← `#minecraft:water`, `ravecraft:medium/lava` ← `#minecraft:lava`; `RaveCraftFluidTagProvider extends FabricTagsProvider.FluidTagsProvider`, added in `RaveCraftDataGenerator`.

- [ ] **Step 4: Regenerate and run them**

Run: `./gradlew runDatagen && ./gradlew runGameTest`
Expected: the generated files appear under `src/main/generated/data/ravecraft/tags/block/acoustic/` and `.../tags/fluid/medium/`; all GameTests PASS.

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL.
```bash
git add src/main/java/com/ravecraft/mod/world/acoustics src/client/java/com/ravecraft/mod/datagen src/main/generated src/gametest
git commit -m "Map blocks and fluids to acoustic materials and media"
```

### Task 5: Server config and network

**Files:**
- Modify: `core/acoustics/AcousticSettings.java`, `config/AcousticSettingsJson.java`, `config/AcousticConfig.java`, `network/payload/AcousticSettingsPayload.java`
- Modify (constructor call sites): `src/test/java/.../PropagationTest.java`, `src/gametest/java/.../AudioRelayGameTest.java`
- Test: `AcousticSettingsTest`, `AcousticSettingsJsonTest`, `AcousticSettingsPayloadTest`, new `src/test/java/com/ravecraft/mod/config/AcousticConfigTest.java`

**Interfaces:**
- Consumes: Task 1 `OcclusionSettings`.
- Produces:
  - `record AcousticSettings(double rolloff, double fadeFraction, double audibilityThresholdDb, double maxRange, DeviceProfile portable, OcclusionSettings occlusion)` + method `double airAbsorptionDbPerBlock()`; `clamped` also clamps `occlusion`.
  - `AcousticSettingsJson.Result read(Reader in, Consumer<String> warn)` with `record Result(AcousticSettings settings, boolean complete)` — `complete` is false when any setting was missing or the old `airAbsorptionDbPerBlock` key was present.
  - `AcousticConfig.load(Path)` rewrites an incomplete file after copying it to `<name>.bak`.

- [ ] **Step 1: Write the failing tests**

- `AcousticSettingsTest.clampsOcclusionToo`: `maxLossDb` 500 → 120 through `AcousticSettings.clamped`.
- `AcousticSettingsJsonTest`:
  - `readsOcclusionSection`: a file with `"occlusion": { "maxLossDb": 40, "materials": { "stone": { "lossDb": 10, "highLossDb": 5 }, "rubber": { "lossDb": 25, "highLossDb": 30 } }, "dimensions": { "ad_astra:moon": "vacuum" } }` → those values, `rubber` present, `dimensionMedium("ad_astra:moon")` = vacuum, `complete` false (other keys missing).
  - `oldFileMigratesAndAsksRewrite`: a 4a file with `"airAbsorptionDbPerBlock": 0.1` and no `occlusion` → `air().highLossDbPerBlock()` 0.1, `complete` false.
  - `writtenDefaultsAreComplete`: `write(DEFAULT)` then `read` → equals `DEFAULT` and `complete` true.
  - `customListsRoundTrip`: settings with `rubber` and a dimension entry → write then read equal.
  - `defaultFileExplainsEverySetting`: extend the key list with `"occlusion":`, `"enabled":`, `"maxLossDb":`, `"media":`, `"impedance":`, `"lossDbPerBlock":`, `"highLossDbPerBlock":`, `"interfaceScale":`, `"interfaceHighRatio":`, `"dimensions":`, `"materials":`, `"lossDb":`, `"highLossDb":`, `"detours":`, `"bendLossDb":`, `"bendHighLossDb":`; each preceded by a `//` line; `"airAbsorptionDbPerBlock":` no longer written.
- `AcousticConfigTest.rewritesIncompleteFileWithBackup` (temp dir): write a 4a file, `load` → `ravecraft-server.json5.bak` holds the old text, the new file contains `"occlusion":` and `"highLossDbPerBlock": 0.1` under `air`; `AcousticConfig.get()` has that value. `leavesCompleteFileAlone`: a complete file is not rewritten (no `.bak`, same bytes).
- `AcousticSettingsPayloadTest`: `roundTripsOcclusion` (custom material, dimension, every scalar); `encodesClampedSettingsAtCaps` (40 materials → clamped → encodes without exception, decodes 32); `decodeBoundsOcclusionValues` (encode a hand-built buffer with `maxLossDb` 500 → decoded 120).

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew test --tests "com.ravecraft.mod.config.*" --tests "com.ravecraft.mod.network.payload.*" --tests "com.ravecraft.mod.core.acoustics.AcousticSettingsTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

JSON: section written after `"profiles"`, with comments as in spec §7.1 / the brainstormed file (one `//` explanation per key, bounds included); media and materials written as one-line objects in list order. Reading: missing keys take defaults and set `complete = false`; `materials`/`media` entries replace defaults by name, unknown names are added. Payload: nested codecs `MATERIAL` (name `stringUtf8(32)`, 2 doubles), `MEDIUM` (name, 3 doubles), `DIMENSION` (id `stringUtf8(64)`, medium `stringUtf8(32)`), lists with `list(MAX_…)`, an `OCCLUSION` composite (10 fields), decode → `.clamped(RaveCraft.LOGGER::warn)` on the whole `AcousticSettings`. `AcousticConfig.load`: if `!result.complete()`, `Files.copy(file, bak, REPLACE_EXISTING)` then `write`; log one info line.

- [ ] **Step 4: Run them and watch them pass**

Run: same command as Step 2, then `./gradlew test`.
Expected: PASS.

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL (GameTests included).
```bash
git add src/main/java/com/ravecraft/mod src/test/java/com/ravecraft/mod src/gametest/java
git commit -m "Add occlusion settings to the server config and payload"
```

### Task 6: Client scheduling and rendering

**Files:**
- Create: `core/acoustics/occlusion/OcclusionRota.java`, `core/acoustics/occlusion/LossSmoother.java`
- Create: `src/client/java/com/ravecraft/mod/client/audio/occlusion/WorldAcousticGrid.java`, `OcclusionScheduler.java`, `src/client/java/com/ravecraft/mod/client/options/OcclusionQuality.java`
- Modify: `client/audio/render/RenderStream.java`, `AcousticRenderer.java`, `client/options/RaveCraftOptions.java`, `client/mixin/SoundOptionsScreenMixin.java`, `client/RaveCraftClient.java`, `assets/ravecraft/lang/en_us.json`, `fr_fr.json`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/OcclusionRotaTest.java`, `LossSmootherTest.java`

**Interfaces:**
- Consumes: Tasks 1–5 (`OcclusionTracer.occlusion`, `BlockAcoustics.sample`, `MediumResolver.medium`, `AcousticSettings.occlusion()`).
- Produces:
  - `OcclusionRota`: `List<Integer> pick(List<Integer> audibleByGain, long tick)` — ids due this tick: new ids (never picked before), then ids left unfinished last tick, then those with `id`'s slot `== tick % 5` (slot assigned on first pick, round-robin); `void unfinished(int id)`; `void forget(Set<Integer> alive)`.
  - `LossSmoother`: `LossSmoother(double rampMs)`; `double step(double target, double dtMs)` — moves linearly toward `target` at `|target − start| / rampMs` per ms, reaching it in `rampMs` (200); first call jumps straight to the target; infinite target ramps to 120 then jumps to ∞ (mute).
  - `RenderStream`: `volatile double occlusionLossDb, occlusionHighLossDb`, `volatile boolean occlusionKnown`; audio-thread `LossSmoother lossRamp, highRamp`.
  - `OcclusionQuality` enum `LOW(5_000)`, `MEDIUM(20_000)`, `HIGH(60_000)` with `int voxelsPerTick()`; option key `ravecraft_occlusionQuality`, default `MEDIUM`.

- [ ] **Step 1: Write the failing tests**

- `OcclusionRotaTest`: `newStreamsFirst` (an id seen for the first time is in `pick` immediately); `eachStreamEveryFifthTick` (10 ids over 5 ticks → each picked exactly once after the first tick); `rotaPutsUnfinishedFirst` (`unfinished(7)` → 7 first next tick, ahead of its slot peers); `forgetsDroppedStreams`.
- `LossSmootherTest`: `firstValueJumps`; `reachesTargetIn200ms` (0 → 20: 10 after 100 ms, 20 after 200 ms, stays); `muteRampsThenSilences` (∞ target: finite and rising before 200 ms, ∞ after).

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew test --tests "com.ravecraft.mod.core.acoustics.occlusion.OcclusionRotaTest" --tests "com.ravecraft.mod.core.acoustics.occlusion.LossSmootherTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement the core pieces, run the tests to PASS**

- [ ] **Step 4: Wire the client**

- `WorldAcousticGrid implements AcousticGrid` over a `ClientLevel`, a `BlockAcoustics` and a `MediumResolver`: unloaded chunk (`!level.hasChunk(x >> 4, z >> 4)`) → air, no material; otherwise `BlockAcoustics.sample(state, resolver.medium(state.getFluidState(), level.dimension().identifier().toString()), out)` (`ResourceKey.identifier()` checked in 26.3).
- `OcclusionScheduler.tick(Minecraft)` on `END_CLIENT_TICK`, registered after `AcousticRenderer.register()`: rebuild `BlockAcoustics`/`MediumResolver` when `AcousticSettingsClient.get().occlusion()` changes (identity) and clear the cache on `CommonLifecycleEvents.TAGS_LOADED` with `client == true`; audible = attached streams that are powered with volume > 0 and within `Propagation.range(...)` of the listener, sorted by last gain (descending); a `TraceBudget(quality.voxelsPerTick())` per tick; for each picked stream, `occlusion(listener, speaker centre, rolloff, range, budget)`: `null` → `rota.unfinished(id)` and stop the tick; else publish into the stream and set `occlusionKnown`. Streams not audible get `occlusionKnown = false`. When `occlusion.enabled()` is false, publish 0/0 without tracing.
- `AcousticRenderer` (`AcousticRenderer` exposes the attached streams to the scheduler through a package-private accessor or by moving the scheduler into `client.audio.render`; the implementer picks one and keeps `RenderStream` package-private): each step, `lossDb = lossRamp.step(stream.occlusionLossDb, dt)` (0 until `occlusionKnown`), same for treble; `stream.gain *= 10^(−lossDb/20)` **before** `SourceSelector.select`; source high gain = air absorption × `10^(−highDb/20)` when `efxWanted`, else 1.
- Player option row: `list.addSmall(RaveCraftOptions.acousticEffects(), RaveCraftOptions.occlusionQuality())`; `process` adds `access.process("ravecraft_occlusionQuality", OCCLUSION_QUALITY)`; the acoustic effects tooltip now also mentions muffling.
- `RaveCraftClient`: log one info line when `FabricLoader.getInstance().isModLoaded("sound_physics_remastered")`.
- Lang (`en_us` / `fr_fr`): `options.ravecraft.occlusionQuality` "Occlusion quality" / "Qualité de l'occlusion"; `options.ravecraft.occlusionQuality.low|medium|high` "Low/Medium/High" / "Basse/Moyenne/Haute"; tooltip `options.ravecraft.occlusionQuality.tooltip` "How much work is spent finding how sound gets around walls. Lower it if your frame rate drops near many speakers." / "Effort consacré à trouver comment le son contourne les murs. Baissez-la si les FPS chutent près de nombreuses enceintes."; update `options.ravecraft.acousticEffects.tooltip` to "Muffled treble through walls and water, air absorption. Turn off if your audio driver has trouble." / "Aigus étouffés par les murs et l'eau, absorption de l'air. Désactivez en cas de souci avec le pilote audio.".

- [ ] **Step 5: Build, smoke-run and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL. Then `./gradlew runClient > build/client-smoke.log 2>&1` (background), wait for the title screen, stop **only that process**; `grep -i "mixin\|exception\|ravecraft" build/client-smoke.log`.
Expected: no mixin error, no RaveCraft exception (the JNA/oshi registry exception is vanilla noise).
```bash
git add src/main/java/com/ravecraft/mod/core src/client src/main/resources/assets src/test
git commit -m "Muffle speakers through blocks on the client"
```

### Task 7: Docs and final checks

**Files:**
- Modify: `docs/superpowers/specs/2026-11-17-acoustic-occlusion-design.md` (§4.2 sealing threshold 2/16, with the pane reason), `docs/SPEC.md` (step 4b line: done pending in-game checks), `README.md` only if it lists config keys.

- [ ] **Step 1: Update the docs** as listed above; the SPEC line mentions materials and media by tag, detours, and the `.json5` `"occlusion"` section.
- [ ] **Step 2: Full verification**

Run: `./gradlew runDatagen && git status --short src/main/generated && ./gradlew build`
Expected: no generated diff left uncommitted; BUILD SUCCESSFUL; JUnit and GameTest counts above 4a's (148 / 51).
- [ ] **Step 3: Commit**
```bash
git add docs
git commit -m "Update spec progress for acoustic occlusion"
```

In-game checklist for the PR (from spec §9): door opened/closed while playing; diving next to a surface speaker and two players underwater; speaker in a 4-wall stone cave; 16 audible speakers at Medium without FPS drop; Sound Physics Remastered installed.
