# Acoustic Debug View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Draw in the world what the acoustic engine computed (sound paths through blocks and openings, room probe rays, echo), toggled with F3+U or two F3+F6 entries.

**Architecture:** `core` gets an optional `PathCapture` filled by `OcclusionTracer` during its normal traces, `EchoFinder.group`, and immutable view snapshots built by pure factories. The client schedulers pass a capture and publish snapshots only while a layer is on; `SoundViewRenderer` turns the snapshots into vanilla gizmos in `LevelExtractionEvents.END_EXTRACTION`. Toggling uses two no-op `DebugScreenEntry`s and a `KeyboardHandler` mixin.

**Tech Stack:** Java 25, Fabric API 0.166.2+26.3 (`fabric-rendering-v1` `LevelExtractionEvents`, `fabric-key-mapping-api-v1` `KeyMappingHelper`), Mojang names, vanilla `net.minecraft.gizmos.Gizmos`, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-11-19-acoustic-debug-view-design.md](../specs/2026-11-19-acoustic-debug-view-design.md)

## Global Constraints

- Export `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"` before any Gradle command.
- `src/main/java` never imports `net.minecraft.client`; everything in `core` is pure Java (no Minecraft types).
- With no layer on: no capture object is passed, no snapshot is allocated, no gizmo is emitted.
- No extra tracing for display: only the results of the existing occlusion and reverb ticks are shown.
- Entries: `ravecraft:sound_paths`, `ravecraft:sound_rooms`, category `AcousticDebugEntry.CATEGORY`.
- Key: `key.ravecraft.debug.soundView`, default `InputConstants.KEY_U`, `KeyMapping.Category.DEBUG`.
- Cycle: NONE → PATHS → ROOMS → ALL → NONE.
- Colours: 0 dB `0xFF5DCAA5`, 10 dB `0xFFEF9F27`, ≥30 dB and mute `0xFFE24B4A`, openings `0xFF85B7EB`, open rays `0xFF888780`, echo `0xFFFAC775`.
- Caps: 8 paths (loudest), 64 crossings per path, 3 detours, 48 rays per room.
- Code, comments and commits in English; no Claude attribution line in commits.
- Do not touch `feat/speaker-ui` work; do not push.

## Review Focus

- Occlusion disabled by the server (`OcclusionTracer.occlusion` returns `Occlusion.NONE` early): the capture must still be reset and show a direct path with no crossings, not the previous trace's crossings. → test in Task 2.
- Direct path muted by vacuum mid-way: the crossings recorded up to the mute point stay, the view is marked mute. → test in Task 2 and Task 4.
- Budget runs out (occlusion returns null): the scheduler must not publish a half-filled capture. → Task 6 step.
- Listener's head in a fluid: no rays, only the fluid label; leaving the fluid must not show a stale room. → test in Task 4, step in Task 6.
- Layer turned off then on: old snapshots cleared on off, so no stale drawing reappears. → Task 6 step.

---

### Task 1: View mode and colours (core)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/debug/view/SoundViewMode.java`
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/debug/view/LossColors.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/debug/view/SoundViewModeTest.java`, `LossColorsTest.java`

**Interfaces:**
- Produces: `enum SoundViewMode { NONE, PATHS, ROOMS, ALL; static SoundViewMode of(boolean paths, boolean rooms); SoundViewMode next(); boolean paths(); boolean rooms(); }`
- Produces: `final class LossColors { static final int OPENING, OPEN_RAY, ECHO; static int forLoss(double lossDb); static int forAbsorption(double absorption); static int withAlpha(int argb, float alpha); }` (constants from Global Constraints).

- [ ] **Step 1: Write the failing tests**
  - `cycle`: `NONE.next()==PATHS`, `PATHS.next()==ROOMS`, `ROOMS.next()==ALL`, `ALL.next()==NONE`.
  - `ofFlags`: `of(false,false)==NONE`, `of(true,false)==PATHS`, `of(false,true)==ROOMS`, `of(true,true)==ALL`; `ALL.paths() && ALL.rooms()`, `!ROOMS.paths()`.
  - `stops`: `forLoss(0)==0xFF5DCAA5`, `forLoss(10)==0xFFEF9F27`, `forLoss(30)==0xFFE24B4A`, `forLoss(45)==0xFFE24B4A`, `forLoss(Double.POSITIVE_INFINITY)==0xFFE24B4A`, `forLoss(-1)==0xFF5DCAA5`.
  - `interpolates`: `forLoss(5)` red channel == `(0x5D+0xEF)/2` rounded, alpha 0xFF.
  - `absorption`: `forAbsorption(0)==forLoss(0)`, `forAbsorption(1)==forLoss(30)`, `forAbsorption(0.5)==forLoss(15)`.
  - `alpha`: `withAlpha(0xFF123456, 0.4f)==0x66123456`.
- [ ] **Step 2: Run** `./gradlew test --tests "*SoundViewModeTest" --tests "*LossColorsTest"` → FAIL (classes missing).
- [ ] **Step 3: Implement.** `forLoss`: linear per channel between stops (0,10) then (10,30), rounding with `Math.round`; NaN treated as mute. `forAbsorption(a) = forLoss(clamp(a,0,1)*30)`.
- [ ] **Step 4: Run** same command → PASS.
- [ ] **Step 5: Commit** `git commit -m "Add sound view mode cycle and loss colours"`.

### Task 2: Path capture in the occlusion tracer (core)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/acoustics/occlusion/PathCapture.java`
- Modify: `src/main/java/com/ravecraft/mod/core/acoustics/occlusion/OcclusionTracer.java` (`trace`, `occlusion`, `Route`, `route`)
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/occlusion/PathCaptureTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class PathCapture {
      public static final int MAX_STEPS = 64;
      public record Step(int x, int y, int z, String name, double lossDb) {}
      public record CapturedDetour(List<double[]> points, double lossDb) {} // points source → listener
      public List<Step> steps();          // listener → source order, as walked
      public int truncated();
      public List<CapturedDetour> detours();
      public @Nullable PathLoss direct();
      public void reset();                // called by the tracer at the start of occlusion()
      // package-private: void step(int x,int y,int z,String name,double lossDb); void direct(PathLoss); void detour(double[][] pts,double lossDb)
  }
  ```
- Produces on `OcclusionTracer`: `public @Nullable Occlusion occlusion(double lx, double ly, double lz, double sx, double sy, double sz, double rolloff, double maxPathLength, TraceBudget budget, @Nullable PathCapture capture)`. The existing 9-arg public overload delegates with `null`; the package-private `maxDetours` overload gains the capture parameter too (existing tests call the 10-arg `maxDetours` form: keep a package-private overload with that exact signature delegating with `null`).

- [ ] **Step 1: Write the failing tests** (reuse `TestGrid`, `OcclusionSettings.DEFAULT`, stone = 15 dB):
  - `stoneWallIsOneStep`: grid `new TestGrid().wall(3, STONE)`, occlusion from listener (0.5,0.5,0.5) to source (6.5,0.5,0.5) with a capture → `steps()` has one step at (3,0,0), name `"stone"`, lossDb 15 (1e-9); `direct().lossDb()==15`.
  - `endsAreNotSteps`: listener and source voxels solid stone, nothing between → `steps()` empty.
  - `waterGivesMediumAndInterfaces`: air→water box→air along x (use `fluidBox`); steps include names `"air→water"` and `"water→air"` with the `Interfaces.lossDb` value, and water voxels named `"water"` whose losses sum to the water absorption over the length crossed.
  - `truncatesAt64`: 70 single-block stone walls between ends → `steps().size()==64`, `truncated()==6`.
  - `detoursAreTheSummedOnes`: the `openDoor()` room from `DetourTest` → `detours()` non-empty, ≤3, each detour's first point is the source and last the listener, its `lossDb` < `direct().lossDb()`; the occlusion returned equals the one returned without capture (assertEquals on both fields).
  - `noCaptureUnchanged`: same scenes with `null` capture give identical `Occlusion` to before (existing `DetourTest`/`OcclusionTracerTest` stay green).
  - `disabledResetsCapture`: capture pre-filled by a first call, then a call with `settings(enabled=false)` → `steps()` empty, `detours()` empty, `direct()==null`.
  - `vacuumKeepsStepsUntilMute`: stone at x=2, vacuum fluid at x=4 → steps contain the stone, `direct().mute()`.
- [ ] **Step 2: Run** `./gradlew test --tests "*PathCaptureTest"` → FAIL.
- [ ] **Step 3: Implement.**
  - Tracer field `@Nullable PathCapture recording`, set only around the direct `trace` call inside `occlusion` (`recording = capture; trace(...); recording = null`), so waypoint and detour traces never record.
  - In `trace`, per voxel with `inside >= MIN_LENGTH`, sum this voxel's broadband contribution (material + absorption added here + interface); if `recording != null` and the sum > 0, record one step. Name priority: material name if the material contributed, else `current.name() + "→" + own.name()` if an interface was crossed, else the medium name. Muting returns keep steps already recorded.
  - `Route` gains `double @Nullable [][] points` (set in `route` only when a capture is present); after choosing, `capture.detour(points, lossDb)` for each chosen route.
  - `occlusion` calls `capture.reset()` first (also before the `!settings.enabled()` early return) and `capture.direct(direct)` after the direct trace.
- [ ] **Step 4: Run** `./gradlew test --tests "*occlusion*"` → PASS (new and existing).
- [ ] **Step 5: Commit** `git commit -m "Capture the direct path and chosen detours of an occlusion trace"`.

### Task 3: Echo group (core)

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/core/acoustics/reverb/EchoFinder.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/reverb/EchoFinderTest.java`

**Interfaces:**
- Produces: `public static List<Integer> group(List<RayHit> hits, EchoSettings settings)` — ascending indices into `hits` of the winning group; empty when `find` would return `Echo.NONE`.

- [ ] **Step 1: Write the failing tests** in `EchoFinderTest`:
  - `groupIsTheWallRays`: `hits(6, 25, 0.02)` → `group(...) == List.of(0,1,2,3,4,5)`.
  - `noEchoNoGroup`: every case in the file that expects `Echo.NONE` also has `group(...).isEmpty()` (add asserts to those tests).
  - `groupIndicesSurviveShuffle`: walls placed at indices 3, 10, 17, 24, 31, 5 of 32 → group equals those indices sorted.
- [ ] **Step 2: Run** `./gradlew test --tests "*EchoFinderTest"` → FAIL.
- [ ] **Step 3: Implement** `group` by moving the candidate/grouping logic of `find` onto indices; `find` computes its echo from `group`'s members. Same tie rule (most members, then nearest centre).
- [ ] **Step 4: Run** `./gradlew test --tests "*reverb*"` → PASS.
- [ ] **Step 5: Commit** `git commit -m "Expose the rays of the echo group"`.

### Task 4: View snapshots (core)

**Files:**
- Create in `src/main/java/com/ravecraft/mod/core/acoustics/debug/view/`: `Vec.java`, `Crossing.java`, `Detour.java`, `PathView.java`, `Ray.java`, `RoomView.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/debug/view/PathViewTest.java`, `RoomViewTest.java`

**Interfaces:**
- Consumes: `PathCapture` (Task 2), `EchoFinder.group` (Task 3), `RoomShape`, `RayHit`, `RayDirections`, `ReverbSettings`.
- Produces:
  ```java
  record Vec(double x, double y, double z) {}
  record Crossing(int x, int y, int z, String name, double lossDb, double cumulativeDb) {}
  record Detour(List<Vec> points, double lossDb) {}
  record PathView(int sessionId, Vec source, Vec listener, double lossDb, double highLossDb, boolean mute,
                  List<Crossing> crossings, int truncated, List<Detour> detours) {
      static PathView of(int sessionId, Vec source, Vec listener, PathCapture capture, Occlusion occlusion);
  }
  record Ray(Vec end, boolean open, double absorption, boolean echo) {}
  record RoomView(Vec origin, List<Ray> rays, @Nullable RoomShape shape, double rt60, Echo echo, @Nullable String fluid) {
      static RoomView of(Vec origin, List<RayHit> hits, ReverbSettings settings);  // uses RoomShape.of, rt60(maxDecaySeconds), EchoFinder.find/group
      static RoomView fluid(Vec origin, String fluid);                           // no rays, null shape, Echo.NONE
  }
  ```
  All lists are `List.copyOf`.

- [ ] **Step 1: Write the failing tests**
  - `PathViewTest.soundOrder`: capture from a trace with stone (15 dB) at x=2 and wood (8 dB) at x=4, listener at x=0.5, source at x=6.5 → `crossings` ordered source → listener: wood (x=4, cumulative 8) then stone (x=2, cumulative 23).
  - `PathViewTest.copiesOcclusion`: `lossDb`, `highLossDb` come from the `Occlusion`, `mute == occlusion.mute()`, `truncated` from the capture, detour points converted in order.
  - `PathViewTest.immutable`: `crossings()` throws on `add`.
  - `RoomViewTest.endsAndOpenRays`: 16 hits (half open at rayLength 64, half closed at 5) → each closed ray end = origin + `RayDirections.sphere(16)[i]` × 5 (1e-9), open rays end at × 64 with `open==true`.
  - `RoomViewTest.echoMarks`: hits with an echo group → exactly the group's rays have `echo==true`; `echo()` equals `EchoFinder.find`.
  - `RoomViewTest.shapeAndRt60`: `shape()` equals `RoomShape.of(hits, rayLength)`, `rt60` equals `shape.rt60(settings.maxDecaySeconds())`.
  - `RoomViewTest.fluid`: `RoomView.fluid(o, "water")` → no rays, `fluid()=="water"`, `shape()==null`.
- [ ] **Step 2: Run** `./gradlew test --tests "*debug.view*"` → FAIL.
- [ ] **Step 3: Implement** the records and factories.
- [ ] **Step 4: Run** same → PASS.
- [ ] **Step 5: Commit** `git commit -m "Add immutable sound view snapshots"`.

### Task 5: Toggle — F3+F6 entries, F3+U, messages (client)

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/debug/SoundViewEntries.java`
- Create: `src/client/java/com/ravecraft/mod/client/mixin/KeyboardHandlerMixin.java`
- Modify: `src/client/resources/ravecraft.client.mixins.json` (add `KeyboardHandlerMixin`)
- Modify: `src/client/java/com/ravecraft/mod/client/RaveCraftClient.java` (call `SoundViewEntries.register()`)
- Modify: `src/main/resources/assets/ravecraft/lang/en_us.json`, `fr_fr.json`

**Interfaces:**
- Consumes: `SoundViewMode` (Task 1), `AcousticDebugEntry.CATEGORY` (make it package-visible or public as needed).
- Produces:
  ```java
  public final class SoundViewEntries {
      public static final Identifier PATHS;  // ravecraft:sound_paths
      public static final Identifier ROOMS;  // ravecraft:sound_rooms
      public static KeyMapping key();
      public static void register();         // entries + KeyMappingHelper.registerKeyMapping
      public static SoundViewMode mode();    // from minecraft.debugEntries.isCurrentlyEnabled(PATHS/ROOMS)
      public static Component cycle();       // applies mode().next(), returns the feedback text
  }
  ```

- [ ] **Step 1: Verify APIs** in the extracted 26.3 sources: `DebugScreenEntries.register(Identifier, DebugScreenEntry)`, `DebugScreenEntryList.setStatus/isOverlayVisible/isCurrentlyEnabled`, `KeyboardHandler.handleDebugKeys(KeyEvent)` (private, returns boolean), `KeyMapping.matches(KeyEvent)`, `Minecraft.showDebugChat(Component)`, and `KeyMappingHelper.registerKeyMapping` in `fabric-key-mapping-api-v1`.
- [ ] **Step 2: Implement.**
  - Entries: a private no-op `DebugScreenEntry` (empty `display`) whose `category()` is `AcousticDebugEntry.CATEGORY`.
  - `cycle()`: for each entry whose on-state changes, `setStatus(id, on ? (isOverlayVisible() ? IN_OVERLAY : ALWAYS_ON) : NEVER)`.
  - Mixin: `@Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)`; if `SoundViewEntries.key().matches(event)`, call `showDebugChat` with the same decoration vanilla uses (`[Debug]:` yellow bold prefix; `@Shadow` or replicate `decorateDebugComponent`) and `cir.setReturnValue(true)`.
  - Lang keys (en / fr): `key.ravecraft.debug.soundView` = "Cycle sound view" / "Changer la vue sonore"; `debug.ravecraft.sound_view.none` = "Sound view: off" / "Vue sonore : désactivée"; `.paths` = "Sound view: paths" / "Vue sonore : chemins"; `.rooms` = "Sound view: rooms" / "Vue sonore : pièces"; `.all` = "Sound view: paths and rooms" / "Vue sonore : chemins et pièces".
- [ ] **Step 3: Build** `./gradlew build` → BUILD SUCCESSFUL (mixin config validated by the GameTest server start).
- [ ] **Step 4: Commit** `git commit -m "Toggle the sound view with F3+U and two debug entries"`.

### Task 6: Publish snapshots from the schedulers (client)

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/audio/render/SoundViewSnapshots.java`
- Modify: `src/client/java/com/ravecraft/mod/client/audio/render/OcclusionScheduler.java`
- Modify: `src/client/java/com/ravecraft/mod/client/audio/render/ReverbScheduler.java`

**Interfaces:**
- Consumes: `SoundViewEntries.mode()` (Task 5), `PathView.of`, `RoomView.of/fluid` (Task 4), the capture overload (Task 2).
- Produces:
  ```java
  public final class SoundViewSnapshots {
      public static Collection<PathView> paths();       // unmodifiable view
      public static @Nullable RoomView listenerRoom();
      public static @Nullable RoomView slotRoom(int slot); // 0 or 1
      static void putPath(PathView view); static void retainPaths(Collection<Integer> audibleIds); static void clearPaths();
      static void listenerRoom(@Nullable RoomView view); static void slotRoom(int slot, @Nullable RoomView view); static void clearRooms();
  }
  ```

- [ ] **Step 1: OcclusionScheduler.** Once per tick read `SoundViewEntries.mode().paths()`. Off → `SoundViewSnapshots.clearPaths()` and pass `null` as capture. On → one reused `PathCapture`; in `Ops.occlusion`, call the capture overload and, when the result is non-null, `putPath(PathView.of(stream.sessionId, source centre, ear, capture, result))`. Never publish when the result is null (budget out) or for the `direct` fallback. After the pass, `retainPaths(AUDIBLE_IDS)`; also clear when there is no level.
- [ ] **Step 2: ReverbScheduler.** Read `mode().rooms()` once per tick. Off → `clearRooms()`. On → when the listener probe ends: `listenerRoom(RoomView.of(new Vec(listenerX, listenerY, listenerZ), probe.hits(), reverb))`; when the head is in a fluid: `listenerRoom(RoomView.fluid(ear, fluid.medium()))`; on leaving the fluid: `listenerRoom(null)` until the next measure ends; when slot i's probe ends: `slotRoom(i, RoomView.of(...speaker centre...))`; an empty slot → `slotRoom(i, null)`; `silence()` → `clearRooms()`.
- [ ] **Step 3: Build** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -m "Publish sound view snapshots while a layer is on"`.

### Task 7: Gizmo renderer (client)

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/debug/SoundViewRenderer.java`
- Modify: `RaveCraftClient.java` (`SoundViewRenderer.register()`)

**Interfaces:**
- Consumes: `SoundViewSnapshots` (Task 6), `SoundViewEntries.mode()` (Task 5), `LossColors` (Task 1).
- Produces: `public static void register()` → `LevelExtractionEvents.END_EXTRACTION.register(ctx -> emit())`.

- [ ] **Step 1: Verify** `LevelExtractionEvents.END_EXTRACTION` / `EndExtraction` signature in fabric-rendering-v1 27.3.1 and that a gizmo collector is active there (`Minecraft` wraps `gameRenderer.render()` in `levelRenderer.collectPerFrameRenderThreadGizmos()`).
- [ ] **Step 2: Implement `emit()`** (return at once when `mode()==NONE`; wrap the body in try/catch logging once per minute, as the schedulers do):
  - Paths (if `paths()`), 8 loudest by smallest `lossDb` among `paths()`: direct line split at each crossing's voxel entry point along source→listener, each piece coloured `forLoss(cumulative so far)`, width 2.5; crossing voxels `Gizmos.cuboid(new BlockPos(x,y,z), 0.02f, GizmoStyle.stroke(forLoss(lossDb)))` + `billboardText(name + " −" + %.1f + " dB", centre, Style.forColorAndCentered(white))`; detours as dashes (0.4 on, 0.4 off) `forLoss(lossDb)` width 1.5; each inner detour point `cuboid(blockPos, 0.3f, stroke(OPENING))` + text; label 0.8 above the source `"S<id> −x.x dB (treble −y.y)"` or `"S<id> mute"`, plus `" +N"` when truncated. Every path gizmo `.setAlwaysOnTop()`.
  - Rooms (if `rooms()`): listener room rays solid `forAbsorption` (echo rays `ECHO`), open rays dashed `OPEN_RAY`, `Gizmos.point(end, colour, 4f)` on closed rays, echo rays add `cuboid(end voxel, 0.0f, stroke(ECHO))`; summary text 1.2 above origin on two rows, format of spec §5.1; fluid → only `"in <fluid> (preset)"`. Slot rooms: same rays with `withAlpha(…, 0.4f)`, label `"S<slot+1> · RT60 x.xx s"`.
  - Numbers formatted with `Locale.ROOT`.
- [ ] **Step 3: Build** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `git commit -m "Draw the sound view with vanilla gizmos"`.

### Task 8: Docs, final build, in-game check

**Files:**
- Modify: `docs/SPEC.md` (audio base section: add the debug view line with the spec link)

- [ ] **Step 1:** Add to `docs/SPEC.md`, next to the 4c item: debug view delivered (F3+U, `ravecraft:sound_paths` / `sound_rooms`, spec link), "à vérifier en jeu".
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL, all JUnit and GameTests green.
- [ ] **Step 3: Commit** `git commit -m "Document the acoustic debug view"`.
- [ ] **Step 4: In-game check by the user** (warn before launching a client; never kill theirs): stone room + neighbour speaker + open door + water pool; F3+U four times; F3+F6 boxes follow.
