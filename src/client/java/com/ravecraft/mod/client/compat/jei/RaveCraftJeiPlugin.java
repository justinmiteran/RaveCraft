package com.ravecraft.mod.client.compat.jei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.client.screen.machine.MachineScreen;
import com.ravecraft.mod.core.machine.MachineLayout;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModMenus;
import com.ravecraft.mod.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Shows the machine recipes in JEI, when JEI is installed: one page per machine, the machine as its catalyst, the
 * screen arrow opening the page and the "+" button filling the machine. Loaded only through the
 * {@code jei_mod_plugin} entrypoint, so nothing here runs without JEI.
 */
public final class RaveCraftJeiPlugin implements IModPlugin {
	private static final Identifier UID = RaveCraft.id("jei_plugin");
	private static final int INVENTORY_SLOTS = 36;

	private final Map<MachineKind, IRecipeHolderType<MachineRecipe>> types = ModRecipes.kinds().stream()
			.collect(Collectors.toMap(Function.identity(), kind -> IRecipeHolderType.create(kind.type())));

	@Override
	public Identifier getPluginUid() {
		return UID;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		for (MachineKind kind : ModRecipes.kinds()) {
			registration.addRecipeCategories(new MachineRecipeCategory(kind, types.get(kind),
					registration.getJeiHelpers().getGuiHelper()));
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		for (MachineKind kind : ModRecipes.kinds()) {
			registration.addRecipes(types.get(kind),
					List.copyOf(level.recipeAccess().getSynchronizedRecipes().getAllOfType(kind.type())));
		}
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (MachineKind kind : ModRecipes.kinds()) {
			registration.addCraftingStation(types.get(kind), kind.machineItem());
		}
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(MachineScreen.class, new ArrowArea());
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		for (MachineKind kind : ModRecipes.kinds()) {
			registration.addRecipeTransferHandler(new MachineTransferInfo(kind, types.get(kind)));
		}
	}

	/** The screen arrow, opening the page of the machine the screen belongs to. */
	private final class ArrowArea implements IGuiContainerHandler<MachineScreen> {
		@Override
		public Collection<IGuiClickableArea> getGuiClickableAreas(MachineScreen screen, double mouseX,
				double mouseY) {
			IRecipeType<?> type = types.get(screen.getMenu().kind());
			return List.of(IGuiClickableArea.createBasic(MachineLayout.ARROW_X, MachineLayout.ARROW_Y,
					MachineLayout.ARROW_WIDTH, MachineLayout.ARROW_HEIGHT, type));
		}
	}

	/**
	 * Fills a machine of one kind: its used inputs and its mold, from the player inventory.
	 *
	 * @param kind the machine kind
	 * @param type the JEI recipe type of the kind
	 */
	private record MachineTransferInfo(MachineKind kind, IRecipeHolderType<MachineRecipe> type)
			implements IRecipeTransferInfo<MachineMenu, RecipeHolder<MachineRecipe>> {
		@Override
		public Class<? extends MachineMenu> getContainerClass() {
			return MachineMenu.class;
		}

		@Override
		public Optional<MenuType<MachineMenu>> getMenuType() {
			return Optional.of(ModMenus.MACHINE);
		}

		@Override
		public IRecipeType<RecipeHolder<MachineRecipe>> getRecipeType() {
			return type;
		}

		@Override
		public boolean canHandle(MachineMenu menu, RecipeHolder<MachineRecipe> recipe) {
			return menu.kind() == kind;
		}

		@Override
		public List<Slot> getRecipeSlots(MachineMenu menu, RecipeHolder<MachineRecipe> recipe) {
			List<Slot> slots = new ArrayList<>(menu.inputSlots());
			if (kind.needsMold()) {
				slots.add(menu.moldSlot());
			}
			return slots;
		}

		@Override
		public List<Slot> getInventorySlots(MachineMenu menu, RecipeHolder<MachineRecipe> recipe) {
			return menu.slots.subList(MachineBlockEntity.SIZE, MachineBlockEntity.SIZE + INVENTORY_SLOTS);
		}
	}
}
