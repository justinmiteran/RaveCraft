# Materials 7a — ores and raw materials Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the three ores (four blocks), their world generation, the five raw-material items and their smelting recipes (PR 7a, branch `feat/materials-ores`).

**Architecture:** Blocks and items are registered in `registry/ModBlocks` and `registry/ModItems` like the speakers. World generation keys live in a new `world/gen/ModWorldgen` (main source set) which also adds the placed features to biomes with Fabric's `BiomeModifications`; the feature and placement objects themselves are bootstrapped in datagen (`buildRegistry` + a `FabricDynamicRegistryProvider`) and land as JSON in `src/main/generated`. Loot, tags, models and recipes extend the existing datagen providers.

**Tech Stack:** Fabric (Minecraft 26.3, Mojang names), Fabric API `fabric-biome-api-v1`, `fabric-data-generation-api-v1`, GameTest, JUnit 5, Pillow texture generator.

**Spec:** [docs/superpowers/specs/2026-11-22-materials-design.md](../specs/2026-11-22-materials-design.md) (sections 1, 3.1, 3.2, 4, 9).

## Global Constraints

- Mojang names only; verify every Minecraft/Fabric class before use (sources in `.gradle/loom-cache/minecraftMaven/.../*-sources.jar`). In 26.3 configured features are `Registries.FEATURE` holding `Feature` objects: `new OreFeature(List<BlockReplacement> targets, int size[, float discardChanceOnAirExposure])`, `BlockReplacement.replace(RuleTest, BlockState)`; placements are `PlacementUtils.register(context, key, Holder<Feature>, List<PlacementModifier>)`.
- Every block/item gets its id before construction (`properties.setId(key)`), as in `ModBlocks.register`.
- No `net.minecraft.client` import in `src/main/java`.
- Javadoc on every class, every public/protected method and every method over 10 lines, with `@param`/`@return` on public methods; cognitive complexity ≤ 15, at most two nested `if`/loops. `RaveCraft.id(path)` for ids, `RaveCraft.LOGGER` for logs.
- Textures: original, 16×16 RGBA, produced by `tools/textures/gen_textures.py`, palette `C`; never copy a Mojang texture. Visual proposals (3–4 rendered concepts) are chosen by the user before the final textures are written.
- Translations in both `assets/ravecraft/lang/en_us.json` and `fr_fr.json`, names exactly as spec 3.1 / 3.2.
- Commits in English, imperative, **no Claude attribution line**. Never `git add art/proposals/` or `.superpowers/`. No push, no PR without the user's explicit OK.
- Done only when `./gradlew runDatagen`, `./gradlew build` and `./gradlew codeQuality` are green (prefix `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`).

Ids and values used throughout (from the spec):

| Block | Class | Strength | Sound | XP when mined | Tool tag |
|---|---|---|---|---|---|
| `oil_shale` | `DropExperienceBlock` | 3.0 / 3.0 | `STONE` | `UniformInt.of(0, 2)` | `needs_stone_tool` |
| `deepslate_neodymium_ore` | `DropExperienceBlock` | 4.5 / 3.0 | `DEEPSLATE` | `ConstantInt.of(0)` | `needs_iron_tool` |
| `silicon_ore` | `DropExperienceBlock` | 3.0 / 3.0 | `STONE` | `UniformInt.of(1, 3)` | `needs_iron_tool` |
| `deepslate_silicon_ore` | `DropExperienceBlock` | 4.5 / 3.0 | `DEEPSLATE` | `UniformInt.of(1, 3)` | `needs_iron_tool` |

All four: `requiresCorrectToolForDrops()`, `mineable/pickaxe`. Items: `bitumen`, `raw_neodymium`, `neodymium_ingot`, `silicon_crystal`, `silicon_wafer` (plain `Item`, stack 64).

## Review Focus

1. A player mining with Fortune III and Silk Touch on one pickaxe gets the ore block, not boosted drops (silk-touch dispatch wins) — pinned in Task 3.
2. Ores blown up by TNT/creepers drop less, like vanilla (explosion decay) — pinned in Task 3.
3. A wooden pickaxe on oil shale, or a stone pickaxe on neodymium/silicon, yields nothing — pinned in Task 3 (`isCorrectToolForDrops`).
4. The Nether and the End never get these ores, and only deserts/badlands get the extra oil shale — pinned in Task 5.
5. A translation missing in one language shows a raw key in game — pinned in Task 2 (`MaterialsLangTest`).

Not testable here, stated in release notes: ores only appear in chunks generated after installing the mod.

---

### Task 1: Visual proposals (gate)

**Files:**
- Create (not committed): `art/proposals/materials-ores/*.png`, one contact sheet

- [ ] **Step 1:** For each of the 4 ore blocks and 5 items, draw 3–4 concepts with Pillow in a scratch script (reuse `C`, `grain`, and the vanilla-like ore style: stone or deepslate base with clusters of the material). Oil shale: dark layered stone with tar-black glossy specks. Neodymium: deepslate with silvery-grey metallic flecks. Silicon: stone/deepslate with blue-grey crystalline facets. Items: tar lump, raw nugget, ingot, crystal shard, round wafer.
- [ ] **Step 2:** Assemble a ×8 contact sheet (labels A–D per texture), send it to the user with SendUserFile, and **stop until they pick** one concept per texture. Record the picks in the ledger.

### Task 2: Register blocks, items, creative tab, translations

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/registry/ModBlocks.java`, `ModItems.java`, `ModCreativeTabs.java`
- Modify: `src/main/resources/assets/ravecraft/lang/en_us.json`, `fr_fr.json`
- Create: `src/test/java/com/ravecraft/mod/MaterialsLangTest.java`
- Create: `src/gametest/java/com/ravecraft/mod/gametest/MaterialsGameTest.java` (declare it in `src/gametest/resources/fabric.mod.json`)

**Interfaces:**
- Produces: `ModBlocks.OIL_SHALE`, `DEEPSLATE_NEODYMIUM_ORE`, `SILICON_ORE`, `DEEPSLATE_SILICON_ORE` (`Block`); `ModBlocks.ores(): List<Block>` (those four, in that order); `ModItems.BITUMEN`, `RAW_NEODYMIUM`, `NEODYMIUM_INGOT`, `SILICON_CRYSTAL`, `SILICON_WAFER` (`Item`); `ModItems.materials(): List<Item>` (those five, in that order).

- [ ] **Step 1: Write the failing tests.**
  - `MaterialsLangTest.everyMaterialHasBothTranslations()`: parse both lang files with Gson; for each of the 4 block ids assert key `block.ravecraft.<id>` exists and is non-blank in both, for each of the 5 item ids assert `item.ravecraft.<id>`.
  - `MaterialsGameTest.oresRequireTheirTool(h)`: for each ore, `state.requiresCorrectToolForDrops()` is true; `DropExperienceBlock` instance.
  - `MaterialsGameTest.materialsInCreativeTab(h)`: every block item of `ModBlocks.ores()` and every `ModItems.materials()` item is in `ModCreativeTabs.RAVECRAFT.getDisplayItems()` after `CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess())` (check the exact rebuild signature in 26.3 sources).
- [ ] **Step 2:** `./gradlew test --tests '*MaterialsLangTest'` → FAIL (missing keys); GameTests compile-fail on missing fields.
- [ ] **Step 3:** Implement. `ModBlocks.register` reused with `BlockItem::new`. Properties per the table above (`mapColor`: `COLOR_BLACK` for oil shale, `DEEPSLATE` for deepslate ores, `STONE` for silicon ore). `ModItems` gets a private `register(String, Function<Item.Properties, Item>)` helper that sets the id first. Creative tab order: ores (`ModBlocks.ores()`), then `ModItems.materials()`, then the speakers. Translations exactly as spec 3.1 / 3.2 (fr: « Schiste bitumineux », « Minerai de néodyme des abîmes », « Minerai de silicium », « Minerai de silicium des abîmes », « Bitume », « Néodyme brut », « Lingot de néodyme », « Cristal de silicium », « Plaquette de silicium »).
- [ ] **Step 4:** `./gradlew test` and `./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Register ores and raw materials`.

### Task 3: Loot tables and tags

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftBlockLootProvider.java`, `RaveCraftBlockTagProvider.java`, `RaveCraftDataGenerator.java`
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftItemTagProvider.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModTags.java` (the `c:` tag keys, so main code and tests share them)
- Modify: `MaterialsGameTest.java`

**Interfaces:**
- Produces: `ModTags.Blocks.ORES`, `ORES_NEODYMIUM`, `ORES_SILICON`, `ORES_OIL_SHALE`, `ORES_IN_GROUND_STONE`, `ORES_IN_GROUND_DEEPSLATE` (`TagKey<Block>`, namespace `c`); `ModTags.Items.` same ore names plus `RAW_MATERIALS_NEODYMIUM` (`c:raw_materials/neodymium`), `INGOTS_NEODYMIUM` (`c:ingots/neodymium`), `GEMS_SILICON` (`c:gems/silicon`).

- [ ] **Step 1: Write the failing GameTests** in `MaterialsGameTest`. Drops via `Block.getDrops(state, h.getLevel(), pos, null, null, tool)`:
  - `oilShaleDropsBitumen`: stone pickaxe, 20 rolls, each roll 1–3 `bitumen` and nothing else.
  - `neodymiumDropsRaw`: iron pickaxe → exactly 1 `raw_neodymium`.
  - `siliconDropsCrystals`: iron pickaxe on both silicon ores → 1–2 `silicon_crystal`.
  - `silkTouchDropsOre`: pickaxe enchanted Silk Touch **and** Fortune III → each ore drops itself (count 1).
  - `fortuneRaisesDrops`: Fortune III on silicon ore over 50 rolls → max drop count > 2.
  - `toolTiers`: `new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(oilShale)` false, stone true; stone pickaxe false on neodymium and both silicon ores, iron true.
  - `commonTags`: each ore `is(ModTags.Blocks.ORES)`; `silicon_ore` in `ORES_IN_GROUND_STONE`, deepslate ores in `ORES_IN_GROUND_DEEPSLATE`; `raw_neodymium` stack `is(RAW_MATERIALS_NEODYMIUM)`, ingot in `INGOTS_NEODYMIUM`, crystal in `GEMS_SILICON`.
  - `explosionsDecayDrops`: `state.getDrops(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withParameter(LootContextParams.EXPLOSION_RADIUS, 100.0F))` on oil shale, 50 rolls → total `bitumen` count < 25 (without the radius parameter, at least 50).
- [ ] **Step 2:** `./gradlew runGameTest` → the new tests FAIL (empty loot, missing tags).
- [ ] **Step 3:** Implement.
  - Loot: neodymium `createOreDrop(block, ModItems.RAW_NEODYMIUM)`; oil shale and silicon built like vanilla `createCopperOreDrops` (silk-touch dispatch, explosion decay, `SetItemCountFunction.setCount(ContextIntProviders.between(1, 3))` resp. `(1, 2)`, then `ApplyBonusCount.addOreBonusCount(fortune)`).
  - Block tags: `mineable/pickaxe`, `needs_stone_tool` (oil shale), `needs_iron_tool` (three others), the `c:` tags above (`c:ores` contains the three sub-tags).
  - Item tags: copy the block ore tags (`copy(blockTag, itemTag)`), plus the three material tags. Register the provider in `RaveCraftDataGenerator`.
- [ ] **Step 4:** `./gradlew runDatagen && ./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Add ore loot tables and common tags`.

### Task 4: Smelting recipes

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftRecipeProvider.java`
- Modify: `MaterialsGameTest.java`

- [ ] **Step 1: Write the failing GameTest** `smeltingRecipes`: through the server `RecipeManager`, look up `ravecraft:<result>_from_<smelting|blasting>_<input>` and assert type, result, cooking time and XP:

  | Input(s) | Result | Smelting | Blasting | XP |
  |---|---|---|---|---|
  | `raw_neodymium`, `deepslate_neodymium_ore` | `neodymium_ingot` | 200 | 100 | 1.0 |
  | `silicon_crystal` | `silicon_wafer` | 200 | none | 0.5 |
  | `silicon_ore`, `deepslate_silicon_ore` | `silicon_crystal` | 200 | 100 | 0.7 |
  | `oil_shale` | `bitumen` | 200 | 100 | 0.1 |

  Also assert no blasting recipe exists for `silicon_crystal`.
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3:** Implement with `oreSmelting(...)` / `oreBlasting(...)` (`RecipeCategory.MISC`, `CookingBookCategory.MISC`, groups `neodymium_ingot`, `silicon_wafer`, `silicon_crystal`, `bitumen`). Each recipe unlocks on having its input (the helper already does this).
- [ ] **Step 4:** `./gradlew runDatagen && ./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Add smelting recipes for raw materials`.

### Task 5: World generation

**Files:**
- Create: `src/main/java/com/ravecraft/mod/world/gen/ModWorldgen.java`
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftOreFeatures.java` (bootstraps for `Registries.FEATURE` and `Registries.PLACED_FEATURE`)
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftWorldgenProvider.java` (`FabricDynamicRegistryProvider`, `entries.addAll(...)` for both registries)
- Modify: `RaveCraftDataGenerator.java` (`buildRegistry`, add provider), `RaveCraft.java` (`ModWorldgen.init()` after `ModItems.init()`)
- Modify: `MaterialsGameTest.java`

**Interfaces:**
- Produces: `ModWorldgen.ORE_OIL_SHALE`, `ORE_NEODYMIUM`, `ORE_SILICON` (`ResourceKey<Feature>`); `ModWorldgen.PLACED_OIL_SHALE`, `PLACED_OIL_SHALE_EXTRA`, `PLACED_NEODYMIUM`, `PLACED_SILICON` (`ResourceKey<PlacedFeature>`, ids `ore_oil_shale`, `ore_oil_shale_extra`, `ore_neodymium`, `ore_silicon`); `ModWorldgen.init()`.

Values (spec 4):

| Feature | Targets | Size | Air discard | Placed | Count | Height |
|---|---|---|---|---|---|---|
| `ore_oil_shale` | stone ore replaceables → `oil_shale` | 17 | 0 | `ore_oil_shale` (overworld) | 12 | `uniform(absolute(20), absolute(80))` |
| | | | | `ore_oil_shale_extra` (`c:is_desert`, `minecraft:is_badlands`) | 8 | same |
| `ore_neodymium` | deepslate ore replaceables → `deepslate_neodymium_ore` | 6 | 0.5 | `ore_neodymium` (overworld) | 3 | `triangle(absolute(-64), absolute(-16))` |
| `ore_silicon` | stone → `silicon_ore`, deepslate → `deepslate_silicon_ore` | 8 | 0 | `ore_silicon` (overworld) | 5 | `triangle(absolute(-32), absolute(48))` |

Rule tests as vanilla `OreFeatures.bootstrap`: `TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES)` / `DEEPSLATE_ORE_REPLACEABLES`. Placement modifiers as vanilla `commonOrePlacement`: `CountPlacement.of(n)`, `InSquarePlacement.spread()`, height, `BiomeFilter.biome()`.

- [ ] **Step 1: Write the failing GameTests** in `MaterialsGameTest`, reading `h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)` and each biome's `getGenerationSettings().features()` at step `GenerationStep.Decoration.UNDERGROUND_ORES`:
  - `overworldBiomesHaveOres`: `plains` and `desert` contain `ore_oil_shale`, `ore_neodymium`, `ore_silicon`.
  - `extraShaleOnlyInDryBiomes`: `desert` and `badlands` contain `ore_oil_shale_extra`; `plains` does not.
  - `noOresOutsideOverworld`: `nether_wastes` and `the_end` contain none of the four.
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3:** Implement. `ModWorldgen.init()` calls `BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), UNDERGROUND_ORES, key)` for the three overworld placements and `BiomeSelectors.tag(...)` (desert `c:is_desert`, `BiomeTags.IS_BADLANDS`) for the extra one. Datagen: `buildRegistry` adds `Registries.FEATURE` then `Registries.PLACED_FEATURE` bootstraps from `RaveCraftOreFeatures`.
- [ ] **Step 4:** `./gradlew runDatagen` (JSON appears under `src/main/generated/data/ravecraft/worldgen/`), then `./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Generate ores in the overworld`.

### Task 6: Textures and models

**Files:**
- Modify: `tools/textures/gen_textures.py` (4 functions in `BLOCKS`, 5 in `ITEMS`, from the concepts picked in Task 1)
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftModelProvider.java`
- Generated: `src/main/resources/assets/ravecraft/textures/{block,item}/*.png`, `src/main/generated/assets/...`

- [ ] **Step 1:** Write the picked textures in `gen_textures.py`; run `python tools/textures/gen_textures.py`; open `art/textures/preview.png` and check the 9 new textures against the picks.
- [ ] **Step 2:** Models: `generators.createTrivialCube(block)` for the 4 ores in `generateBlockStateModels`; `generators.generateFlatItem(item, ModelTemplates.FLAT_ITEM)` for the 5 items in `generateItemModels`.
- [ ] **Step 3:** `./gradlew runDatagen && ./gradlew build && ./gradlew codeQuality` → all green.
- [ ] **Step 4: Commit** `Add ore and raw material textures`.

### Task 7: In-game check and docs

**Files:**
- Modify: `docs/SPEC.md` (section 11: add the line `- [ ] Matériaux et composants (spec [2026-11-22-materials-design.md](superpowers/specs/2026-11-22-materials-design.md), quatre PR)` with sub-item `- [x] 7a minerais et matières (PR feat/materials-ores, plan [2026-11-22-materials-ores.md](superpowers/plans/2026-11-22-materials-ores.md))`; tick it only after the user's in-game check)

- [ ] **Step 1:** Give the user this check list for a new creative world: the 4 ores and 5 items in the RaveCraft tab with textures and names in both languages; `/locate` is not available for ores, so in a new survival world find oil shale between Y 20 and 80, silicon in caves below Y 48, neodymium in deepslate below Y −16; mining with the right/wrong pickaxe; Fortune and Silk Touch; furnace and blast furnace recipes appear in the recipe book after picking up each input.
- [ ] **Step 2:** After the user confirms, update `docs/SPEC.md` and commit `Track materials 7a in the spec`.
