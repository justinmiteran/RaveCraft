package com.ravecraft.mod.menu;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.MachineBlock;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.core.machine.MachineLayout;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModMenus;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModTags;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The menu of any machine: three inputs, the redstone energy slot, the output and the mold slot, above the player's
 * inventory. It
 * syncs the progress, the energy and the item being made through a {@link ContainerData} and uses the furnace recipe book, whose
 * placement is done by {@link MachineRecipePlacer}. The slot positions are those of
 * {@link MachineLayout}.
 */
public class MachineMenu extends RecipeBookMenu {
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_TOTAL = 1;
	public static final int DATA_ENERGY = 2;
	/** The units of the last energy item, which a full bolt stands for. */
	public static final int DATA_CAPACITY = 3;
	/** The registry id of the item being made, or {@link #NO_RESULT}; it gives the arrow its colour. */
	public static final int DATA_RESULT = 4;
	public static final int DATA_COUNT = 5;
	/** {@link #DATA_RESULT} while the machine has no recipe. */
	public static final int NO_RESULT = -1;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 84;
	private static final int INVENTORY_START = MachineBlockEntity.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int HOTBAR_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;
	private final MachineKind kind;
	private final Level level;

	/**
	 * The client-side menu, opened from the position the server sends.
	 *
	 * @param id the container id
	 * @param inventory the player's inventory
	 * @param pos the machine's position, which gives its kind
	 */
	public MachineMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(MachineBlockEntity.SIZE), new SimpleContainerData(DATA_COUNT),
				kindAt(inventory, pos));
	}

	/**
	 * The server-side menu of a machine.
	 *
	 * @param id the container id
	 * @param inventory the player's inventory
	 * @param machine the machine
	 */
	public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) {
		this(id, inventory, machine, machine.data(), machine.kind());
	}

	/** Adds the machine slots, the player's inventory and the synced data. */
	private MachineMenu(int id, Inventory inventory, Container container, ContainerData data, MachineKind kind) {
		super(ModMenus.MACHINE, id);
		checkContainerSize(container, MachineBlockEntity.SIZE);
		checkContainerDataCount(data, DATA_COUNT);
		this.container = container;
		this.data = data;
		this.kind = kind;
		this.level = inventory.player.level();
		for (int i = MachineBlockEntity.INPUT_0; i <= MachineBlockEntity.INPUT_2; i++) {
			addSlot(new MachineSlot(container, level, i, MachineLayout.inputX(i, kind.inputs()),
					MachineLayout.INPUT_Y, i < kind.inputs()));
		}
		addSlot(new MachineSlot(container, level, MachineBlockEntity.ENERGY, MachineLayout.ENERGY_X, MachineLayout.ENERGY_Y,
				true));
		addSlot(new MachineSlot(container, level, MachineBlockEntity.OUTPUT, MachineLayout.OUTPUT_X, MachineLayout.OUTPUT_Y,
				true));
		addSlot(new MachineSlot(container, level, MachineBlockEntity.MOLD, MachineLayout.MOLD_X, MachineLayout.MOLD_Y,
				kind.needsMold()));
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
		addDataSlots(data);
	}

	private static MachineKind kindAt(Inventory inventory, BlockPos pos) {
		return inventory.player.level().getBlockState(pos).getBlock() instanceof MachineBlock machine
				? machine.kind()
				: ModRecipes.WINDING;
	}

	/**
	 * Returns the machine's kind.
	 *
	 * @return the kind, which gives the recipe book tab
	 */
	public MachineKind kind() {
		return kind;
	}

	/**
	 * Returns the progress of the current operation.
	 *
	 * @return a fraction between 0 and 1
	 */
	public float progress() {
		int total = data.get(DATA_TOTAL);
		return total <= 0 ? 0 : Math.clamp((float) data.get(DATA_PROGRESS) / total, 0, 1);
	}

	/**
	 * Returns the energy reserve.
	 *
	 * @return a fraction of the largest reserve, between 0 and 1
	 */
	public float energy() {
		int capacity = data.get(DATA_CAPACITY);
		return capacity <= 0 ? 0 : Math.clamp((float) data.get(DATA_ENERGY) / capacity, 0, 1);
	}

	/**
	 * Returns the item the machine is making.
	 *
	 * @return the result of the current recipe, empty without one
	 */
	public Optional<Item> result() {
		int id = data.get(DATA_RESULT);
		return id == NO_RESULT ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.byId(id));
	}

	/**
	 * Returns the input slots the machine uses, in order.
	 *
	 * @return the used input slots
	 */
	public List<Slot> inputSlots() {
		return slots.subList(MachineBlockEntity.INPUT_0, MachineBlockEntity.INPUT_0 + kind.inputs());
	}

	/**
	 * Returns the mold slot, shown and used only when the machine needs a mold.
	 *
	 * @return the mold slot
	 */
	public Slot moldSlot() {
		return slots.get(MachineBlockEntity.MOLD);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	/**
	 * Shift-click: the output and the machine slots go to the inventory; from the inventory, a mold goes to the mold
	 * slot, redstone to the energy slot (or onto redstone already in an input), anything else to the inputs, and what
	 * does not fit moves between inventory and hotbar.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack clicked = stack.copy();
		if (!moveFrom(index, stack)) {
			return ItemStack.EMPTY;
		}
		if (index == MachineBlockEntity.OUTPUT) {
			slot.onQuickCraft(stack, clicked);
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == clicked.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return clicked;
	}

	/** Moves a shift-clicked stack; returns whether anything moved. */
	private boolean moveFrom(int index, ItemStack stack) {
		if (index < INVENTORY_START) {
			return moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, index == MachineBlockEntity.OUTPUT);
		}
		if (moveIntoMachine(stack)) {
			return true;
		}
		return index < HOTBAR_START
				? moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)
				: moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false);
	}

	/**
	 * From the inventory: a mold goes to the mold slot only, on a machine that uses one; redstone to the energy
	 * slot (or onto redstone in an input); anything else to the inputs.
	 */
	private boolean moveIntoMachine(ItemStack stack) {
		if (kind.needsMold() && stack.is(ModTags.Items.MOLDS)) {
			return moveItemStackTo(stack, MachineBlockEntity.MOLD, MachineBlockEntity.MOLD + 1, false);
		}
		if (MachineBlockEntity.isEnergy(level, stack)) {
			return moveItemStackTo(stack, MachineBlockEntity.ENERGY, MachineBlockEntity.ENERGY + 1, false)
					|| mergeIntoInputs(stack);
		}
		return moveItemStackTo(stack, MachineBlockEntity.INPUT_0, MachineBlockEntity.INPUT_2 + 1, false);
	}

	/**
	 * Redstone goes to an input only to join redstone already there (a recipe ingredient), never to an empty input:
	 * a full energy slot must not spill fuel into the inputs and jam the recipe.
	 */
	private boolean mergeIntoInputs(ItemStack stack) {
		boolean moved = false;
		for (Slot input : inputSlots()) {
			if (ItemStack.isSameItemSameComponents(input.getItem(), stack)) {
				moved |= moveItemStackTo(stack, input.index, input.index + 1, false);
			}
		}
		return moved;
	}

	@Override
	public PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear,
			RecipeHolder<?> recipe, ServerLevel level, Inventory inventory) {
		if (recipe.value() instanceof MachineRecipe machineRecipe && machineRecipe.kind() == kind) {
			return MachineRecipePlacer.place(this, machineRecipe, useMaxItems, inventory);
		}
		return PostPlaceAction.NOTHING;
	}

	@Override
	public void fillCraftSlotsStackedContents(StackedItemContents contents) {
		for (Slot slot : inputSlots()) {
			contents.accountStack(slot.getItem());
		}
		if (kind.needsMold()) {
			contents.accountStack(moldSlot().getItem());
		}
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return MachineBookType.MACHINE;
	}

	/**
	 * A machine slot: the energy slot takes the machine energy items only, the mold slot molds only, the output nothing, the inputs
	 * what the machine takes; the inputs (and the mold slot) the machine does not use are hidden and take nothing.
	 */
	private static final class MachineSlot extends Slot {
		/** The vanilla redstone silhouette shown in the empty energy slot. */
		private static final Identifier EMPTY_ENERGY_ICON = Identifier.withDefaultNamespace("container/slot/redstone_dust");
		/** The mold silhouette shown in the empty mold slot. */
		private static final Identifier EMPTY_MOLD_ICON = RaveCraft.id("container/slot/mold");

		private final Level level;
		private final boolean active;

		MachineSlot(Container container, Level level, int slot, int x, int y, boolean active) {
			super(container, slot, x, y);
			this.level = level;
			this.active = active;
		}

		@Override
		public @Nullable Identifier getNoItemIcon() {
			return switch (getContainerSlot()) {
				case MachineBlockEntity.ENERGY -> EMPTY_ENERGY_ICON;
				case MachineBlockEntity.MOLD -> EMPTY_MOLD_ICON;
				default -> null;
			};
		}

		@Override
		public boolean isActive() {
			return active;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!active) {
				return false;
			}
			return switch (getContainerSlot()) {
				case MachineBlockEntity.ENERGY -> MachineBlockEntity.isEnergy(level, stack);
				case MachineBlockEntity.MOLD -> stack.is(ModTags.Items.MOLDS);
				case MachineBlockEntity.OUTPUT -> false;
				default -> container.canPlaceItem(getContainerSlot(), stack);
			};
		}

		/** An empty slot can be modified too: vanilla asks whether nothing may be placed, which the rules above refuse. */
		@Override
		public boolean allowModification(Player player) {
			return active && !hasItem() ? mayPickup(player) : super.allowModification(player);
		}
	}
}
