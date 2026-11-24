# REI machine recipe pages — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the coil winder, magnetizer, etcher and extruder recipes in Roughly Enough Items, laid out like the JEI pages, without duplicating the layout.

**Architecture:**
- The screen positions and the page layout become pure integer math in `core`, used by the menu, the screen, JEI and REI.
- On the server, a common plugin turns each loaded `MachineRecipe` into a `MachineDisplay`, which REI syncs. Its payload is `MachineDisplayData`, a class with no REI type, so GameTests run it without REI.
- On the client, a plugin adds the categories, the workstations, the clickable arrow and the `+` button.

**Tech Stack:** Fabric 26.3 (Mojang names), REI 26.3.824 (compile only), JUnit 5, GameTests.

**Spec:** `docs/superpowers/specs/2026-11-24-rei-compat-design.md`. Branch `feat/rei-compat`, stacked on `feat/jei-compat`.

## Global Constraints

- `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot"` before Gradle.
- `./gradlew build` and `./gradlew codeQuality` are green at the end of every task.
- No `net.minecraft.client` import and no REI *client* API import in `src/main`. Nothing outside `compat/rei` and `client/compat/rei` imports `me.shedaniel`.
- Nothing from REI, Architectury or basic-math is in the jar or in `depends`.
- Category ids: `ravecraft:winding`, `ravecraft:magnetizing`, `ravecraft:etching`, `ravecraft:extruding`, i.e. `RaveCraft.id(kind.process())`.
- Translation keys are reused as they are: `gui.ravecraft.jei.energy`, `gui.ravecraft.jei.time`. No new key.
- The JEI pages do not change.
- Commits in English, imperative, no Claude attribution. Never `git add art/proposals/` or `.superpowers/`. No push, no PR.

## Facts checked in `RoughlyEnoughItems-26.3.824.jar`

Read from the bytecode, not yet run. They settle the open points of the spec (§5):

- **`Rectangle` and `Point`** (`me.shedaniel.math`) are not in the REI jar. They come from `me.shedaniel.cloth:basic-math:0.6.1` on `https://maven.shedaniel.me`, a second compile-only artifact that the spec does not list.
- **Architectury**: `EntryStacks.of` and `EntryIngredients.of` have overloads taking its `FluidStack`. Add it only if javac asks. `EntryIngredients.ofItemStacks` has no such overload.
- **Transfer, server side** (`InputSlotCrafter`): ingredient *i* goes to slot *i* of the list the client sends, **one item each**. `InputIngredient.getIndex()` and stack counts are not used. The slots can be any list, so the mold is not a problem. A count of *n* is sent as *n* one-item ingredients, with the same slot repeated *n* times.
- **Transfer, shift-click** multiplies every ingredient up to the item's stack size without checking the slot, so a repeated slot could exceed it. The handler always asks for one operation.
- **Transfer, refused item**: an item already taken from the inventory is lost when `Slot.mayPlace` refuses it. Task 3 tests that the menu never refuses.
- **Transfer, without REI on the server**: REI itself disables the button (`error.rei.not.on.server`).
- **Per-stack tooltip**: `EntryStack.tooltip(Component...)` exists, so the energy fallback of the spec is not needed.
- `Display.getDisplayLocation()` is `Optional<Identifier>`. `DisplaySerializer.of(MapCodec, StreamCodec)` and `DisplaySerializerRegistry.register(Identifier, serializer)` exist.

## Review Focus

1. **A recipe that takes several of an item** (voice coil 4, plastic casing 3, reinforced membrane 2, chip 1-2-1): `+` moves exactly that many, all in one slot. Test: `unitSlotsRepeatEachCount` (Task 3).
2. **A recipe item the machine slot refuses**: REI would destroy it. Test: `machineSlotsAcceptEveryRecipeUnit` (Task 3), which fills the slots in REI's order.
3. **Shift-click on `+`**: the machine receives one operation, never more than a stack in a slot. Task 5 step 2, checked by hand in Task 6.
4. **REI missing on the server or on the client**: no error, no REI class loaded. The GameTest server runs without REI; the rest is checked by hand in Task 6.
5. **A machine screen with the recipe book open**: the screen moves and the clickable arrow follows. `MachineScreen.arrowArea()` uses `leftPos`; checked by hand in Task 6.

---

### Task 1: Shared layout, used by the menu, the screen and JEI

**Files:**
- Create: `src/main/java/com/ravecraft/mod/core/machine/MachineLayout.java`
- Create: `src/main/java/com/ravecraft/mod/core/machine/MachineRecipeLayout.java`
- Create: `src/client/java/com/ravecraft/mod/client/compat/MachineEnergyView.java`
- Modify: `src/main/java/com/ravecraft/mod/menu/MachineMenu.java`
- Modify: `src/client/java/com/ravecraft/mod/client/screen/machine/MachineScreen.java`
- Modify: `src/client/java/com/ravecraft/mod/client/compat/jei/MachineRecipeCategory.java`, `RaveCraftJeiPlugin.java` (same folder)
- Modify: `src/gametest/java/com/ravecraft/mod/gametest/MachineMenuGameTest.java:184`
- Test: `src/test/java/com/ravecraft/mod/core/machine/MachineRecipeLayoutTest.java`

**Interfaces (produces):**
- `final class MachineLayout`: `INPUT_CENTER_X=48`, `INPUT_Y=17`, `SLOT_SPACING=18`, `ENERGY_X=48`, `ENERGY_Y=53`, `OUTPUT_X=125`, `OUTPUT_Y=35`, `MOLD_X=26`, `MOLD_Y=17`, `ARROW_X=89`, `ARROW_Y=34`, `ARROW_WIDTH=24`, `ARROW_HEIGHT=16`, `BOLT_X=49`, `BOLT_Y=36`, `BOLT_SIZE=14`; `static int inputX(int index, int inputs)`, moved from `MachineMenu`.
- `record MachineRecipeLayout(int inputs, boolean mold)`: `HEIGHT=54`, `TIME_Y=45`, `TIME_HEIGHT=9`; nested `record Pos(int x, int y)`; `int width()`, `Pos input(int index)`, `Pos moldSlot()`, `Pos energy()`, `Pos output()`, `Pos bolt()`, `Pos arrow()`; `static String seconds(int ticks)`. Slot positions are those of the item (inside the slot border), as in the menu.
- `final class MachineEnergyView`: `static List<ItemStack> items()` and `static int operations(ItemStack)`, moved from `MachineRecipeCategory`; empty list and 0 without a client level.

- [ ] **Step 1: Write the failing test** `MachineRecipeLayoutTest`

```java
@Test
void oneInputMatchesTheCoilWinderPage() {
	MachineRecipeLayout page = new MachineRecipeLayout(1, false);
	assertEquals(99, page.width());
	assertEquals(new Pos(1, 1), page.input(0));
	assertEquals(new Pos(1, 37), page.energy());
	assertEquals(new Pos(78, 19), page.output());
	assertEquals(new Pos(2, 20), page.bolt());
	assertEquals(new Pos(42, 18), page.arrow());
}

@Test
void threeInputsMatchTheEtcherPage() {
	MachineRecipeLayout page = new MachineRecipeLayout(3, false);
	assertEquals(117, page.width());
	assertEquals(new Pos(1, 1), page.input(0));
	assertEquals(new Pos(19, 1), page.input(1));
	assertEquals(new Pos(37, 1), page.input(2));
	assertEquals(new Pos(19, 37), page.energy());
	assertEquals(new Pos(96, 19), page.output());
	assertEquals(new Pos(60, 18), page.arrow());
}

@Test
void moldComesFirstOnTheExtruderPage() {
	MachineRecipeLayout page = new MachineRecipeLayout(1, true);
	assertEquals(121, page.width());
	assertEquals(new Pos(1, 1), page.moldSlot());
	assertEquals(new Pos(23, 1), page.input(0));
	assertEquals(new Pos(100, 19), page.output());
	assertEquals(new Pos(64, 18), page.arrow());
}

@Test
void secondsHaveNoTrailingZero() {
	assertEquals("10", MachineRecipeLayout.seconds(200));
	assertEquals("1.5", MachineRecipeLayout.seconds(30));
	assertEquals("0.05", MachineRecipeLayout.seconds(1));
}
```

- [ ] **Step 2: Run it.** `./gradlew test --tests '*MachineRecipeLayoutTest'`. Expected: compilation fails, `MachineRecipeLayout` does not exist.
- [ ] **Step 3: Write `MachineLayout` and `MachineRecipeLayout`** (formulas: `shiftX` and `SHIFT_Y` of today's `MachineRecipeCategory`; width `OUTPUT_X - shiftX + 21`).
- [ ] **Step 4: Run it again.** Expected: 4 tests pass.
- [ ] **Step 5: Use them.** Remove the moved constants and `inputX` from `MachineMenu`. `MachineScreen` and both JEI classes drop their own arrow, bolt and time constants. `MachineRecipeCategory` keeps a `MachineRecipeLayout` field and calls `MachineEnergyView`; its `energyItems`, `operations`, `seconds` and `shiftX` go away. The GameTest reads `MachineLayout.MOLD_X`.
- [ ] **Step 6: Check.** `./gradlew build` then `./gradlew codeQuality`. Expected: both green, 217 GameTests.
- [ ] **Step 7: Commit** `Share the machine recipe layout between recipe viewers`.

---

### Task 2: REI on the compile classpath

**Files:** Modify `build.gradle`, `src/main/resources/fabric.mod.json`.

- [ ] **Step 1: Repository.** A second `exclusiveContent` block: name `Shedaniel`, url `https://maven.shedaniel.me`, `includeGroup "me.shedaniel.cloth"`.
- [ ] **Step 2: Dependencies**, under a comment in the style of the JEI one:

```groovy
compileOnly "maven.modrinth:nfn13YXA:rKsAE6bX"
clientCompileOnly "maven.modrinth:nfn13YXA:rKsAE6bX"
clientCompileOnly "me.shedaniel.cloth:basic-math:0.6.1"
```

Later tasks add Architectury 22.0.6 (`maven.modrinth:lhGA9TYQ:Njpa4lhR`, `compileOnly` and `clientCompileOnly`) only if javac reports a missing `dev.architectury` class.

- [ ] **Step 3: Manifest.** `"suggests"` gains `"roughlyenoughitems": "*"`. The entrypoints come with their classes (Tasks 4 and 5).
- [ ] **Step 4: Check.** `./gradlew build`, then `unzip -l build/libs/ravecraft-*.jar | grep -iE "shedaniel|architectury|basic-math"`. Expected: green, no line.
- [ ] **Step 5: Commit** `Add REI as a compile only dependency`.

---

### Task 3: Synced display data, without REI

**Files:**
- Create: `src/main/java/com/ravecraft/mod/compat/rei/MachineDisplayData.java`
- Modify: `src/gametest/java/com/ravecraft/mod/gametest/MachineMenuGameTest.java` (its `menu(h, player, block)` helper becomes package-private)
- Test: `src/gametest/java/com/ravecraft/mod/gametest/RecipeViewerGameTest.java`

**Interfaces (produces):** `record MachineDisplayData(Identifier id, MachineRecipe recipe)` with
- `static final MapCodec<MachineDisplayData> MAP_CODEC`: fields `id` (`Identifier.CODEC`) and `recipe` (`Recipe.DIRECT_CODEC`, an error when the recipe is not a `MachineRecipe`);
- `static final StreamCodec<RegistryFriendlyByteBuf, MachineDisplayData> STREAM_CODEC`: `Identifier.STREAM_CODEC` and `Recipe.STREAM_CODEC`, a `DecoderException` when the recipe is not a `MachineRecipe`;
- `static MachineDisplayData of(RecipeHolder<MachineRecipe> holder)`;
- `List<Integer> unitSlots()`: the machine menu slot of each item one operation takes, e.g. `[0, 0, 5]` for two of the input and a mold (`MachineBlockEntity.INPUT_0 + i`, `MachineBlockEntity.MOLD`);
- `List<Ingredient> units()`: the ingredient of each of those items, same order and size.

The formats are those of the recipes: the record carries the recipe itself (spec §3.2).

- [ ] **Step 1: Write the failing GameTests.** A helper `recipes(h)` lists every `RecipeHolder<MachineRecipe>` of `ModRecipes.kinds()`.
  - `displayDataSurvivesTheNetwork`: for each recipe, encode and decode with `STREAM_CODEC`, then `assertSame`. At least 10 recipes checked.
  - `displayDataSurvivesJson`: the same with `MAP_CODEC.codec()` through `registryAccess().createSerializationContext(JsonOps.INSTANCE)`. At least 10 recipes checked.
  - `assertSame(h, expected, actual)` helper: equal id, same `kind()` object, same number of ingredients, each `count()`, the items of each ingredient (`items().toList()`), the mold items, `ItemStack.matches` of `result().create()`, `processingTime()`.
  - `unitSlotsRepeatEachCount`: for each recipe, `unitSlots()` and `units()` have the size of the sum of the counts (plus 1 with a mold); slot `i` appears `ingredients().get(i).count()` times; the last one is `MachineBlockEntity.MOLD` exactly when there is a mold.
  - `machineSlotsAcceptEveryRecipeUnit`: for each recipe, open a fresh menu on the block of `kind.machineItem()`. For each unit in order, take one of the first item of its ingredient, assert `slot.mayPlace(one)`, and put it (`set` in an empty slot, `grow(1)` otherwise). At the end, every input slot holds its count. Set the block to air before each menu so the slots start empty.
- [ ] **Step 2: Run.** `./gradlew runGameTest`. Expected: compilation fails, `MachineDisplayData` does not exist.
- [ ] **Step 3: Write `MachineDisplayData`.**
- [ ] **Step 4: Run.** `./gradlew build` then `./gradlew codeQuality`. Expected: green, 221 GameTests, with no REI on the runtime classpath.
- [ ] **Step 5: Commit** `Add the machine recipe data synced to REI`.

---

### Task 4: Common plugin

**Files:**
- Create: `src/main/java/com/ravecraft/mod/compat/rei/MachineDisplay.java`, `RaveCraftReiCommonPlugin.java` (same folder)
- Modify: `src/main/resources/fabric.mod.json`

**Interfaces:**
- Consumes: `MachineDisplayData` (Task 3).
- Produces: `record MachineDisplay(MachineDisplayData data) implements Display`, with
  - `static final DisplaySerializer<MachineDisplay> SERIALIZER`: both codecs of the data, mapped;
  - `static CategoryIdentifier<MachineDisplay> category(MachineKind kind)`;
  - `getInputEntries()`: for each ingredient, every accepted item at the counted amount, then the mold; `getOutputEntries()`: the result; `getDisplayLocation()`: `Optional.of(data.id())`; `getCategoryIdentifier()`: the kind's category;
  - `getInputIngredients(menu, player)`: one per unit, `InputIngredient.of(unit, entry, stacks of 1)`, where `entry` is the index of the unit's entry in `getInputEntries()`, so REI marks the right slot red when it is missing.
- `RaveCraftReiCommonPlugin implements REICommonPlugin`:
  - `registerDisplaySerializer`: `RaveCraft.id("machine")` → `SERIALIZER`;
  - `registerDisplays`: `beginRecipeFiller(MachineRecipe.class).fill(holder -> new MachineDisplay(MachineDisplayData.of(holder)))`.

- [ ] **Step 1: Write both classes** and the entrypoint `"rei_common": ["com.ravecraft.mod.compat.rei.RaveCraftReiCommonPlugin"]`.
- [ ] **Step 2: Check.** `./gradlew build` and `./gradlew codeQuality`. Expected: green, 221 GameTests (the server starts without REI, so the entrypoint is not loaded).
- [ ] **Step 3: Commit** `Send the machine recipes to REI`.

These two classes have no automated test because they need REI at run time. Task 6 checks them in game.

---

### Task 5: Client plugin

**Files:**
- Create: `src/client/java/com/ravecraft/mod/client/compat/rei/MachineDisplayCategory.java`, `MachineTransferHandler.java`, `RaveCraftReiClientPlugin.java` (same folder)
- Modify: `src/client/java/com/ravecraft/mod/client/screen/machine/MachineScreen.java`, `src/main/resources/fabric.mod.json`

**Interfaces:**
- Consumes: `MachineRecipeLayout`, `MachineLayout`, `MachineEnergyView` (Task 1), `MachineDisplayData.unitSlots()` (Task 3), `MachineDisplay` (Task 4).
- Produces: `MachineScreen`: `public ScreenRectangle arrowArea()`, the progress arrow in screen coordinates.

- [ ] **Step 1: `MachineDisplayCategory implements DisplayCategory<MachineDisplay>`**, constructor `(MachineKind kind)`.
  - Title and icon: the machine item. `getDisplayWidth` = `layout.width() + 2 * PADDING`, `getDisplayHeight` = `HEIGHT + 2 * PADDING`, `PADDING = 5`. Every position is added to `bounds.x + PADDING, bounds.y + PADDING`.
  - `setupDisplay`, in this order:
    - the recipe base;
    - one slot `markInput()` per ingredient of the recipe, then the mold slot `markInput()` when there is a mold (the order of `getInputEntries()`);
    - the energy slot, unmarked, one `EntryStack` per `MachineEnergyView.items()` with the tooltip `gui.ravecraft.jei.energy`;
    - the result background, then the output slot `disableBackground().markOutput()`;
    - the bolt sprite `container/machine/energy` (`createDrawableWidget`, `blitSprite`);
    - the arrow, `animationDurationTicks(processingTime)`;
    - the label `gui.ravecraft.jei.time`, `rightAligned().noShadow().color(0xFF808080)`, at the right edge of the layout and `TIME_Y`.
- [ ] **Step 2: `MachineTransferHandler implements SimpleTransferHandler`**, constructor `(MachineKind kind)`.
  - `checkApplicable`: applicable when the menu is a `MachineMenu` of this kind and the display a `MachineDisplay` of this kind.
  - `getInputSlots`: `unitSlots()` mapped to `SlotAccessor.fromSlot(menu.getSlot(i))`. `getInventorySlots`: the 36 slots from `MachineBlockEntity.SIZE`.
  - `handle(context)`: the default one, called with `Context.create(context.isActuallyCrafting(), false, context.getContainerScreen(), context.getDisplay())`, so shift-click also fills one operation.
- [ ] **Step 3: `RaveCraftReiClientPlugin implements REIClientPlugin`.**
  - `registerCategories`: for each kind, the category and `addWorkstations` with the machine item.
  - `registerTransferHandlers`: one `MachineTransferHandler` per kind.
  - `registerScreens`: one `registerClickArea(MachineScreen.class, …)` that returns `ClickArea.Result.success().category(...)` for the kind of the menu when the mouse is in `arrowArea()`, else `fail()`.
- [ ] **Step 4: Entrypoint** `"rei_client": ["com.ravecraft.mod.client.compat.rei.RaveCraftReiClientPlugin"]`.
- [ ] **Step 5: Check.** `./gradlew build` and `./gradlew codeQuality`. Expected: green. Then `grep -rl me.shedaniel src` lists only files under `compat/rei` and `client/compat/rei`.
- [ ] **Step 6: Commit** `Show the machine recipes in REI`.

---

### Task 6: Documentation and in-game check

**Files:** Modify `docs/SPEC.md`: after the 7d line, a `[x]` line for REI with the spec and plan links and « (pas encore vérifié en jeu) »; EMI under « Prévu plus tard ».

- [ ] **Step 1: Edit the SPEC.**
- [ ] **Step 2: Full check.** `./gradlew build` then `./gradlew codeQuality`. Expected: green, 221 GameTests.
- [ ] **Step 3: Commit** `Record the REI integration in the SPEC`.
- [ ] **Step 4: In-game check by the user**, with REI, Architectury and Cloth Config (26.3) in `run/mods`. Ask before downloading the jars.
  - **REI only:** copper wire opens the coil winder page (any copper ingot, energy scrolling with its operations, result ×4, time in seconds); a casing page shows its mold on the left; the machine item is listed as the workstation; the arrow of each machine screen opens the right page, with the recipe book closed and open.
  - **`+` button:** fills the machine for voice coil (4 of an item), chip (1-2-1) and plastic casing (3 and a mold); shift-click fills one operation only; a missing ingredient is shown in red and nothing moves.
  - **JEI only:** the pages look as before.
  - **Neither:** the game starts, no error in the log.
  - **REI on the client only** (dedicated server without REI): joining works, vanilla recipes are shown, no error in the log, `+` reports that REI is not on the server.

If `+` moves wrong items in game, remove `MachineTransferHandler` and its registration, and list it in the SPEC as a follow-up (spec §3.3).
