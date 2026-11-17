# Portable Speaker (Audio core PR 1/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `ravecraft:portable_speaker` block/item: placeable and carryable, with a persistent identity and settings that survive place → break → place, an on/off toggle, a provisional vanilla recipe, original textures and a custom model. No audio yet.

**Architecture:** Pure settings logic in `core.speaker` (JUnit). A `SpeakerData` data component (UUID + settings) is the single source of truth while the speaker is an item; a block entity holds it while placed. Vanilla implicit-component hooks plus a `copy_components` loot function move it between the two. The `POWERED` block-state property mirrors `settings.powered` for rendering.

**Tech Stack:** Fabric (Loom 1.19, Fabric API 0.166.2+26.3), Minecraft 26.3 Mojang names, Java 25, JUnit 5, Fabric GameTest, Pillow, Blockbench MCP.

**Spec:** [docs/superpowers/specs/2026-11-17-audio-core-design.md](../specs/2026-11-17-audio-core-design.md) — this plan covers delivery step 1 (`feat/portable-speaker`, spec §10) and spec §3.4 (identity + `powered` + `volume` only), §5.1, §5.2, §5.5. Plans for PRs 2–5 are written when each starts, because they depend on research listed in spec §12.

## Global Constraints

- Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`, Java 25 — do not change versions.
- Mojang names only (no Yarn). Verify any MC/Fabric API in `.gradle/loom-cache/.../*-sources.jar` (run `./gradlew genSources` if missing) before using it.
- `core` package: no `net.minecraft`/Fabric import. `src/main`: no `net.minecraft.client` import.
- Every block/item gets its id before construction: `properties.setId(key)` (see `ModBlocks.register`).
- Textures: original, 16×16 RGBA, produced by `tools/textures/gen_textures.py` with palette `C`; never copy Mojang textures. Check `art/textures/preview.png` and a Blockbench render before calling a visual done.
- Recipe is **provisional**: pattern `FFF / NRN / FFF` with `F` = `Items.IRON_INGOT`, `N` = `Items.NOTE_BLOCK`, `R` = `Items.REDSTONE`, marked provisional in a code comment and in `docs/SPEC.md`.
- Volume range `0..100`, default `70`. Speaker defaults to **off**.
- Translations in both `en_us.json` and `fr_fr.json`. Code, comments and commit messages in English; no `Co-Authored-By` trailer.
- Branch `feat/portable-speaker`; commit locally per task; **never push or open the PR without asking the user**.
- Done = `./gradlew runDatagen && ./gradlew build` green.

## Review Focus

1. A speaker taken from the creative tab has no `SpeakerData` component: placing it or using it in the air must create a fresh identity, never crash or leave a null id. → tests in Task 3 (`creativeItemWithoutDataGetsIdentityOnPlace`, `useInAirWithoutDataAssignsIdentity`).
2. World save/reload must keep the identity and settings: the component codec must round-trip exactly. → Task 2 (`speakerDataCodecRoundTrip`).
3. Speakers are unique objects: they must never stack (two identities in one stack would merge). → Task 3 (`speakerDoesNotStack`).
4. Breaking the block by any means that drops it (player, explosion) must keep identity and settings on the dropped item. → Task 3 (`breakingDropsItemWithSameData`, using a drop-enabled destroy).
5. Placing the item must face the player and restore the visual on/off state from the item data. → Task 3 (`placingItemRestoresDataAndPower`).

---

### Task 1: Speaker settings (pure logic)

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/speaker/SpeakerSettings.java`
- Test: `src/test/java/com/ravecraft/mod/core/speaker/SpeakerSettingsTest.java`

**Interfaces:**
- Produces: `public record SpeakerSettings(boolean powered, int volume)` with
  - `public static final int MIN_VOLUME = 0, MAX_VOLUME = 100, DEFAULT_VOLUME = 70;`
  - `public static final SpeakerSettings DEFAULT` = `(false, 70)`
  - compact constructor clamps `volume` into `[0, 100]`
  - `SpeakerSettings togglePower()`, `SpeakerSettings withVolume(int volume)` (clamped)

- [ ] **Step 1: Create the branch**

Run: `git switch main && git pull && git switch -c feat/portable-speaker`
Expected: on branch `feat/portable-speaker`.

- [ ] **Step 2: Write the failing tests**

```java
@Test void defaultIsOffAtSeventy() {
    assertEquals(new SpeakerSettings(false, 70), SpeakerSettings.DEFAULT);
}
@Test void volumeIsClamped() {
    assertEquals(0, new SpeakerSettings(true, -5).volume());
    assertEquals(100, new SpeakerSettings(true, 250).volume());
    assertEquals(100, SpeakerSettings.DEFAULT.withVolume(101).volume());
}
@Test void togglePowerFlipsOnlyPower() {
    SpeakerSettings on = SpeakerSettings.DEFAULT.withVolume(40).togglePower();
    assertEquals(new SpeakerSettings(true, 40), on);
    assertEquals(new SpeakerSettings(false, 40), on.togglePower());
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew test --tests "com.ravecraft.mod.core.speaker.SpeakerSettingsTest"`
Expected: compilation failure, `SpeakerSettings` not found.

- [ ] **Step 4: Implement `SpeakerSettings`** as specified in Interfaces.

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew test --tests "com.ravecraft.mod.core.speaker.SpeakerSettingsTest"`
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ravecraft/mod/core/speaker src/test/java/com/ravecraft/mod/core/speaker
git commit -m "Add speaker settings core logic"
```

---

### Task 2: `SpeakerData` data component

**Files:**
- Create: `src/main/java/com/ravecraft/mod/component/SpeakerData.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModDataComponents.java`
- Modify: `src/main/java/com/ravecraft/mod/RaveCraft.java` (call `ModDataComponents.init()` **before** `ModBlocks.init()`)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/PortableSpeakerGameTest.java` (new class)
- Modify: `src/gametest/resources/fabric.mod.json` (add `com.ravecraft.mod.gametest.PortableSpeakerGameTest` to `fabric-gametest`)

**Interfaces:**
- Consumes: `SpeakerSettings` (Task 1).
- Produces:
  - `public record SpeakerData(UUID id, SpeakerSettings settings)` with `static SpeakerData create()` (random UUID, `SpeakerSettings.DEFAULT`), `SpeakerData withSettings(SpeakerSettings)`, `Codec<SpeakerData> CODEC` (fields `id` via `UUIDUtil.CODEC`, `powered` bool, `volume` int — settings flattened), `StreamCodec<ByteBuf, SpeakerData> STREAM_CODEC` (`StreamCodec.composite` with `UUIDUtil.STREAM_CODEC`, `ByteBufCodecs.BOOL`, `ByteBufCodecs.VAR_INT`).
  - `ModDataComponents.SPEAKER : DataComponentType<SpeakerData>` registered as `ravecraft:speaker` via `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RaveCraft.id("speaker"), DataComponentType.<SpeakerData>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build())`; `static void init()`.

- [ ] **Step 1: Write the failing GameTest**

```java
@GameTest
public void speakerDataCodecRoundTrip(GameTestHelper helper) {
    SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 42));
    var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    Tag encoded = SpeakerData.CODEC.encodeStart(ops, data).getOrThrow();
    helper.assertValueEqual(SpeakerData.CODEC.parse(ops, encoded).getOrThrow(), data, "decoded");
    helper.succeed();
}
```

Register the class in `src/gametest/resources/fabric.mod.json`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew runGameTest`
Expected: compilation failure, `SpeakerData` not found.

- [ ] **Step 3: Implement `SpeakerData` and `ModDataComponents`**, wire `init()` in `RaveCraft.onInitialize`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew runGameTest`
Expected: `speakerDataCodecRoundTrip` passes (plus the template's existing test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ravecraft/mod src/gametest
git commit -m "Add speaker data component"
```

---

### Task 3: Portable speaker block, block entity and item

**Files:**
- Create: `src/main/java/com/ravecraft/mod/block/PortableSpeakerBlock.java`
- Create: `src/main/java/com/ravecraft/mod/blockentity/PortableSpeakerBlockEntity.java`
- Create: `src/main/java/com/ravecraft/mod/item/PortableSpeakerItem.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModBlockEntities.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModCreativeTabs.java`
- Modify: `src/main/java/com/ravecraft/mod/registry/ModBlocks.java` (register `PORTABLE_SPEAKER`; overload `register` with a `BiFunction<Block, Item.Properties, Item>` item factory)
- Modify: `src/main/java/com/ravecraft/mod/RaveCraft.java` (init order: components, blocks, block entities, items, creative tabs)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/PortableSpeakerGameTest.java`

**Interfaces:**
- Consumes: `SpeakerData`, `ModDataComponents.SPEAKER` (Task 2), `SpeakerSettings` (Task 1).
- Produces:
  - `ModBlocks.PORTABLE_SPEAKER : Block` (id `ravecraft:portable_speaker`), item is a `PortableSpeakerItem` with `stacksTo(1)`.
  - `PortableSpeakerBlock extends BaseEntityBlock` — `FACING = HorizontalDirectionalBlock.FACING`, `POWERED = BlockStateProperties.POWERED`; `codec()` via `simpleCodec`; `getStateForPlacement` faces the player (`context.getHorizontalDirection().getOpposite()`) and sets `POWERED` from the held stack's `SpeakerData` (false if absent); `getShape`: box `(3,0,5)-(13,8,11)` pixels for NORTH/SOUTH, `(5,0,3)-(11,8,13)` for EAST/WEST; `useWithoutItem` (server side) toggles power via the block entity and returns `InteractionResult.SUCCESS`; render shape `MODEL`; properties: `mapColor(MapColor.COLOR_GRAY)`, `sound(SoundType.METAL)`, `strength(1.0F)`, `noOcclusion()`, **no** `requiresCorrectToolForDrops()` (it must always drop).
  - `PortableSpeakerBlockEntity extends BlockEntity` — field `SpeakerData data = SpeakerData.create()`; `SpeakerData data()`; `void togglePower()` (updates data, sets `POWERED` on the block state, `setChanged()`); `saveAdditional`/`loadAdditional` store `data` under key `"speaker"` with `SpeakerData.CODEC`; `applyImplicitComponents` reads `ModDataComponents.SPEAKER` if present; `collectImplicitComponents` writes it; `removeComponentsFromTag` discards `"speaker"`.
  - `ModBlockEntities.PORTABLE_SPEAKER : BlockEntityType<PortableSpeakerBlockEntity>` = `new BlockEntityType<>(PortableSpeakerBlockEntity::new, Set.of(ModBlocks.PORTABLE_SPEAKER))` registered under `ravecraft:portable_speaker`.
  - `PortableSpeakerItem extends BlockItem` — `use(Level, Player, InteractionHand)` (server side): if the stack has no `SpeakerData`, set `SpeakerData.create()`; then toggle power on the stack's component; return `InteractionResult.SUCCESS`. Placeholder behaviour until the UI PR replaces it with "open the screen" (comment says so).
  - `ModCreativeTabs.RAVECRAFT` registered as `ravecraft:main` via `FabricCreativeModeTab.builder()`, title `itemGroup.ravecraft.main`, icon and contents = the portable speaker.

- [ ] **Step 1: Write the failing GameTests** in `PortableSpeakerGameTest`

```java
@GameTest public void placedSpeakerHasIdentity(GameTestHelper h) {
    BlockPos pos = new BlockPos(1, 1, 1);
    h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
    SpeakerData data = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
    h.assertTrue(data.id() != null, "id set");
    h.assertValueEqual(data.settings(), SpeakerSettings.DEFAULT, "settings");
    h.succeed();
}
@GameTest public void usingBlockTogglesPower(GameTestHelper h) {
    BlockPos pos = new BlockPos(1, 1, 1);
    h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
    h.useBlock(pos);
    h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, true);
    h.assertTrue(h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data().settings().powered(), "data powered");
    h.succeed();
}
@GameTest public void breakingDropsItemWithSameData(GameTestHelper h) {
    BlockPos pos = new BlockPos(1, 1, 1);
    h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
    h.useBlock(pos);
    SpeakerData before = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
    h.getLevel().destroyBlock(h.absolutePos(pos), true);   // drops, unlike helper.destroyBlock
    ItemEntity drop = h.getEntities(EntityType.ITEM, pos, 2).getFirst();
    h.assertValueEqual(drop.getItem().get(ModDataComponents.SPEAKER), before, "dropped data");
    h.succeed();
}
@GameTest public void placingItemRestoresDataAndPower(GameTestHelper h) {
    SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 33));
    ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
    stack.set(ModDataComponents.SPEAKER, data);
    Player player = h.makeMockPlayer(GameType.SURVIVAL);
    BlockPos pos = new BlockPos(1, 1, 1);
    h.placeAt(player, stack, pos.below(), Direction.UP);
    h.assertValueEqual(h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data(), data, "restored data");
    h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, true);
    h.succeed();
}
@GameTest public void creativeItemWithoutDataGetsIdentityOnPlace(GameTestHelper h) {
    Player player = h.makeMockPlayer(GameType.CREATIVE);
    BlockPos pos = new BlockPos(1, 1, 1);
    h.placeAt(player, new ItemStack(ModBlocks.PORTABLE_SPEAKER), pos.below(), Direction.UP);
    SpeakerData data = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
    h.assertTrue(data.id() != null, "fresh id");
    h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, false);
    h.succeed();
}
@GameTest public void useInAirWithoutDataAssignsIdentity(GameTestHelper h) {
    Player player = h.makeMockPlayer(GameType.SURVIVAL);
    ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    stack.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
    SpeakerData data = player.getMainHandItem().get(ModDataComponents.SPEAKER);
    h.assertTrue(data != null && data.settings().powered(), "identity assigned and powered on");
    h.succeed();
}
@GameTest public void speakerDoesNotStack(GameTestHelper h) {
    h.assertValueEqual(new ItemStack(ModBlocks.PORTABLE_SPEAKER).getMaxStackSize(), 1, "max stack size");
    h.succeed();
}
```

Note: `placeAt(player, stack, pos, face)` places against the block at `pos` on `face`; if the helper's semantics differ in 26.3, read `GameTestHelper.placeAt` (line ~483 of the sources) and adjust the target position, not the assertions.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew runGameTest`
Expected: compilation failure (`PortableSpeakerBlock`, `ModBlocks.PORTABLE_SPEAKER` not found).

- [ ] **Step 3: Implement** the block, block entity, item, `ModBlockEntities`, `ModCreativeTabs` and the `ModBlocks` changes as specified in Interfaces.

Vanilla calls `BlockEntity.applyImplicitComponents` when a `BlockItem` is placed and the loot function (Task 4) copies `collectImplicitComponents` back, so no custom placement code is needed beyond `getStateForPlacement`.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew runGameTest`
Expected: all 8 `PortableSpeakerGameTest` tests pass, except `breakingDropsItemWithSameData`, which needs the loot table from Task 4 — it must fail **only** on the dropped data assertion, which proves the test is wired. Leave it failing until Task 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ravecraft/mod src/gametest
git commit -m "Add portable speaker block, block entity and item"
```

---

### Task 4: Assets, data generation and template cleanup

**Files:**
- Modify: `tools/textures/gen_textures.py` (add `portable_speaker_front`, `portable_speaker_front_on`, `portable_speaker_side`, `portable_speaker_top`; remove `example_block`, `example_item`; icon stays generated)
- Create: `art/models/portable_speaker.bbmodel` (Blockbench MCP), exported to `src/main/resources/assets/ravecraft/models/block/portable_speaker.json` and `portable_speaker_on.json` (same geometry, front texture `_on`)
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftModelProvider.java`
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftBlockLootProvider.java`
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftBlockTagProvider.java`
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftRecipeProvider.java`
- Modify: `src/main/resources/assets/ravecraft/lang/en_us.json`, `fr_fr.json`
- Delete: `ModItems.EXAMPLE_ITEM`, `ModBlocks.EXAMPLE_BLOCK` and their textures/lang keys; `src/gametest/.../ExampleGameTest.java` (and its `fabric.mod.json` entry); `core/Cooldown.java` and `CooldownTest.java`
- Regenerated: `src/main/generated/**` (never edited by hand)

**Interfaces:**
- Consumes: `ModBlocks.PORTABLE_SPEAKER`, `PortableSpeakerBlock.POWERED/FACING`, `ModDataComponents.SPEAKER`.

- [ ] **Step 1: Textures.** Add the four 16×16 functions using palette `C` and helpers (`casing`, `inset`, `grain`): front = dark grille with a round speaker cone and a small status LED (dim `accent_d` off, bright `accent`/`accent_l` on); side and top = casing with a carry handle hint on top. Register in `BLOCKS`. Run `python tools/textures/gen_textures.py` and **open `art/textures/preview.png`**; iterate until it reads as a small speaker.

- [ ] **Step 2: Model.** With the Blockbench MCP (`bbmodel_create` → `bbmodel_edit` → `bbmodel_validate` → `bbmodel_render`), build a box matching the collision shape (6 px tall body `3,0,5 → 13,8,11` for north facing, front on the north face) plus a 1 px handle on top; textures `ravecraft:block/portable_speaker_front|side|top`. Export with `bbmodel_export_java_block` to `models/block/portable_speaker.json`; copy it to `portable_speaker_on.json` with the front texture swapped to `portable_speaker_front_on`. Check the render image before moving on.

- [ ] **Step 3: Datagen.**
  - Model provider: replace the example with
    `generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.PORTABLE_SPEAKER).with(BlockModelGenerators.createBooleanModelDispatch(PortableSpeakerBlock.POWERED, plainVariant(on), plainVariant(off))).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));` with `off = ModelLocationUtils.getModelLocation(ModBlocks.PORTABLE_SPEAKER)`, `on = ModelLocationUtils.getModelLocation(ModBlocks.PORTABLE_SPEAKER, "_on")`, and `generators.registerSimpleItemModel(ModBlocks.PORTABLE_SPEAKER, off)`. Remove the example item model.
  - Loot: `add(ModBlocks.PORTABLE_SPEAKER, LootTable.lootTable().withPool(applyExplosionCondition(ModBlocks.PORTABLE_SPEAKER, LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModBlocks.PORTABLE_SPEAKER).apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(ModDataComponents.SPEAKER))))));` — modeled on vanilla `createShulkerBoxDrop`.
  - Tags: `BlockTags.MINEABLE_WITH_PICKAXE` gets the speaker (faster with a pickaxe; still drops by hand).
  - Recipe: shaped, category `RecipeCategory.REDSTONE`, pattern `FFF`/`NRN`/`FFF` (`F` iron ingot, `N` note block, `R` redstone), `unlockedBy("has_note_block", has(Items.NOTE_BLOCK))`, preceded by `// PROVISIONAL: replaced by the materials sub-project (docs/SPEC.md).`

- [ ] **Step 4: Translations.** `en_us`: `"block.ravecraft.portable_speaker": "Portable Speaker"`, `"itemGroup.ravecraft.main": "RaveCraft"`. `fr_fr`: `"Enceinte portable"`, `"RaveCraft"`. Remove the example keys.

- [ ] **Step 5: Remove the template examples** listed in Files (registries, gametest class + its `fabric.mod.json` entry, `Cooldown` + test).

- [ ] **Step 6: Regenerate and build**

Run: `./gradlew runDatagen && ./gradlew build`
Expected: `BUILD SUCCESSFUL`; `src/main/generated` contains `blockstates/portable_speaker.json`, `items/portable_speaker.json`, `loot_table/blocks/portable_speaker.json` (with `minecraft:copy_components`), `recipe/portable_speaker.json`, and no `example_*` file. All GameTests pass, including `breakingDropsItemWithSameData`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Add portable speaker assets, recipe and loot; remove template examples"
```

---

### Task 5: In-game check, docs and pull request

**Files:**
- Modify: `docs/SPEC.md` (Contenu: add the portable speaker row, note the provisional recipe; Étapes: PR 1/5 done)

- [ ] **Step 1: Check in game.** `./gradlew runClient` (or the IntelliJ run config), creative world: the RaveCraft tab shows the speaker; place it in the four directions (faces the player, shape matches the model, no see-through faces); right-click toggles the LED texture; break it and re-place it: still on; use it in the air: toggles; craft it in survival with the recipe. Report anything off before continuing.

- [ ] **Step 2: Update `docs/SPEC.md`** as listed in Files, then commit.

```bash
git add docs/SPEC.md
git commit -m "Update spec progress for portable speaker"
```

- [ ] **Step 3: Final verification**

Run: `./gradlew runDatagen && ./gradlew build && git status`
Expected: `BUILD SUCCESSFUL`, clean working tree (datagen produced no diff).

- [ ] **Step 4: Ask the user** whether to push `feat/portable-speaker` and open the pull request (title `Add portable speaker`, body from `.github/pull_request_template.md`, in French, no Claude attribution). Do nothing until they answer.
