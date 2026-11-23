# Materials 7b — machine core and coil winder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared machine base (pure-Java process and matcher, recipe type, block, block entity with sided inventory, menu, screen, vanilla recipe book) and ship it with the first machine, the **coil winder**, plus the two items it makes (PR 7b, branch `feat/machine-core`, stacked on `feat/materials-ores`).

**Architecture:** All machine rules (energy reserve, progress, output blocking, reset on recipe change, ingredient-to-slot assignment) live in `core.machine` in pure Java and are unit-tested. One generic `MachineRecipe` serves every machine; a `MachineKind` record bundles a machine's `RecipeType`, `RecipeSerializer`, `RecipeBookCategory` and display, so 7c only adds kinds. `MachineBlockEntity` adapts the core to the world (`WorldlyContainer`, save, tick), `MachineMenu extends RecipeBookMenu` with `RecipeBookType.FURNACE`, and the client screen uses a `RecipeBookComponent` subclass with one tab per machine.

**Tech Stack:** Fabric (Minecraft 26.3, Mojang names), Fabric API `fabric-menu-api-v1` (`ExtendedMenuType`), `fabric-data-generation-api-v1`, GameTest, JUnit 5, Pillow texture generator, Blockbench MCP.

**Spec:** [docs/superpowers/specs/2026-11-22-materials-design.md](../specs/2026-11-22-materials-design.md) (sections 1 "Découpage" 7b, 2 D3–D5 D8 D10, 3, 5, 6.1 `coil_winder`, 6.2, 7, 8, 9).

## Global Constraints

- Mojang names only; verify every class before use (sources: `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-{common,clientOnly}-*/26.3/*-sources.jar`). Checked for this plan: `RecipeBookMenu` (`handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear, RecipeHolder<?>, ServerLevel, Inventory)`, `fillCraftSlotsStackedContents(StackedItemContents)`, `getRecipeBookType()`); `Recipe<T extends RecipeInput>` (`matches`, `assemble(T)`, `showNotification`, `group`, `getSerializer`, `getType`, `placementInfo`, `display()`, `recipeBookCategory()`); `RecipeSerializer<T>(MapCodec<T>, StreamCodec<RegistryFriendlyByteBuf,T>)` is a record; results are `ItemStackTemplate`; `RecipeDisplay.Type<T>(MapCodec, StreamCodec)` registered in `BuiltInRegistries.RECIPE_DISPLAY`; `new RecipeBookCategory()` registered in `BuiltInRegistries.RECIPE_BOOK_CATEGORY`; client `RecipeBookComponent<M>` with `fillGhostRecipe(GhostSlots, RecipeDisplay, ContextMap)`, `selectMatchingRecipes`, `isCraftingSlot`, `TabInfo(Item, RecipeBookCategory)` (model: `FurnaceRecipeBookComponent`, `AbstractFurnaceScreen`); Fabric `ExtendedMenuType` / `ExtendedMenuProvider`.
- Every block/item gets its id before construction (`properties.setId(key)`). No `net.minecraft.client` import in `src/main/java`.
- Javadoc on every class, public/protected method and method over 10 lines (`@param`/`@return` on public ones); cognitive complexity ≤ 15, cyclomatic ≤ 10, at most two nested `if`/loops; named constants in `core`; `RaveCraft.id(path)`; `RaveCraft.LOGGER`.
- Performance (CLAUDE.md, spec 5.1): an idle machine does no work per tick (early return on a cached flag); the recipe lookup runs only when the inventory changed; no block scans.
- Spec values: slots 0–2 inputs, 3 energy, 4 output; redstone dust = 8 operations, redstone block = 72; 1 unit per operation; `DEFAULT_PROCESSING_TICKS = 100`; at most 3 ingredients with counts, each in its own input slot, unused inputs empty; machine mined with a pickaxe, minimal tier stone; `facing` toward the player, `lit` during an operation.
- Coil winder recipes (spec 5.2): 1 `minecraft:copper_ingot` → 4 `copper_wire`; 4 `copper_wire` → 1 `voice_coil`. Crafting (6.1): `coil_winder` = `III / CRC / III` (`I` iron ingot, `C` copper ingot, `R` redstone dust). Unlock: one `has_<item>` criterion per distinct ingredient, OR-ed (6.2).
- Names (fr / en): `coil_winder` Bobineuse / Coil Winder; `copper_wire` Fil de cuivre / Copper Wire; `voice_coil` Bobine mobile / Voice Coil; recipe-book tab and screen title use the machine name. Both `en_us.json` and `fr_fr.json`.
- Textures 16×16 from `tools/textures/gen_textures.py` (vanilla art direction: start from the closest vanilla textures, no black outlines on items); the machine model is a Blockbench model in `art/models`, exported to `assets/ravecraft/models/block/`. Only the picks from Task 1.
- Commits in English, imperative, **no Claude attribution line**. Never `git add art/proposals/` or `.superpowers/`. No push, no PR without the user's explicit OK.
- Done only when `./gradlew runDatagen`, `./gradlew build` and `./gradlew codeQuality` are green (prefix `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"`).

Rulings already taken for this plan (spec is silent):
- **Sound and particles:** like the vanilla furnace, `animateTick` on the client plays a vanilla `SoundEvents` entry now and then and spawns particles while `lit`; no new sound file. Coil winder: a soft mechanical sound (pick among vanilla in Task 1) and copper-coloured `DustParticleOptions` at the front.
- **One generic recipe class:** `MachineRecipe` carries its `MachineKind` (not serialised; each kind's serializer builds recipes of its kind). The optional `mold` field is parsed now and required only for `extruding` (7c).
- **Craft recipe, loot, creative tab and translations of the coil winder ship in 7b**, so the machine is obtainable; the "Made in" tooltips stay in 7d.

## Review Focus

1. Shift-clicking from the player inventory sends redstone dust/blocks to the energy slot, other items to the inputs, and the output back to the inventory, without duplicating — pinned in Task 5 (`quickMoveStackRoutes`).
2. Clicking a recipe in the book while the inputs already hold other items returns them to the player before placing (no loss, no duplication), and a click without enough items leaves a ghost recipe — pinned in Task 5 (`placementReturnsOldInputs`, `placementGhostWhenMissing`).
3. Breaking a machine in the middle of an operation drops inputs, energy items, output, and nothing twice — pinned in Task 4 (`breakingDropsContents`).
4. A hopper adding more of the same input during an operation keeps the progress; replacing the input with something else resets it — pinned in Task 2 (JUnit) and Task 4 (`sameInputKeepsProgress`).
5. Saving and reloading the machine keeps progress and the energy reserve — pinned in Task 4 (`saveRoundTrip`).

---

### Task 1: Visual proposals (gate)

Per the user's rule, show 3–4 rendered options for each and wait for the picks before Task 6; record the picks in the ledger and spec section 9 ("Choix de 7b").

- [ ] **Step 1:** Extract the closest vanilla references (furnace, blast furnace, smoker front/side/top, loom, grindstone; items: copper ingot, string, chain) into the scratchpad and measure them.
- [ ] **Step 2:** Coil winder block: 3–4 Blockbench models (full cube or near-cube, front with a visible spool), each rendered off and `_on` (animated front: spinning spool, 4–8 frames). Render with `bbmodel_render`, contact sheet next to the vanilla furnace.
- [ ] **Step 3:** Screen background (176×166, vanilla container style, slots 3 inputs / energy / output, progress arrow, energy gauge on 72): 3 options.
- [ ] **Step 4:** Items `copper_wire` and `voice_coil`: 3–4 options each, shown at ×8 and ×3 on the inventory grey next to copper ingot and string.
- [ ] **Step 5:** Pick the vanilla sound for the loop (play names only, e.g. `block.grindstone.use`, `block.loom.take_result`, `ui.loom.select_pattern`). Wait for the user's picks. 💡 mark the recommended option.

### Task 2: Pure-Java machine core

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/machine/MachineProcess.java`, `ShapelessMatcher.java`, `MachineConstants.java`, `package-info.java`
- Test: `src/test/java/com/ravecraft/mod/core/machine/MachineProcessTest.java`, `ShapelessMatcherTest.java`

**Interfaces:**
- Produces:
  - `MachineConstants.OPERATIONS_PER_DUST = 8`, `OPERATIONS_PER_BLOCK = 72`, `DEFAULT_PROCESSING_TICKS = 100`, `MAX_INGREDIENTS = 3`, `MAX_ENERGY = 72`.
  - `final class MachineProcess`: `int energy()`, `int progress()`, `int total()`, `boolean running()`; `void setRecipe(@Nullable Object recipeKey, int processingTicks)` (same key keeps progress; different key or null resets to 0); `TickResult tick(boolean outputFits, IntSupplier refuel)` where `refuel` returns the units of one energy item it consumed (0 if none) and is called only when an operation must start with an empty reserve; `enum TickResult { IDLE, WAITING_ENERGY, BLOCKED, WORKING, DONE }` — `DONE` means "produce once now", after which progress is 0; `void load(int energy, int progress)`; energy is spent (1 unit) when an operation starts, never refunded.
  - `final class ShapelessMatcher`: `static <S, I> Optional<int[]> match(List<I> ingredients, ToIntFunction<I> count, BiPredicate<I, S> test, List<S> slots, Predicate<S> isEmpty, ToIntFunction<S> slotCount)` → for each ingredient the index of its slot, or empty. Rules: each ingredient in a distinct slot holding at least its count; every slot not assigned must be empty; at most `MAX_INGREDIENTS`. Backtracking over at most 3! orders.

- [ ] **Step 1: Write the failing tests**
  - `MachineProcessTest`: `dustGivesEightOperations` (refuel returns 8; 8 operations finish with no second refuel; the 9th calls refuel again); `blockGivesSeventyTwo`; `operationTakesHundredTicksAndProducesOnce` (99 ticks `WORKING`, tick 100 `DONE`, next tick starts over); `fullOutputBlocksAndKeepsProgress` (`outputFits=false` at the end → `BLOCKED`, progress stays at 100 until it fits, then `DONE`); `noEnergyWaitsWithProgressKept` (refuel returns 0 → `WAITING_ENERGY`, progress unchanged; later refuel 8 → resumes); `changedRecipeResetsProgress`; `sameRecipeKeepsProgress`; `noRecipeIsIdle`; `energyNeverExceedsMax`.
  - `ShapelessMatcherTest`: `matchesInAnyOrder`, `rejectsInsufficientCount`, `rejectsExtraNonEmptySlot`, `rejectsTwoIngredientsInOneSlot`, `matchesSingleIngredientInAnySlot`.
- [ ] **Step 2:** `./gradlew test --tests '*core.machine*'` → FAIL (classes missing).
- [ ] **Step 3:** Implement the three classes in `core/machine`.
- [ ] **Step 4:** `./gradlew test --tests '*core.machine*'` → PASS.
- [ ] **Step 5: Commit** `Add the pure-Java machine process and shapeless matcher`.

### Task 3: Machine recipe type, coil winder items and recipes

**Files:**
- Create: `src/main/java/com/ravecraft/mod/recipe/MachineRecipe.java`, `MachineRecipeInput.java`, `MachineRecipeDisplay.java`, `CountedIngredient.java`, `MachineKind.java`
- Create: `src/main/java/com/ravecraft/mod/registry/ModRecipes.java` (`init()` from `RaveCraft.onInitialize`)
- Modify: `registry/ModItems.java` (`COPPER_WIRE`, `VOICE_COIL`, plain `Item`, stack 64; `List<Item> components()`), lang files
- Create: `src/client/java/com/ravecraft/mod/datagen/MachineRecipeBuilder.java`; Modify: `RaveCraftRecipeProvider.java`
- Test: `src/gametest/java/com/ravecraft/mod/gametest/MachineGameTest.java` (declare in `src/gametest/resources/fabric.mod.json`)

**Interfaces:**
- Consumes: `ShapelessMatcher.match`, `MachineConstants`.
- Produces:
  - `record CountedIngredient(Ingredient ingredient, int count)` with `CODEC` (`{"ingredient": …, "count": n}`, count ≥ 1, default 1) and `STREAM_CODEC`.
  - `record MachineRecipeInput(List<ItemStack> inputs, ItemStack mold) implements RecipeInput` (`getItem(i)`: inputs then mold; `size()` = inputs + 1).
  - `MachineRecipe implements Recipe<MachineRecipeInput>`: `MachineKind kind()`, `List<CountedIngredient> ingredients()`, `Optional<Ingredient> mold()`, `ItemStackTemplate result()`, `int processingTime()`; `matches` = mold ok and `ShapelessMatcher.match(...)` present; `Optional<int[]> assignment(MachineRecipeInput)`; `assemble` = `result.create()`; `display()` = one `MachineRecipeDisplay`; `placementInfo()` = `PlacementInfo.create(ingredient list)`; `recipeBookCategory()` = `kind.category()`. JSON exactly as spec 5.3 (`ingredients`, optional `mold`, `result`, `processing_time` default 100, plus `Recipe.CommonInfo` / group like vanilla).
  - `record MachineKind(String name, RecipeType<MachineRecipe> type, RecipeSerializer<MachineRecipe> serializer, RecipeBookCategory category, boolean needsMold)`; `ModRecipes.WINDING` (`ravecraft:winding`, category `ravecraft:coil_winder`, `needsMold=false`); `ModRecipes.MACHINE_DISPLAY` (`RecipeDisplay.Type<MachineRecipeDisplay>` id `ravecraft:machine`).
  - `record MachineRecipeDisplay(List<SlotDisplay> inputs, List<Integer> counts, SlotDisplay mold, SlotDisplay energy, SlotDisplay result, SlotDisplay craftingStation, int duration) implements RecipeDisplay`; `energy` = redstone dust and block (`SlotDisplay.Composite` of `ItemSlotDisplay`), shown as a ghost like the furnace fuel.
  - datagen `MachineRecipeBuilder.machine(MachineKind, ItemLike result, int count)` `.input(ItemLike, int)` `.mold(ItemLike)` `.save(RecipeOutput, String name)`, adding one `has_<item>` criterion per distinct ingredient (OR); recipe ids `<result>_from_<kind>` (e.g. `copper_wire_from_winding`).
- [ ] **Step 1: Write the failing GameTests** in `MachineGameTest`: `windingRecipesExist` (both recipes found by key, kind `WINDING`, counts 1→4 and 4→1, processing time 100); `windingMatchesAnySlotOrder` (copper ingot in slot 2 matches; plus a stray item in slot 0 does not); `recipeJsonRoundTrip` (encode/decode a recipe with `MachineRecipe` codec gives an equal recipe, including a `mold`).
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL (compile errors on missing classes count as the red step).
- [ ] **Step 3:** Implement, register the type, serializer, category and display in `ModRecipes`, items and translations; write the two recipes and their unlock criteria in `RaveCraftRecipeProvider`; `./gradlew runDatagen`.
- [ ] **Step 4:** `./gradlew runGameTest` → all pass.
- [ ] **Step 5: Commit** `Add the machine recipe type and the winding recipes`.

### Task 4: Machine block and block entity, coil winder block

**Files:**
- Create: `src/main/java/com/ravecraft/mod/block/MachineBlock.java`, `CoilWinderBlock.java`; `src/main/java/com/ravecraft/mod/blockentity/MachineBlockEntity.java`
- Modify: `registry/ModBlocks.java` (`COIL_WINDER`: strength 3.5 like the furnace, sound `METAL`, `requiresCorrectToolForDrops`, light 13 when `lit`), `registry/ModBlockEntities.java` (`MACHINE` for all machines), `registry/ModCreativeTabs.java` (order: …materials, `copper_wire`, `voice_coil`, `coil_winder`, then speakers), lang files
- Modify datagen: `RaveCraftBlockLootProvider` (`dropSelf`), `RaveCraftBlockTagProvider` (`mineable/pickaxe`, `needs_stone_tool`), `RaveCraftRecipeProvider` (`coil_winder` `III / CRC / III`, criteria `has_iron_ingot`, `has_copper_ingot`, `has_redstone`)
- Test: `MachineGameTest`

**Interfaces:**
- Consumes: `MachineProcess`, `MachineKind`, `MachineRecipe.assignment`.
- Produces:
  - `abstract class MachineBlock extends BaseEntityBlock` with `FACING` (horizontal, toward the player on placement), `LIT`; `abstract MachineKind kind()`; server ticker only; on use opens the menu (Task 5 hooks it); contents dropped when removed (verify the 26.3 hook, e.g. `BlockEntity.preRemoveSideEffects`).
  - `MachineBlockEntity extends BlockEntity implements WorldlyContainer`: slot constants `INPUT_0..2 = 0..2`, `ENERGY = 3`, `OUTPUT = 4`, `SIZE = 5`; `static void serverTick(Level, BlockPos, BlockState, MachineBlockEntity)`; `int progress()`, `int total()`, `int energy()`; a cached `dirty` flag set in `setChanged()` drives the single recipe lookup (`ServerLevel.recipeAccess().getRecipeFor(kind.type(), input, level)`); on `DONE`: consume each ingredient's count from its assigned slot, merge `assemble` into `OUTPUT`; sets `LIT` only when it changes. Saves `Items`, `energy`, `progress` (ValueOutput/ValueInput as the speaker block entity does).
  - Faces: `UP` → slots 0–2, accepted when the slot already holds that item, or is empty and no other input holds it; sides → `ENERGY`, only `minecraft:redstone` and `minecraft:redstone_block`; `DOWN` → `OUTPUT`, extract only. Energy units: dust 8, block 72 (`MachineConstants`).
- [ ] **Step 1: Write the failing GameTests:** `winderMakesWire` (1 copper ingot + 1 dust, run 100 ticks → 4 wire in output, ingot gone, reserve 7); `winderMakesCoil` (4 wire → 1 voice coil); `outputFullBlocks` (output holds 64 wire → nothing consumed); `noEnergyWaits`; `sameInputKeepsProgress` (add a second ingot at tick 50 → output at tick 100); `changedInputResets` (swap the ingot for 4 wire at tick 50 → no output before tick 150); `hopperTopFillsInputs`, `hopperSideOnlyEnergy` (dust in, cobblestone stays in the hopper), `hopperBottomExtractsOutput`; `breakingDropsContents` (mid-operation: ingot, dust, wire dropped once each); `saveRoundTrip` (save to a `TagValueOutput`, load into a new entity: progress and energy equal); `litDuringOperation`; `pickaxeTier` (wooden pickaxe drops nothing, stone pickaxe drops the winder).
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3:** Implement; register; datagen loot, tags, craft recipe; `./gradlew runDatagen`. Placeholder model: the generated `cube_all` on a temporary texture is fine until Task 6.
- [ ] **Step 4:** `./gradlew runGameTest` → all pass.
- [ ] **Step 5: Commit** `Add the machine block entity and the coil winder`.

### Task 5: Menu, screen and recipe book

**Files:**
- Create: `src/main/java/com/ravecraft/mod/menu/MachineMenu.java`, `MachineRecipePlacer.java`; `src/main/java/com/ravecraft/mod/registry/ModMenus.java`
- Create: `src/client/java/com/ravecraft/mod/client/screen/machine/MachineScreen.java`, `MachineRecipeBookComponent.java`; Modify: `RaveCraftClient.java` (`MenuScreens.register`)
- Modify: `MachineBlock` (open the menu with `ExtendedMenuProvider`, position as the opening data), lang files (screen and tab names)
- Test: `MachineGameTest`

**Interfaces:**
- Consumes: `MachineBlockEntity` slots and getters, `MachineRecipe`, `MachineRecipeDisplay`, `ShapelessMatcher`.
- Produces:
  - `MachineMenu extends RecipeBookMenu`: slots 0–4 as the block entity, then the player inventory; `ContainerData` of 3 (`progress`, `total`, `energy`); `getRecipeBookType()` = `RecipeBookType.FURNACE` (spec D8); `quickMoveStack`: output → inventory; redstone dust/block → energy then inputs; other → inputs; inventory ↔ hotbar like the furnace; `handlePlacement` delegates to `MachineRecipePlacer`; `fillCraftSlotsStackedContents` accounts the three inputs; `MenuType` via Fabric `ExtendedMenuType` with a `BlockPos` stream codec.
  - `MachineRecipePlacer.place(MachineMenu, MachineRecipe, boolean useMaxItems, Inventory)` → `PostPlaceAction`: first moves the current inputs back to the player (if they do not fit, returns `NOTHING` and moves nothing); then puts each ingredient's count (with `useMaxItems`: as many multiples as stacks and inventory allow) into slot `i` for ingredient `i`; if an ingredient is short, places nothing and returns `PLACE_GHOST_RECIPE`.
  - Client: `MachineRecipeBookComponent extends RecipeBookComponent<MachineMenu>` (`isCraftingSlot` 0–3; `fillGhostRecipe` puts each input, the energy ghost if the energy slot is empty, and the result; `selectMatchingRecipes` keeps `MachineRecipeDisplay`); one `TabInfo(item of the machine, kind.category())`; `MachineScreen` draws background, progress arrow (`progress/total`), energy gauge (`energy / MAX_ENERGY`), recipe-book button, title = machine name.
- [ ] **Step 1: Write the failing GameTests** with `GameTestHelper.makeMockPlayer`: `quickMoveStackRoutes` (dust → slot 3, ingot → slot 0, output → player inventory, totals unchanged); `placementFillsInputs` (player has 8 wire: click `voice_coil` → slot 0 holds 4; with `useMaxItems` → 8); `placementReturnsOldInputs` (slot 1 holds cobblestone → back in the player inventory); `placementGhostWhenMissing` (3 wire → `PLACE_GHOST_RECIPE`, inventory unchanged); `bookTypeIsFurnace`.
- [ ] **Step 2:** `./gradlew runGameTest` → FAIL.
- [ ] **Step 3:** Implement menu, placer, registration, client screen and component; translations.
- [ ] **Step 4:** `./gradlew runGameTest` → all pass; `./gradlew build` green.
- [ ] **Step 5: Commit** `Add the machine menu, screen and recipe book`.

### Task 6: Coil winder visuals

**Files:**
- Modify: `tools/textures/materials.py` or a new sibling `tools/textures/machines.py` (block faces, animated `_on` front with `.png.mcmeta`, items, GUI background and sprites), `art/models/coil_winder.bbmodel`, `src/main/resources/assets/ravecraft/models/block/coil_winder{,_on}.json`
- Modify: `RaveCraftModelProvider` (blockstate `facing` × `lit` with `ROTATION_HORIZONTAL_FACING`, item model on the off model; `generateFlatItem` for `copper_wire`, `voice_coil`); `CoilWinderBlock.animateTick` (sound and particles picked in Task 1)

- [ ] **Step 1:** Port the picked textures and model; regenerate; open `art/textures/preview.png` and render the model off/on with `bbmodel_render`.
- [ ] **Step 2:** `./gradlew runDatagen && ./gradlew build && ./gradlew codeQuality` → all green.
- [ ] **Step 3: Commit** `Add the coil winder model, textures and screen`.

### Task 7: In-game check and docs

- [ ] **Step 1:** Give the user this check list: craft the coil winder from the recipe book (unlocked by iron, copper or redstone); place it (faces the player); open it, the recipe book has a coil winder tab and opening/filtering it also does it in the furnace; click the copper wire recipe (ingredients placed, Shift = max), missing items show as ghosts with the redstone ghost; run with dust: progress arrow, energy gauge, lit front animated, sound and particles; output full stops; hoppers on top, side and bottom; break it while running (contents drop); quit and reload (progress and energy kept); names in both languages.
- [ ] **Step 2:** After the user confirms, tick `7b` under "Matériaux et composants" in `docs/SPEC.md` section 11 (with links to this plan) and commit `Track materials 7b in the spec`.
