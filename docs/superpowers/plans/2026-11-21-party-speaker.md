# Party Speaker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A second speaker, the party speaker (120 W, directional, about 96 blocks), placed, carried or worn on the back, with its own acoustic profile, look and recipe.

**Architecture:** A pure `SpeakerKind` names the speaker type. Each kind has its own `DeviceProfile` in `AcousticSettings` (`profiles.portable`, `profiles.party`). The block, block entity and item code is factored into `SpeakerBlock`, `SpeakerBlockEntity` and `SpeakerItem`, shared by both kinds. A carried speaker now knows its place (hand, inventory, back), and `SpeakerHostPayload` carries the kind and the place, so the client picks the profile and the sound axis by itself, with no extra packet. Back wearing uses the vanilla `CHEST` equippable slot. A player render layer draws the speaker on the back. Trinkets Updated is an optional compile-only integration.

**Tech Stack:** Java 25, Fabric (MC 26.3, Mojang names), Fabric API 0.166.2 (render layer callback, networking, item tooltip), Trinkets Updated 4.2.x+26.3 (optional, compile-only), Blockbench MCP, Pillow, JUnit 5, Fabric GameTests.

**Spec:** [docs/superpowers/specs/2026-11-21-party-speaker-design.md](../specs/2026-11-21-party-speaker-design.md) §4 (PR 6b), §5, §6. 6a (§3) is already on this branch's base.

## Global Constraints

- `DeviceProfile.PARTY = new DeviceProfile(120, 87, -2, 80, new Directivity(60, -10))`. `DeviceProfile.PORTABLE` is unchanged.
- Expected range at full volume on axis: between 90 and 100 blocks (threshold 68 dB, server cap 256).
- Sound axis (§4.3): placed block → its `facing`; in hand or inventory → the carrier's horizontal look direction; on the back → the opposite of the carrier's body yaw; on the ground → omnidirectional (`Directivity.OMNI`, whatever the kind).
- Block id `ravecraft:party_speaker`, properties `facing`, `powered`, `waterlogged` like the portable. Model 12 × 18 × 10 pixels plus 3 × 3 × 3 corners. The `VoxelShape` is the outer box, clamped to 16 px high so the block never fills two blocks.
- The block entity type keeps the id `ravecraft:portable_speaker` and covers both blocks, so existing worlds load unchanged.
- Dye: only the grille is dyed; default colour `SpeakerBlock.DEFAULT_COLOR` (`0x3C3C44`). The copper logo, corners and panel are never dyed.
- Recipe, shaped, category `REDSTONE`: `I N I / C P C / I R I` with `I` = `Items.IRON_INGOT`, `N` = `Items.NOTE_BLOCK`, `C` = `Items.COPPER_INGOT`, `P` = `ModBlocks.PORTABLE_SPEAKER`, `R` = `Items.REDSTONE_BLOCK`. The portable speaker is consumed with its queue (no component transfer).
- Back slot: vanilla `Equippable` on `EquipmentSlot.CHEST`, no armor model (`assetId` empty). The key M opens the back speaker first (inventory slot 38, `Inventory.INVENTORY_SIZE + EquipmentSlot.CHEST.getIndex()`; verify against `Inventory` before use).
- Trinkets Updated: `modCompileOnly` only, never in the jar or in `depends`. Every Trinkets class lives in `com.ravecraft.mod.compat.trinkets` and is reached only behind `FabricLoader.getInstance().isModLoaded("trinkets")`. A failure is logged once with `RaveCraft.LOGGER.warn` and the chest slot keeps working.
- Back render: scale 0.6, grille facing out of the back, dye and LED (`powered`) shown.
- Tooltip: base line (power, range), Shift for the full sheet (sensitivity, bass, directivity), then the key line, as for the portable. Lines come from the speaker's kind.
- Every new text in both `en_us.json` and `fr_fr.json`. Code, comments and commits in English, imperative, no Claude attribution.
- Textures only from `tools/textures/gen_textures.py`; check `art/textures/preview.png` after each change. Never copy a Mojang texture.
- `./gradlew build` and `./gradlew codeQuality` green at the end of each task, prefix `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`. `./gradlew runDatagen` after Tasks 5 and 6.

## Review Focus

- **A party speaker thrown on the ground while playing.** Expect omnidirectional sound, not the last look direction frozen. Pinned by `SpeakerAxisTest.groundIsOmni` (Task 2).
- **A player who equips a chestplate over nothing, then swaps the party speaker into the chest slot by shift-click while it plays.** Expect no pause, host place BACK. Pinned by `PartySpeakerGameTest.chestSlotMakesWearerHost` (Task 7).
- **An old world with portable speakers placed before 6b.** Expect them to load with their queue, since the block entity type id is unchanged. Pinned by `PartySpeakerGameTest.portableStillUsesPortableType` (Task 5).
- **A server config written before 6b (no `profiles.party`).** Expect the party profile to fall back to `DeviceProfile.PARTY`, the portable values to be kept. Pinned by `AcousticSettingsJsonTest.missingPartyUsesDefault` (Task 1).
- **Trinkets absent.** Expect no `NoClassDefFoundError` at start-up or in the tooltip. Pinned by the GameTest run itself (no Trinkets on the test classpath) and by `TrinketsCompat` only being referenced from `RaveCraft.onInitialize` behind `isModLoaded` (Task 8 review point).

---

## File Structure

| File | Role |
|---|---|
| `src/main/java/com/ravecraft/mod/core/speaker/SpeakerKind.java` | Pure enum `PORTABLE`, `PARTY`, with `id()` (`"portable"`, `"party"`) |
| `src/main/java/com/ravecraft/mod/core/speaker/CarryPlace.java` | Pure enum `HAND`, `INVENTORY`, `BACK` |
| `src/main/java/com/ravecraft/mod/core/acoustics/DeviceProfiles.java` | Pure record `(portable, party)` with `of(SpeakerKind)` |
| `src/main/java/com/ravecraft/mod/core/acoustics/SpeakerAxis.java` | Pure: facing vector and directivity of a speaker from its host place |
| `src/main/java/com/ravecraft/mod/block/SpeakerBlock.java` | Shared abstract block (was `PortableSpeakerBlock`'s body) |
| `src/main/java/com/ravecraft/mod/block/PortableSpeakerBlock.java` | Thin subclass: portable shapes, kind, codec |
| `src/main/java/com/ravecraft/mod/block/PartySpeakerBlock.java` | Thin subclass: party shapes, kind, codec |
| `src/main/java/com/ravecraft/mod/blockentity/SpeakerBlockEntity.java` | Renamed from `PortableSpeakerBlockEntity` |
| `src/main/java/com/ravecraft/mod/item/SpeakerItem.java` | Renamed from `PortableSpeakerItem`; reports the carry place |
| `src/main/java/com/ravecraft/mod/compat/trinkets/TrinketsCompat.java` | Optional back slot through Trinkets Updated |
| `src/client/java/com/ravecraft/mod/client/render/BackSpeakerLayer.java` | Player render layer: the speaker worn on the back |
| `src/gametest/java/com/ravecraft/mod/gametest/PartySpeakerGameTest.java` | Chest slot host, recipe, old block entity type |
| `art/models/party_speaker.bbmodel` | Blockbench source; exported to `models/block/party_speaker[_on].json` |

Modified: `DeviceProfile`, `AcousticSettings`, `AcousticSettingsJson`, `AcousticSettingsPayload`, `Propagation` callers (`AudioRelay`, `AudioWorker`, `OcclusionScheduler`, `RenderStream`, `SpeakerTooltip`), `SpeakerHostPayload`, `SpeakerHost`, `CarrierHandle`, `SpeakerHosts`, `SpeakerLookup`, `SpeakerHostNotices`, `SpeakerHandle`, `SpeakerKeyTarget`, `OpenSpeakerKey`, `ModBlocks`, `ModBlockEntities`, the datagen providers, `gen_textures.py`, both lang files, `build.gradle`, `RaveCraft`, `RaveCraftClient`, `docs/SPEC.md`.

---

### Task 1: Speaker kinds and per-kind profiles

**Files:**
- Create: `core/speaker/SpeakerKind.java`, `core/acoustics/DeviceProfiles.java`
- Modify: `core/acoustics/DeviceProfile.java`, `core/acoustics/AcousticSettings.java`, `config/AcousticSettingsJson.java`, `network/payload/AcousticSettingsPayload.java`, every `portable()` caller listed in the File Structure
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/PropagationTest.java`, `AcousticSettingsTest`, `AcousticSettingsJsonTest`, `AcousticSettingsPayloadTest`

**Interfaces:**
- Produces: `enum SpeakerKind { PORTABLE, PARTY; String id(); }`; `DeviceProfile.PARTY`; `record DeviceProfiles(DeviceProfile portable, DeviceProfile party) { static DeviceProfiles DEFAULT; DeviceProfile of(SpeakerKind kind); }`; `AcousticSettings` component `DeviceProfiles profiles` replacing `DeviceProfile portable`, plus convenience `DeviceProfile profile(SpeakerKind kind)`.

- [ ] **Step 1: Write the failing tests**

```java
// PropagationTest
@Test void partyReachesAboutNinetySixBlocks() {
	double range = Propagation.range(AcousticSettings.DEFAULT, DeviceProfile.PARTY, 1.0);
	assertTrue(range >= 90 && range <= 100, "range " + range);
}
// DirectivityTest (existing file or new)
@Test void partyFullGainAtSixtyDegrees() { assertEquals(0, DeviceProfile.PARTY.directivity().gain(Math.cos(Math.toRadians(60))), 1e-9); }
@Test void partyMinusTenAtRear() { assertEquals(-10, DeviceProfile.PARTY.directivity().gain(-1), 1e-9); }
// AcousticSettingsJsonTest
@Test void missingPartyUsesDefault() {
	AcousticSettings s = read("{\"profiles\":{\"portable\":{\"powerWatts\":40}}}");
	assertEquals(DeviceProfile.PARTY, s.profile(SpeakerKind.PARTY));
	assertEquals(40, s.profile(SpeakerKind.PORTABLE).powerWatts());
}
@Test void partySectionIsRead() { assertEquals(200, read("{\"profiles\":{\"party\":{\"powerWatts\":200}}}").profile(SpeakerKind.PARTY).powerWatts()); }
// AcousticSettingsPayloadTest
@Test void partyProfileRoundTrips() { /* encode settings with a custom party profile, decode, assertEquals the party profile */ }
```

Check the gain unit of `Directivity.gain` before writing the two directivity assertions (dB or linear); assert the spec's values in that unit.

- [ ] **Step 2: Run them** — `./gradlew test` → FAIL (`PARTY`, `SpeakerKind`, `profile` missing).

- [ ] **Step 3: Implement.** `clamped()` bounds `profiles.party.*` with the same limits as `profiles.portable.*`. The JSON writer emits a `party` section next to `portable`; the reader falls back to `DeviceProfiles.DEFAULT` per missing section. The payload encodes both profiles with the existing `PROFILE` codec. Callers keep their behaviour with `profile(SpeakerKind.PORTABLE)` for now; Task 3 replaces them by the speaker's kind. Update the existing tests that build `AcousticSettings` by hand.

- [ ] **Step 4: Run** `./gradlew build codeQuality` → green.

- [ ] **Step 5: Commit** `Add per-kind speaker profiles and the party profile`

### Task 2: Sound axis by host place

**Files:**
- Create: `core/speaker/CarryPlace.java`, `core/acoustics/SpeakerAxis.java`
- Test: `src/test/java/com/ravecraft/mod/core/acoustics/SpeakerAxisTest.java`

**Interfaces:**
- Produces: `enum CarryPlace { HAND, INVENTORY, BACK }`; `record SpeakerAxis(double x, double z, boolean directed)` with
  - `static SpeakerAxis block(int stepX, int stepZ)` — directed;
  - `static SpeakerAxis carried(CarryPlace place, float lookYawDeg, float bodyYawDeg)` — HAND and INVENTORY use the look yaw, BACK uses `bodyYaw + 180`; Minecraft yaw: `x = -sin(yaw)`, `z = cos(yaw)`;
  - `static final SpeakerAxis GROUND` — `(0, 1, false)`;
  - `Directivity directivity(DeviceProfile profile)` — the profile's directivity when directed, else `Directivity.OMNI`.

- [ ] **Step 1: Failing tests** `blockFollowsFacing` (step (1,0) → x 1, z 0), `handFollowsLook` (yaw 90 → x -1, z ≈ 0), `backPointsBehind` (body yaw 0 → z -1), `groundIsOmni` (`GROUND.directivity(DeviceProfile.PARTY)` equals `Directivity.OMNI`), `directedKeepsProfile`.
- [ ] **Step 2: Run** `./gradlew test --tests '*SpeakerAxisTest'` → FAIL (class missing).
- [ ] **Step 3: Implement** in pure Java.
- [ ] **Step 4: Run** → PASS; `./gradlew build codeQuality` green.
- [ ] **Step 5: Commit** `Add the speaker sound axis`

### Task 3: Kind and place on the host, profile and axis on the client

**Files:**
- Modify: `world/audio/SpeakerHost.java`, `CarrierHandle.java`, `SpeakerHosts.java`, `SpeakerLookup.java`, `SpeakerHostNotices.java`, `SpeakerHandle.java`, `network/payload/SpeakerHostPayload.java`, `network/AudioRelay.java`, `item/PortableSpeakerItem.java`; client `ClientSpeakerHosts`, `render/RenderStream.java`, `render/AudioWorker.java`, `render/OcclusionScheduler.java`
- Test: `src/test/java/com/ravecraft/mod/network/payload/SpeakerHostPayloadTest.java` (create if missing), existing `CarriedSpeakerGameTest`

**Interfaces:**
- Consumes: `SpeakerKind`, `CarryPlace`, `SpeakerAxis`, `AcousticSettings.profile(kind)`.
- Produces:
  - `SpeakerHost.Carrier(UUID player, ResourceKey<Level> dimension, CarryPlace place)` (replaces `inHand`);
  - `SpeakerHosts.carried(ServerPlayer, ItemStack, CarryPlace)`;
  - `SpeakerHandle.kind()` → `SpeakerKind` (from the block or the stack's item);
  - `SpeakerHostPayload(UUID speakerId, ResourceKey<Level> dimension, Optional<BlockPos> block, int entityId, SpeakerKind kind, Optional<CarryPlace> place, int volume, boolean powered)` — `place` empty for a block or the ground; both enums encoded by ordinal (`ByteBufCodecs.idMapper` or `VAR_INT` mapped);
  - `RenderStream` fields `volatile DeviceProfile profile` and `volatile Directivity directivity` set in `refresh`, read by `AudioWorker` and `OcclusionScheduler` instead of `settings.portable()`.

- [ ] **Step 1: Failing tests**
  - `SpeakerHostPayloadTest.roundTripsKindAndPlace`: encode/decode a carrier payload with `PARTY` and `BACK`, assert equality.
  - In `CarriedSpeakerGameTest`, change the hand assertion to `place() == CarryPlace.HAND` and add `inventoryIsInventoryPlace` (speaker in slot 20 → `INVENTORY`).
- [ ] **Step 2: Run** `./gradlew test` → FAIL (constructor mismatch).
- [ ] **Step 3: Implement.** `PortableSpeakerItem.inventoryTick`: `MAINHAND`/`OFFHAND` → `HAND`, `CHEST` → `BACK`, else `INVENTORY`. `CarrierHandle.position`: `HAND` from the eyes, `INVENTORY` and `BACK` at chest height. `AudioRelay` relay radius uses `handle.kind()`'s profile. `RenderStream.refresh`: a block host takes `SpeakerAxis.block` from the block's `FACING` (any `SpeakerBlock`, Task 4 widens the check), a carrier takes `SpeakerAxis.carried(place, entity.getYRot(), ((LivingEntity) entity).yBodyRot)`, a dropped item `SpeakerAxis.GROUND`; then `profile = settings.profile(host.kind())`, `directivity = axis.directivity(profile)`. Verify `yBodyRot` exists on `LivingEntity` in 26.3 before use.
- [ ] **Step 4: Run** `./gradlew build codeQuality` → green.
- [ ] **Step 5: Commit** `Send the speaker kind and carry place to listeners`

### Task 4: Shared speaker block, block entity and item

**Files:**
- Create: `block/SpeakerBlock.java`; rename `blockentity/PortableSpeakerBlockEntity.java` → `SpeakerBlockEntity.java`, `item/PortableSpeakerItem.java` → `SpeakerItem.java` (`git mv`)
- Modify: `block/PortableSpeakerBlock.java`, `registry/ModBlocks.java`, `registry/ModBlockEntities.java`, and every file referencing the renamed classes (`grep -rl "PortableSpeakerBlockEntity\|PortableSpeakerItem\|PortableSpeakerBlock\.FACING" src`)
- Test: existing suites (pure refactor)

**Interfaces:**
- Produces:
  - `abstract class SpeakerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock` with `FACING`, `POWERED`, `WATERLOGGED`, `DEFAULT_COLOR`, `protected SpeakerBlock(Properties, SpeakerKind, VoxelShape northSouth, VoxelShape eastWest)`, `SpeakerKind kind()`;
  - `PortableSpeakerBlock extends SpeakerBlock` keeping its current shapes and `simpleCodec`;
  - `SpeakerBlockEntity` (kind from `getBlockState().getBlock()`);
  - `ModBlockEntities.SPEAKER` (id `portable_speaker`), replacing `PORTABLE_SPEAKER`;
  - `SpeakerItem extends BlockItem`, `SpeakerKind kind()` from its block.

- [ ] **Step 1: Run the suite first** `./gradlew build` → green (baseline for the refactor).
- [ ] **Step 2: Move the code** without behaviour change. Callers that tested `state.is(ModBlocks.PORTABLE_SPEAKER)` or `stack.is(...)` test `instanceof SpeakerBlock` / `instanceof SpeakerItem` instead.
- [ ] **Step 3: Run** `./gradlew build codeQuality` → green, same test counts as Step 1 (585 JUnit, 117 GameTests on the base, plus Tasks 1–3's new tests).
- [ ] **Step 4: Commit** `Factor the speaker block, block entity and item`

### Task 5: Party speaker look and block

Before any texture or model work, show the user 3–4 rendered variants of the v3 look (grille mesh density, copper logo shape, panel placement) and implement the one they pick. The spec fixes the v3 direction; the variants only settle the details.

**Files:**
- Create: `block/PartySpeakerBlock.java`, `art/models/party_speaker.bbmodel`, `assets/ravecraft/models/block/party_speaker.json`, `party_speaker_on.json`
- Modify: `registry/ModBlocks.java`, `ModBlockEntities.java` (add the block to the type's set), `tools/textures/gen_textures.py`, `RaveCraftModelProvider`, `RaveCraftBlockLootProvider`, `RaveCraftBlockTagProvider`, `RaveCraftRecipeProvider`, the creative tab, both lang files, the client block colour and item tint registration (same as the portable's fabric tint, on the grille tint index only)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/PartySpeakerGameTest.java` (declare it in `src/gametest/resources/fabric.mod.json`)

**Interfaces:**
- Consumes: `SpeakerBlock`, `SpeakerKind.PARTY`.
- Produces: `ModBlocks.PARTY_SPEAKER`, textures `party_speaker_grille`, `party_speaker_rubber`, `party_speaker_panel`, `party_speaker_panel_on` (+ item icon layers as for the portable).

- [ ] **Step 1: Failing GameTests**
  - `placedPartySpeakerHasData`: place `PARTY_SPEAKER`, assert its block entity is a `SpeakerBlockEntity` with a non-null id and `kind() == PARTY`.
  - `portableStillUsesPortableType`: `BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(ModBlockEntities.SPEAKER)` equals `RaveCraft.id("portable_speaker")`.
  - `recipeConsumesPortable`: the recipe `ravecraft:party_speaker` exists and its `P` ingredient matches the portable speaker item.
- [ ] **Step 2: Run** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3: Textures and model.** Texture functions in `gen_textures.py` reusing `C`, `casing`, `grain` and the portable's rubber; regenerate and look at `art/textures/preview.png`. Model in Blockbench (12 × 18 × 10 body, eight 3 × 3 × 3 corners, grille on the front with tint index 0, panel on the top of one side), `bbmodel_validate`, `bbmodel_render`, export both states.
- [ ] **Step 4: Register and datagen.** `PartySpeakerBlock` with its shapes, block + `SpeakerItem` in `ModBlocks`, loot like the portable's (copies the components), pickaxe tag, shaped recipe from Global Constraints, lang keys `block.ravecraft.party_speaker` ("Party Speaker" / "Enceinte de soirée"). `./gradlew runDatagen`.
- [ ] **Step 5: Run** `./gradlew build codeQuality` → green.
- [ ] **Step 6: Commit** `Add the party speaker block`

### Task 6: Party speaker tooltip

**Files:**
- Modify: `client/SpeakerTooltip.java`, both lang files
- Test: `src/test/java/com/ravecraft/mod/client/...` is not possible (client); cover the pure part with `PropagationTest` (Task 1) and check in game.

**Interfaces:**
- Consumes: `SpeakerItem.kind()`, `AcousticSettings.profile(kind)`.

- [ ] **Step 1:** `register` matches any `SpeakerItem`; `addLines` takes the profile of `item.kind()`. The directivity line shows `directional` with the half-angle and rear gain for a non-OMNI profile: new key `tooltip.ravecraft.speaker.directivity.cone` = "%s° cone, %s dB behind" / "cône de %s°, %s dB à l'arrière".
- [ ] **Step 2: Run** `./gradlew build codeQuality` → green.
- [ ] **Step 3: Commit** `Show each speaker's own data sheet`

### Task 7: Wearing on the back

**Files:**
- Modify: `registry/ModBlocks.java` (party item properties), `core/speaker/SpeakerKeyTarget.java`, `client/OpenSpeakerKey.java`
- Create: `client/render/BackSpeakerLayer.java`; register it in `RaveCraftClient`
- Test: `src/test/java/com/ravecraft/mod/core/speaker/SpeakerKeyTargetTest.java`, `PartySpeakerGameTest`

**Interfaces:**
- Consumes: `CarryPlace.BACK` from Task 3's `inventoryTick` mapping.
- Produces: `SpeakerKeyTarget.BACK = 38`, checked before the selected slot.

- [ ] **Step 1: Failing tests**
  - `SpeakerKeyTargetTest.backComesFirst`: speakers at 38 and at the selected slot → 38.
  - `SpeakerKeyTargetTest.noSpeakerIsEmpty` stays green.
  - `PartySpeakerGameTest.chestSlotMakesWearerHost`: a mock player with a playing party speaker in `EquipmentSlot.CHEST`; after two ticks, its host is `Carrier` with `place() == BACK` and the session is still playing.
  - `PartySpeakerGameTest.portableIsNotWearable`: the portable item has no `DataComponents.EQUIPPABLE`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** Party item properties get `.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build())` (check the 26.3 builder for the equip sound and the absent asset id). `OpenSpeakerKey.stack` maps 38 to the chest equipment slot. `BackSpeakerLayer extends RenderLayer<AvatarRenderState, PlayerModel>` (check the exact render-state and model types in 26.3), registered with `LivingEntityRenderLayerRegistrationCallback.EVENT` for `AvatarRenderer`: follows `body`, translates to the back, rotates the grille outwards, scales 0.6, renders the party speaker block model with the stack's dye and the `powered` state from `SpeakerData`.
- [ ] **Step 4: Run** `./gradlew build codeQuality` → green.
- [ ] **Step 5: Commit** `Let the party speaker be worn on the back`

### Task 8: Optional Trinkets Updated back slot

**Files:**
- Modify: `build.gradle` (repository `https://api.modrinth.com/maven`, `modCompileOnly "maven.modrinth:XaT8sLP6:GjCPLWaf"`; check with `./gradlew dependencies` that it resolves, else pick the 4.2.1+26.3 version id), `RaveCraft.java`, `world/audio/SpeakerLookup.java`, `client/OpenSpeakerKey.java`
- Create: `compat/trinkets/TrinketsCompat.java`, `src/main/resources/data/trinkets/tags/item/chest/back.json` (`["ravecraft:party_speaker"]`)

**Interfaces:**
- Produces: `TrinketsCompat.init()` (registers a trinket whose tick calls `SpeakerHosts.carried(player, stack, CarryPlace.BACK)`), `TrinketsCompat.findBack(Player, Predicate<ItemStack>) → Optional<ItemStack>`; a holder `SpeakerBackSlots` in `world/audio` with a nullable finder that the compat sets, so `SpeakerLookup.findCarried` and `OpenSpeakerKey` call it without importing Trinkets.

- [ ] **Step 1: Read the Trinkets Updated 26.3 API** from its jar (`unzip -l`, then `javap` or the sources jar): the trinket registration call, the tick signature, the component lookup, and the slot group/name of the back slot (`chest/back` expected). Write what you found as a comment in `TrinketsCompat`'s Javadoc. If the API differs so much that the integration cannot compile, stop at this step, ledger a ruling, and ship Tasks 1–7 without Trinkets (the spec allows it: the chest slot remains).
- [ ] **Step 2: Implement** behind `FabricLoader.getInstance().isModLoaded("trinkets")`, inside a `try/catch (LinkageError | RuntimeException e)` that logs once.
- [ ] **Step 3: Run** `./gradlew build codeQuality` → green (Trinkets absent from the GameTest classpath, proving the optional path).
- [ ] **Step 4: Commit** `Accept the party speaker in the Trinkets back slot`

### Task 9: Docs and in-game check

- [ ] **Step 1:** Update `docs/SPEC.md` (step 6 done: 6a and 6b).
- [ ] **Step 2:** `./gradlew runDatagen && ./gradlew build codeQuality` → green.
- [ ] **Step 3:** In game (`runClient`), with the user: front and rear range of a placed party speaker, worn on the back (render and sound behind), held, on the ground, key M, tooltip with and without Shift, recipe; with and without Trinkets Updated in `run/mods`.
- [ ] **Step 4: Commit** `Mark the party speaker done in the spec`
