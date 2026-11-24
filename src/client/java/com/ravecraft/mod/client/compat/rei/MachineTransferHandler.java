package com.ravecraft.mod.client.compat.rei;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.compat.rei.MachineDisplay;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.MachineKind;
import java.util.List;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Fills a machine of one kind from the player inventory. REI puts one item of ingredient <i>i</i> in slot <i>i</i> of
 * the list it is given, so a slot is listed once per item it takes.
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
	public Iterable<SlotAccessor> getInputSlots(Context context) {
		if (!(context.getDisplay() instanceof MachineDisplay display)) {
			return List.of();
		}
		AbstractContainerMenu menu = context.getMenu();
		return display.data().unitSlots().stream().map(slot -> SlotAccessor.fromSlot(menu.getSlot(slot))).toList();
	}

	@Override
	public Iterable<SlotAccessor> getInventorySlots(Context context) {
		return context.getMenu().slots
				.subList(MachineBlockEntity.SIZE, MachineBlockEntity.SIZE + Inventory.INVENTORY_SIZE).stream()
				.map(SlotAccessor::fromSlot).toList();
	}

	/** Always one operation: REI multiplies a shift-click by the stack size, which overfills a slot listed twice. */
	@Override
	public Result handle(Context context) {
		return SimpleTransferHandler.super.handle(Context.create(context.isActuallyCrafting(), false,
				context.getContainerScreen(), context.getDisplay()));
	}
}
