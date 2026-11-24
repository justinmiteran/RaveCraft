package com.ravecraft.mod.client.compat.jei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.compat.MachineEnergyView;
import com.ravecraft.mod.core.machine.MachineLayout;
import com.ravecraft.mod.core.machine.MachineRecipeLayout;
import com.ravecraft.mod.recipe.CountedIngredient;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The JEI page of one machine, laid out like the furnace page and like the machine screen: the counted inputs (and
 * the mold) on top, the energy bolt and the energy slot (every machine energy item) below them, the progress arrow, the result and the
 * operation time. Positions are those of {@link MachineRecipeLayout}.
 */
final class MachineRecipeCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
	private static final Identifier BOLT_SPRITE = RaveCraft.id("container/machine/energy");

	private final IGuiHelper guiHelper;
	private final MachineRecipeLayout layout;

	/**
	 * Creates the page of a machine.
	 *
	 * @param kind the machine kind
	 * @param type the JEI recipe type of the kind
	 * @param guiHelper the JEI drawing helper
	 */
	MachineRecipeCategory(MachineKind kind, IRecipeHolderType<MachineRecipe> type, IGuiHelper guiHelper) {
		super(type, new ItemStack(kind.machineItem()).getHoverName(),
				guiHelper.createDrawableItemLike(kind.machineItem()), layout(kind).width(),
				MachineRecipeLayout.HEIGHT);
		this.guiHelper = guiHelper;
		this.layout = layout(kind);
	}

	private static MachineRecipeLayout layout(MachineKind kind) {
		return new MachineRecipeLayout(kind.inputs(), kind.needsMold());
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
		MachineRecipe recipe = holder.value();
		List<CountedIngredient> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			builder.addInputSlot(layout.input(i).x(), layout.input(i).y())
					.addItemStacks(stacks(ingredients.get(i)))
					.setStandardSlotBackground();
		}
		recipe.mold().ifPresent(mold -> builder.addInputSlot(layout.moldSlot().x(), layout.moldSlot().y())
				.add(mold)
				.setStandardSlotBackground());
		builder.addSlot(RecipeIngredientRole.RENDER_ONLY, layout.energy().x(), layout.energy().y())
				.addItemStacks(MachineEnergyView.items())
				.setStandardSlotBackground()
				.addRichTooltipCallback((view, tooltip) -> view.getDisplayedItemStack().ifPresent(stack ->
						tooltip.add(Component.translatable("gui.ravecraft.jei.energy",
								MachineEnergyView.operations(stack)))));
		builder.addOutputSlot(layout.output().x(), layout.output().y())
				.add(recipe.result())
				.setOutputSlotBackground();
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder,
			IFocusGroup focuses) {
		int time = holder.value().processingTime();
		MachineRecipeLayout.Pos arrow = layout.arrow();
		builder.addDrawableWidget(guiHelper.createDrawableSprite(BOLT_SPRITE, MachineLayout.BOLT_SIZE,
				MachineLayout.BOLT_SIZE))
				.setPosition(layout.bolt().x(), layout.bolt().y());
		builder.addAnimatedRecipeArrowWidget(time).setPosition(arrow.x(), arrow.y());
		builder.addText(Component.translatable("gui.ravecraft.jei.time", MachineRecipeLayout.seconds(time)),
				getWidth() - arrow.x(), MachineRecipeLayout.TIME_HEIGHT)
				.setPosition(arrow.x(), MachineRecipeLayout.TIME_Y)
				.setTextAlignment(HorizontalAlignment.RIGHT)
				.setColor(0xFF808080);
	}

	/** Every item the ingredient accepts, each at the counted amount. */
	private static List<ItemStack> stacks(CountedIngredient counted) {
		return counted.ingredient().items().map(item -> new ItemStack(item, counted.count())).toList();
	}
}
