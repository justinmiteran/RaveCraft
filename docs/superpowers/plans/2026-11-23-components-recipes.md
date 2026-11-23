# Materials 7d: components and final recipes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the last four components (membrane, speaker driver, amplifier board, woofer). Give both speakers their final recipes built from those components. Add the "Made in" and mold tooltips, and close the materials sub-project in the SPEC.

**Architecture:**
- The four items are plain `Item`s in `ModItems`, listed in `components()` in tab order. Models and the creative tab follow that list, as they already do.
- Crafts go in `RaveCraftRecipeProvider` through one helper that adds a `has_<item>` criterion per distinct ingredient.
- Two static lookup tables in common code, `item/ItemOrigins`, say which machine makes an item and which part a mold makes. A GameTest keeps both tables equal to the loaded machine recipes. The client tooltip reads the tables.
- Textures are pixel grids in `tools/textures/machine_parts.py`, like the 7c items.

**Tech Stack:** Fabric 26.3 (Mojang names), Fabric datagen, GameTests, Pillow generator.

**Spec:** `docs/superpowers/specs/2026-11-22-materials-design.md` (sections 3.2, 5.2, 6, 8, 10). Branch `feat/components-recipes`, stacked on `feat/machines`.

## Global Constraints

- **Ids and names (fr / en):**
  - `membrane`: Membrane / Membrane
  - `speaker_driver`: Haut-parleur simple / Speaker Driver
  - `amplifier_board`: Carte d'amplification / Amplifier Board
  - `woofer`: Haut-parleur de grave / Woofer
- **Crafts (spec 6.1):**
  - `membrane` ×2: shapeless, 2 paper + 1 `plastic_sheet`.
  - `speaker_driver`: ` M  / IVI /  G `, with M = `membrane`, I = iron ingot, V = `voice_coil`, G = `iron_magnet`.
  - `woofer`: the same pattern, with M = `reinforced_membrane` and G = `neodymium_magnet`.
  - `amplifier_board`: shapeless, 1 `chip` + 1 `simple_circuit` + 2 `copper_wire`.
  - `portable_speaker`: ` D  /  B  /  S `, with D = `speaker_driver`, B = `plastic_casing`, S = `simple_circuit`.
  - `party_speaker`: `IWI / CPC / IAI`, with W = `woofer`, C = copper ingot, P = `portable_speaker`, A = `amplifier_board`.
- **Removed:** the provisional speaker crafts (`FFF / NRN / FFF` and `INI / CPC / IRI`). The dye recipes (`*_dyed`) stay.
- **Reinforced membrane (user decision, 7d):** extruding takes 1 `membrane` + 1 `bitumen` with `membrane_mold` and gives 1 `reinforced_membrane`. It replaces 2 `bitumen`.
- **Unlock:** one `has_<item>` criterion per distinct ingredient, any of them unlocks the recipe (spec 6.2).
- **Tooltips:** a grey line at the bottom of the tooltip, shown without Shift.
  - Machine products: `tooltip.ravecraft.made_in` = "Made in: %s" / "Fabriqué dans : %s", where `%s` is the machine's block name.
  - Molds: `tooltip.ravecraft.mold_for` = "For the extruder: %s" / "Pour l'extrudeuse : %s", where `%s` is the product's item name.
- **Tab order (spec 6.4):**
  - ores, shale family, storage blocks, materials;
  - tier 1: `copper_wire, voice_coil, iron_magnet, membrane, plastic_sheet, plastic_casing, circuit_board, simple_circuit, speaker_driver`;
  - tier 2: `neodymium_magnet, reinforced_membrane, chip, amplifier_board, woofer`;
  - molds, machines, speakers.
- **Textures chosen by the user**, all saved in `art/proposals/items7d/`, which is not versioned:
  - membrane A: the reinforced membrane grid recoloured to paper (`membrane_A.png`);
  - speaker driver E and woofer E: square steel flange with 4 screws, membrane inset, dark gasket (`speaker_driver_E.png`, `woofer_E.png`);
  - amplifier board B: chip and blue capacitors on the circuit board (`amplifier_board_B.png`).
- **Project rules (CLAUDE.md):**
  - Mojang names.
  - No `net.minecraft.client` import in `src/main`.
  - Javadoc and complexity rules, Checkstyle `ParameterNumber` ≤ 6.
  - `./gradlew runDatagen`, `build` and `codeQuality` green.
  - Commits in English, in the imperative, with no Claude attribution.

## Review Focus

1. **A dyed or named portable speaker still crafts into a party speaker.** The ingredient matches any portable speaker, whatever its components. Pinned in Task 3 (`partyAcceptsDyedPortable`).
2. **The old provisional speaker crafts are gone.** Iron, note blocks and redstone no longer give a speaker. Pinned in Task 3 (`oldSpeakerCraftsGone`).
3. **A membrane put in the extruder lands in an input, never in the mold slot,** and the reinforced membrane recipe runs with it. Pinned in Task 1 (`extruderTakesMembraneAsInput`).
4. **The tooltip tables never drift from the recipes.** Every machine recipe result has the right machine, and no item claims a machine that does not make it. Pinned in Task 4 (`madeInMatchesRecipes`, `moldForMatchesRecipes`).
5. **Items with no origin get no line.** Raw materials, vanilla items and speakers show no "Made in" line. Pinned in Task 4 (`noOriginWithoutMachine`).

---

### Task 1: The four components, their crafts and the reinforced membrane chain

**Files:**
- Modify: `src/main/java/com/ravecraft/mod/registry/ModItems.java`, `src/client/java/com/ravecraft/mod/datagen/RaveCraftRecipeProvider.java`, `src/main/resources/assets/ravecraft/lang/en_us.json`, `fr_fr.json`
- Create: `src/gametest/java/com/ravecraft/mod/gametest/ComponentsGameTest.java` (declare it in `src/gametest/resources/fabric.mod.json`)
- Modify: `src/gametest/java/com/ravecraft/mod/gametest/MachineRecipesGameTest.java` (the `reinforced_membrane_from_extruding` check)

**Interfaces:**
- Produces:
  - `ModItems.MEMBRANE`, `SPEAKER_DRIVER`, `AMPLIFIER_BOARD`, `WOOFER`.
  - `ModItems.components()` returns the 14 items in tab order: tier 1, then tier 2, as in Global Constraints.
  - `RaveCraftRecipeProvider.craft(...)`: a private helper that adds one `has_<item path>` per distinct item. Shaped variant: `shapedCraft(ItemLike result, int count, String symbols, ItemLike[] items, String... rows)`. Shapeless variant: `shapelessCraft(ItemLike result, int count, ItemLike... ingredients)`, where repeated ingredients mean a count. Reuse the existing `machineCraft` body: `machineCraft` becomes `shapedCraft(machine, 1, ...)`.
  - Recipe ids: `ravecraft:membrane`, `ravecraft:speaker_driver`, `ravecraft:woofer`, `ravecraft:amplifier_board`. Category MISC.

- [ ] **Step 1: Write the failing tests in `ComponentsGameTest`.** Use a helper `shaped(h, name)` / `shapeless(h, name)` that loads `ravecraft:<name>` from the recipe manager and fails if it is missing or of the wrong class.
  - `componentCraftsExist`:
    - `membrane` is shapeless, gives 2, and has 3 ingredients: 2 matching paper and 1 matching `plastic_sheet`.
    - `amplifier_board` is shapeless, gives 1, and has 4 ingredients: chip, simple circuit and 2 copper wires.
    - `speaker_driver` and `woofer` are shaped 3×3. Their `getIngredients()` index 1 is the membrane (or the reinforced membrane), 3 and 5 are iron, 4 is `voice_coil`, 7 is the magnet, and 0, 2, 6 and 8 are empty.
  - `componentsInTabOrder`: `ModItems.components()` equals exactly `List.of(COPPER_WIRE, VOICE_COIL, IRON_MAGNET, MEMBRANE, PLASTIC_SHEET, PLASTIC_CASING, CIRCUIT_BOARD, SIMPLE_CIRCUIT, SPEAKER_DRIVER, NEODYMIUM_MAGNET, REINFORCED_MEMBRANE, CHIP, AMPLIFIER_BOARD, WOOFER)`.
  - `craftsUnlockByAnyIngredient`:
    - Load the advancement `ravecraft:recipes/misc/<name>` from `server.getAdvancements()`.
    - For `membrane`, its criteria are exactly `{has_paper, has_plastic_sheet, has_the_recipe}`.
    - For `speaker_driver`, they are exactly `{has_membrane, has_iron_ingot, has_voice_coil, has_iron_magnet, has_the_recipe}`.
    - Each requirement is a single "or" group.
  - `extruderTakesMembraneAsInput`:
    - Place an extruder and set `MEMBRANE_MOLD` in slot `MOLD`.
    - `canPlaceItem(INPUT_0, membrane)` is true and `canPlaceItem(MOLD, membrane)` is false.
    - With 1 membrane and 1 bitumen in the inputs and redstone in `ENERGY`, run `h.runAfterDelay(110, ...)`. The output is 1 `reinforced_membrane`, and both inputs are empty.
  - In `MachineRecipesGameTest.machineRecipesExist`, change the reinforced membrane check to `ModItems.MEMBRANE, 1, ModItems.BITUMEN, 1`, in that order.

- [ ] **Step 2: Run** `./gradlew runGameTest`. Expected: it fails to compile because `MEMBRANE` and the others are missing. That counts as RED.

- [ ] **Step 3: Implement.**
  - Register the four items with `register(name, Item::new)`, and update `components()`.
  - Add the lang entries (Global Constraints names).
  - Add the four crafts and replace `extrusion(REINFORCED_MEMBRANE, 1, 2, MEMBRANE_MOLD)` with `MachineRecipeBuilder.machine(EXTRUDING, REINFORCED_MEMBRANE, 1, this::has).input(MEMBRANE, 1).input(BITUMEN, 1).mold(MEMBRANE_MOLD)`.
  - The flat item models come from the existing `components()` loop in `RaveCraftModelProvider`. Until Task 2, the textures are missing and show the missing-texture checkerboard, which is accepted between the two commits.

- [ ] **Step 4: Run** `./gradlew runDatagen && ./gradlew runGameTest`. Expected: all required tests pass, and `src/main/generated` contains the 4 recipe JSONs and the reinforced membrane recipe with `ravecraft:membrane`.

- [ ] **Step 5: Commit** `Add the membrane, speaker driver, amplifier board and woofer` (src, including `src/main/generated`).

### Task 2: Textures of the four components

**Files:**
- Modify: `tools/textures/machine_parts.py`, `tools/textures/machines.py` (docstring only)
- Generated: `src/main/resources/assets/ravecraft/textures/item/{membrane,speaker_driver,amplifier_board,woofer}.png`, `art/textures/preview.png`

**Interfaces:**
- Consumes: the four chosen PNGs in `art/proposals/items7d/` (Global Constraints).
- Produces: `machine_parts.MEMBRANE`, `SPEAKER_DRIVER`, `AMPLIFIER_BOARD`, `WOOFER` as `(palette, rows)` grids, added to `machine_parts.ITEMS`.

- [ ] **Step 1: Encode the four PNGs as grids.** Use the same encoding as the 7c items: palette sorted by luminance, `a` is the darkest, `.` is transparent, one 16-letter string per row. Add them to `ITEMS` under the lowercase ids.
- [ ] **Step 2: Run** `python tools/textures/gen_textures.py`, then compare each generated PNG with its proposal pixel by pixel. Expected: 0 differing pixels for all four.
- [ ] **Step 3: Check visually.** Open `art/textures/preview.png`, and render the four items next to `reinforced_membrane`, `simple_circuit` and `iron_magnet` at x8.
- [ ] **Step 4: Run** `./gradlew build`. Expected: green.
- [ ] **Step 5: Commit** `Add the component textures` (tools, `src/main/resources`, `art/textures/preview.png`; never `art/proposals`).

### Task 3: Final speaker recipes

**Files:**
- Modify: `src/client/java/com/ravecraft/mod/datagen/RaveCraftRecipeProvider.java`
- Modify: `src/gametest/java/com/ravecraft/mod/gametest/PartySpeakerGameTest.java`, `ComponentsGameTest.java`

**Interfaces:**
- Consumes: `shapedCraft` (Task 1) and the four items.
- Produces: recipe ids `ravecraft:portable_speaker` and `ravecraft:party_speaker`, both kept (category REDSTONE). The dye recipes are unchanged.

- [ ] **Step 1: Write the failing tests** in `ComponentsGameTest`, plus the one change in `PartySpeakerGameTest`.
  - `portableSpeakerCraft`: shaped 3×3. Index 1 is `speaker_driver`, 4 is `plastic_casing`, 7 is `simple_circuit`, and every other index is empty.
  - `partySpeakerCraft`:
    - Index 0, 2, 6 and 8 are iron, 1 is `woofer`, 3 and 5 are copper, 4 is the portable speaker, and 7 is `amplifier_board`.
    - The advancement criteria are exactly `{has_iron_ingot, has_woofer, has_copper_ingot, has_portable_speaker, has_amplifier_board, has_the_recipe}`.
  - `partyAcceptsDyedPortable`: a portable speaker stack carrying `DataComponents.DYED_COLOR` and `CUSTOM_NAME` still passes index 4's ingredient test.
  - `oldSpeakerCraftsGone`:
    - Build a `CraftingInput` 3×3 with `FFF / NRN / FFF` (F iron, N note block, R redstone). `recipeAccess().getRecipeFor(CRAFTING, ...)` is empty.
    - Do the same with `INI / CPC / IRI` (redstone block in R, a portable speaker in P). It is empty too.
  - `PartySpeakerGameTest.recipeConsumesPortable` stays as it is: it must still pass.
- [ ] **Step 2: Run** `./gradlew runDatagen && ./gradlew runGameTest`. Expected: the four new tests fail, because the old recipes still exist.
- [ ] **Step 3: Implement.** Replace the two provisional `shaped(...)` blocks and their `PROVISIONAL` comments with `shapedCraft(...)` calls. The speakers keep `RecipeCategory.REDSTONE`, so give `shapedCraft` a `RecipeCategory` parameter if Task 1 did not; that keeps it at 6 parameters at most.
- [ ] **Step 4: Run** `./gradlew runDatagen && ./gradlew runGameTest`. Expected: all pass. The generated `portable_speaker.json` and `party_speaker.json` contain no `note_block`.
- [ ] **Step 5: Commit** `Craft the speakers from components`.

### Task 4: "Made in" and mold tooltips

**Files:**
- Create: `src/main/java/com/ravecraft/mod/item/ItemOrigins.java`
- Create: `src/client/java/com/ravecraft/mod/client/ComponentTooltip.java`, registered in `RaveCraftClient` next to `SpeakerTooltip.register()`
- Modify: the lang files (two keys from Global Constraints), `ComponentsGameTest.java`

**Interfaces:**
- Produces:
  - `ItemOrigins.madeIn(Item item)`: returns `Optional<Block>`.
  - `ItemOrigins.moldFor(Item mold)`: returns `Optional<Item>`.
  - Both are backed by immutable maps, built lazily on first use, from `ModItems` and `ModBlocks` constants. There is no recipe access, so they work on the client.
  - Table contents:
    - Coil winder: `copper_wire`, `voice_coil`.
    - Magnetizer: `iron_magnet`, `neodymium_magnet`.
    - Etcher: `simple_circuit`, `chip`.
    - Extruder: `plastic_sheet`, `plastic_casing`, `reinforced_membrane`, `circuit_board`.
    - Molds: `sheet_mold` → `plastic_sheet`, `casing_mold` → `plastic_casing`, `membrane_mold` → `reinforced_membrane`, `board_mold` → `circuit_board`.

- [ ] **Step 1: Write the failing tests** in `ComponentsGameTest`.
  - `madeInMatchesRecipes`:
    - For each kind in `{WINDING, MAGNETIZING, ETCHING, EXTRUDING}` and each loaded recipe of `kind.type()`, `madeIn(result item)` is the block whose id path is `kind.machine()`.
    - The set of items with a `madeIn` value equals the set of all those results.
  - `moldForMatchesRecipes`: for every `EXTRUDING` recipe, `moldFor(the mold item)` is the result item. This also covers every mold in `ModItems.molds()`.
  - `noOriginWithoutMachine`: `madeIn` is empty for `BITUMEN`, `NEODYMIUM_INGOT`, `MEMBRANE`, `SPEAKER_DRIVER`, `Items.IRON_INGOT` and the portable speaker item. `moldFor(BITUMEN)` is empty.
- [ ] **Step 2: Run** `./gradlew runGameTest`. Expected: it fails to compile because `ItemOrigins` is missing.
- [ ] **Step 3: Implement.**
  - Write `ItemOrigins`.
  - `ComponentTooltip.register()` uses `ItemTooltipCallback` to append:
    - `Component.translatable("tooltip.ravecraft.made_in", block.getName())` in `ChatFormatting.GRAY` when `madeIn` is present;
    - `Component.translatable("tooltip.ravecraft.mold_for", item.getName(...))` in gray for molds. Use the item's default name component (`new ItemStack(item).getHoverName()`).
  - Add the lang keys.
- [ ] **Step 4: Run** `./gradlew runGameTest && ./gradlew build && ./gradlew codeQuality`. Expected: all green.
- [ ] **Step 5: Commit** `Show where machine parts are made and what each mold makes`.

### Task 5: In-game check and SPEC

- [ ] **Step 1:** Give the user this checklist:
  - In a new survival world, follow the book from the plastic sheet to the party speaker. The membrane, speaker driver, woofer, amplifier board and both speakers unlock from any one of their ingredients.
  - The reinforced membrane extrudes from a membrane and a bitumen.
  - A dyed portable speaker crafts into a new party speaker.
  - The "Made in" line shows on the 10 machine products, and "For the extruder" on the 4 molds, in both languages, with no line on raw materials.
  - The four new textures look right in the hand and in the inventory.
  - The tab order is as listed.
- [ ] **Step 2:** Update `docs/SPEC.md` as the spec's section 10 asks:
  - section 3.7: delivered content;
  - section 9: "Obtention des matériaux" settled (both), "Énergie" still open, since redstone is only a machine fuel;
  - section 11: tick 7d with links to this plan, and tick "Matériaux et composants" with the spec and the four plans. Add "(vérifié en jeu)" only if the user confirms.
  - Remove "Recette définitive de l'enceinte portable" from "Prévu plus tard".
- [ ] **Step 3:** In the materials spec section 9, add "Choix de 7d": membrane A, speaker drivers E, amplifier board B, and the reinforced membrane now made from the membrane.
- [ ] **Step 4:** Commit `Track materials 7d in the spec`.
