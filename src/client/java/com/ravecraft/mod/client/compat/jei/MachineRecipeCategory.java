package com.ravecraft.mod.client.compat.jei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The JEI page of one machine: the counted inputs where the machine shows them, the mold, the energy bolt, the
 * progress arrow, the result and the operation time. Positions follow {@link MachineMenu}, shifted left.
 */
final class MachineRecipeCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
	private static final int SHIFT_X = 22;
	private static final int WIDTH = 124;
	private static final int HEIGHT = 72;
	private static final int ARROW_X = 67;
	private static final int ARROW_Y = 34;
	private static final int BOLT_X = 27;
	private static final int BOLT_Y = 37;
	private static final int BOLT_SIZE = 14;
	private static final int TIME_X = 64;
	private static final int TIME_Y = 60;
	private static final int TIME_WIDTH = 60;
	private static final int TIME_HEIGHT = 10;
	private static final float TICKS_PER_SECOND = 20F;
	private static final Identifier BOLT_SPRITE = RaveCraft.id("container/machine/energy");

	private final MachineKind kind;
	private final IGuiHelper guiHelper;

	/**
	 * Creates the page of a machine.
	 *
	 * @param kind the machine kind
	 * @param type the JEI recipe type of the kind
	 * @param guiHelper the JEI drawing helper
	 */
	MachineRecipeCategory(MachineKind kind, IRecipeHolderType<MachineRecipe> type, IGuiHelper guiHelper) {
		super(type, new ItemStack(kind.machineItem()).getHoverName(),
				guiHelper.createDrawableItemLike(kind.machineItem()), WIDTH, HEIGHT);
		this.kind = kind;
		this.guiHelper = guiHelper;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
		MachineRecipe recipe = holder.value();
		List<CountedIngredient> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			builder.addInputSlot(MachineMenu.inputX(i, kind.inputs()) - SHIFT_X, MachineMenu.INPUT_Y)
					.addItemStacks(stacks(ingredients.get(i)))
					.setStandardSlotBackground();
		}
		recipe.mold().ifPresent(mold -> builder.addInputSlot(MachineMenu.MOLD_X - SHIFT_X, MachineMenu.MOLD_Y)
				.add(mold)
				.setStandardSlotBackground());
		builder.addOutputSlot(MachineMenu.OUTPUT_X - SHIFT_X, MachineMenu.OUTPUT_Y)
				.add(recipe.result())
				.setOutputSlotBackground();
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder,
			IFocusGroup focuses) {
		int time = holder.value().processingTime();
		builder.addDrawableWidget(guiHelper.createDrawableSprite(BOLT_SPRITE, BOLT_SIZE, BOLT_SIZE))
				.setPosition(BOLT_X, BOLT_Y);
		builder.addAnimatedRecipeArrowWidget(time).setPosition(ARROW_X, ARROW_Y);
		builder.addText(Component.translatable("gui.ravecraft.jei.time", seconds(time)), TIME_WIDTH, TIME_HEIGHT)
				.setPosition(TIME_X, TIME_Y);
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
