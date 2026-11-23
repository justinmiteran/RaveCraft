package com.ravecraft.mod.blockentity;

import com.ravecraft.mod.block.MachineBlock;
import com.ravecraft.mod.core.machine.MachineConstants;
import com.ravecraft.mod.core.machine.MachineProcess;
import com.ravecraft.mod.core.machine.MachineProcess.TickResult;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.recipe.MachineRecipeInput;
import com.ravecraft.mod.registry.ModBlockEntities;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The contents and the work of any machine: three input slots, a redstone energy slot and an output slot. The recipe
 * is looked up again only after a change of contents ({@link #setChanged()}), never every tick; {@link MachineProcess}
 * does the timing and the energy. Hoppers fill the inputs from the top and the energy from the sides, and empty the
 * output from below. It opens a {@link MachineMenu}, whose client side finds the machine from its position.
 */
public class MachineBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos> {
	public static final int INPUT_0 = 0;
	public static final int INPUT_1 = 1;
	public static final int INPUT_2 = 2;
	public static final int ENERGY = 3;
	public static final int OUTPUT = 4;
	public static final int SIZE = 5;
	private static final int[] SLOTS_UP = {INPUT_0, INPUT_1, INPUT_2};
	private static final int[] SLOTS_SIDE = {ENERGY};
	private static final int[] SLOTS_DOWN = {OUTPUT};
	private static final String ENERGY_KEY = "energy";
	private static final String PROGRESS_KEY = "progress";
	private static final String RECIPE_KEY = "recipe";

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private final MachineProcess process = new MachineProcess();
	private @Nullable RecipeHolder<MachineRecipe> recipe;
	private boolean dirty = true;
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case MachineMenu.DATA_PROGRESS -> process.progress();
				case MachineMenu.DATA_TOTAL -> process.total();
				default -> process.energy();
			};
		}

		@Override
		public void set(int index, int value) {
			// Read-only: the server is the source of truth.
		}

		@Override
		public int getCount() {
			return MachineMenu.DATA_COUNT;
		}
	};

	/**
	 * An empty machine.
	 *
	 * @param pos the block position
	 * @param state the machine block state
	 */
	public MachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MACHINE, pos, state);
	}

	/**
	 * Advances the machine by one tick: finds the recipe if the contents changed, runs the process, produces the
	 * result and lights the block while it works.
	 *
	 * @param level the level
	 * @param pos the block position
	 * @param state the block state
	 * @param machine the block entity
	 */
	public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
		if (machine.dirty && level instanceof ServerLevel serverLevel) {
			machine.findRecipe(serverLevel);
		}
		TickResult result = machine.process.tick(machine.outputFits(), machine::refuel);
		if (result == TickResult.DONE) {
			machine.craft();
		} else if (result == TickResult.WORKING) {
			setChanged(level, pos, state);
		}
		boolean lit = result == TickResult.WORKING || result == TickResult.DONE;
		if (state.getValue(MachineBlock.LIT) != lit) {
			level.setBlock(pos, state.setValue(MachineBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}

	/**
	 * Returns the machine's kind.
	 *
	 * @return the kind of the block this entity belongs to
	 */
	public MachineKind kind() {
		return ((MachineBlock) getBlockState().getBlock()).kind();
	}

	/**
	 * Returns the progress of the current operation.
	 *
	 * @return the elapsed ticks, 0 when idle
	 */
	public int progress() {
		return process.progress();
	}

	/**
	 * Returns the duration of the current operation.
	 *
	 * @return the ticks one operation takes, 0 without a recipe
	 */
	public int total() {
		return process.total();
	}

	/**
	 * Returns the energy reserve.
	 *
	 * @return the operations left before the next redstone is used
	 */
	public int energy() {
		return process.energy();
	}

	/**
	 * Returns the values the menu syncs: progress, total and energy.
	 *
	 * @return the read-only menu data
	 */
	public ContainerData data() {
		return data;
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new MachineMenu(id, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	private MachineRecipeInput input() {
		return new MachineRecipeInput(List.of(items.get(INPUT_0), items.get(INPUT_1), items.get(INPUT_2)),
				ItemStack.EMPTY);
	}

	private void findRecipe(ServerLevel level) {
		dirty = false;
		recipe = level.recipeAccess().getRecipeFor(kind().type(), input(), level).orElse(null);
		if (recipe == null) {
			process.setRecipe(null, 0);
		} else {
			process.setRecipe(recipe.id(), recipe.value().processingTime());
		}
	}

	private boolean outputFits() {
		if (recipe == null) {
			return false;
		}
		ItemStack result = recipe.value().assemble(input());
		ItemStack output = items.get(OUTPUT);
		return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
				&& output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

	/** Uses one redstone from the energy slot, if there is one. */
	private int refuel() {
		ItemStack fuel = items.get(ENERGY);
		int units = energyUnits(fuel);
		if (units > 0) {
			fuel.shrink(1);
		}
		return units;
	}

	/**
	 * Whether a stack can go in the energy slot.
	 *
	 * @param stack the stack
	 * @return true for redstone dust and redstone blocks
	 */
	public static boolean isEnergy(ItemStack stack) {
		return energyUnits(stack) > 0;
	}

	private static int energyUnits(ItemStack stack) {
		if (stack.is(Items.REDSTONE)) {
			return MachineConstants.OPERATIONS_PER_DUST;
		}
		return stack.is(Items.REDSTONE_BLOCK) ? MachineConstants.OPERATIONS_PER_BLOCK : 0;
	}

	/** Consumes each ingredient's count from its slot and adds the result to the output. */
	private void craft() {
		if (recipe == null) {
			return;
		}
		MachineRecipe current = recipe.value();
		MachineRecipeInput input = input();
		int[] slots = current.assignment(input).orElse(null);
		if (slots == null) {
			return;
		}
		ItemStack result = current.assemble(input);
		for (int i = 0; i < slots.length; i++) {
			items.get(slots[i]).shrink(current.ingredients().get(i).count());
		}
		ItemStack output = items.get(OUTPUT);
		if (output.isEmpty()) {
			items.set(OUTPUT, result);
		} else {
			output.grow(result.getCount());
		}
		setChanged();
	}

	/** Marks the contents changed: the recipe is looked up again on the next tick. */
	@Override
	public void setChanged() {
		dirty = true;
		super.setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putInt(ENERGY_KEY, process.energy());
		output.putInt(PROGRESS_KEY, process.progress());
		output.storeNullable(RECIPE_KEY, Recipe.KEY_CODEC, recipe == null ? null : recipe.id());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ContainerHelper.loadAllItems(input, items);
		ResourceKey<Recipe<?>> saved = input.read(RECIPE_KEY, Recipe.KEY_CODEC).orElse(null);
		process.load(input.getIntOr(ENERGY_KEY, 0), input.getIntOr(PROGRESS_KEY, 0), saved);
		dirty = true;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, count);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
	}

	/**
	 * Energy slot: redstone dust and blocks only. Output: nothing. Inputs: a stack of the same item, or an empty slot
	 * when no other input holds that item (so one ingredient does not spread over the three slots); the
	 * inputs the machine does not use take nothing.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == ENERGY) {
			return isEnergy(stack);
		}
		return slot != OUTPUT && fitsInput(slot, stack);
	}

	private boolean fitsInput(int slot, ItemStack stack) {
		if (slot >= kind().inputs()) {
			return false;
		}
		ItemStack here = items.get(slot);
		if (!here.isEmpty()) {
			return ItemStack.isSameItemSameComponents(here, stack);
		}
		return Arrays.stream(SLOTS_UP)
				.noneMatch(other -> other != slot && ItemStack.isSameItemSameComponents(items.get(other), stack));
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.UP) {
			return Arrays.copyOf(SLOTS_UP, kind().inputs());
		}
		return side == Direction.DOWN ? SLOTS_DOWN.clone() : SLOTS_SIDE.clone();
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && slot == OUTPUT;
	}
}
