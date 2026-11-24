package com.ravecraft.mod.client.compat.jei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineEnergy;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The JEI page of one machine, laid out like the furnace page and like the machine screen: the counted inputs (and
 * the mold) on top, the energy bolt and the energy slot (every machine energy item) below them, the progress arrow, the result and the
 * operation time. Positions are those of {@link MachineMenu}, moved to the top left corner.
 */
final class MachineRecipeCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
	private static final int SHIFT_Y = MachineMenu.INPUT_Y - 1;
	private static final int HEIGHT = 54;
	private static final int OUTPUT_MARGIN = 21;
	private static final int ARROW_X = 89;
	private static final int ARROW_Y = 34;
	private static final int BOLT_X = 49;
	private static final int BOLT_Y = 36;
	private static final int BOLT_SIZE = 14;
	private static final int TIME_Y = 45;
	private static final int TIME_HEIGHT = 9;
	private static final float TICKS_PER_SECOND = 20F;
	private static final Identifier BOLT_SPRITE = RaveCraft.id("container/machine/energy");

	private final MachineKind kind;
	private final IGuiHelper guiHelper;
	private final int shiftX;

	/**
	 * Creates the page of a machine.
	 *
	 * @param kind the machine kind
	 * @param type the JEI recipe type of the kind
	 * @param guiHelper the JEI drawing helper
	 */
	MachineRecipeCategory(MachineKind kind, IRecipeHolderType<MachineRecipe> type, IGuiHelper guiHelper) {
		super(type, new ItemStack(kind.machineItem()).getHoverName(),
				guiHelper.createDrawableItemLike(kind.machineItem()), MachineMenu.OUTPUT_X - shiftX(kind)
						+ OUTPUT_MARGIN, HEIGHT);
		this.kind = kind;
		this.guiHelper = guiHelper;
		this.shiftX = shiftX(kind);
	}

	/** How far left the machine layout moves: its leftmost slot ends one pixel from the edge. */
	private static int shiftX(MachineKind kind) {
		return (kind.needsMold() ? MachineMenu.MOLD_X : MachineMenu.inputX(0, kind.inputs())) - 1;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
		MachineRecipe recipe = holder.value();
		List<CountedIngredient> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			builder.addInputSlot(MachineMenu.inputX(i, kind.inputs()) - shiftX, MachineMenu.INPUT_Y - SHIFT_Y)
					.addItemStacks(stacks(ingredients.get(i)))
					.setStandardSlotBackground();
		}
		recipe.mold().ifPresent(mold -> builder.addInputSlot(MachineMenu.MOLD_X - shiftX, MachineMenu.MOLD_Y - SHIFT_Y)
				.add(mold)
				.setStandardSlotBackground());
		builder.addSlot(RecipeIngredientRole.RENDER_ONLY, MachineMenu.ENERGY_X - shiftX, MachineMenu.ENERGY_Y - SHIFT_Y)
				.addItemStacks(energyItems())
				.setStandardSlotBackground()
				.addRichTooltipCallback((view, tooltip) -> view.getDisplayedItemStack().ifPresent(stack ->
						tooltip.add(Component.translatable("gui.ravecraft.jei.energy", operations(stack)))));
		builder.addOutputSlot(MachineMenu.OUTPUT_X - shiftX, MachineMenu.OUTPUT_Y - SHIFT_Y)
				.add(recipe.result())
				.setOutputSlotBackground();
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder,
			IFocusGroup focuses) {
		int time = holder.value().processingTime();
		int arrowX = ARROW_X - shiftX;
		builder.addDrawableWidget(guiHelper.createDrawableSprite(BOLT_SPRITE, BOLT_SIZE, BOLT_SIZE))
				.setPosition(BOLT_X - shiftX, BOLT_Y - SHIFT_Y);
		builder.addAnimatedRecipeArrowWidget(time).setPosition(arrowX, ARROW_Y - SHIFT_Y);
		builder.addText(Component.translatable("gui.ravecraft.jei.time", seconds(time)), getWidth() - arrowX,
				TIME_HEIGHT)
				.setPosition(arrowX, TIME_Y)
				.setTextAlignment(HorizontalAlignment.RIGHT)
				.setColor(0xFF808080);
	}

	/** Every item of the synchronised {@link MachineEnergy} recipes, in recipe order. */
	private static List<ItemStack> energyItems() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return List.of();
		}
		return level.recipeAccess().getSynchronizedRecipes().getAllOfType(MachineEnergy.TYPE).stream()
				.flatMap(holder -> holder.value().ingredient().items())
				.map(ItemStack::new).toList();
	}

	private static int operations(ItemStack stack) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 0 : MachineEnergy.operations(level, stack);
	}

	/** Every item the ingredient accepts, each at the counted amount. */
	private static List<ItemStack> stacks(CountedIngredient counted) {
		return counted.ingredient().items().map(item -> new ItemStack(item, counted.count())).toList();
	}

	/** The operation time in seconds, without a trailing zero. */
	private static String seconds(int ticks) {
		float value = ticks / TICKS_PER_SECOND;
		return value == Math.floor(value) ? Integer.toString((int) value) : Float.toString(value);
	}
}
