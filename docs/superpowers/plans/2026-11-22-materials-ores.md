# Materials 7a — ores, shale and raw materials Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two ores, the shale rock family (13 blocks), their world generation, the five raw-material items, smelting and stonecutting recipes (PR 7a, branch `feat/materials-ores`).

**Architecture:** Blocks and items are registered in `registry/ModBlocks` and `registry/ModItems` like the speakers; the three shale `BlockFamily` objects live in `registry/ModBlockFamilies` so datagen models and recipes come from vanilla's family helpers. World generation keys live in `world/gen/ModWorldgen` (main source set), which adds the placed features to biomes with Fabric's `BiomeModifications`; feature and placement objects are bootstrapped in datagen and land as JSON in `src/main/generated`. Loot, tags, models and recipes extend the existing datagen providers. Oil shale and neodymium ore use several textures picked at random per block (blockstate variants).

**Tech Stack:** Fabric (Minecraft 26.3, Mojang names), Fabric API `fabric-biome-api-v1`, `fabric-data-generation-api-v1`, GameTest, JUnit 5, Pillow texture generator.

**Spec:** [docs/superpowers/specs/2026-11-22-materials-design.md](../specs/2026-11-22-materials-design.md) (sections 1, 2 D9–D13, 3.1, 3.2, 4, 9).

## Global Constraints

- Mojang names only; verify every Minecraft/Fabric class before use (sources in `.gradle/loom-cache/minecraftMaven/.../*-sources.jar`). In 26.3 configured features are `Registries.FEATURE` holding `Feature` objects: `new OreFeature(List<BlockReplacement> targets, int size[, float discardChanceOnAirExposure])`, `BlockReplacement.replace(RuleTest, BlockState)`; placements are `PlacementUtils.register(context, key, Holder<Feature>, List<PlacementModifier>)`. Families: `BlockFamilies.familyBuilder(base)….getFamily()` (see `BlockFamilies.TUFF` / `POLISHED_TUFF` / `TUFF_BRICKS`), `RecipeProvider.generateRecipes(BlockFamily, FeatureFlagSet)`, `BlockModelGenerators.family(Block).generateFor(BlockFamily)`.
- Every block/item gets its id before construction (`properties.setId(key)`), as in `ModBlocks.register`.
- No `net.minecraft.client` import in `src/main/java`.
- Javadoc on every class, every public/protected method and every method over 10 lines, with `@param`/`@return` on public methods; cognitive complexity ≤ 15, at most two nested `if`/loops. `RaveCraft.id(path)` for ids, `RaveCraft.LOGGER` for logs.
- Textures: 16×16 RGBA, produced by `tools/textures/gen_textures.py`, palette `C`, from the picks recorded in the ledger (Task 1). **Exceptions (spec D13):** the deepslate background of the neodymium ore is vanilla `deepslate.png`, read unchanged from the Loom client jar by the generator; the neodymium ingot is vanilla `item/iron_ingot.png` recoloured (each tone mapped by luminance rank to the picked 8-tone palette, `ingot_v8.py`). Both documented in `LICENSE-ASSETS.md` and `CLAUDE.md`. No other Mojang pixel is copied.
- Translations in both `assets/ravecraft/lang/en_us.json` and `fr_fr.json`, names exactly as spec 3.1 / 3.2.
- Commits in English, imperative, **no Claude attribution line**. Never `git add art/proposals/` or `.superpowers/`. No push, no PR without the user's explicit OK.
- Done only when `./gradlew runDatagen`, `./gradlew build` and `./gradlew codeQuality` are green (prefix `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`).

Blocks and values (from the spec):

| Block | Class | Strength | Sound | XP when mined | Tier tag | Map colour |
|---|---|---|---|---|---|---|
| `oil_shale` | `DropExperienceBlock` | 3.0 / 3.0 | `STONE` | `UniformInt.of(0, 2)` | `needs_stone_tool` | `COLOR_BLACK` |
| `deepslate_neodymium_ore` | `DropExperienceBlock` | 4.5 / 3.0 | `DEEPSLATE` | `ConstantInt.of(0)` | `needs_iron_tool` | `DEEPSLATE` |
| `shale` | `Block` | 1.5 / 6.0 | `TUFF` | — | — | `COLOR_GRAY` |
| `polished_shale` | `Block` | 1.5 / 6.0 | `POLISHED_TUFF` | — | — | `COLOR_GRAY` |
| `shale_bricks`, `chiseled_shale` | `Block` | 1.5 / 6.0 | `TUFF_BRICKS` | — | — | `COLOR_GRAY` |
| `<base>_stairs` / `_slab` / `_wall` | `StairBlock` / `SlabBlock` / `WallBlock` | `ofFullCopy(base)` (wall also `forceSolidOn()`) | | | | |

Stair/slab/wall ids: `shale_*`, `polished_shale_*`, `shale_brick_*`. All 15 blocks: `requiresCorrectToolForDrops()`, `mineable/pickaxe`, instrument `BASEDRUM`. Items: `bitumen`, `raw_neodymium`, `neodymium_ingot`, `silicon`, `silicon_wafer` (plain `Item`, stack 64).

## Review Focus

1. A player mining with Fortune III and Silk Touch on one pickaxe gets the ore block, not boosted drops (silk-touch dispatch wins) — pinned in Task 3.
2. Ores blown up by TNT/creepers drop less, like vanilla (explosion decay) — pinned in Task 3.
3. A wooden pickaxe on oil shale, or a stone pickaxe on neodymium, yields nothing; any pickaxe works on shale blocks, a bare hand does not — pinned in Task 3.
4. The Nether and the End never get these ores or shale, and only deserts/badlands get the extra oil shale — pinned in Task 5.
5. A double shale slab drops two slabs, not one — pinned in Task 3.

Not testable here, stated in release notes: ores and shale only appear in chunks generated after installing the mod.

---

### Task 1: Visual proposals (gate) — done

All picks are in the ledger (`.superpowers/sdd/2026-11-22-materials-ores/progress.md`), rendered in `art/proposals/materials-ores/` (not committed) from scratchpad scripts `shale_v4.py`, `oil_v10.py`, `summary_v1.py`, `derivs_v1.py`, `ore_proposals.py`, `items_v3.py`, `items_v4.py`, `wafer_v5.py`. Summary in spec section 9.

### Task 2: Register blocks, items, families, creative tab, translations

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/registry/ModBlocks.java`, `ModItems.java`, `ModCreativeTabs.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModBlockFamilies.java`
- Modify: `src/main/resources/assets/ravecraft/lang/en_us.json`, `fr_fr.json`
- Create: `src/test/java/com/ravecraft/mod/MaterialsLangTest.java`
- Create: `src/gametest/java/com/ravecraft/mod/gametest/MaterialsGameTest.java` (declare it in `src/gametest/resources/fabric.mod.json`)

**Interfaces:**
- Produces: `ModBlocks.OIL_SHALE`, `DEEPSLATE_NEODYMIUM_ORE`, `SHALE`, `SHALE_STAIRS`, `SHALE_SLAB`, `SHALE_WALL`, `POLISHED_SHALE`, `POLISHED_SHALE_STAIRS`, `POLISHED_SHALE_SLAB`, `POLISHED_SHALE_WALL`, `SHALE_BRICKS`, `SHALE_BRICK_STAIRS`, `SHALE_BRICK_SLAB`, `SHALE_BRICK_WALL`, `CHISELED_SHALE` (`Block`); `ModBlocks.ores(): List<Block>` (`OIL_SHALE`, `DEEPSLATE_NEODYMIUM_ORE`); `ModBlocks.shaleFamily(): List<Block>` (the 13 shale blocks in the order above); `ModItems.BITUMEN`, `RAW_NEODYMIUM`, `NEODYMIUM_INGOT`, `SILICON`, `SILICON_WAFER` (`Item`); `ModItems.materials(): List<Item>` (those five, in that order); `ModBlockFamilies.SHALE`, `POLISHED_SHALE`, `SHALE_BRICKS` (`BlockFamily`, built like vanilla tuff: shale → stairs/slab/wall/polished, `generateStonecutterRecipe()`; polished → stairs/slab/wall/bricks; bricks → stairs/slab/wall/chiseled).

- [ ] **Step 1: Write the failing tests.**
  - `MaterialsLangTest.everyMaterialHasBothTranslations()`: parse both lang files with Gson; for each of the 15 block ids assert `block.ravecraft.<id>` exists and is non-blank in both, for each of the 5 item ids `item.ravecraft.<id>`.
  - `MaterialsGameTest.oresRequireTheirTool(h)`: both ores are `DropExperienceBlock` and `requiresCorrectToolForDrops()`.
  - `MaterialsGameTest.shaleFamilyShapes(h)`: stairs are `StairBlock`, slabs `SlabBlock`, walls `WallBlock`; every family block `requiresCorrectToolForDrops()`.
  - `MaterialsGameTest.materialsInCreativeTab(h)`: every item of `ores()`, `shaleFamily()` and `materials()` is in `ModCreativeTabs.RAVECRAFT.getDisplayItems()` after `CreativeModeTabs.tryRebuildTabContents(...)` (check the exact signature in 26.3 sources).
- [ ] **Step 2:** `./gradlew test --tests '*MaterialsLangTest'` → FAIL (missing keys); GameTests compile-fail on missing fields.
- [ ] **Step 3:** Implement per the table above. `ModItems` gets a private `register(String, Function<Item.Properties, Item>)` helper that sets the id first. Creative tab order: `ores()`, `shaleFamily()`, `materials()`, then the speakers. Translations exactly as spec 3.1 / 3.2.
- [ ] **Step 4:** `./gradlew test` and `./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Register ores, shale family and raw materials`.

### Task 3: Loot tables and tags

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftBlockLootProvider.java`, `RaveCraftBlockTagProvider.java`, `RaveCraftDataGenerator.java`
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftItemTagProvider.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModTags.java` (the `c:` tag keys)
- Modify: `MaterialsGameTest.java`

**Interfaces:**
- Produces: `ModTags.Blocks.ORES`, `ORES_NEODYMIUM`, `ORES_OIL_SHALE`, `ORES_IN_GROUND_STONE`, `ORES_IN_GROUND_DEEPSLATE` (`TagKey<Block>`, namespace `c`); `ModTags.Items.` the same ore names plus `RAW_MATERIALS_NEODYMIUM` (`c:raw_materials/neodymium`), `INGOTS_NEODYMIUM` (`c:ingots/neodymium`), `SILICON` (`c:silicon`).

- [ ] **Step 1: Write the failing GameTests.** Drops via `Block.getDrops(state, h.getLevel(), pos, null, null, tool)`:
  - `oilShaleDropsBitumen`: stone pickaxe, 20 rolls, each roll 1–3 `bitumen` and nothing else.
  - `neodymiumDropsRaw`: iron pickaxe → exactly 1 `raw_neodymium`.
  - `silkTouchDropsOre`: pickaxe with Silk Touch **and** Fortune III → each ore drops itself (count 1).
  - `fortuneRaisesDrops`: Fortune III on oil shale over 50 rolls → max drop count > 3.
  - `toolTiers`: wooden pickaxe `isCorrectToolForDrops(oil_shale)` false, stone true; stone false on neodymium, iron true; wooden pickaxe true on every shale family block.
  - `shaleBlocksDropThemselves`: wooden pickaxe → each family block drops one of itself; a double `shale_slab` (`SlabBlock.TYPE = DOUBLE`) drops 2.
  - `commonTags`: each ore `is(ModTags.Blocks.ORES)`; oil shale in `ORES_IN_GROUND_STONE`, neodymium in `ORES_IN_GROUND_DEEPSLATE`; item tags on `raw_neodymium`, ingot, `silicon`; stairs/slabs/walls in `BlockTags.STAIRS` / `SLABS` / `WALLS`.
  - `explosionsDecayDrops`: oil shale with `EXPLOSION_RADIUS` 100, 50 rolls → total `bitumen` < 25 (without the radius, at least 50).
- [ ] **Step 2:** `./gradlew runGameTest` → the new tests FAIL.
- [ ] **Step 3:** Implement.
  - Loot: neodymium `createOreDrop(block, ModItems.RAW_NEODYMIUM)`; oil shale like vanilla `createCopperOreDrops` with `between(1, 3)`; family blocks `dropSelf`, slabs `createSlabItemTable`.
  - Block tags: `mineable/pickaxe` (all 15), `needs_stone_tool` (oil shale), `needs_iron_tool` (neodymium), `stairs`, `slabs`, `walls`, the `c:` tags (`c:ores` contains its sub-tags). Item tags: copy the ore tags and stairs/slabs/walls, plus the three material tags; register the provider.
- [ ] **Step 4:** `./gradlew runDatagen && ./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Add loot tables and tags for ores and shale`.

### Task 4: Smelting and shale recipes

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftRecipeProvider.java`
- Modify: `MaterialsGameTest.java`

- [ ] **Step 1: Write the failing GameTests.**
  - `smeltingRecipes`: through the server `RecipeManager`, look up `ravecraft:<result>_from_<smelting|blasting>_<input>` and assert type, result, cooking time and XP:

    | Input(s) | Result | Smelting | Blasting | XP |
    |---|---|---|---|---|
    | `raw_neodymium`, `deepslate_neodymium_ore` | `neodymium_ingot` | 200 | 100 | 1.0 |
    | `minecraft:quartz` | `silicon` | none | 100 | 0.5 |
    | `silicon` | `silicon_wafer` | 200 | none | 0.5 |
    | `oil_shale` | `bitumen` | 200 | 100 | 0.1 |

    Also assert no smelting recipe for quartz and no blasting recipe for `silicon`.
  - `shaleRecipes`: shaped recipes exist for `polished_shale` (4 from 4 shale), `shale_bricks` (4 from 4 polished), `chiseled_shale` (from 2 `shale_brick_slab`), and stairs/slab/wall of each base; stonecutting from `shale` yields every other family block, from `polished_shale` its derivatives plus bricks, their derivatives and chiseled, from `shale_bricks` its derivatives and chiseled.
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3:** Implement: `oreSmelting` / `oreBlasting` (`RecipeCategory.MISC`, groups `neodymium_ingot`, `silicon`, `silicon_wafer`, `bitumen`); `generateRecipes(family, flags)` for the three `ModBlockFamilies`, plus explicit `stonecutterResultFromBase` lines for the cross-family results that vanilla adds by hand for tuff (compare with `VanillaRecipeProvider` tuff lines).
- [ ] **Step 4:** `./gradlew runDatagen && ./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Add smelting and shale recipes`.

### Task 5: World generation

**Files:**
- Create: `src/main/java/com/ravecraft/mod/world/gen/ModWorldgen.java`
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftOreFeatures.java`
- Create: `src/client/java/com/ravecraft/mod/datagen/RaveCraftWorldgenProvider.java` (`FabricDynamicRegistryProvider`)
- Modify: `RaveCraftDataGenerator.java` (`buildRegistry`, add provider), `RaveCraft.java` (`ModWorldgen.init()` after `ModItems.init()`)
- Modify: `MaterialsGameTest.java`

**Interfaces:**
- Produces: `ModWorldgen.ORE_SHALE`, `ORE_OIL_SHALE`, `ORE_NEODYMIUM` (`ResourceKey<Feature>`); `PLACED_SHALE`, `PLACED_OIL_SHALE`, `PLACED_OIL_SHALE_EXTRA`, `PLACED_NEODYMIUM` (`ResourceKey<PlacedFeature>`, ids `ore_shale`, `ore_oil_shale`, `ore_oil_shale_extra`, `ore_neodymium`); `ModWorldgen.init()`.

| Feature | Targets | Size | Air discard | Placed | Count | Height |
|---|---|---|---|---|---|---|
| `ore_shale` | `base_stone_overworld` → `shale` | 64 | 0 | `ore_shale` (overworld) | 2 | `uniform(absolute(0), absolute(80))` |
| `ore_oil_shale` | stone ore replaceables → `oil_shale`; `BlockMatchTest(shale)` → `oil_shale` | 17 | 0 | `ore_oil_shale` (overworld) | 12 | `uniform(absolute(20), absolute(80))` |
| | | | | `ore_oil_shale_extra` (`c:is_desert`, `minecraft:is_badlands`) | 8 | same |
| `ore_neodymium` | deepslate ore replaceables → `deepslate_neodymium_ore` | 6 | 0.5 | `ore_neodymium` (overworld) | 3 | `triangle(absolute(-64), absolute(-16))` |

Rule tests as vanilla `OreFeatures.bootstrap`; placement modifiers as vanilla `commonOrePlacement` (`CountPlacement.of(n)`, `InSquarePlacement.spread()`, height, `BiomeFilter.biome()`).

- [ ] **Step 1: Write the failing GameTests**, reading each biome's `getGenerationSettings().features()` at `UNDERGROUND_ORES`:
  - `overworldBiomesHaveOres`: `plains` and `desert` contain `ore_shale`, `ore_oil_shale`, `ore_neodymium`.
  - `extraShaleOnlyInDryBiomes`: `desert` and `badlands` contain `ore_oil_shale_extra`; `plains` does not.
  - `noOresOutsideOverworld`: `nether_wastes` and `the_end` contain none of the four.
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3:** Implement with `BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), UNDERGROUND_ORES, key)` and `BiomeSelectors.tag(...)` for the extra one; datagen bootstraps in `buildRegistry`. Register `ore_shale` before `ore_oil_shale` so oil shale veins can cut through shale pockets.
- [ ] **Step 4:** `./gradlew runDatagen`, then `./gradlew runGameTest` → PASS.
- [ ] **Step 5: Commit** `Generate ores and shale in the overworld`.

### Task 6: Textures, models and asset licence note

**Files:**
- Modify: `tools/textures/gen_textures.py`
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftModelProvider.java`
- Modify: `LICENSE-ASSETS.md`, `CLAUDE.md` (the D13 exception)
- Generated: `src/main/resources/assets/ravecraft/textures/{block,item}/*.png`, `src/main/generated/assets/...`

Texture names: `shale_0` … `shale_5`, `oil_shale_0` … `oil_shale_7`, `deepslate_neodymium_ore_0` … `_5`, `polished_shale`, `shale_bricks`, `chiseled_shale`; items `bitumen`, `raw_neodymium`, `neodymium_ingot`, `silicon`, `silicon_wafer`.

- [ ] **Step 1:** Port the picked scratch functions (copies in `art/proposals/materials-ores/scripts/`, approved PNGs in `art/proposals/materials-ores/final/`) into `gen_textures.py`, seeds unchanged so the output matches the approved PNGs pixel for pixel: shale (`shale_v13.build` weights A, the 6 seeds chosen by `shale_v14`, shared 10-colour median-cut palette), oil shale (`family_v2.oil`: shale variant + straight tar seam), neodymium (`ore_big_v1` MEDIUM grains, seeds 110–115, background read from `~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar` → `assets/minecraft/textures/block/deepslate.png`, failing with a clear message if the jar is missing), polished (`polished_v5` B), bricks and chiseled (`family_v2.bricks` / `chiseled`), items (`items_v10` re-texture of the validated shapes), ingot (`ingot_v8` recolour B of the jar's `item/iron_ingot.png`). Add a check that compares each generated PNG to `final/` while porting (scratch, not committed). Run the generator, open `art/textures/preview.png`.
- [ ] **Step 2:** Models: shale gets a random-variant blockstate like oil shale (`shale_0..5`), its stairs, slab and wall use `shale_0`; `family(POLISHED_SHALE)` and `family(SHALE_BRICKS)` (chiseled comes with the bricks family); oil shale and neodymium get a blockstate whose single variant holds all their models (`MultiVariant` of plain `Variant`s, equal weight, no rotation — check the 26.3 API in `BlockModelGenerators`), item model on `_0`; `generateFlatItem` for the 5 items.
- [ ] **Step 3:** Add the D13 note to `LICENSE-ASSETS.md` (`deepslate_neodymium_ore_*.png` contain Mojang's deepslate texture and `neodymium_ingot.png` is a recolour of Mojang's iron ingot, © Mojang, not covered by CC BY-NC-SA) and to the texture rules of `CLAUDE.md`.
- [ ] **Step 4:** `./gradlew runDatagen && ./gradlew build && ./gradlew codeQuality` → all green.
- [ ] **Step 5: Commit** `Add ore, shale and raw material textures`.

### Task 7: In-game check and docs

**Files:**
- Modify: `docs/SPEC.md` (section 11: `- [ ] Matériaux et composants (spec [2026-11-22-materials-design.md](superpowers/specs/2026-11-22-materials-design.md), quatre PR)` with sub-item `- [x] 7a minerais, schiste et matières (PR feat/materials-ores, plan [2026-11-22-materials-ores.md](superpowers/plans/2026-11-22-materials-ores.md))`; tick only after the user's in-game check)

- [ ] **Step 1:** Give the user this check list: in a new creative world, the 2 ores, 13 shale blocks and 5 items in the RaveCraft tab, textures and names in both languages; walls of oil shale and neodymium show varied textures; in a new survival world find shale pockets and oil shale between Y 20 and 80, neodymium in deepslate below Y −16; right/wrong pickaxes, Fortune, Silk Touch; quartz in the blast furnace gives silicon; stonecutter on shale; recipes appear in the book after picking up each input.
- [ ] **Step 2:** After the user confirms, update `docs/SPEC.md` and commit `Track materials 7a in the spec`.
