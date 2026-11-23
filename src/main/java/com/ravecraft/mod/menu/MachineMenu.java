package com.ravecraft.mod.menu;

import com.ravecraft.mod.block.MachineBlock;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.core.machine.MachineConstants;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModMenus;
import com.ravecraft.mod.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The menu of any machine: three inputs, the redstone energy slot and the output, above the player's inventory. It
 * syncs the progress and the energy through a {@link ContainerData} and uses the furnace recipe book, whose
 * placement is done by {@link MachineRecipePlacer}. The slot positions follow the screen layout.
 */
public class MachineMenu extends RecipeBookMenu {
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_TOTAL = 1;
	public static final int DATA_ENERGY = 2;
	public static final int DATA_COUNT = 3;
	public static final int INPUT_X = 30;
	public static final int INPUT_Y = 17;
	public static final int SLOT_SPACING = 18;
	public static final int ENERGY_X = 48;
	public static final int ENERGY_Y = 53;
	public static final int OUTPUT_X = 125;
	public static final int OUTPUT_Y = 35;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 84;
	private static final int INVENTORY_START = MachineBlockEntity.SIZE;
	private static final int HOTBAR_START = INVENTORY_START + 27;
	private static final int HOTBAR_END = HOTBAR_START + 9;

	private final Container container;
	private final ContainerData data;
	private final MachineKind kind;

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
		for (int i = MachineBlockEntity.INPUT_0; i <= MachineBlockEntity.INPUT_2; i++) {
			addSlot(new MachineSlot(container, i, INPUT_X + i * SLOT_SPACING, INPUT_Y));
		}
		addSlot(new MachineSlot(container, MachineBlockEntity.ENERGY, ENERGY_X, ENERGY_Y));
		addSlot(new MachineSlot(container, MachineBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y));
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
		return Math.clamp((float) data.get(DATA_ENERGY) / MachineConstants.MAX_ENERGY, 0, 1);
	}

	/**
	 * Returns the three input slots, in order.
	 *
	 * @return the input slots
	 */
	public List<Slot> inputSlots() {
		return slots.subList(MachineBlockEntity.INPUT_0, MachineBlockEntity.INPUT_2 + 1);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	/**
	 * Shift-click: the output and the machine slots go to the inventory; from the inventory, redstone goes to the
	 * energy slot first, anything else to the inputs, and what does not fit moves between inventory and hotbar.
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
		boolean energy = MachineBlockEntity.isEnergy(stack)
				&& moveItemStackTo(stack, MachineBlockEntity.ENERGY, MachineBlockEntity.ENERGY + 1, false);
		if (energy || moveItemStackTo(stack, MachineBlockEntity.INPUT_0, MachineBlockEntity.INPUT_2 + 1, false)) {
			return true;
		}
		return index < HOTBAR_START
				? moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)
				: moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false);
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
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.FURNACE;
	}

	/** A machine slot: the energy slot takes redstone only, the output nothing, the inputs what the machine takes. */
	private static final class MachineSlot extends Slot {
		MachineSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			int slot = getContainerSlot();
			if (slot == MachineBlockEntity.ENERGY) {
				return MachineBlockEntity.isEnergy(stack);
			}
			return slot != MachineBlockEntity.OUTPUT && container.canPlaceItem(slot, stack);
		}
	}
}
