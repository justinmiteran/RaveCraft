package com.ravecraft.mod.client.compat.rei;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.compat.rei.MachineDisplay;
import com.ravecraft.mod.compat.rei.MachineFill;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.MachineKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.common.entry.InputIngredient;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fills a machine of one kind from the player inventory, with the items and slots chosen by {@link MachineFill}.
 */
final class MachineTransferHandler implements SimpleTransferHandler {
	private final MachineKind kind;

	/**
	 * Creates the handler of a machine.
	 *
	 * @param kind the machine kind
	 */
	MachineTransferHandler(MachineKind kind) {
		this.kind = kind;
	}

	@Override
	public ApplicabilityResult checkApplicable(Context context) {
		return context.getMenu() instanceof MachineMenu menu && menu.kind() == kind
				&& context.getDisplay() instanceof MachineDisplay display && display.data().recipe().kind() == kind
				? ApplicabilityResult.createApplicable()
				: ApplicabilityResult.createNotApplicable();
	}

	@Override
	public List<InputIngredient<ItemStack>> getInputsIndexed(Context context) {
		List<MachineFill.Unit> units = fill(context).map(MachineFill::units).orElse(List.of());
		List<InputIngredient<ItemStack>> inputs = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			inputs.add(InputIngredient.of(i, units.get(i).pageSlot(), units.get(i).choices()));
		}
		return inputs;
	}

	@Override
	public Iterable<SlotAccessor> getInputSlots(Context context) {
		AbstractContainerMenu menu = context.getMenu();
		return fill(context).map(MachineFill::slots).orElse(List.of()).stream()
				.map(slot -> SlotAccessor.fromSlot(menu.getSlot(slot))).toList();
	}

	@Override
	public Iterable<SlotAccessor> getInventorySlots(Context context) {
		return inventorySlots(context.getMenu()).stream().map(SlotAccessor::fromSlot).toList();
	}

	private static List<Slot> inventorySlots(AbstractContainerMenu menu) {
		return menu.slots.subList(MachineBlockEntity.SIZE, MachineBlockEntity.SIZE + Inventory.INVENTORY_SIZE);
	}

	/** What to place in the open machine, or empty when REI asks outside a machine. */
	private static Optional<MachineFill> fill(Context context) {
		if (context.getDisplay() instanceof MachineDisplay display && context.getMenu() instanceof MachineMenu menu) {
			return Optional.of(MachineFill.of(display.data().recipe(), menu.moldSlot().getItem(),
					inventorySlots(menu).stream().map(Slot::getItem).toList()));
		}
		return Optional.empty();
	}

	/** Always one operation: REI multiplies a shift-click by the stack size, which overfills a slot listed twice. */
	@Override
	public Result handle(Context context) {
		return SimpleTransferHandler.super.handle(Context.create(context.isActuallyCrafting(), false,
				context.getContainerScreen(), context.getDisplay()));
	}
}
